package com.toolbox.service.ocr;

import com.toolbox.service.ocr.OcrResult.WordBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TableStructureRecognizer 单元测试 — 用模拟词坐标验证表格重建逻辑
 *
 * @author toolbox
 * @since 2026-08-05
 */
class TableStructureRecognizerTest {

    private final TableStructureRecognizer recognizer = new TableStructureRecognizer();

    /**
     * 构造一个规整 3 列 x 3 行表格的词坐标。
     * 列 X 坐标对齐，列间隙稳定，模拟清晰表格的 OCR 输出。
     */
    private List<WordBox> regularTableBoxes() {
        // 列起点: col0=0, col1=200, col2=400；词宽约 60，高约 30
        return List.of(
                // 表头 y=10
                new WordBox("名称", 0, 10, 60, 30),
                new WordBox("数量", 200, 10, 60, 30),
                new WordBox("价格", 400, 10, 60, 30),
                // 行1 y=60
                new WordBox("苹果", 0, 60, 60, 30),
                new WordBox("3", 200, 60, 40, 30),
                new WordBox("5元", 400, 60, 60, 30),
                // 行2 y=110
                new WordBox("香蕉", 0, 110, 60, 30),
                new WordBox("2", 200, 110, 40, 30),
                new WordBox("8元", 400, 110, 60, 30));
    }

    @Test
    @DisplayName("规整表格能正确重建为 3x3 网格")
    void recognize_regularTable_returnsGrid() {
        List<List<String>> grid = recognizer.recognize(regularTableBoxes());

        assertThat(grid).isNotNull();
        assertThat(grid).hasSize(3);
        assertThat(grid.get(0)).containsExactly("名称", "数量", "价格");
        assertThat(grid.get(1)).containsExactly("苹果", "3", "5元");
        assertThat(grid.get(2)).containsExactly("香蕉", "2", "8元");
    }

    @Test
    @DisplayName("单列文本（非表格）返回 null")
    void recognize_singleColumn_returnsNull() {
        // 无列间隙：所有词在同一列，无分隔线
        List<WordBox> boxes = List.of(
                new WordBox("一", 0, 10, 30, 30),
                new WordBox("二", 0, 50, 30, 30),
                new WordBox("三", 0, 90, 30, 30));

        assertThat(recognizer.recognize(boxes)).isNull();
    }

    @Test
    @DisplayName("词过少（不足 MIN_WORDS）返回 null")
    void recognize_tooFewWords_returnsNull() {
        List<WordBox> boxes = List.of(
                new WordBox("a", 0, 0, 20, 20),
                new WordBox("b", 100, 0, 20, 20));

        assertThat(recognizer.recognize(boxes)).isNull();
    }
}
