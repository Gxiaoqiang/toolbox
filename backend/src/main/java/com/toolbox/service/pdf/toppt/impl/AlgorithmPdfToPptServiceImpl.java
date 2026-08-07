package com.toolbox.service.pdf.toppt.impl;

import com.toolbox.service.pdf.toppt.DocxWriter;
import com.toolbox.service.pdf.toppt.Paragraph;
import com.toolbox.service.pdf.toppt.ParagraphAssembler;
import com.toolbox.service.pdf.toppt.PdfImageExtractor;
import com.toolbox.service.pdf.toppt.PdfTextExtractor;
import com.toolbox.service.pdf.toppt.PdfToPptService;
import com.toolbox.service.pdf.toppt.PptxWriter;
import com.toolbox.service.pdf.toppt.TableDetector;
import com.toolbox.service.pdf.toppt.WordBox;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 算法还原引擎 — PDFBox 提取坐标 → 段落聚拢/表格检测/图片提取 → 生成可编辑文档
 * <p>
 * 纯离线、快。
 * <ul>
 *   <li>{@code format=ppt}：整页底图 + 可编辑文本框（B1），版面按原样。</li>
 *   <li>{@code format=word}：段落 + 标题 + 表格 + 内嵌图片 → POI XWPF，线性流重排。</li>
 * </ul>
 * 图形/图表不重建。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Service
public class AlgorithmPdfToPptServiceImpl implements PdfToPptService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlgorithmPdfToPptServiceImpl.class);

    private final PdfTextExtractor textExtractor;
    private final ParagraphAssembler assembler;
    private final TableDetector tableDetector;
    private final PdfImageExtractor imageExtractor;
    private final PptxWriter pptxWriter;
    private final DocxWriter docxWriter;

    public AlgorithmPdfToPptServiceImpl(PdfTextExtractor textExtractor,
                                        ParagraphAssembler assembler,
                                        TableDetector tableDetector,
                                        PdfImageExtractor imageExtractor,
                                        PptxWriter pptxWriter,
                                        DocxWriter docxWriter) {
        this.textExtractor = textExtractor;
        this.assembler = assembler;
        this.tableDetector = tableDetector;
        this.imageExtractor = imageExtractor;
        this.pptxWriter = pptxWriter;
        this.docxWriter = docxWriter;
    }

    @Override
    public boolean isAvailable() {
        return true; // 算法引擎无外部依赖
    }

    @Override
    public byte[] convert(byte[] pdfBytes, String originalFilename, String format) throws IOException {
        boolean word = "word".equalsIgnoreCase(format);
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            if (word) {
                return buildWord(doc);
            }
            return buildPpt(doc);
        }
    }

    /**
     * Word：每页识别段落/表格/图片，按 y 排序后写 docx
     */
    private byte[] buildWord(PDDocument doc) throws IOException {
        List<List<WordBox>> pageBoxes = textExtractor.extract(doc);
        List<DocxWriter.DocElement> elements = new ArrayList<>();

        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            List<WordBox> boxes = new ArrayList<>(pageBoxes.get(i));

            // 1. 表格检测 + 剔除表格区域的字级框
            List<TableDetector.DetectedTable> tables = tableDetector.detect(boxes);
            Set<WordBox> tableBoxes = new HashSet<>();
            for (TableDetector.DetectedTable t : tables) {
                tableBoxes.addAll(t.boxes());
            }
            boxes.removeIf(tableBoxes::contains);

            // 2. 剩余字级框 → 段落聚拢
            List<Paragraph> paragraphs = assembler.assemble(boxes);

            // 3. 页面块（段落/表格/图片）按 y 排序
            List<DocxWriter.DocElement> pageElements = new ArrayList<>();
            for (Paragraph p : paragraphs) {
                pageElements.add(DocxWriter.DocElement.paragraph(p));
            }
            for (TableDetector.DetectedTable t : tables) {
                float y = (float) t.boxes().stream().mapToDouble(WordBox::y).min().orElse(0);
                pageElements.add(DocxWriter.DocElement.table(t.grid(), y));
            }
            for (PdfImageExtractor.ImageInfo img : imageExtractor.extract(page)) {
                pageElements.add(DocxWriter.DocElement.image(img.pngBytes(), img.y()));
            }
            pageElements.sort(Comparator.comparingDouble(DocxWriter.DocElement::y));

            elements.addAll(pageElements);
        }

        LOGGER.info("[AlgorithmPdfToPptServiceImpl#buildWord] pages={}, elements={}",
                doc.getNumberOfPages(), elements.size());
        return docxWriter.write(elements);
    }

    /**
     * PPT：整页底图 + 段落文本框
     */
    private byte[] buildPpt(PDDocument doc) throws IOException {
        int pages = doc.getNumberOfPages();
        List<List<WordBox>> pageBoxes = textExtractor.extract(doc);

        PDFRenderer renderer = new PDFRenderer(doc);
        List<PptxWriter.PageData> pageDataList = new ArrayList<>(pages);

        for (int i = 0; i < pages; i++) {
            PDPage page = doc.getPage(i);
            float width = page.getMediaBox().getWidth();
            float height = page.getMediaBox().getHeight();

            List<Paragraph> paragraphs = assembler.assemble(pageBoxes.get(i));

            BufferedImage bg = renderer.renderImageWithDPI(i, PptxWriter.BACKGROUND_DPI);
            byte[] bgPng = pptxWriter.encodeBackground(bg);

            pageDataList.add(new PptxWriter.PageData(width, height, bgPng, paragraphs));
        }

        LOGGER.info("[AlgorithmPdfToPptServiceImpl#buildPpt] pages={}", pages);
        return pptxWriter.write(pageDataList);
    }
}
