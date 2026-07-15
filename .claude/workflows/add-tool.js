export const meta = {
  name: 'add-tool',
  description: '新增工具箱工具 — 需求澄清 → PRD → 技术方案 → 实施',
  phases: [
    { title: '需求澄清', detail: 'brainstorming 搞清楚做什么、边界、验收标准' },
    { title: 'PRD 文档', detail: '输出产品需求文档到 docs/superpowers/specs/' },
    { title: '技术方案', detail: '确定文件清单、后端需求、依赖变更' },
    { title: '实施', detail: 'Scaffold 前端 → 后端骨架 → 注册验证 → 测试' }
  ],
}

// args: { toolName: string, description?: string, category?: string }

const toolName = args?.toolName
const description = args?.description || ''
const category = args?.category || 'file'

if (!toolName) {
  log('用法: /workflow add-tool --args \'{"toolName": "pdf-to-word", "description": "PDF 转 Word 文档", "category": "file"}\'')
  throw new Error('缺少必填参数 toolName')
}

const projectRoot = '/Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox'

// ========== Phase 1: 需求澄清 ==========
phase('需求澄清')

log(`开始需求澄清: ${toolName} — ${description}`)

const clarify = await agent(
  `对即将开发的工具箱工具进行需求澄清。

## 工具信息
- 工具 ID: ${toolName}
- 一句话描述: ${description}
- 预设分类: ${category}

## 任务
1. 阅读 ${projectRoot}/CLAUDE.md 了解项目规约，确认这个工具应该属于什么分类(@see frontend/src/tools/types.ts: ToolMeta.category)
2. 阅读 ${projectRoot}/HANDOFF.md 了解已有工具和避坑指南
3. 检查 frontend/src/tools/ 下是否有类似工具可参考
4. 输出结构化的需求摘要:
   - 核心功能：做什么、不做什么
   - 输入/输出：文件格式、数量限制、大小限制
   - 是否需要后端支持（requiresBackend）
   - 验收标准：功能正常 + 测试通过
   - UI 布局倾向：三栏（文件类）还是单页（数据类）
   - 参考哪个现有工具的风格最接近

## 输出格式
直接用中文文本输出，每条一行，不需要代码。`,

  { label: '需求澄清', phase: '需求澄清' }
)

log('需求澄清完成')

// ========== Phase 2: PRD 文档 ==========
phase('PRD 文档')

const today = new Date().toISOString().slice(0, 10)
const prdPath = `docs/superpowers/specs/${today}-${toolName}-prd.md`

log(`编写 PRD: ${prdPath}`)

const prd = await agent(
  `编写产品需求文档，保存到 ${projectRoot}/${prdPath}。

## 需求摘要
${clarify}

## 任务
参考 ${projectRoot}/docs/superpowers/specs/ 下已有的 PRD 文档格式，编写完整的 PRD：

必须包含以下章节:
1. **概述** — 工具用途、目标用户、使用场景
2. **功能需求** — 按优先级排列的功能点
3. **用户交互流程** — 从进入页面到完成操作的完整流程、以及各个细节点
4. **UI 设计** — 布局结构（三栏/单页）、组件层级、状态机
5. **后端 API 需求** — 如果需要后端，列出 API 路径、请求/响应格式
6. **校验规则** — 文件格式、大小、数量限制
7. **验收标准** — 功能验收 + 测试验收

## 重要约束
- 前端必须使用 CSS 自定义属性（var(--bg-main) 等），禁止硬编码颜色
- Vue 组件需要 defineOptions({ inheritAttrs: false }) 和 export const meta
- 后端遵循阿里巴巴 Java 规约分层

保存完成后，报告文件路径和文档行数。`,

  { label: '编写 PRD', phase: 'PRD 文档' }
)

// ========== Phase 3: 技术方案 ==========
phase('技术方案')

const planPath = `docs/superpowers/plans/${today}-${toolName}-plan.md`

log(`编写技术方案: ${planPath}`)

