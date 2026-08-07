package com.toolbox.service.pdf.toppt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ParagraphAssembler 单元测试 — 验证字级坐标 → 段落级文本框的聚拢逻辑
 * <p>
 * 坐标采用 PDF 用户空间（y 向上，顶部行 y 大）。
 *
 * @author toolbox
 * @since 2026-08-05
 */
class ParagraphAssemblerTest {

    private final ParagraphAssembler assembler = new ParagraphAssembler();

    /**
     * 模拟一页（y 向下：顶部 y 小）：标题(32pt, y=30) + 两行正文(16pt, y=90/120)，行距 30。
     */
    private List<WordBox> titlePlusBody() {
        return List.of(
                // 标题（拆成中英文两段，模拟实测现象）
                new WordBox(50, 30, 200, 32, "产品技术"),
                new WordBox(250, 30, 150, 32, "方案汇报"),
                // 正文行1（y=90）
                new WordBox(60, 90, 300, 16, "本方案采用 Java 后端"),
                // 正文行2（y=120）
                new WordBox(60, 120, 250, 16, "实现 PDF 文档处理"));
    }

    @Test
    @DisplayName("同 y 多片段合并成一行，且按行距聚成段")
    void assemble_titlePlusBody_returnsTwoParagraphs() {
        List<Paragraph> ps = assembler.assemble(titlePlusBody());

        assertThat(ps).hasSize(2);
        // 顶部标题
        assertThat(ps.get(0).text()).isEqualTo("产品技术方案汇报");
        assertThat(ps.get(0).fontSize()).isEqualTo(32f);
        assertThat(ps.get(0).x()).isEqualTo(50f);
        // 正文两行聚成一个段落，行间用换行分隔
        assertThat(ps.get(1).text()).isEqualTo("本方案采用 Java 后端\n实现 PDF 文档处理");
        assertThat(ps.get(1).fontSize()).isEqualTo(16f);
    }

    @Test
    @DisplayName("行距过大时拆成两个段落")
    void assemble_largeGap_splitsParagraphs() {
        List<WordBox> boxes = List.of(
                new WordBox(50, 50, 100, 16, "标题"),
                new WordBox(50, 300, 100, 16, "很远的一段")); // 与标题行距 234

        List<Paragraph> ps = assembler.assemble(boxes);

        assertThat(ps).hasSize(2);
        assertThat(ps.get(0).text()).isEqualTo("标题");
        assertThat(ps.get(1).text()).isEqualTo("很远的一段");
    }

    @Test
    @DisplayName("词间距明显时补空格")
    void assemble_wordGap_insertsSpace() {
        List<WordBox> boxes = List.of(
                new WordBox(50, 400, 40, 16, "Hello"),
                new WordBox(150, 400, 50, 16, "World")); // 间隙 60 > 中位字宽*0.6

        List<Paragraph> ps = assembler.assemble(boxes);

        assertThat(ps).hasSize(1);
        assertThat(ps.get(0).text()).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("字级框过少时返回空列表")
    void assemble_tooFewBoxes_returnsEmpty() {
        List<Paragraph> ps = assembler.assemble(List.of(
                new WordBox(0, 0, 10, 10, "a")));

        assertThat(ps).isEmpty();
    }
}
