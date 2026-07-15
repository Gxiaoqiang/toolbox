export const meta = {
  name: 'code-review',
  description: '多维度并行代码审查: Java 分层架构 + Vue 组件规范 + 安全漏洞扫描',
  whenToUse: '提交代码前、PR Review、或完成一个功能后做质量检查',
  phases: [
    { title: '并行审查', detail: 'Java / Vue / 安全 三个维度同时审查' },
    { title: '汇总', detail: '合并结果，按严重程度排序，给出合并建议' }
  ],
}

const REVIEW_SCHEMA = {
  type: 'object',
  properties: {
    findings: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          file: { type: 'string', description: '问题所在文件路径' },
          line: { type: 'number', description: '行号（无法确定时填 0）' },
          severity: { type: 'string', enum: ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] },
          category: { type: 'string', description: '分类: bug / security / style / perf / maintainability' },
          title: { type: 'string', description: '一句话问题描述' },
          detail: { type: 'string', description: '详细说明 + 修复建议 + 代码示例' }
        },
        required: ['file', 'severity', 'title', 'detail']
      }
    }
  },
  required: ['findings']
}

// args: { projectRoot?: string, baseRef?: string }

const projectRoot = args?.projectRoot || '/Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox'
const baseRef = args?.baseRef || 'HEAD'

// =============================================================================
// Phase 1: 并行审查
// =============================================================================
phase('并行审查')
log(`🔍 三路并行审查 (base: ${baseRef})...`)

const reviews = await parallel([
  // Java 审查
  () => agent(
    `审查本次改动中的 Java 代码。

## 审查标准
请先阅读 ${projectRoot}/CLAUDE.md 中的后端规范（阿里巴巴 Java 嵩山版），然后逐条检查:

1. **代码分层** — Controller(薄层,只做校验+委托) → Service(接口) → ServiceImpl(核心逻辑)。是否有越层调用？
2. **注释规范** — 类注释含 @Author @Version @Description；方法注释说明实现过程（参考 CLAUDE.md 2.3 节）
3. **异常处理** — 使用 BusinessException + ErrorCodeEnum；禁止吞异常；禁止 catch 后只打日志不处理
4. **日志规范** — SLF4J；格式 [ClassName#methodName]；英文描述（参考 CLAUDE.md 2.5 节）
5. **R 响应体** — Controller 返回 R<T>；错误码通过 ErrorCodeEnum
6. **命名规范** — 类 PascalCase、方法 camelCase、常量 UPPER_SNAKE_CASE
7. **不可变** — 禁止直接修改传入参数
8. **魔法值** — 禁止硬编码数字/字符串（用常量或枚举）
9. **文件大小** — 单文件 ≤800 行，方法 ≤50 行

## 获取改动
运行: cd ${projectRoot} && git diff ${baseRef} --name-only
只审查 .java 文件。

## 输出
使用 StructuredOutput 返回所有发现的问题。不确定的问题标为 LOW。`,

    { label: 'java-review', phase: '并行审查', schema: REVIEW_SCHEMA }
  ),

  // Vue 审查
  () => agent(
    `审查本次改动中的 Vue/TypeScript 代码。

## 审查标准
请先阅读 ${projectRoot}/CLAUDE.md 中的前端规范，然后逐条检查:

1. **meta 导出** — 必须用独立 <script lang="ts"> 块 + export const meta（参考 HANDOFF.md 第四节第1条）
2. **CSS 变量** — 使用 var(--bg-main) 等自定义属性；禁止硬编码颜色（参考 CLAUDE.md 主题系统节）
3. **defineOptions/defineExpose** — inheritAttrs: false + expose({ meta })
4. **Composition API** — ref/reactive/computed 使用正确，无响应性丢失
5. **状态机** — 文件类工具: idle → ready → processing → done/error
6. **布局约定** — 文件类工具: 左输入 | 中操作 | 右结果
7. **动画属性** — 只用 transform/opacity 等 compositor-friendly 属性（参考 ECC web/performance.md）
8. **console.log** — 禁止在最终代码中使用
9. **TypeScript 类型** — 公共 API 有显式类型，避免 any

## 获取改动
运行: cd ${projectRoot} && git diff ${baseRef} --name-only
只审查 .vue/.ts/.js 文件。

## 输出
使用 StructuredOutput 返回所有发现的问题。`,

    { label: 'vue-review', phase: '并行审查', schema: REVIEW_SCHEMA }
  ),

  // 安全审查
  () => agent(
    `审查本次改动中的安全问题。

## 审查标准
1. **密钥泄露** (CRITICAL) — 硬编码密码/Token/API Key/私钥
2. **路径遍历** (HIGH) — 文件操作未校验 ../ 越权
3. **文件上传** (HIGH) — 缺少类型/大小/数量校验
4. **命令注入** (CRITICAL) — 用户输入拼接到系统命令（如 soffice --headless）
5. **日志泄露** (MEDIUM) — 日志打印敏感信息（密码、token、身份证号）
6. **输入校验** (HIGH) — Controller 参数缺少 @Valid/@NotNull 等校验注解
7. **CSRF/CORS** (MEDIUM) — 过于宽松的跨域配置

## 获取改动
运行: cd ${projectRoot} && git diff ${baseRef} --name-only
审查所有变更文件。

## 输出
使用 StructuredOutput 返回所有安全问题。CRITICAL 级别必须阻止合并。`,

    { label: 'security-review', phase: '并行审查', schema: REVIEW_SCHEMA }
  )
])

// =============================================================================
// Phase 2: 汇总
// =============================================================================
phase('汇总')
log('📊 汇总审查结果...')

const allFindings = reviews
  .filter(Boolean)
  .flatMap(r => r.findings || [])
  .sort((a, b) => {
    const order = { CRITICAL: 0, HIGH: 1, MEDIUM: 2, LOW: 3 }
    return (order[a.severity] ?? 99) - (order[b.severity] ?? 99)
  })

const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 }
allFindings.forEach(f => { if (counts[f.severity] !== undefined) counts[f.severity]++ })

log(`审查完成: ${allFindings.length} 个问题`)
log(`  CRITICAL: ${counts.CRITICAL} | HIGH: ${counts.HIGH} | MEDIUM: ${counts.MEDIUM} | LOW: ${counts.LOW}`)

let verdict, emoji
if (counts.CRITICAL > 0) {
  verdict = 'BLOCK'
  emoji = '🚫'
  log('🚫 存在 CRITICAL 问题，必须修复后才能合并！')
} else if (counts.HIGH > 0) {
  verdict = 'WARN'
  emoji = '⚠️'
  log('⚠️ 存在 HIGH 问题，建议修复后再合并')
} else if (counts.MEDIUM > 0) {
  verdict = 'APPROVE_WITH_COMMENTS'
  emoji = '💬'
  log('💬 仅有 MEDIUM/LOW 问题，可以合并')
} else {
  verdict = 'APPROVE'
  emoji = '✅'
  log('✅ 未发现问题，代码质量良好')
}

return {
  verdict,
  emoji,
  summary: { total: allFindings.length, ...counts },
  findings: allFindings
}
