# PRD: PDF 压缩

**日期**: 2026-07-14
**状态**: draft
**标签**: `ready-for-agent`

---

## Problem Statement

用户经常需要将 PDF 文件压缩后用于邮件发送、网页上传或存储归档。现有工具箱提供了 PDF 切分、合并、转图片等能力，但缺少**直接压缩 PDF 文件体积**的功能。用户需要一个简单直观的工具：上传 PDF，选择压缩等级，看到压缩前后的体积对比，然后下载压缩后的文件。

---

## Solution

基于 PDFBox 3.0.3 对 PDF 内部资源（嵌入图片、冗余对象、元数据）进行优化压缩。提供 **5 档预设压缩等级**，从极度压缩到高质量轻度压缩，覆盖"牺牲画质换体积"到"尽量保持画质"的全场景。转换完成后清晰展示**压缩率、体积变化**，让用户直观感知效果。

---

## User Stories

1. 作为用户，我希望上传一个 PDF 文件并选择一个压缩等级，点击按钮即可获得压缩后的 PDF
2. 作为用户，我希望看到 5 个清晰的压缩等级选项，从"极度压缩"到"轻度压缩"，每个等级有明确的标签和简短描述，方便我做出选择
3. 作为用户，我希望能看到压缩前后的文件体积对比（原始大小 → 压缩后大小），以及压缩率百分比
4. 作为用户，当压缩效果不理想（压缩率很低甚至变大）时，我希望能看到明确提示，知道该文件不适合进一步压缩
5. 作为用户，压缩过程中文件不能移除、参数不能修改，结果区显示加载动画而非旧结果
6. 作为用户，压缩完成后可以一键下载压缩后的 PDF
7. 作为用户，如果压缩失败（文件加密、格式损坏等），我希望能看到清晰的错误提示
8. 作为用户，我可以重新选择压缩等级对同一文件再次压缩，无需重新上传
9. 作为用户，上传新文件或移除文件后，之前的结果自动清空
10. 作为用户，文件超过 50MB 或非 PDF 格式时我能看到明确的错误提示

---

## 压缩等级设计

共 5 档预设，覆盖从极度压缩到轻度压缩的全场景：

| 等级 | 标签 | 图片降采样 DPI | JPEG 质量 | 元数据 | 说明 |
|------|------|---------------|-----------|--------|------|
| 1 | 极度压缩 | 72 | 0.4 | 移除 | 以最低画质换取极限体积缩减。图片缩至屏幕分辨率（72 DPI），JPEG 以低质量重编码，移除文档元数据。**适合**：内部流转、长期归档、对画质无要求的批量存储。**注意**：放大查看时图片可能出现明显锯齿和色块 |
| 2 | 高度压缩 | 100 | 0.55 | 移除 | 显著缩小体积，同时保持基础可读性。图片降至 100 DPI，中等 JPEG 质量，移除元数据。**适合**：邮件附件、OA 审批上传、有文件大小限制的平台提交 |
| 3 | 推荐压缩 | 150 | 0.7 | 保留 | **默认选项**，均衡压缩率与视觉质量。图片降至 150 DPI（常规打印可接受），中高 JPEG 质量，保留文档元数据。**适合**：日常分享、文档归档、大部分通用场景 |
| 4 | 轻度压缩 | 200 | 0.85 | 保留 | 保持较优质画面，适度减小体积。图片降至 200 DPI（接近印刷标准），高 JPEG 质量。**适合**：需要较高画质的报告、标书、宣传材料 |
| 5 | 极限画质 | 300 | 0.95 | 保留 | 画质优先，仅去除文档内冗余数据并轻微压缩图片。图片保持 300 DPI（标准印刷分辨率），JPEG 近无损质量。**适合**：画册、设计稿、需要放大审阅或再次印刷的场景。**注意**：体积缩减幅度可能较小 |

**压缩策略说明：**
- **图片降采样**：将 PDF 中嵌入图片的分辨率降低到目标 DPI，这是压缩率的主要来源
- **JPEG 重压缩**：对嵌入的 JPEG 图片以目标质量重新编码
- **元数据清理**：移除文档信息（作者、创建者、生产者等），进一步减小体积
- **PDFBox 保存优化**：使用 `setCompress() + RemoveAllSecurity` 等 PDFBox 内置压缩选项

**注：** 对于纯文本 PDF（无嵌入图片），压缩效果有限。此时前端应展示"该文件无可压缩内容"提示。

---

## Implementation Decisions

### 1. 技术选型

使用 PDFBox 3.0.3（项目已依赖），核心策略：
- `PDPageContentStream` 遍历页面资源词典，定位所有 `PDImageXObject`
- 对每个图片对象：降采样（`AffineTransform` 缩放 → 新 BufferedImage）+ 重新编码
- 替换原资源中的图片引用
- `PDDocument.save()` 时启用压缩选项
- `PDDocumentInformation` 置空（低压缩等级时）

