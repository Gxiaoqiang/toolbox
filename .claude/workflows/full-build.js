export const meta = {
  name: 'full-build',
  description: '全量构建验证: 前端构建 → 后端打包 → 启动 + 健康检查（三步顺序验证）',
  whenToUse: '提交前验证整体构建是否通过，或部署前确认打包产物可用',
  phases: [
    { title: '前端构建', detail: 'npm run build → 验证 static/ 产物更新' },
    { title: '后端打包', detail: 'mvn clean package -DskipTests' },
    { title: '启动验证', detail: '启动 JAR → HTTP 健康检查 → 停止' }
  ],
}

// args: { projectRoot?: string, skipFrontend?: boolean, skipBackend?: boolean, skipSmoke?: boolean }

const projectRoot = args?.projectRoot || '/Users/xiaoqiang/Desktop/GWQ/project/toolbox/toolbox'
const skipFrontend = args?.skipFrontend || false
const skipBackend = args?.skipBackend || false
const skipSmoke = args?.skipSmoke || false

let frontendOk = skipFrontend
let backendOk = skipBackend
let smokeOk = skipSmoke

// =============================================================================
// Phase 1: 前端构建
// =============================================================================
if (!skipFrontend) {
  phase('前端构建')
  log('📦 前端构建中...')

  const frontend = await agent(
    `执行前端构建。

## 任务
1. cd ${projectRoot}/frontend && npm run build
2. 检查 ${projectRoot}/backend/src/main/resources/static/ 下 index.html 是否更新
3. 列出 static/assets/ 下所有新生成的文件名（hash 变更）
4. 报告: 构建成功/失败 + 产物数量

如果失败，报告完整错误信息并标记为 BUILD_FAILED。`,

    { label: 'npm-build', phase: '前端构建' }
  )

  frontendOk = frontend && !frontend.includes('BUILD_FAILED') && !frontend.includes('error')
  log(frontendOk ? '✅ 前端构建成功' : '❌ 前端构建失败')
} else {
  log('⏭️ 跳过前端构建')
}

// =============================================================================
// Phase 2: 后端打包
// =============================================================================
if (!skipBackend && frontendOk) {
  phase('后端打包')
  log('🔧 后端打包中...')

  const backend = await agent(
    `执行后端打包。

## 任务
1. cd ${projectRoot}/backend && mvn clean package -DskipTests
2. 验证 ${projectRoot}/backend/target/toolbox-1.0.0.jar 存在
3. 报告 jar 文件大小（MB）
4. 报告: 打包成功/失败

如果失败，报告完整错误信息并标记为 PACKAGE_FAILED。`,

    { label: 'mvn-package', phase: '后端打包' }
  )

  backendOk = backend && !backend.includes('PACKAGE_FAILED') && !backend.includes('BUILD FAILURE')
  log(backendOk ? '✅ 后端打包成功' : '❌ 后端打包失败')
} else if (!frontendOk) {
  log('⏭️ 跳过后端打包（前端构建未通过）')
}

// =============================================================================
// Phase 3: 启动验证
// =============================================================================
if (!skipSmoke && backendOk) {
  phase('启动验证')
  log('🚀 启动验证中...')

  const verify = await agent(
    `启动应用并执行健康检查。

## 任务
1. 停止旧实例: pkill -f "toolbox-1.0.0.jar" 2>/dev/null; sleep 1
2. 启动: cd ${projectRoot}/backend && nohup java -jar target/toolbox-1.0.0.jar > /tmp/toolbox.log 2>&1 &
3. 等待 10 秒让 Spring Boot 完全启动
4. 健康检查:
   - 首页: curl -s -o /dev/null -w "%{http_code}" http://localhost:8899/
   - API: curl -s -o /dev/null -w "%{http_code}" http://localhost:8899/api/pdf/split
5. 停止: pkill -f "toolbox-1.0.0.jar"
6. 报告: 首页 HTTP 状态码 + API HTTP 状态码 + 是否正常

## 预期结果
- 首页: HTTP 200
- API: HTTP 400（路由可达，缺少参数）
- HTTP 000 = 应用未启动 → 检查 /tmp/toolbox.log`,

    { label: 'smoke-test', phase: '启动验证' }
  )

  smokeOk = verify && (verify.includes('200') || verify.includes('400'))
  log(smokeOk ? '✅ 启动验证通过' : '❌ 启动验证失败')
} else if (!backendOk) {
  log('⏭️ 跳过启动验证（后端打包未通过）')
}

// =============================================================================
// 汇总
// =============================================================================
const allOk = frontendOk && backendOk && smokeOk

log('')
log('═══════════════════════════════════')
log(`  构建结果: ${allOk ? '✅ 全部通过' : '❌ 存在问题'}`)
log(`  前端: ${frontendOk ? '✅' : skipFrontend ? '⏭️' : '❌'}`)
log(`  后端: ${backendOk ? '✅' : skipBackend ? '⏭️' : '❌'}`)
log(`  启动: ${smokeOk ? '✅' : skipSmoke ? '⏭️' : '❌'}`)
log('═══════════════════════════════════')

return {
  success: allOk,
  stages: { frontend: frontendOk, backend: backendOk, smoke: smokeOk }
}
