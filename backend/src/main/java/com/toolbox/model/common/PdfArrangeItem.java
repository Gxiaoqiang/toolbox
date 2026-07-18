package com.toolbox.model.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * PDF 编排计划中的单条记录——表示输出 PDF 中的一页。
 * <p>
 * 两种类型互斥：
 * <ul>
 *   <li>源文件页面: file/page 有值，rotate 可选</li>
 *   <li>空白页: blank=true，width/height 可选（省略时后端跟随前一页，首位退化 A4）</li>
 * </ul>
 *
 * @author toolbox
 * @since 2026-07-18
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public record PdfArrangeItem(

        /** 源文件下标（0-based，对应上传文件顺序），blank 页时 null */
        @JsonProperty("file")
        Integer file,

        /** 源文件中的页码（1-based），blank 页时 null */
        @JsonProperty("page")
        Integer page,

        /** 旋转度数: 0/90/180/270，缺省 0 */
        @JsonProperty("rotate")
        Integer rotate,

        /** 是否为空白页 */
        @JsonProperty("blank")
        Boolean blank,

        /** 空白页宽度（pt），缺省跟随前一页/退化 A4 */
        @JsonProperty("width")
        Float width,

        /** 空白页高度（pt），缺省跟随前一页/退化 A4 */
        @JsonProperty("height")
        Float height) {

    /** 紧凑构造器：源文件页面（无旋转） */
    public static PdfArrangeItem fromFile(int fileIndex, int pageNumber) {
        return new PdfArrangeItem(fileIndex, pageNumber, 0, false, null, null);
    }

    /** 紧凑构造器：源文件页面（带旋转） */
    public static PdfArrangeItem fromFile(int fileIndex, int pageNumber, int rotation) {
        return new PdfArrangeItem(fileIndex, pageNumber, rotation, false, null, null);
    }

    /** 紧凑构造器：空白页（无尺寸） */
    public static PdfArrangeItem newBlank() {
        return new PdfArrangeItem(null, null, 0, true, null, null);
    }
}
