package com.toolbox.service.pdf.toppt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 表格检测器 — 从字级坐标识别连续「表格行组」，重建网格
 * <p>
 * 思路：先按 Y 聚类成行；每行按 X 中心聚类得到该行的列锚点；
 * 列数 ≥2 的行为候选表格行；相邻候选行（列锚点相近）组成表格行组；
 * 组内行数 ≥3 则识别为表格，按公共列锚点重建单元格网格。
 * 对规整表格有效；合并单元格/嵌套表近似。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Component
public class TableDetector {

    private static final Logger LOGGER = LoggerFactory.getLogger(TableDetector.class);

    /** 行聚类因子：中位字高 × 该值，超过视为不同行 */
    private static final double LINE_CLUSTER_FACTOR = 0.7;
    /** X 聚类因子：中位字宽 × 该值，超过视为不同列。
     *  注意：输入为字符级坐标（字间距约 1 字宽），需大于 1 以跨越同词字符间距 */
    private static final double COL_CLUSTER_FACTOR = 1.5;
    /** 相邻行列锚点匹配容差 */
    private static final double ANCHOR_TOLERANCE = 20.0;
    /** 最少表格行数 */
    private static final int MIN_TABLE_ROWS = 2;

    /**
     * 检测页面中的表格
     *
     * @param boxes 页面字级坐标（y 向下）
     * @return 检测到的表格（网格 + 组成表格的字级框）；无表格返回空列表
     */
    public List<DetectedTable> detect(List<WordBox> boxes) {
        if (boxes == null || boxes.size() < 10) {
            return List.of();
        }

        // 1. 按 Y 聚类成行
        List<List<WordBox>> rows = clusterIntoRows(boxes);
        if (rows.size() < MIN_TABLE_ROWS) {
            return List.of();
        }

        // 2. 每行计算列锚点（用全页统一聚类距离，避免窄数字行拆列）
        double clusterDist = medianWidth(boxes) * COL_CLUSTER_FACTOR;
        List<List<Float>> rowAnchors = new ArrayList<>();
        for (List<WordBox> row : rows) {
            rowAnchors.add(columnAnchors(row, clusterDist));
        }

        // 3. 扫描连续表格行组，重建网格
        List<DetectedTable> result = new ArrayList<>();
        List<List<WordBox>> group = null;
        List<Float> groupAnchors = null;
        for (int i = 0; i < rows.size(); i++) {
            List<Float> anchors = rowAnchors.get(i);
            boolean isTableRow = anchors.size() >= 2;
            if (isTableRow && (group == null || anchorsSimilar(anchors, groupAnchors))) {
                // 续入当前表格行组
                if (group == null) {
                    group = new ArrayList<>();
                    groupAnchors = anchors;
                }
                group.add(rows.get(i));
            } else {
                // 结束当前组，尝试提交
                if (group != null && group.size() >= MIN_TABLE_ROWS) {
                    result.add(buildTable(group, groupAnchors));
                }
                group = isTableRow ? new ArrayList<>() : null;
                groupAnchors = isTableRow ? anchors : null;
                if (isTableRow) {
                    group.add(rows.get(i));
                }
            }
        }
        if (group != null && group.size() >= MIN_TABLE_ROWS) {
            result.add(buildTable(group, groupAnchors));
        }

        if (!result.isEmpty()) {
            LOGGER.info("[TableDetector#detect] tables={}", result.size());
        }
        return result;
    }

    /**
     * 由表格行组构建 DetectedTable
     */
    private DetectedTable buildTable(List<List<WordBox>> group, List<Float> anchors) {
        List<List<String>> grid = new ArrayList<>();
        List<WordBox> allBoxes = new ArrayList<>();
        for (List<WordBox> row : group) {
            grid.add(buildRow(row, anchors));
            allBoxes.addAll(row);
        }
        return new DetectedTable(grid, allBoxes);
    }

    // ======================== 行聚类 ========================

    private List<List<WordBox>> clusterIntoRows(List<WordBox> boxes) {
        double threshold = medianHeight(boxes) * LINE_CLUSTER_FACTOR;
        List<WordBox> sorted = new ArrayList<>(boxes);
        sorted.sort(Comparator.comparingDouble(b -> b.y() + b.height() / 2.0));

        List<List<WordBox>> rows = new ArrayList<>();
        for (WordBox box : sorted) {
            double centerY = box.y() + box.height() / 2.0;
            if (!rows.isEmpty()) {
                List<WordBox> last = rows.get(rows.size() - 1);
                double lastCenter = last.stream().mapToDouble(b -> b.y() + b.height() / 2.0).average().orElse(centerY);
                if (Math.abs(centerY - lastCenter) < threshold) {
                    last.add(box);
                    continue;
                }
            }
            List<WordBox> newRow = new ArrayList<>();
            newRow.add(box);
            rows.add(newRow);
        }
        return rows;
    }

    // ======================== 列锚点 ========================

    /**
     * 行内 X 中心贪心聚类成列锚点
     *
     * @param row         该行字级框
     * @param clusterDist 全局聚类距离（全页中位字宽 × 因子）
     */
    private List<Float> columnAnchors(List<WordBox> row, double clusterDist) {
        if (row.size() < 2) {
            return List.of();
        }
        List<Float> centers = row.stream().map(b -> b.x() + b.width() / 2.0f).sorted().toList();

        // 相邻 gap 聚类：排序后相邻点间距 < clusterDist 则同簇，避免均值漂移
        List<List<Float>> clusters = new ArrayList<>();
        List<Float> current = new ArrayList<>();
        current.add(centers.get(0));
        clusters.add(current);
        for (int i = 1; i < centers.size(); i++) {
            float c = centers.get(i);
            float last = current.get(current.size() - 1);
            if (c - last <= clusterDist) {
                current.add(c);
            } else {
                current = new ArrayList<>();
                current.add(c);
                clusters.add(current);
            }
        }
        return clusters.stream().map(cl -> {
            List<Float> s = new ArrayList<>(cl);
            s.sort(Float::compareTo);
            return s.get(s.size() / 2);
        }).sorted().toList();
    }

    // ======================== 表格行组 ========================

    private boolean anchorsSimilar(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (Math.abs(a.get(i) - b.get(i)) > ANCHOR_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    // ======================== 网格重建 ========================

    /**
     * 将行词分配到最近列锚点
     */
    private List<String> buildRow(List<WordBox> row, List<Float> anchors) {
        List<String> cells = new ArrayList<>(Collections.nCopies(anchors.size(), ""));
        for (WordBox box : row) {
            float cx = box.x() + box.width() / 2.0f;
            int idx = nearest(cx, anchors);
            if (cells.get(idx).isEmpty()) {
                cells.set(idx, box.text());
            } else {
                cells.set(idx, cells.get(idx) + box.text()); // 字符级直接拼接，不加空格
            }
        }
        return cells;
    }

    private int nearest(float x, List<Float> anchors) {
        int best = 0;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < anchors.size(); i++) {
            float d = Math.abs(x - anchors.get(i));
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
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

    /**
     * 检测到的表格
     *
     * @param grid 网格（每行 = 一列字符串）
     * @param boxes 组成该表格的字级框（用于从段落聚拢中剔除）
     */
    public record DetectedTable(List<List<String>> grid, List<WordBox> boxes) {
    }
}
