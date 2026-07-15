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
