# Toolbox 项目编码规范

> 扩展全局 ECC rules，针对 toolbox 项目的特有约束。

---

## 1. 工具注册约定

### 新增工具 checklist
- [ ] `frontend/src/tools/<id>/index.vue` 存在
- [ ] 使用独立 `<script lang="ts">` 块 + `export const meta: ToolMeta = {...}`
- [ ] `defineOptions({ inheritAttrs: false })` + `defineExpose({ meta })`
- [ ] `id` 与文件夹名一致
- [ ] `category` 为 `'file'` | `'develop'` | `'data'`
- [ ] 文件类工具需要后端 → 设置 `requiresBackend: true`
- [ ] CSS 只用 `var(--*)` 自定义属性，禁止硬编码颜色
- [ ] `import.meta.glob` 自动扫描，registry.ts 不需手动修改

### meta 导出模板
```vue
<script lang="ts">
import type { ToolMeta } from '@/tools/types'
export const meta: ToolMeta = {
  id: 'tool-id',
  name: '工具名称',
  description: '简短描述',
  icon: '🔧',
  category: 'file',
  group: 'PDF 工具包',       // 可选，同一分类下分组
  requiresBackend: true,      // 可选，默认 false
}
</script>
```

---

## 2. 文件校验参数速查表

| 工具 | 最多文件 | 单文件上限 | 允许格式 |
|------|---------|-----------|---------|
| PDF 切分 | 1 | 50MB | .pdf |
| PDF 合并 | 10 | 5MB | .pdf |
| 文档转 PDF | 5 | 50MB | .doc/.docx/.wps |
| PDF 压缩 | 1 | 50MB | .pdf |
| PDF 转图片 | 1 | 50MB | .pdf |
| Markdown 转 DOCX | 1 | 10MB | .md |

---

## 3. 前端布局约定

### 文件处理类工具（category: file）
```
┌─────────────┐    ┌──────┐    ┌─────────────┐
│  左: 输入区  │ → │ 中:  │ → │  右: 结果区  │
│  虚线框上传  │    │ 操作 │    │  虚线框展示  │
│  文件列表    │    │ 按钮 │    │  下载/预览   │
└─────────────┘    └──────┘    └─────────────┘
```
- 左/右虚线框: `border-2 border-dashed rounded-2xl`
- 中操作列: 固定宽度 `w-[80px]`，按钮竖排

### 数据处理类工具（category: develop/data）
```
┌───────────────────────────────────┐
│  单页全宽，输入/输出上下排列       │
│  编辑器 + 操作栏 + 结果区         │
└───────────────────────────────────┘
```

---

## 4. 状态机规范

```
idle → ready → processing → done
  │                        │
  └────────────────────────┘ (选新文件时重置)
                   │
                 error (可重试 → ready)
```

processing 状态下:
- 文件不可移除
- 参数不可修改
- 按钮保持可见但置灰不可点击
- 显示 SVG 旋转加载动画（22×22px 圆环）

---

## 5. 后端分层规范

```
Controller  (薄层: 参数校验 + 委托 Service)
  → Service 接口
    → ServiceImpl (核心逻辑)
```

- Controller 返回 `ResponseEntity<byte[]>` 或 `R<T>`
- 业务异常: `throw new BusinessException(ErrorCodeEnum.XXX)`
- 全局异常处理: `GlobalExceptionHandler` 统一捕获
- 日志: `log.info("[ClassName#methodName] description {}", var)`

---

## 6. 错误码命名规范

`ErrorCodeEnum` 命名: `模块_操作_错误类型`

```
PDF_SPLIT_FILE_EMPTY
PDF_MERGE_INSUFFICIENT_FILES
PDF_MERGE_TOO_MANY_FILES
PDF_COMPRESS_LEVEL_INVALID
DOCUMENT_CONVERT_UNSUPPORTED_FORMAT
DOCUMENT_CONVERT_PROCESS_ERROR
```

---

## 7. 侧边栏菜单规范

参考 memory: [[sidebar-menu-pattern]]

- 三级缩进: 分类 `px-2` → 分组/工具 `pl-6` → 组内工具 `pl-9`
- 分组可折叠: `collapsedGroups: Set<string>`，默认展开
- PDF 工具包图标: 📑，颜色 `#6366f1`（indigo）
