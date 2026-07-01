# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# 前端开发 (port 3000)
cd frontend && npm install && npm run dev

# 前端构建 (输出到 backend/src/main/resources/static/)
cd frontend && npm run build

# 后端编译
cd backend && mvn compile

# 打包 (含前端静态资源)
cd backend && mvn clean package -DskipTests

# 启动 (port 8899)
cd backend && java -jar target/toolbox-1.0.0.jar
```

## Architecture

```
frontend/src/tools/          # 约定式工具组件 — 加文件夹即注册
frontend/src/tools/registry.ts  # import.meta.glob 自动扫描
frontend/src/tools/types.ts     # ToolMeta 接口 (id/name/category/group)
frontend/src/layouts/           # 主布局 (可收缩侧边栏 + 内容区)
frontend/src/composables/       # useClipboard / useToast
backend/.../controller/convert/ # POST /api/convert/md-to-docx
backend/.../model/common/R.java # 统一响应体 {code, message, data}
```

**新增工具:** 在 `frontend/src/tools/<id>/index.vue` 创建组件，导出 `meta: ToolMeta` 即可 — 路由和菜单自动生成。

**后端规范:** 阿里巴巴 Java 嵩山版 — 分层 controller→service, R<T> 统一响应, SLF4J 日志, Javadoc 含 @author/@since。

---

## Superpowers + ECC 协同强制规范

### 场景 A：完整业务开发/模块新增/重构

执行 Superpowers 七段式流程: brainstorming → writing-plans → tdd → subagent执行 → 两轮review → finalize

每阶段联动 ECC:
1. brainstorming → 检索项目存量代码，避免重复造轮子
2. writing-plans → 扫描目标目录，生成精准文件修改清单
3. TDD编码 → 子代理统一用批量编辑，禁止单文件零散修改
4. 评审 → SP 架构评审 + ECC 安全扫描 + 代码格式化
5. finalize → ECC 统一格式化，标准化 Git 提交文案

### 场景 B：轻量操作（单文件/查找替换/审计）

直接独立使用 ECC 快捷指令，无需启动 SP 完整流程。

### 优先级

1. SP 流程调度指令 > ECC 工具指令
2. 禁止并行启动 SP + ECC 独立流程
3. 子代理仅允许调用 ECC 文件工具，屏蔽原生文件读写
4. 同一会话只允许一套主线流程运行
5. 大型项目分段执行，限定业务模块目录，不扫描全项目根目录
