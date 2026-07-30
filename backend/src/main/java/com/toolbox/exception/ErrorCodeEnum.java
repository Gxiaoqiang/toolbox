package com.toolbox.exception;

/**
 * 统一错误码枚举
 *
 * @author toolbox
 * @since 2026-07-01
 */
public enum ErrorCodeEnum {

    /** 系统未知异常 */
    SYSTEM_ERROR(500, "系统内部错误"),
    /** 参数校验失败 */
    PARAM_INVALID(400, "参数校验失败"),
    /** 文件格式不支持 */
    FILE_FORMAT_UNSUPPORTED(400, "文件格式不支持"),
    /** 文件转换失败 */
    CONVERT_ERROR(500, "文件转换失败"),
    /** PDF 文件格式不正确 */
    PDF_FORMAT_INVALID(400, "请上传 PDF 格式的文件"),
    /** PDF 文件为空 */
    PDF_FILE_EMPTY(400, "请选择有效的 PDF 文件"),
    /** PDF 已加密 */
    PDF_ENCRYPTED(400, "暂不支持加密的 PDF 文件"),
    /** 页码超出范围 */
    PDF_PAGE_OUT_OF_RANGE(400, "页码范围超出文档总页数"),
    /** 页码格式错误 */
    PDF_PAGE_FORMAT_ERROR(400, "页码范围格式不正确，请输入如 \"1,3,5-8\""),
    /** 页码重复或重叠 */
    PDF_PAGE_OVERLAP(400, "页码范围存在重复或重叠"),
    /** 每 N 页参数无效 */
    PDF_EVERY_N_INVALID(400, "每页拆分数量必须为正整数"),
    /** PDF 处理异常 */
    PDF_PROCESS_ERROR(500, "PDF 处理失败，请稍后重试"),
    /** 文档格式不支持 */
    DOC_FORMAT_INVALID(400, "仅支持 .doc / .docx / .wps 格式"),
    /** 文档文件为空 */
    DOC_FILE_EMPTY(400, "请选择有效的文档文件"),
    /** 超过最大文件数 */
    DOC_TOO_MANY_FILES(400, "单次最多上传 5 个文件"),
    /** 文档文件超过大小限制 */
    DOC_FILE_TOO_LARGE(400, "单个文件不能超过 50MB"),
    /** 文档转换失败 */
    DOC_CONVERT_ERROR(500, "文档转换失败"),
    /** 转换服务不可用 */
    DOC_SERVICE_UNAVAILABLE(500, "转换服务不可用，请联系管理员"),
    /** PDF 合并文件数量不足 */
    PDF_MERGE_TOO_FEW(400, "至少需要 2 个 PDF 文件才能合并"),
    /** PDF 合并文件数量超限 */
    PDF_MERGE_TOO_MANY(400, "单次最多合并 10 个文件"),
    /** PDF 转图片 DPI 参数无效 */
    PDF_IMAGE_DPI_INVALID(400, "DPI 参数无效，有效范围 72-600"),
    /** PDF 转图片格式不支持 */
    PDF_IMAGE_FORMAT_INVALID(400, "图片格式不支持，仅支持 png / jpeg"),
    /** PDF 转图片 JPEG 质量参数无效 */
    PDF_IMAGE_QUALITY_INVALID(400, "JPEG 质量参数无效，有效范围 0.0-1.0"),
    /** PDF 转图片处理失败 */
    PDF_IMAGE_PROCESS_ERROR(500, "PDF 转图片失败，请稍后重试"),
    /** PDF 压缩等级无效 */
    PDF_COMPRESS_LEVEL_INVALID(400, "压缩等级无效，有效范围 1-5"),
    /** PDF 压缩处理失败 */
    PDF_COMPRESS_PROCESS_ERROR(500, "PDF 压缩失败，请稍后重试"),

    // ===== PDF 涂黑遮盖 =====
    /** 遮盖矩形数据为空 */
    PDF_REDACT_RECTS_EMPTY(400, "请至少添加一个遮盖方块"),
    /** 遮盖矩形数据格式错误 */
    PDF_REDACT_RECTS_FORMAT_ERROR(400, "遮盖方块数据格式不正确"),
    /** 遮盖模式无效 */
    PDF_REDACT_MODE_INVALID(400, "遮盖模式无效，仅支持 standard / deep"),
    /** PDF 涂黑处理失败 */
    PDF_REDACT_PROCESS_ERROR(500, "PDF 涂黑处理失败，请稍后重试"),

    // ===== 图片转 PDF =====
    /** 图片文件数量无效 */
    IMAGE_FILE_COUNT_INVALID(400, "图片数量无效，需要 1-50 张"),
    /** 单张图片文件过大 */
    IMAGE_FILE_TOO_LARGE(400, "单张图片不能超过 5MB"),
    /** 图片总大小超限 */
    IMAGE_TOTAL_SIZE_EXCEEDED(400, "图片总大小不能超过 100MB"),
    /** 图片格式不支持 */
    IMAGE_FORMAT_UNSUPPORTED(400, "仅支持 JPG / PNG / WEBP / GIF 格式"),
    /** 图片转 PDF 处理失败 */
    IMAGE_TO_PDF_PROCESS_ERROR(500, "图片转 PDF 失败，请稍后重试"),

