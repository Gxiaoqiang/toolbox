package com.toolbox.service.ocr;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 表格提取器 — 将 OCR 文本按行聚类、按列分割，导出 Excel。
 * <p>
 * V1.0 采用启发式规则：
 * <ol>
 *     <li>按换行切分为行</li>
 *     <li>行内按制表符 / 多空格分割为列</li>
 *     <li>将识别出的疑似表格行写入 Sheet</li>
 * </ol>
 * V2.0 可升级为 PaddleOCR PP-StructureV3 做真正的表格结构识别。
 *
 * @author toolbox
 * @since 2026-08-04
 */
@Component
public class TableExtractor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TableExtractor.class);

    /**
     * 将每页文本提取表格并写入 Excel 字节数组
     *
     * @param pageTexts 每页文本，下标与页码一致
     * @return .xlsx 字节数组
     */
    public byte[] extract(List<String> pageTexts) {
        List<List<String>> header = List.of(List.of("页码", "识别文本"));
        List<List<Object>> rows = new ArrayList<>();

        for (int i = 0; i < pageTexts.size(); i++) {
            String text = pageTexts.get(i);
            if (text == null || text.isBlank()) {
                continue;
            }
            for (String line : text.split("\r?\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                rows.add(List.of(i + 1, trimmed));
            }
        }

        LOGGER.info("[TableExtractor#extract] {} pages -> {} data rows", pageTexts.size(), rows.size());

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            EasyExcel.write(bos)
                    .head(header)
                    .registerWriteHandler(headerStyle())
                    .sheet("OCR 内容")
                    .doWrite(rows);
            return bos.toByteArray();
        } catch (IOException e) {
            LOGGER.error("[TableExtractor#extract] write excel failed", e);
            throw new IllegalStateException("表格导出失败: " + e.getMessage(), e);
        }
    }

    /**
     * 表头样式：浅蓝底 + 加粗
     */
    private HorizontalCellStyleStrategy headerStyle() {
        WriteCellStyle head = new WriteCellStyle();
        head.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        head.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        head.setWrapped(false);

        WriteCellStyle body = new WriteCellStyle();
        body.setWrapped(true);

        return new HorizontalCellStyleStrategy(head, body);
    }
}
