package com.toolbox.service.ocr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 表格结构识别器 — 基于 OCR 词级坐标（wordBoxes）重建表格网格
 * <p>
 * OCR 纯文本输出会丢失表格结构，必须用每个词的包围盒做布局分析：
 * <ol>
 *     <li>按 Y 中心聚类成文本行</li>
 *     <li>按 X 中心聚类成列锚点（对 OCR 分词不一致鲁棒）</li>
 *     <li>每行词按 X 分配到最近列锚点，切分单元格</li>
 * </ol>
 * 采用列锚点对齐而非词边界 gap 检测，因为 OCR 对不同行的分词可能不同
 * （如表头拆成多词、数据行合并成一词），基于边界的检测会因位置不齐而失败。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Component
public class TableStructureRecognizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TableStructureRecognizer.class);

    /** 最少词数：少于该数量不可能是表格 */
    private static final int MIN_WORDS = 6;

    /** 最少行数 */
    private static final int MIN_ROWS = 2;

    /** 列锚点需至少在该比例的行中出现 */
    private static final double ANCHOR_SUPPORT = 0.3;

    /** 列一致性要求：至少 50% 行能分配到全部列 */
    private static final double COLUMN_CONSISTENCY = 0.5;

    /** X 聚类距离因子：中位字宽 × 该值，超过视为不同列 */
    private static final double CLUSTER_DIST_FACTOR = 0.8;

    /**
     * 从词级坐标识别表格网格
     *
     * @param boxes 该页 OCR 的词级包围盒（Tesseract 像素坐标，左上角原点）
     * @return 表格网格（每行 = 一列字符串）；非表格返回 null
     */
    public List<List<String>> recognize(List<OcrResult.WordBox> boxes) {
        if (boxes == null || boxes.size() < MIN_WORDS) {
            return null;
        }

        // 1. 按 Y 中心聚类成文本行
        List<List<OcrResult.WordBox>> rows = clusterIntoRows(boxes);
        if (rows.size() < MIN_ROWS) {
            return null;
        }

        // 2. 按 X 中心聚类成列锚点
        double clusterDist = medianWidth(boxes) * CLUSTER_DIST_FACTOR;
        List<Float> anchors = findColumnAnchors(rows, clusterDist);
        if (anchors.size() < 2) {
            return null; // 少于 2 列 → 非表格
        }

        // 3. 每行词按 X 分配到最近列锚点
        List<List<String>> grid = new ArrayList<>();
        for (List<OcrResult.WordBox> row : rows) {
            grid.add(buildRow(row, anchors));
        }

        // 4. 列一致性校验：多数行能分配到全部列
        long match = grid.stream().filter(r -> isFullyPopulated(r, anchors.size())).count();
        if (match < grid.size() * COLUMN_CONSISTENCY) {
            return null;
        }

        LOGGER.info("[TableStructureRecognizer#recognize] table: {} rows x {} cols", grid.size(), anchors.size());
        return grid;
    }

    // ======================== 行聚类 ========================

    /**
     * 按 Y 中心聚类成文本行
     */
    private List<List<OcrResult.WordBox>> clusterIntoRows(List<OcrResult.WordBox> boxes) {
        List<OcrResult.WordBox> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(b -> b.y() + b.height() / 2.0));

        double rowThreshold = medianHeight(boxes) * 0.8;
        List<List<OcrResult.WordBox>> rows = new ArrayList<>();

        for (OcrResult.WordBox box : sorted) {
            double centerY = box.y() + box.height() / 2.0;
            if (!rows.isEmpty()) {
                List<OcrResult.WordBox> lastRow = rows.get(rows.size() - 1);
                double lastRowCenter = lastRow.stream()
                        .mapToDouble(b -> b.y() + b.height() / 2.0)
                        .average().orElse(centerY);
                if (Math.abs(centerY - lastRowCenter) < rowThreshold) {
                    lastRow.add(box);
                    continue;
                }
            }
            List<OcrResult.WordBox> newRow = new ArrayList<>();
            newRow.add(box);
            rows.add(newRow);
        }
        return rows;
    }

    // ======================== 列锚点聚类 ========================

    /**
     * 按 X 中心贪心聚类成列锚点，过滤支持度不足的列
     */
    private List<Float> findColumnAnchors(List<List<OcrResult.WordBox>> rows, double clusterDist) {
        // 收集所有词的 X 中心
        List<Float> centers = new ArrayList<>();
        for (List<OcrResult.WordBox> row : rows) {
            for (OcrResult.WordBox box : row) {
                centers.add(box.x() + box.width() / 2.0f);
            }
        }
        centers.sort(Float::compareTo);

        // 贪心聚类：与组均值差在 clusterDist 内则归组
        List<List<Float>> clusters = new ArrayList<>();
        for (float center : centers) {
            boolean placed = false;
            for (List<Float> cluster : clusters) {
                double avg = cluster.stream().mapToDouble(Float::doubleValue).average().orElse(center);
                if (Math.abs(center - avg) <= clusterDist) {
                    cluster.add(center);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                List<Float> newCluster = new ArrayList<>();
                newCluster.add(center);
                clusters.add(newCluster);
            }
        }

        // 每列取中位锚点，过滤支持度不足的列
        int totalRows = rows.size();
        List<Float> anchors = new ArrayList<>();
        for (List<Float> cluster : clusters) {
            if (cluster.size() < Math.max(MIN_ROWS, totalRows * ANCHOR_SUPPORT)) {
                continue;
            }
            anchors.add(median(cluster));
        }
        anchors.sort(Float::compareTo);
        return anchors;
    }

    /**
     * 将行内词分配到最近列锚点，生成单元格
     */
    private List<String> buildRow(List<OcrResult.WordBox> row, List<Float> anchors) {
        List<String> cells = new ArrayList<>(Collections.nCopies(anchors.size(), ""));
        for (OcrResult.WordBox box : row) {
            float centerX = box.x() + box.width() / 2.0f;
            int idx = nearestAnchor(centerX, anchors);
            if (cells.get(idx).isEmpty()) {
                cells.set(idx, box.text());
            } else {
                cells.set(idx, cells.get(idx) + " " + box.text());
            }
        }
        return cells;
    }

    /**
     * 最近列锚点下标
     */
    private int nearestAnchor(float x, List<Float> anchors) {
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < anchors.size(); i++) {
            float dist = Math.abs(x - anchors.get(i));
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    /**
     * 行是否分配到全部列（列一致性依据）
     */
    private boolean isFullyPopulated(List<String> cells, int colCount) {
        if (cells.size() < colCount) {
            return false;
        }
        return cells.stream().filter(c -> !c.isEmpty()).count() >= colCount - 1;
    }

    // ======================== 统计工具 ========================

    private double medianWidth(List<OcrResult.WordBox> boxes) {
        return boxes.stream().mapToDouble(OcrResult.WordBox::width).sorted()
                .skip(Math.max(0, boxes.size() / 2)).findFirst().orElse(10.0);
    }

    private double medianHeight(List<OcrResult.WordBox> boxes) {
        return boxes.stream().mapToDouble(OcrResult.WordBox::height).sorted()
                .skip(Math.max(0, boxes.size() / 2)).findFirst().orElse(20.0);
    }

    private float median(List<Float> values) {
        List<Float> sorted = new ArrayList<>(values);
        sorted.sort(Float::compareTo);
        return sorted.get(sorted.size() / 2);
    }
}
