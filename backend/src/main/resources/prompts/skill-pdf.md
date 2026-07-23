## PDF 工具参数速查

| 工具 | 可选参数 | 默认值 | 可选值 |
|------|---------|--------|--------|
| **PDF 切分** | mode | by-page | by-page(逐页拆) / by-range(指定范围) / by-n(每N页一组) |
| | pages | — | mode=by-range 时: 如 "1,3,5-8" |
| | everyN | 1 | mode=by-n 时: 常用 2/5/10 |
| **PDF 压缩** | level | 3 | 1(极度:72dpi) / 2(高度:100dpi) / 3(推荐:150dpi) / 4(轻度:200dpi) / 5(极限:300dpi) |
| **PDF 转图片** | format | png | png(无损大) / jpeg(有损小) / webp(平衡) |
| | dpi | 150 | 72(最小) / 150(清晰) / 300(高清) / 600(印刷级) |
| | quality | 0.9 | 仅 jpeg: 0.7(小文件) / 0.9(平衡) / 1.0(最大) |
| | pageRange | 全部 | 如 "1-5" 或 "1,3,5" |
| | trimMargin | false | true(裁剪白色边框) / false(保留原图) |
| **PDF 编排** | rotate | 0 | 90 / 180 / 270（单页旋转） |
| | blank | — | 插入空白页: plan 中加入 {"blank":true} |
| | width/height | A4 | 空白页尺寸(pt)，省略时跟随前一页 |
| **PDF 加密** | userPassword | — | 用户密码（打开密码），≥6位含数字+字母 |
| | ownerPassword | — | 所有者密码（权限密码），≥6位含数字+字母 |
| | canPrint | true | 允许打印 |
| | canCopy | true | 允许复制/提取内容 |
| | canModify | true | 允许修改文档内容 |
| | canAnnotate | true | 允许编辑注释和填写表单 |
| | canAssemble | true | 允许页面组装 |

## PDF 编排流程（三步）

- 第一步：如果用户上传了多个 PDF 并要求编排（如"删除第3页""把A的第2页插到B的第5页"），
  先调用 pdfInfo 查询每个文件的页数和尺寸。
- 第二步：根据 pdfInfo 返回的页数，生成正确的 plan JSON（file 字段=文件上传顺序的下标，
  page 字段=1-based 页码）。
- 第三步：调用 pdfArrange(fileIds, plan) 执行编排。
- 如果 pdfArrange 返回校验错误（含各文件实际页数），根据提示修正 plan 后重试。

## PDF 加密流程

- 用户上传 PDF 后，询问需要设置什么密码和权限。
- 密码要求：≥6位，需包含数字和字母，两个密码不能相同。
- 至少填写一个密码（用户密码或所有者密码）。
- 如果用户设置了所有者密码，需确认权限设置（默认全部允许，至少关闭一项）。
- 权限包括：打印、复制/提取内容、修改文档内容、编辑注释和填写表单、页面组装。