### 2. API 设计

```
POST /api/pdf/compress
Content-Type: multipart/form-data
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| file | MultipartFile | 必填 | PDF ≤ 50MB |
| level | int | 3 | 压缩等级 1-5 |

**返回：** `application/pdf` 二进制流，`Content-Disposition` 包含 `compressed-{原文件名}.pdf`

**错误响应：**
```json
{ "code": 400, "message": "...", "data": null }
```

### 3. 分层架构

| 层 | 职责 |
|----|------|
| Controller | 文件非空校验、扩展名校验、大小校验，委托 Service |
| Service | 压缩等级校验、PDF 加载、图片遍历与压缩、元数据处理、保存 |
| 常量 | `PdfCompressConstant` — 压缩等级枚举 `CompressLevel`（含 dpi/jpegQuality/removeMeta/label/description）、文件大小上限 |
| DTO | `PdfCompressResult` — 原始字节用于流式返回，同时包含 `originalSize` / `compressedSize` 供前端展示 |

### 4. CompressLevel 枚举设计

```java
public enum CompressLevel {
    EXTREME(1,
        "极度压缩",
        "以最低画质换取极限体积缩减。图片降至 72 DPI，JPEG 低质量重编码，移除文档元数据。适合内部流转、长期归档。注意：放大查看时可能出现锯齿和色块",
        72, 0.4f, true),
    HIGH(2,
        "高度压缩",
        "显著缩小体积，保持基础可读性。图片降至 100 DPI，中等 JPEG 质量，移除元数据。适合邮件附件、OA 审批、有文件大小限制的提交",
        100, 0.55f, true),
    RECOMMENDED(3,
        "推荐压缩",
        "均衡压缩率与视觉质量。图片降至 150 DPI，中高 JPEG 质量，保留文档元数据。适合日常分享、文档归档等大部分通用场景",
        150, 0.7f, false),
    LIGHT(4,
        "轻度压缩",
        "保持较优质画面，适度减小体积。图片降至 200 DPI，高 JPEG 质量。适合报告、标书、宣传材料等需要较高画质的文档",
        200, 0.85f, false),
    LOSSLESS(5,
        "极限画质",
        "画质优先，仅去除文档冗余数据并轻微压缩图片。图片保持 300 DPI，JPEG 近无损质量。适合画册、设计稿等需放大审阅或再印刷的场景。注意：体积缩减可能较小",
        300, 0.95f, false);

    private final int level;
    private final String label;
    private final String description;  // 详细说明，展示在 UI 卡片中
    private final int targetDpi;
    private final float jpegQuality;
    private final boolean removeMeta;

    public static CompressLevel fromLevel(int level) { ... }
}
```

### 5. 压缩结果响应

压缩结果通过 HTTP 响应头传递元信息，前端无需额外请求：

```
Content-Type: application/pdf
Content-Disposition: attachment; filename*=UTF-8''compressed-report.pdf
X-Original-Size: 5242880
X-Compressed-Size: 1572864
```

`X-Original-Size` 和 `X-Compressed-Size` 自定义响应头让前端直接读取体积对比数据，无需再请求。

### 6. 状态机（前端，复用 pdf-to-image 模式）

```
noFile → ready → processing → done/error
                  ↑              |
                  └──────────────┘ (可重新转换)
