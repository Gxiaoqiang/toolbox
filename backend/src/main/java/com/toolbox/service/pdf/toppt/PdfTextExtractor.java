package com.toolbox.service.pdf.toppt;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 字级坐标提取器 — 用 PDFBox 逐页提取字符包围盒
 * <p>
 * 重写 {@code writeString} 收集每个字符的 {@code TextPosition}，转成 {@link WordBox}。
 * <p>
 * 注意：PDFBox {@code TextPosition.getYDirAdj()} 对多数办公 PDF（如 LibreOffice 生成）
 * 返回「顶部为原点、y 向下增大」（顶部 y=0）——与 PPT/屏幕坐标一致，
 * 后续段落聚拢与生成直接使用，无需翻转。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Component
public class PdfTextExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfTextExtractor.class);

    /**
     * 提取每页的字级坐标
     *
     * @param doc 已加载的 PDF
     * @return 每页一个 {@link WordBox} 列表；若某页无文字返回空列表
     * @throws IOException 提取失败
     */
    public List<List<WordBox>> extract(PDDocument doc) throws IOException {
        int pages = doc.getNumberOfPages();
        List<List<WordBox>> result = new ArrayList<>(pages);

        for (int i = 0; i < pages; i++) {
            PageExtractor pe = new PageExtractor();
            pe.setSortByPosition(true);
            pe.setStartPage(i + 1);
            pe.setEndPage(i + 1);
            pe.getText(doc);
            result.add(pe.boxes);
        }
        return result;
    }

    /**
     * 单页字符收集器 — 将 y 归一化为向下
     */
    private static final class PageExtractor extends PDFTextStripper {

        private final List<WordBox> boxes = new ArrayList<>();

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            for (TextPosition tp : textPositions) {
                String ch = tp.getUnicode();
                if (ch == null || ch.isEmpty() || ch.isBlank()) {
                    continue;
                }
                boxes.add(new WordBox(
                        tp.getXDirAdj(),
                        tp.getYDirAdj(),
                        tp.getWidthDirAdj(),
                        tp.getHeightDir(),
                        ch));
            }
        }
    }
}
