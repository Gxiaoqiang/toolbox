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

# 后端测试
cd backend && mvn test

# 打包 (含前端静态资源)
cd backend && mvn clean package -DskipTests

# 启动 (port 8899)
cd backend && java -jar target/toolbox-1.0.0.jar

# Docker 部署
docker build -t toolbox:1.0.0 .
docker run -d -p 8899:8899 --name toolbox toolbox:1.0.0
```

## Architecture

```
frontend/src/tools/          # 约定式工具组件 — 加文件夹即注册
frontend/src/tools/registry.ts  # import.meta.glob 自动扫描
frontend/src/tools/types.ts     # ToolMeta 接口 (id/name/category/group)
frontend/src/layouts/           # 主布局 (可收缩侧边栏 + 内容区)
frontend/src/composables/       # useClipboard / useToast / useTheme
backend/.../controller/convert/ # POST /api/convert/md-to-docx
backend/.../model/common/R.java # 统一响应体 {code, message, data}
```

**新增工具:** 在 `frontend/src/tools/<id>/index.vue` 创建组件，导出 `meta: ToolMeta` 即可 — 路由和菜单自动生成。

### 前端关键设计

- **约定式路由**: `import.meta.glob` 扫描 `tools/*/index.vue`，路由由 `router/index.ts` 中的 `beforeEach` 守卫懒加载注册表后，通过 `ToolPage.vue` 动态渲染。
- **Hash 路由**: `createWebHashHistory()` — 兼容 Spring Boot 单 JAR 部署，前端路由不会与后端路径冲突。
- **无 dev proxy**: 前端目前是纯客户端工具集，仅 md-to-docx 需要后端。开发时如调用后端 API，需在 `vite.config.ts` 中配置 `server.proxy`。
- **路径别名**: `@` → `frontend/src/`

### 工具组件约定

每个 `tools/<id>/index.vue` 必须：

```typescript
defineOptions({ inheritAttrs: false })
const meta: ToolMeta = { id: '...', name: '...', description: '...', icon: '...', category: '...' }
defineExpose({ meta })
```

- `id` 自动取自文件夹名（registry 会覆盖 meta 中的 id）
- `category`: `'document'` | `'develop'` | `'data'`
- `group`: 可选，同一分类下相同 group 的工具聚合展示
- `requiresBackend`: 可选，标记需要后端支持（菜单中显示琥珀色圆点）

### 主题系统

5 套主题通过 CSS 自定义属性切换，`document.documentElement.dataset.theme` 控制：

| 主题 | 选择器 |
|------|--------|
| 默认白 | `:root`, `[data-theme="default"]` |
| 护眼绿 | `[data-theme="green"]` |
| 暖色奶油 | `[data-theme="warm"]` |
| 深色暗夜 | `[data-theme="dark"]` |
| 浅灰柔白 | `[data-theme="gray"]` |

所有组件使用 `var(--bg-main)`, `var(--text-primary)`, `var(--border-color)`, `var(--accent-color)` 等变量，**禁止硬编码颜色**。

`useTheme()` composable 管理状态，localStorage 持久化用户选择。

### Composable 概览

| Composable | 特点 |
|------------|------|
| `useClipboard` | `navigator.clipboard.writeText` + `document.execCommand('copy')` 降级 |
| `useToast` | 全局单例（模块级 `toasts` ref），自动消失 |
| `useTheme` | 全局单例，5 套主题，localStorage 持久化 |

### 前端依赖

| 包 | 用途 |
|----|------|
| `marked` | Markdown GFM 渲染（md-toolbox） |
| `js-yaml` | YAML ↔ JSON 互转（yaml-formatter） |
| `spark-md5` | 浏览器端 MD5 哈希（hash-generator） |
| `vue-router` | Hash 模式路由 |
| `tailwindcss` v4 | 工具类 + Vite 插件 |

## 后端规范

**阿里巴巴 Java 嵩山版** — 分层 controller→service, R<T> 统一响应, SLF4J 日志, Javadoc 含 @author/@since。

### 异常体系

```
GlobalExceptionHandler (@RestControllerAdvice)
  └── BusinessException(code, message)
        └── ErrorCodeEnum (错误码枚举)
```

所有业务异常抛出 `BusinessException`，由 `GlobalExceptionHandler` 统一捕获为 `R` 格式返回。

### 文件上传

`application.yml` 中配置 `spring.servlet.multipart.max-file-size: 50MB`。

### 后端依赖

| 包 | 用途 |
|----|------|
| `flexmark-all` | Markdown → HTML（服务端） |
| `docx4j-JAXB-ReferenceImpl` | AltChunk 方式嵌入 HTML 生成 DOCX |

### API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/convert/md-to-docx` | Markdown 转 DOCX 文件下载（multipart） |

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
