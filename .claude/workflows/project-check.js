export const meta = {
  name: 'project-check',
  description: '项目健康检查 — 依赖版本、构建状态、测试覆盖率、Git 状态全量诊断',
  whenToUse: '切换分支后验证环境、排查奇怪问题、或定期项目体检',
  phases: [
    { title: '环境检查', detail: 'Node/Maven/Java 版本 + 依赖安装状态' },
    { title: '构建检查', detail: '前端 typecheck + 后端 compile' },
    { title: '测试检查', detail: '运行测试并报告结果' },
    { title: 'Git 状态', detail: '未提交文件、分支状态、远程同步' }
  ],
}

// args: { projectRoot?: string }

const projectRoot = args?.projectRoot || '/Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox'

const results = {}

// =============================================================================
// Phase 1: 环境检查
// =============================================================================
phase('环境检查')
log('🔍 检查开发环境...')

const env = await agent(
  `检查开发环境是否就绪。

## 任务
依次执行以下命令并报告结果:
1. node --version (预期 ≥ 18)
2. npm --version
3. java --version (预期 17)
4. mvn --version
5. ls ${projectRoot}/frontend/node_modules/ 是否存在 (依赖已安装?)
6. ls ${projectRoot}/backend/target/ 是否存在 (至少编译过一次?)

## 输出格式
```
Node: vXX.X.X ✅/❌
npm: X.X.X ✅
Java: XX.X.X ✅/❌ (需要 JDK 17)
Maven: X.X.X ✅
前端依赖: 已安装/未安装 ⚠️
后端编译产物: 存在/不存在 ⚠️
```》,

  { label: '环境信息', phase: '环境检查' }
)

results.env = env
log('环境检查完成')

// =============================================================================
// Phase 2: 构建检查（并行）
// =============================================================================
phase('构建检查')
log('🔧 并行检查前端类型 + 后端编译...')

const [frontendBuild, backendBuild] = await parallel([
  () => agent(
    `检查前端构建。

## 任务
1. cd ${projectRoot}/frontend
2. npx vue-tsc --noEmit 2>&1 | tail -20 (类型检查)
3. npm run build 2>&1 | tail -10 (快速验证构建)
4. 报告: 类型检查通过/失败 + 构建通过/失败 + 前端 bundle 总大小

简洁输出，不需要完整日志。`,

    { label: '前端检查', phase: '构建检查' }
  ),

  () => agent(
    `检查后端编译。

## 任务
1. cd ${projectRoot}/backend
2. mvn compile -q 2>&1 | tail -10
3. mvn test -q 2>&1 | grep -E "(Tests run|BUILD)" | tail -5
4. 报告: 编译通过/失败 + 测试数量 + 失败数

简洁输出，不需要完整日志。`,

    { label: '后端检查', phase: '构建检查' }
  )
])

results.frontend = frontendBuild
results.backend = backendBuild

const buildOk = frontendBuild && !frontendBuild.includes('error') &&
                backendBuild && !backendBuild.includes('FAILURE')

log(buildOk ? '✅ 构建检查通过' : '❌ 构建检查发现问题')

// =============================================================================
// Phase 3: Git 状态
// =============================================================================
phase('Git 状态')
log('📋 检查 Git 状态...')

const gitStatus = await agent(
  `检查 Git 仓库状态。

## 任务
1. cd ${projectRoot}
2. git status --short (未提交变更)
3. git branch --show-current (当前分支)
4. git log --oneline -5 (最近 5 次提交)
5. git stash list (是否有暂存)

## 输出格式
```
分支: main
未提交文件: X 个
最近提交:
  abc1234 feat: xxx
  def5678 fix: xxx
暂存: 无 / X 个
状态: 干净 ✅ / 有未提交文件 ⚠️
```》,

  { label: 'Git 状态', phase: 'Git 状态' }
)

results.git = gitStatus

// =============================================================================
// 汇总报告
// =============================================================================
log('')
log('═══════════════════════════════════')
log('  Toolbox 项目健康检查报告')
log('═══════════════════════════════════')
log('')
log(`环境: ${results.env ? '已检查' : '未检查'}`)
log(`构建: ${buildOk ? '✅ 通过' : '❌ 有问题'}`)
log(`Git: ${gitStatus ? '已检查' : '未检查'}`)
log('')
log('详细报告见上方各阶段输出。')
log('═══════════════════════════════════')

return {
  healthy: buildOk,
  results
}
