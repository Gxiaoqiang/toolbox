package com.toolbox.service.pdf.toppt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 段落聚拢算法（A2 粒度）— 将字级坐标重建为段落级文本框
 * <p>
 * 实测发现：同一逻辑行常被 PDFBox 按字体/中英文切换拆成多个片段
 * （如「后端基于 Spring Boot 3」与「，集成 PDFBox」同 y 但拆开）。
 * 因此必须先做两级聚拢：
 * <ol>
 *     <li>按 Y 聚类成行（Y 中心相近的字符归同一行）</li>
 *     <li>行内按 X 排序合并成一行文本，取包围盒与主字号</li>
 *     <li>按行距聚成段（相邻行垂直间隙小于阈值则同段）</li>
 * </ol>
 * 输入坐标约定为「顶部原点、y 向下」（由 {@link PdfTextExtractor} 归一化），
 * 输出段落的 y 亦为向下坐标，供生成阶段直接使用。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Component
public class ParagraphAssembler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ParagraphAssembler.class);

    /** Y 行聚类阈值：中位字高 × 该值，超过视为不同行 */
    private static final double LINE_CLUSTER_FACTOR = 0.7;

    /** 段聚类阈值：相邻行垂直间隙（行距）小于 中位行高 × 该值 视为同段 */
    private static final double PARAGRAPH_GAP_FACTOR = 0.9;

    /** 相邻行合并前的最小间隙（避免超小阈值把不同行误合并） */
    private static final double MIN_PARAGRAPH_GAP = 2.0;

    /** 最少字级框数：过少不可能是有效版面 */
    private static final int MIN_BOXES = 2;

    /**
     * 将字级坐标聚拢为段落列表（按阅读顺序）
     *
     * @param boxes 该页字级坐标（同页）
     * @return 段落列表（从版面底部到顶部排序，即 PDF y 从大到小）
     */
    public List<Paragraph> assemble(List<WordBox> boxes) {
        if (boxes == null || boxes.size() < MIN_BOXES) {
            return List.of();
        }

        List<List<WordBox>> lines = clusterIntoLines(boxes);
        if (lines.isEmpty()) {
            return List.of();
        }

        List<Line> textLines = lines.stream()
                .map(this::toLine)
                .sorted(Comparator.comparingDouble(Line::y)) // y 向下：顶部行 y 小，靠前
                .toList();

        return clusterIntoParagraphs(textLines);
    }

    // ======================== 行聚类 ========================

    /**
     * 按 Y 中心聚类成文本行
     */
    private List<List<WordBox>> clusterIntoLines(List<WordBox> boxes) {
        double lineThreshold = medianHeight(boxes) * LINE_CLUSTER_FACTOR;

        // 按 Y 中心升序（y 向下：顶部 y 小，先排）
        List<WordBox> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(b -> b.y() + b.height() / 2.0));

        List<List<WordBox>> lines = new ArrayList<>();
        for (WordBox box : sorted) {
            double centerY = box.y() + box.height() / 2.0;
            if (!lines.isEmpty()) {
                List<WordBox> lastLine = lines.get(lines.size() - 1);
                double lastCenter = lastLine.stream()
                        .mapToDouble(b -> b.y() + b.height() / 2.0)
                        .average().orElse(centerY);
                if (Math.abs(centerY - lastCenter) < lineThreshold) {
                    lastLine.add(box);
                    continue;
                }
            }
            List<WordBox> newLine = new ArrayList<>();
            newLine.add(box);
            lines.add(newLine);
        }
        return lines;
    }

    // ======================== 行内合并 ========================

    /**
     * 将一行内字符按 X 排序合并为一行文本，取包围盒与主字号
     */
    private Line toLine(List<WordBox> boxes) {
        boxes.sort(Comparator.comparingDouble(WordBox::x));

        StringBuilder sb = new StringBuilder();
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = 0, y1 = 0;
        float lastXEnd = 0;
        for (WordBox b : boxes) {
            // 相邻字符间若存在明显横向空隙（字宽一半以上），补一个空格（用于还原词间距）
            float gap = b.x() - lastXEnd;
            if (sb.length() > 0 && gap > medianWidth(boxes) * 0.6) {
                sb.append(' ');
            }
            sb.append(b.text());
            lastXEnd = b.x() + b.width();
            x0 = Math.min(x0, b.x());
            y0 = Math.min(y0, b.y());
            x1 = Math.max(x1, b.x() + b.width());
            y1 = Math.max(y1, b.y() + b.height());
        }

        float height = (float) medianHeight(boxes);
        // 单行字符可能有字形起伏，用包围盒高度兜底但不至于过大
        float actualHeight = Math.max(height, (float) (y1 - y0));

        return new Line(sb.toString(), x0, y0, x1 - x0, actualHeight, dominantFontSize(boxes));
    }

    /**
     * 取行内出现次数最多的字号（pt）
     */
    private float dominantFontSize(List<WordBox> boxes) {
        Map<Float, Integer> freq = new HashMap<>();
        for (WordBox b : boxes) {
            float fs = b.height();
            freq.merge(fs, 1, Integer::sum);
        }
        return freq.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(18f);
    }

    // ======================== 段聚类 ========================

    /**
     * 按行距聚成段：相邻行垂直间隙小于阈值则同段
     * <p>
     * 行间隙 = 前一行底边 - 后一行顶边（PDF y 向上，前一行在顶部）。
     */
    private List<Paragraph> clusterIntoParagraphs(List<Line> lines) {
        List<Paragraph> paragraphs = new ArrayList<>();

        // 段聚类阈值以「中位行高」为基准，避免标题等大字号行拉高阈值导致误合并
        double[] hs = lines.stream().mapToDouble(Line::height).sorted().toArray();
        double medianLineHeight = hs.length > 0 ? hs[hs.length / 2] : 16.0;
        double gapThreshold = Math.max(medianLineHeight * PARAGRAPH_GAP_FACTOR, MIN_PARAGRAPH_GAP);

        StringBuilder text = new StringBuilder();
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = 0, y1 = 0;
        float fontSize = lines.get(0).fontSize();
        Line prev = null;

        for (Line line : lines) {
            boolean newParagraph = false;
            if (prev != null) {
                // 垂直间隙：当前行顶边 到 前一行底边 的距离（y 向下）
                float gap = line.y() - (prev.y() + prev.height());
                if (gap > gapThreshold) {
                    newParagraph = true; // 间隙过大 → 新段
                }
            }
            if (newParagraph) {
                paragraphs.add(new Paragraph(text.toString().trim(), x0, y0, x1 - x0, y1 - y0, fontSize));
                text.setLength(0);
                x0 = Float.MAX_VALUE;
                y0 = Float.MAX_VALUE;
                x1 = 0;
                y1 = 0;
            }

            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(line.text());

            x0 = Math.min(x0, line.x());
            y0 = Math.min(y0, line.y());
            x1 = Math.max(x1, line.x() + line.width());
            y1 = Math.max(y1, line.y() + line.height());
            fontSize = line.fontSize();
            prev = line;
        }

        if (text.length() > 0) {
            paragraphs.add(new Paragraph(text.toString().trim(), x0, y0, x1 - x0, y1 - y0, fontSize));
        }

        LOGGER.info("[ParagraphAssembler#assemble] boxes={}, paragraphs={}", lines.size(), paragraphs.size());
        return paragraphs;
    }

    // ======================== 工具 ========================

    private double medianHeight(List<WordBox> boxes) {
        List<Float> hs = boxes.stream().map(WordBox::height).sorted().toList();
        return hs.get(hs.size() / 2);
    }

    private double medianWidth(List<WordBox> boxes) {
        List<Float> ws = boxes.stream().map(WordBox::width).sorted().toList();
        return ws.get(ws.size() / 2);
    }

    /** 行合并结果 */
    private record Line(String text, float x, float y, float width, float height, float fontSize) {
    }
}
