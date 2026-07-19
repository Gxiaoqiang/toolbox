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

# Docker 部署 (含 LibreOffice + 中文字体)
docker build -t toolbox-lo:1.0.0 .

# 导出镜像供离线服务器使用
docker save -o toolbox-lo-1.0.0.tar toolbox-lo:1.0.0
gzip toolbox-lo-1.0.0.tar

# 离线服务器导入并启动
docker load -i toolbox-lo-1.0.0.tar
docker run -d --name toolbox -p 8899:8899 --restart unless-stopped toolbox-lo:1.0.0
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
| `pdfbox` 3.0.3 | PDF 切分、合并、元数据处理 |

### LibreOffice 配置

`DocumentServiceImpl` 通过 `@Value("${toolbox.libreoffice.binary-path:soffice}")` 读取 soffice 路径。Docker 镜像基于 `eclipse-temurin:17-jre-jammy`（Ubuntu 22.04），已内置 `libreoffice-writer` + 中文字体（Noto CJK + WQY）。**不要用 Alpine 安装 LibreOffice**：缺少 .wps 过滤器且 CJK 渲染有问题。

### API 清单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/convert/md-to-docx` | Markdown 转 DOCX 文件下载（multipart） |
| POST | `/api/pdf/split` | PDF 切分（1 个文件，≤50MB） |
| POST | `/api/pdf/merge` | PDF 合并（2-10 个文件，≤5MB/个） |
| POST | `/api/document/convert-to-pdf` | 文档转 PDF（≤5 个文件，≤50MB/个，.doc/.docx/.wps） |

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

## 编码规约(必须遵守)

### 2.1 类注释
遵循 File Header 设置。
*   **模版内容**：
```java
/**
 * @Author ${USER}
 * @Version ${NAME}.java, v 0.1 ${YEAR}年${MONTH}月${DAY}日 ${TIME} ${USER}
 * @Description: TODO
 */
```

### 2.2 接口注释
1.  说明方法作用。
2.  说明参数（是否必填、分页限制等）。
3.  枚举需增加 `@see`。
4.  废弃接口需增加 `@Deprecated` 并指出替代接口。

**案例**：
```java
/**
 * xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
 *
 * @param nodeCode    中心仓code 必填
 * @param solutionKey 解决方案 必填 (中心仓：centerwms-mmc-alibaba)
 * @param isTest      测试标 (预发：1, 正式：0)
 * @param pageSize    页大小 100 
 * @return 
 * @see xxxxx
 */
```

### 2.3 方法注释
1.  功能介绍，参数介绍。
2.  **实现过程说明**：说明每一个步骤的作用和内容。
3.  每个方法上都要添加注释，做好具体作用的说明

**案例**：
```java
/**
 * 保存指定调拨单下的支援人员
 *
 * @param processInstanceId         支援调拨单号
 * @param currentSupportLaborModels 该调拨单下需要保存的支援人员
 */
public void saveSupportLabor(String processInstanceId, List<SupportLaborModel> currentSupportLaborModels) {
    HumanException.throwIfOneObjIsNull(HumanErrorEnum.PARAM_IS_NULL, processInstanceId);

    // 1. 数据预处理
    // 1.1 根据调拨单号查询调拨单模型
    SupportDocumentModel supportDocumentModel = supportDocumentQueryDomainService.getSupportDocumentModel(processInstanceId);

    // 2. 参数和业务规则检查
    // 2.1 参数检查
    checkParam(supportDocumentModel, currentSupportLaborModels);
    // 2.2 业务规则检查
    checkBizRule(supportDocumentModel, currentSupportLaborModels);

    // 3. 保存支援明细
    processSaveSupportLabor(supportDocumentModel, currentSupportLaborModels);
}
```
### 2.4 代码设计
所有代码分层遵循如下的代码分层结构，
#### 2.4.1 代码分层
        1.网关层 负责切面的拦截或者server的filter 如果有
        2.接口层 XxxxController 接收请求和对外输出，输出对象统一为VO对象。封装采用Spirng的ResponseEntity进行封装。接收的参数统一以xxxRequest进行命名。主要做基础参数校验，具体的逻辑处理不要在Controller层做，要在Service层进行处理。
        3.应用层 XxxxxBizService领域层的编排、事件订阅和事件发布。rpc的应用服务调用。
        4.领域层 XxxxDomainService 核心的业务逻辑都在领域层
        5.仓储层 隔离持久化和业务逻辑之间的耦合。接口放到领域层中，持久化层依赖领域层的仓储的接口。依赖导致。
        6.持久化层 Mapper或者Dao如果存在需要存储的则进行放到该层级中。
      如果只是简单的查询 可以直接应用层调用仓储层进行，使用CQRS的方式。不比严格遵守代码分层。

#### 2.4.2 模型转换
        1.所有的对象转换使用MapStruct组件进行转换。转换的方法使用XxxxConvert
        2.所有的请求都已XxxxRequest进行命名。所有的Controller的输出使用XxxxxVO进行命名。
        3.xxxRequest          -->          xxxDO      --->    xxxPO
          接口层 应用层(Request->DO)     领域层 仓储层(DO->PO)   持久化层
          xxxRequest          <--          xxxDO      <---    xxxPO
          接口层 应用层(VO<-DO)     领域层 仓储层(DO<-PO)   持久化层 或
          接口层 应用层                   仓储层(VO<-PO)            持久化层 或
#### 2.4.3 代码划分
        不同的功能按照领域进行划分，可以先试用package的方式，如果后面功能比较多可以采用子module的方式。
        
### 2.5 日志打印
    志打印要把基础的类方法等信息打印出来,使用slf4j方式。，不要使用中文描述，要使用英文描述。
 ```
 log.info("[className#methodName]  xxxxxx {}",xxxx);
 log.error("[className#methodName]  xxxxxx process exception  {},params={}",ex,ex.getMessage,xxxx);
 
```

### 2.6 代码通用要求
    1.禁止代码中使用魔法值。
    2.每个方法上都要添加注释，做好具体作用的说明。参考 2.3 方法注释、
    3.每个类上也要做好注释。参考2.1 类注释、2.2 接口注释
    4.写代码或者方案的时候，需要考虑是否存在内存OOM或者泄漏的风险，比如：ThreadLocal必须在finally中删除；锁必须释放；不能一直向list或者其他存储中存放数据等等；
    5.方法最好能做到复用，而不是什么都写一个新的，这个需要你对整个项目都要做一定的了解和分析
    
### 2.7 事务使用
    事务只能使用编程式，不能使用声明式事务。
    mq发送消息不能放到事务中。

### 2.8 方法定义
    1.每个方法不要超过80行；
    2.每个方法上都要添加注释，做好具体作用的说明。参考 2.3 方法注释、
    3.