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

## PDF 添加水印（pdfWatermark）

| 参数 | 说明 | 默认 | 可选值 |
|------|------|------|--------|
| source | 水印来源 | text | text(文字) / image(图片，需上传图片) |
| text | 水印文字 | — | 如 "内部资料"、"机密文件" |
| fontSize | 字号(pt) | 28 | 常用 16/24/28/36 |
| color | 颜色 | #808080 | hex，如 #000000/#CC0000 |
| angle | 旋转(度) | 0 | 0 / 45 / -45 / 自定义 |
| opacity | 透明度 | 0.5 | 0-1，如 0.3(浅)/0.8(深) |
| ratio | 图片水印宽度占页面% | 50 | 5-100 |
| fixedRatio | 固定比例(不随页面缩放) | false | true/false |
| alignX | 水平对齐 | center | left / center / right |
| alignY | 垂直对齐 | middle | top / middle / bottom |
| offsetX/offsetY | 偏移(cm) | 0 | 可正负 |
| range | 应用范围 | all | all / pageRange |
| fromPage/toPage | 页范围 | — | 1-based，range=pageRange 时 |
| subset | 子集 | all | all / odd(奇数页) / even(偶数页) |

**流程**：用户要求"加个水印"，需询问：文字还是图片水印、水印内容、是否要斜排/透明度等外观、位置（居中/上方/下方/页脚）、应用到哪些页。图片水印需用户上传图片。

## PDF 去水印（pdfDewatermark）

| 参数 | 说明 | 默认 | 可选值 |
|------|------|------|--------|
| position | 水印大致位置 | — | center(居中)/top(上方)/bottom(下方)/left(左)/right(右)/tl/tr/bl/br(四角) |
| applyTo | 应用范围 | all | all(所有页) / page(仅指定页) |
| page | 指定页 | — | 1-based，applyTo=page 时 |

**流程**：用户要求"去掉水印"，询问水印在什么位置（居中/页眉/页脚/左上角等），据此填 position。若水印每页都在则用 all；只在某页则 applyTo=page。无法自动去除的区域会提示。

## PDF 涂黑遮盖（pdfRedact）

| 参数 | 说明 | 默认 | 可选值 |
|------|------|------|--------|
| position | 遮盖位置 | — | center/top/bottom/left/right/tl/tr/bl/br |
| mode | 遮盖模式 | standard | standard(覆盖) / deep(彻底清除底层) |
| applyTo | 应用范围 | all | all / page |
| page | 指定页 | — | 1-based，applyTo=page 时 |

**流程**：用户要求"把某处涂黑/遮盖"，询问要遮盖的位置（如"第2页下半部分"→bottom），填 position。敏感信息需要彻底清除时用 deep 模式。