const plan = await agent(
  `编写技术实现方案，保存到 ${projectRoot}/${planPath}。

## PRD 文档
${prd}

## 任务
参考 ${projectRoot}/docs/superpowers/plans/ 下已有的实现方案格式，输出：

1. **文件变更清单**（精确到每个文件）
   - 新增文件: frontend/src/tools/${toolName}/index.vue, ...
   - 修改文件: frontend/src/tools/registry.ts (自动扫描，通常不需要改), ...
   - 后端文件（如需要）: controller/..., service/...

2. **前端组件设计**
   - 状态机: idle → ready → processing → done/error
   - 布局结构: 三栏（左输入 | 中操作 | 右结果）或其他
   - 复用模式: 从哪个现有工具参考（pdf-merge, doc-to-pdf 等）

3. **后端设计（如需要）**
   - Controller 路径和参数
   - Service 接口方法签名
   - 错误码（ErrorCodeEnum 新增项）
   - 测试用例数量预估

4. **依赖变更**
   - 前端是否需要新 npm 包
   - 后端是否需要新 Maven 依赖

5. **实施顺序**
   - 后端先还是前端先？
   - 每一步的验证方式

保存完成后，报告文件路径和行数。`,

  { label: '编写技术方案', phase: '技术方案' }
)

// ========== Phase 4: 实施 ==========
phase('实施')

log('开始实施，按照技术方案执行...')

// 4a. 前端 scaffold
const scaffold = await agent(
  `创建前端工具组件。

## 技术方案
${plan}

## 任务
1. 创建 ${projectRoot}/frontend/src/tools/${toolName}/index.vue
2. 参考 ${projectRoot}/frontend/src/tools/pdf-merge/index.vue 的代码风格和结构
3. 必须包含:
   - <script lang="ts"> 块: import type { ToolMeta } from '@/tools/types'; export const meta: ToolMeta = {...}
   - <script setup lang="ts"> 块: 组件逻辑
   - <template> 块: 使用 CSS 自定义属性（var(--bg-main) 等）
   - defineOptions({ inheritAttrs: false })
   - defineExpose({ meta })
4. 遵循侧边栏样式规范（memory/sidebar-menu-pattern.md）中定义的缩进层级
5. 如果是文件类工具且需要后端: 添加 requiresBackend: true 到 meta

创建完成后报告文件路径和行数。`,

  { label: '前端组件', phase: '实施' }
)

// 4b. 后端（如需要）
const backend = await agent(
  `根据技术方案判断是否需要后端，如果需要则实施。

## 技术方案
${plan}

## 前端组件已创建
${scaffold}

## 任务
如果需要后端:
1. 在 ${projectRoot}/backend/src/main/java/com/toolbox/controller/ 创建 Controller
2. 在 ${projectRoot}/backend/src/main/java/com/toolbox/service/ 创建 Service 接口和 Impl
3. 如需要新错误码，在 ErrorCodeEnum 中添加
4. 创建单元测试（至少 3 个用例）
5. 运行 mvn test 确认通过

如果不需要后端:
报告 "此工具为纯前端工具，无需后端支持" 并说明原因。

报告创建的文件清单和测试结果。`,

  { label: '后端实施', phase: '实施' }
)

// 4c. 验证
const verify = await agent(
  `最终验证。

## 已完成
- 前端组件: ${scaffold}
- 后端: ${backend}

## 任务
1. 确认 import.meta.glob 自动扫描能发现新工具（检查 registry.ts 的 glob 模式是否覆盖）
2. 确认前端构建通过: cd ${projectRoot}/frontend && npm run build
3. 确认后端测试通过: cd ${projectRoot}/backend && mvn test
4. 确认新工具出现在侧边栏菜单中（正确的分类和分组）

报告验证结果: 通过/失败 + 具体输出。`,

  { label: '验证', phase: '实施' }
)

return {
  toolName,
  phases: {
    clarify,
    prd: prdPath,
    plan: planPath,
    scaffold,
    backend,
    verify
  }
}
