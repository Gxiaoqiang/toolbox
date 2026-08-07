package com.toolbox.service.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Markdown 文档生成器
 * <p>
 * 将各页 OCR 文本与原生页文本按页序拼接，并进行标题 / 列表 / 表格识别。
 * 扫描页优先用词级坐标重建表格（{@link TableStructureRecognizer}），非表格再走纯文本启发式。
 *
 * @author toolbox
 * @since 2026-08-04
 */
@Component
public class MarkdownBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarkdownBuilder.class);

    private final TableStructureRecognizer tableRecognizer;

    public MarkdownBuilder(TableStructureRecognizer tableRecognizer) {
        this.tableRecognizer = tableRecognizer;
    }

    /**
     * 生成 Markdown 文档
     *
     * @param pageTexts   每页文本，下标与页码一致（原生页为直接提取文本，扫描页为 OCR 文本）
     * @param ocrResults  每页 OCR 结果（含词级坐标），原生页为 null
     * @param types       每页类型标记
     * @return Markdown 字符串
     */
    public String build(List<String> pageTexts, List<OcrResult> ocrResults,
                        List<PdfAnalyzer.PageType> types) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pageTexts.size(); i++) {
            String page = pageTexts.get(i);
            if (page == null || page.isBlank()) {
                continue;
            }
            sb.append("<!-- Page ").append(i + 1).append(" -->\n\n");
            sb.append(convertPage(i, pageTexts, ocrResults, types));
            sb.append("\n\n");
        }
        LOGGER.info("[MarkdownBuilder#build] {} pages -> {} chars", pageTexts.size(), sb.length());
        return sb.toString().trim() + "\n";
    }

    /**
     * 转换单页：扫描页优先表格识别，否则纯文本启发式
     */
    private String convertPage(int index, List<String> pageTexts,
                               List<OcrResult> ocrResults, List<PdfAnalyzer.PageType> types) {
        // 扫描页且有词级坐标 → 尝试表格识别
        if (types.get(index) == PdfAnalyzer.PageType.SCANNED
                && ocrResults != null && ocrResults.get(index) != null) {
            List<List<String>> grid = tableRecognizer.recognize(ocrResults.get(index).wordBoxes());
            if (grid != null && grid.size() >= 2) {
                return buildTable(grid);
            }
        }
        return convertPlainText(pageTexts.get(index));
    }

    /**
     * 将识别出的网格渲染为 Markdown 表格
     */
    private String buildTable(List<List<String>> grid) {
        StringBuilder sb = new StringBuilder();
        int cols = grid.get(0).size();

        // 表头
        sb.append("| ").append(String.join(" | ", grid.get(0))).append(" |\n");
        // 分隔行
        sb.append("| ").append(String.join(" | ", java.util.Collections.nCopies(cols, "---")))
                .append(" |\n");
        // 数据行
        for (int i = 1; i < grid.size(); i++) {
            List<String> row = grid.get(i);
            while (row.size() < cols) {
                row.add("");
            }
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        return sb.toString();
    }

    /**
     * 纯文本启发式转换（标题 / 列表 / 段落）
     */
    private String convertPlainText(String text) {
        StringBuilder out = new StringBuilder();
        for (String raw : text.split("\r?\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                out.append("\n");
                continue;
            }
            out.append(toMarkdownLine(line)).append("\n");
        }
        return out.toString();
    }

    /**
     * 单行文本 → Markdown（标题 / 列表 / 普通段落）
     */
    private String toMarkdownLine(String line) {
        if (looksLikeHeading(line)) {
            return "# " + line;
        }
        if (line.startsWith("•") || line.startsWith("·") || line.startsWith("- ")) {
            return "- " + line.replaceFirst("^[•·-]\\s*", "");
        }
        if (line.matches("^\\d+[.、]\\s+.*")) {
            return line.replaceFirst("^(\\d+)[.、]\\s+", "$1. ");
        }
        return line;
    }

    /**
     * 启发式判断是否像标题：短行、无句号/逗号结尾
     */
    private boolean looksLikeHeading(String line) {
        if (line.length() > 30) {
            return false;
        }
        char last = line.charAt(line.length() - 1);
        return last != '。' && last != '.' && last != '，' && last != ',' && last != '！'
                && last != '!' && last != '？' && last != '?';
    }
}
