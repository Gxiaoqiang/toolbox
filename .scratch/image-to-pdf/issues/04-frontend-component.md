# 04 — 前端组件收尾 + 提交

**What to review / adjust:** 已有 `ImageToPdf.vue` 组件（Variant B 布局：左图网格 + 右设置面板），需对照 PRD 校验并调整，然后提交代码。

**Blocked by:** None — 可与后端并行启动

**Status:** ready-for-agent

- [ ] 审查已有 `ImageToPdf.vue`，对照 PRD user stories 逐条验证覆盖度
- [ ] 确认 `index.vue` 的 meta 导出符合约定（id/category/group/icon/requiresBackend）
- [ ] 确认文件校验逻辑与 PRD 一致：50 张上限、单张 5MB、总计 100MB、MIME type 白名单
- [ ] 确认拖拽排序使用原生 HTML5 拖拽（不引入 vuedraggable）
- [ ] 确认设置面板包含：页面方向（portrait/landscape）、边距（none/small/large）、适配方式（contain/cover/stretch）、合并开关
- [ ] 确认 `URL.createObjectURL()` 用于本地预览，`removeImage()` 中调用 `URL.revokeObjectURL()` 防止内存泄漏
- [ ] 确认错误提示清晰：格式不支持、单张超限、总量超限
- [ ] 确认 CSS 使用 `var(--*)` 自定义属性，无硬编码颜色
- [ ] 确认 `fetch('/api/image/to-pdf')` 路径与后端 Controller 一致
- [ ] 如有调整，前端 `vue-tsc --noEmit` 类型检查通过
- [ ] `npm run build` 构建通过