    // ===== PDF 编排 =====
    /** 编排计划为空 */
    PDF_ARRANGE_PLAN_EMPTY(400, "编排计划不能为空"),
    /** 编排计划条目过多 */
    PDF_ARRANGE_PLAN_TOO_LARGE(400, "编排计划条目不能超过 300"),
    /** 编排计划文件下标无效 */
    PDF_ARRANGE_PLAN_FILE_INDEX_INVALID(400, "编排计划引用了不存在的源文件"),
    /** 编排计划页码越界 */
    PDF_ARRANGE_PLAN_PAGE_OUT_OF_RANGE(400, "页码范围超出文件实际页数"),
    /** 编排计划旋转度数无效 */
    PDF_ARRANGE_PLAN_ROTATE_INVALID(400, "旋转度数仅支持 90 / 180 / 270"),
    /** PDF 编排处理失败 */
    PDF_ARRANGE_PROCESS_ERROR(500, "PDF 编排失败，请稍后重试"),

    // ===== PDF 加密 =====
    /** 密码为空 */
    PDF_ENCRYPT_PASSWORD_EMPTY(400, "请至少填写一个密码"),
    /** 两个密码相同 */
    PDF_ENCRYPT_PASSWORD_SAME(400, "两个密码不能相同"),
    /** 密码强度不足 */
    PDF_ENCRYPT_PASSWORD_WEAK(400, "密码强度不足：至少6位，需包含数字和字母"),
    /** 权限全开 */
    PDF_ENCRYPT_PERMISSION_ALL_OPEN(400, "至少需要关闭一项权限"),
    /** PDF 已加密 */
    PDF_ENCRYPT_ALREADY_ENCRYPTED(400, "该 PDF 已加密，请先解密"),
    /** PDF 加密处理失败 */
    PDF_ENCRYPT_PROCESS_ERROR(500, "PDF 加密失败，请稍后重试"),

    // ===== HTML 转 PDF =====
    /** URL 为空 */
    HTML_TO_PDF_URL_EMPTY(400, "请输入网页 URL"),
    /** URL 格式不合法 */
    HTML_TO_PDF_URL_INVALID(400, "URL 格式不正确"),
    /** URL 无法访问 */
    HTML_TO_PDF_URL_UNREACHABLE(400, "URL 无法访问，请检查链接是否正确"),
    /** HTML 文件为空 */
    HTML_TO_PDF_FILE_EMPTY(400, "请选择有效的 HTML 文件"),
    /** HTML 文件超过大小限制 */
    HTML_TO_PDF_FILE_TOO_LARGE(400, "HTML 文件不能超过 10MB"),
    /** 不是 HTML 文件 */
    HTML_TO_PDF_FORMAT_INVALID(400, "仅支持 .html / .htm 格式"),
    /** 渲染超时 */
    HTML_TO_PDF_RENDER_TIMEOUT(500, "网页渲染超时，请稍后重试"),
    /** 渲染失败 */
    HTML_TO_PDF_RENDER_ERROR(500, "网页转 PDF 失败，请稍后重试"),
    /** 服务繁忙 */
    HTML_TO_PDF_BUSY(503, "当前转换任务较多，请稍后重试"),
    HTML_TO_PDF_MAIN_HTML_NOT_FOUND(400, "未找到主 HTML 文件"),
    HTML_TO_PDF_TOO_MANY_FILES(400, "文件数量不能超过 100 个"),
    HTML_TO_PDF_FOLDER_TOO_LARGE(400, "文件夹总大小不能超过 50MB"),

    // ===== Agent 文档处理助手 =====
    /** Agent LLM 调用超时 */
    AGENT_LLM_TIMEOUT(500, "AI 服务响应超时，请稍后重试"),
    /** Agent 无法理解用户意图 */
    AGENT_INTENT_UNCLEAR(400, "无法理解您的需求，请更具体地描述"),
    /** Agent 不支持的操作 */
    AGENT_TOOL_NOT_FOUND(400, "暂不支持该操作"),
    /** 服务器磁盘空间不足 */
    AGENT_DISK_FULL(500, "服务器存储空间不足，请联系管理员"),
    /** Agent 会话未找到 */
    AGENT_SESSION_NOT_FOUND(404, "对话不存在或已过期"),
    /** Agent 并发连接数超限 */
    AGENT_TOO_MANY_CONNECTIONS(503, "当前使用人数较多，请稍后重试");

    /** 错误码 */
    private final Integer code;
    /** 错误描述 */
    private final String desc;

    ErrorCodeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }
}
