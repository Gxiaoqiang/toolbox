package com.toolbox.service.pdf.toppt;

import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Word 生成器 — 用 Apache POI (XWPF) 将「段落 + 表格 + 图片」写成可编辑 DOCX
 * <p>
 * Word 为线性流，内容按阅读顺序写为原生段落/表格/图片。
 * 标题按字号启发式识别为 Heading 样式。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Component
public class DocxWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocxWriter.class);

    /** 标题字号阈值（pt）：≥ 该值识别为 Heading1 */
    private static final float H1_FONT = 22f;
    /** 二级标题阈值：≥ 该值识别为 Heading2 */
    private static final float H2_FONT = 16f;

    /** 默认正文字体 */
    private static final String BODY_FONT = "微软雅黑";

    /**
     * 生成 DOCX
     *
     * @param elements 有序内容元素（按阅读顺序）
     * @return DOCX 字节
     * @throws IOException 生成失败
     */
    public byte[] write(List<DocElement> elements) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (DocElement el : elements) {
                switch (el.type) {
                    case PARAGRAPH -> writeParagraph(doc, el.text, el.fontSize);
                    case TABLE -> writeTable(doc, el.grid);
                    case IMAGE -> writeImage(doc, el.imagePng);
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            LOGGER.info("[DocxWriter#write] elements={}", elements.size());
            return out.toByteArray();
        }
    }

    /**
     * 写入段落，按字号识别标题层级（手动加粗放大，避免依赖模板 Heading 样式）
     */
    private void writeParagraph(XWPFDocument doc, String text, float fontSize) {
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setFontFamily(BODY_FONT);

        if (fontSize >= H1_FONT) {
            run.setBold(true);
            run.setFontSize(18);
        } else if (fontSize >= H2_FONT) {
            run.setBold(true);
            run.setFontSize(14);
        } else {
            run.setFontSize(fontSize > 0 ? (int) fontSize : 11);
        }
    }

    /**
     * 写入表格
     */
    private void writeTable(XWPFDocument doc, List<List<String>> grid) {
        if (grid == null || grid.isEmpty()) {
            return;
        }
        int rows = grid.size();
        int cols = grid.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable table = doc.createTable(rows, cols);
        for (int r = 0; r < rows; r++) {
            XWPFTableRow row = table.getRow(r);
            for (int c = 0; c < cols; c++) {
                String cell = r < grid.size() && c < grid.get(r).size() ? grid.get(r).get(c) : "";
                XWPFTableCell xc = row.getCell(c);
                xc.removeParagraph(0);
                XWPFParagraph cp = xc.addParagraph();
                XWPFRun run = cp.createRun();
                run.setText(cell);
                run.setFontFamily(BODY_FONT);
            }
        }
        // 表格后空一行
        doc.createParagraph();
    }

    /**
     * 写入图片（读 PNG 尺寸）
     */
    private void writeImage(XWPFDocument doc, byte[] png) throws IOException {
        try {
            doWriteImage(doc, png);
        } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException e) {
            throw new IOException("add picture to docx failed", e);
        }
    }

    private void doWriteImage(XWPFDocument doc, byte[] png)
            throws IOException, org.apache.poi.openxml4j.exceptions.InvalidFormatException {
        if (png == null || png.length == 0) {
            return;
        }
        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(png));
        int width = bi != null ? bi.getWidth() : 400;
        int height = bi != null ? bi.getHeight() : 300;
        XWPFParagraph p = doc.createParagraph();
        XWPFRun run = p.createRun();
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            run.addPicture(in, Document.PICTURE_TYPE_PNG, "image.png", width, height);
        }
        // 图片后空一行
        doc.createParagraph();
    }

    /**
     * 文档元素 — 段落 / 表格 / 图片之一
     *
     * @param type     类型
     * @param text     段落文本（PARAGRAPH 时有效）
     * @param fontSize 段落字号（PARAGRAPH 时有效，标题识别用）
     * @param grid     表格网格（TABLE 时有效）
     * @param imagePng 图片 PNG 字节（IMAGE 时有效）
     * @param y        该元素在页内的 y 坐标（向下，用于排序）
     */
    public record DocElement(Type type, String text, float fontSize,
                             List<List<String>> grid, byte[] imagePng, float y) {

        public enum Type { PARAGRAPH, TABLE, IMAGE }

        public static DocElement paragraph(Paragraph p) {
            return new DocElement(Type.PARAGRAPH, p.text(), p.fontSize(), null, null, p.y());
        }

        public static DocElement table(List<List<String>> grid, float y) {
            return new DocElement(Type.TABLE, null, 0, grid, null, y);
        }

        public static DocElement image(byte[] png, float y) {
            return new DocElement(Type.IMAGE, null, 0, null, png, y);
        }
    }
}
