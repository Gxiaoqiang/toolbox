package com.toolbox.service.pdf.toppt.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.toolbox.service.pdf.toppt.PdfToPptService;

import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 重排引擎 — 提取文本 → 大模型结构化/清洗/重排 → POI 生成 PPTX
 * <p>
 * 受非多模态限制：看不到版面坐标，故**不还原原版面**，产出为 AI 内容重排
 * （每页「标题 + 要点」）。
 * <p>
 * 直接调用 deepseek 的 OpenAI 兼容 {@code /chat/completions}，复用
 * {@code toolbox.agent.llm-*} 配置；未配置 api-key 时 {@link #isAvailable()} 返回 false，
 * 前端禁用 AI 选项。
 *
 * @author toolbox
 * @since 2026-08-05
 */
@Service
public class AiPdfToPptServiceImpl implements PdfToPptService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiPdfToPptServiceImpl.class);

    private static final int SLIDE_WIDTH = 720;   // 10 inch (pt)
    private static final int SLIDE_HEIGHT = 540;  // 7.5 inch (pt)

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${toolbox.agent.llm-api-key:}")
    private String apiKey;

    @Value("${toolbox.agent.llm-base-url:https://api.deepseek.com}")
    private String baseUrl;

    @Value("${toolbox.agent.llm-model:deepseek-chat}")
    private String model;

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public byte[] convert(byte[] pdfBytes, String originalFilename, String format) throws IOException {
        if (!isAvailable()) {
            throw new IllegalStateException("AI engine requires LLM api key");
        }

        List<String> pageTexts = extractPageTexts(pdfBytes);
        List<SlideContent> contents = new ArrayList<>();
        for (int i = 0; i < pageTexts.size(); i++) {
            if (!pageTexts.get(i).isBlank()) {
                contents.add(structurePage(pageTexts.get(i), i + 1));
            }
        }

        boolean word = "word".equalsIgnoreCase(format);
        return word ? buildWordDocx(contents) : buildPptPptx(contents);
    }

    /**
     * PPT：每页标题 + 要点
     */
    private byte[] buildPptPptx(List<SlideContent> contents) throws IOException {
        try (XMLSlideShow pptx = new XMLSlideShow()) {
            pptx.setPageSize(new java.awt.Dimension(SLIDE_WIDTH, SLIDE_HEIGHT));
            for (SlideContent content : contents) {
                renderSlide(pptx, content);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pptx.write(out);
            LOGGER.info("[AiPdfToPptServiceImpl#buildPptPptx] slides={}", contents.size());
            return out.toByteArray();
        }
    }

    /**
     * Word：标题 + 要点段落
     */
    private byte[] buildWordDocx(List<SlideContent> contents) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (SlideContent content : contents) {
                XWPFParagraph title = doc.createParagraph();
                XWPFRun titleRun = title.createRun();
                titleRun.setText(content.title());
                titleRun.setBold(true);
                titleRun.setFontSize(18);
                titleRun.setFontFamily("微软雅黑");

                for (String point : content.points()) {
                    XWPFParagraph p = doc.createParagraph();
                    XWPFRun run = p.createRun();
                    run.setText("• " + point);
                    run.setFontSize(14);
                    run.setFontFamily("微软雅黑");
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            LOGGER.info("[AiPdfToPptServiceImpl#buildWordDocx] sections={}", contents.size());
            return out.toByteArray();
        }
    }

    /**
     * 逐页提取纯文本
     */
    private List<String> extractPageTexts(byte[] pdfBytes) throws IOException {
        List<String> texts = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pages = doc.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                texts.add(stripper.getText(doc));
            }
        }
        return texts;
    }

    /**
     * 调用 LLM 将该页文本结构化为「标题 + 要点」
     */
    private SlideContent structurePage(String pageText, int pageNo) throws IOException {
        String userPrompt = "请将以下 PDF 页面文本提炼为一页演示幻灯片，只输出 JSON，不要任何其他文字。"
                + "JSON 格式：{\"title\":\"幻灯片标题\",\"points\":[\"要点1\",\"要点2\"]}\n"
                + "页面文本：\n" + truncate(pageText);

        RestClient client = RestClient.builder().baseUrl(baseUrl).build();
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.3,
                "messages", List.of(
                        Map.of("role", "system",
                                "content", "你是专业的演示文稿生成助手，擅长把文档内容提炼成结构清晰的幻灯片。"),
                        Map.of("role", "user", "content", userPrompt)));

        String respJson = client.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        String content = extractContent(respJson);
        JsonNode node = objectMapper.readTree(content);

        String title = node.path("title").asText("第 " + pageNo + " 页");
        List<String> points = new ArrayList<>();
        node.path("points").forEach(p -> points.add(p.asText()));

        return new SlideContent(title, points);
    }

    /**
     * 从 chat/completions 响应中提取 message.content，容错 JSON 代码块包裹
     */
    private String extractContent(String respJson) throws IOException {
        JsonNode root = objectMapper.readTree(respJson);
        String content = root.path("choices").get(0).path("message").path("content").asText();
        // 去除 ```json ... ``` 包裹
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```[a-zA-Z]*\\n?", "")
                    .replaceFirst("\\n?```$", "").trim();
        }
        return content;
    }

    private String truncate(String text) {
        return text.length() > 4000 ? text.substring(0, 4000) : text;
    }

    /**
     * 渲染一页：标题（大字）+ 要点列表
     */
    private void renderSlide(XMLSlideShow pptx, SlideContent content) {
        XSLFSlide slide = pptx.createSlide();

        // 标题（POI 坐标单位为 point）
        XSLFTextShape titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(36, 36, 648, 63));
        XSLFTextParagraph titleP = titleBox.getTextParagraphs().get(0);
        XSLFTextRun titleRun = titleP.addNewTextRun();
        titleRun.setText(content.title());
        titleRun.setFontSize(32d);
        titleRun.setBold(true);
        titleRun.setFontFamily("微软雅黑");

        // 要点
        XSLFTextShape bodyBox = slide.createTextBox();
        bodyBox.setAnchor(new Rectangle2D.Double(54, 118, 612, 378));
        for (String point : content.points()) {
            XSLFTextParagraph p = bodyBox.addNewTextParagraph();
            XSLFTextRun run = p.addNewTextRun();
            run.setText("• " + point);
            run.setFontSize(18d);
            run.setFontFamily("微软雅黑");
        }
    }

    /**
     * 单页结构化结果
     *
     * @param title  标题
     * @param points 要点列表
     */
    private record SlideContent(String title, List<String> points) {
    }
}
