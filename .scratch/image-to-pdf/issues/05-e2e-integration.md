# 05 — 全量编译 + E2E 集成验证

**What to build:** 前后端联调，验证图片转 PDF 完整流程端到端可用。

**Blocked by:** 03 (Agent Toolkit), 04 (前端组件)

**Status:** ready-for-agent

- [ ] 后端全量测试 `mvn test` — 所有已有测试 + ImageToPdfServiceTest + ImageControllerTest 全部 PASS
- [ ] 前端类型检查 `vue-tsc --noEmit` 通过
- [ ] 前端构建 `npm run build` 通过，产物输出到 `backend/src/main/resources/static/`
- [ ] 后端打包 `mvn clean package -DskipTests` 成功
- [ ] 启动后端 `java -jar target/toolbox-1.0.0.jar`，curl 验证 `POST /api/image/to-pdf` 端点可用
- [ ] 启动前端 dev server，侧边栏「图片工具包」分组出现「图片转 PDF」菜单项
- [ ] 手动验证清单：
  - 拖拽上传 JPG/PNG/WEBP/GIF 各一张 → 缩略图网格正常显示
  - 拖拽排序图片 → 顺序变化
  - 删除图片 → 从网格移除
  - 切换方向/边距/适配方式 → 设置面板状态正确
  - 点击「开始转换」→ 下载 PDF 文件，文件有效
  - 取消勾选「合并为一个 PDF」→ 下载 ZIP 包，内含多个独立 PDF
  - 上传超过 5MB 的图片 → 显示错误提示
  - 上传非图片格式 → 跳过该文件
- [ ] 如有修复，提交最终代码