```

- `noFile`: 未选择文件，压缩等级可选择但转换按钮禁用
- `ready`: 已选文件，可选择等级，点击转换
- `processing`: 转换中 — **文件不可移除**、**等级不可切换**、结果区显示加载动画
- `done`: 展示压缩前后对比（体积、压缩率），可重新转换（切换等级后再次点击）
- `error`: 展示错误信息，可重试

### 7. UI 布局（三栏，遵循项目约定）

```
┌─────────────────────┬────┬──────────────────────────┐
│   左侧：上传 + 等级  │ →  │   右侧：结果区            │
│                     │    │                          │
│  📤 上传区          │ 转  │  压缩前: 5.0 MB          │
│  (50MB 上限)       │ 换  │  压缩后: 1.5 MB          │
│                     │    │  压缩率: 70% ↓           │
│  ○ 极度压缩         │    │  ████████░░ 进度条       │
│  ● 推荐压缩 (默认)  │    │                          │
│  ○ 轻度压缩         │    │  [下载压缩文件]          │
│  ○ 极限画质         │    │                          │
│  ○ 高度压缩         │    │                          │
│                     │    │                          │
└─────────────────────┴────┴──────────────────────────┘
```

### 8. 前端压缩等级选择组件

采用**卡片式单选列表**，每张卡片展示丰富信息帮助用户决策：

```
┌──────────────────────────────────────────┐
│ ● 推荐压缩                    （默认选中）│
│   均衡压缩率与视觉质量。图片降至 150 DPI， │
│   中高 JPEG 质量，保留文档元数据。        │
│   适合日常分享、文档归档等大部分通用场景   │
├──────────────────────────────────────────┤
│ ○ 高度压缩                               │
│   显著缩小体积，保持基础可读性。图片降至   │
│   100 DPI，中等 JPEG 质量，移除元数据。   │
│   适合邮件附件、OA 审批等有大小限制的场景  │
├──────────────────────────────────────────┤
│ ○ 极度压缩                               │
│   以最低画质换取极限体积缩减。图片降至     │
│   72 DPI，JPEG 低质量重编码，移除元数据。 │
│   适合内部流转、长期归档。                │
│   ⚠ 放大查看时可能出现锯齿和色块         │
├──────────────────────────────────────────┤
│ ○ 轻度压缩                               │
│   保持较优质画面，适度减小体积。图片降至   │
│   200 DPI，高 JPEG 质量。                 │
│   适合报告、标书、宣传材料等场景          │
├──────────────────────────────────────────┤
│ ○ 极限画质                               │
│   画质优先，仅去除冗余数据并轻微压缩图片。 │
│   图片保持 300 DPI，JPEG 近无损质量。     │
│   适合画册、设计稿等需再印刷的场景        │
│   ⚠ 体积缩减幅度可能较小                 │
└──────────────────────────────────────────┘
```

- 左侧单选圆圈指示选中状态
- 标签加粗（如"推荐压缩"），默认项带蓝色 `（默认）` 标记
- 详细描述折行展示，文字大小 12px，颜色 `var(--text-muted)`
- 带 `⚠` 的注意项用更淡的颜色（10px），提醒用户该档位的取舍
- 排序：推荐 → 高度 → 极度 → 轻度 → 极限画质（按实用性排列，而非数字顺序）

### 9. 压缩率展示

结果区在 `done` 状态下展示：
- 原始体积（格式化后）
- 压缩后体积（格式化后）
- 压缩率 = `(原始 - 压缩后) / 原始 × 100%`
- 可视化进度条：绿色 = 已压缩部分，灰色 = 保留部分
- 若压缩后体积 > 原始体积（极端情况），显示黄色警告"该文件不适合压缩"

### 10. 图片压缩实现要点

- 使用 PDFBox 的 `PDResources.getXObjectNames()` 遍历页面资源
- `PDImageXObject` 获取图片流 → `BufferedImage`
- `AffineTransform` 按比例缩放（targetDpi / currentDpi）
- 重新编码时对 JPEG 使用 `ImageWriteParam` 设置质量，PNG 保持不变
- 替换原 `PDImageXObject` 引用
- 串行处理（PDFBox PDDocument 非线程安全，与 pdf-to-image 一致）

---

## Testing Decisions

- 测试 `CompressLevel.fromLevel()` 枚举解析（边界值 0、6、负数）
- 测试 `PdfCompressConstant` 中各等级参数的非空性和范围
- 测试 Controller 的文件校验逻辑（空文件、非 PDF、超大文件）
- 集成测试：用含高清图片的 PDF 调用 API，验证各等级输出体积递减
- 42 个已有回归测试全部通过
- 前端测试：验证状态机转换逻辑、压缩率计算正确性

---

## Out of Scope

- 自定义 DPI / JPEG 质量手动调节（已由 5 档预设覆盖）
- 批量压缩多个 PDF
- 压缩进度推送（WebSocket）
- PDF/A 归档格式转换
- 加密 PDF 解密后压缩（直接拒绝加密文件）

---

## Files

| 文件 | 操作 |
|------|------|
| `backend/.../service/pdf/PdfCompressService.java` | 新建 — 接口 |
| `backend/.../service/pdf/impl/PdfCompressServiceImpl.java` | 新建 — 实现（图片遍历+压缩+保存） |
| `backend/.../controller/pdf/PdfCompressController.java` | 新建 — Controller |
| `backend/.../service/pdf/PdfCompressConstant.java` | 新建 — 常量 + CompressLevel 枚举 |
| `backend/.../exception/ErrorCodeEnum.java` | 修改 — 新增 PDF_COMPRESS_* 错误码 |
| `frontend/src/tools/pdf-compress/index.vue` | 新建 — 前端页面 |

---

## Further Notes

- 与 pdf-to-image 功能共享 `ImageConvertConstant.MAX_FILE_SIZE`（50MB）
- 保持与 pdf-merge / pdf-to-image 一致的三栏布局（`flex-1` + 80px 按钮列 + `flex-1`）
- 前端状态机复用 pdf-to-image 的 5 阶段模式（`noFile → ready → processing → done → error`）
- JPEG 重压缩时需注意 PDF 中的 JPEG2000（JPX）格式 — 降级为 JPEG 处理
- 图片重编码后文件变大（如 PNG 重编 JPEG）需判断取较小者
