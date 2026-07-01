package com.toolbox.service.convert.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.convert.ConvertService;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.AlternativeFormatInputPart;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.relationships.Relationship;
import org.docx4j.wml.CTAltChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * 文件转换服务实现
 *
 * <p>使用 Flexmark 将 Markdown 转为 HTML，再通过 docx4j AltChunk
 * 将 HTML 嵌入 DOCX。Word 打开时自动渲染标题、粗体、表格、
 * 代码块等完整格式。</p>
 *
 * @author toolbox
 * @since 2026-07-01
 */
@Service
public class ConvertServiceImpl implements ConvertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertServiceImpl.class);

    private static final String HTML_TEMPLATE = "<!DOCTYPE html>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head>"
            + "<meta charset=\"UTF-8\"/>"
            + "<style>"
            + "  body { font-family: 'Microsoft YaHei', SimSun, sans-serif; font-size: 11pt; line-height: 1.7; color: #1a1a2e; }"
            + "  h1 { font-size: 18pt; font-weight: bold; color: #1a1a2e; border-bottom: 2px solid #ccc; padding-bottom: 4pt; margin-top: 12pt; }"
            + "  h2 { font-size: 14pt; font-weight: bold; color: #333; border-bottom: 1px solid #ddd; padding-bottom: 2pt; margin-top: 10pt; }"
            + "  h3 { font-size: 12pt; font-weight: bold; color: #444; margin-top: 8pt; }"
            + "  h4, h5, h6 { font-weight: bold; margin-top: 6pt; }"
            + "  p { margin: 4pt 0; }"
            + "  code { font-family: Consolas, 'Courier New', monospace; font-size: 9pt; background: #f0f0f0; padding: 2px 4px; }"
            + "  pre { background: #f5f5f5; padding: 8pt; border-left: 3px solid #3b82f6; font-family: Consolas, 'Courier New', monospace; font-size: 9pt; line-height: 1.4; white-space: pre-wrap; }"
            + "  table { border-collapse: collapse; width: 100%%; margin: 6pt 0; }"
            + "  th, td { border: 1px solid #ccc; padding: 4pt 8pt; text-align: left; }"
            + "  th { background: #f0f0f0; font-weight: bold; }"
            + "  tr:nth-child(even) { background: #fafafa; }"
            + "  blockquote { border-left: 3px solid #3b82f6; padding-left: 8pt; color: #555; margin-left: 0; }"
            + "  a { color: #3b82f6; }"
            + "  del { color: #999; text-decoration: line-through; }"
            + "  ul, ol { padding-left: 20pt; margin: 4pt 0; }"
            + "  li { margin: 2pt 0; }"
            + "  hr { border: none; border-top: 1px solid #ccc; margin: 8pt 0; }"
            + "  img { max-width: 100%%; }"
            + "  input[type=\"checkbox\"] { margin-right: 4pt; }"
            + "</style>"
            + "</head>"
            + "<body>%s</body>"
            + "</html>";

    private final Parser parser;
    private final HtmlRenderer renderer;

    public ConvertServiceImpl() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create()
        ));
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    @Override
    public byte[] convertMarkdownToDocx(String markdownContent) {
        try {
            // Step 1: Markdown → HTML (Flexmark GFM)
            String htmlBody = renderer.render(parser.parse(markdownContent));
            String fullHtml = String.format(HTML_TEMPLATE, htmlBody);

            // Step 2: 创建 DOCX 并通过 AltChunk 嵌入 HTML
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage();
            MainDocumentPart mainPart = wordMLPackage.getMainDocumentPart();

            // 添加 AltChunk 引用 (HTML 类型)
            AlternativeFormatInputPart altChunkPart = new AlternativeFormatInputPart(
                    org.docx4j.openpackaging.parts.WordprocessingML.AltChunkType.Html
            );
            altChunkPart.setBinaryData(fullHtml.getBytes("UTF-8"));
            Relationship altChunkRel = mainPart.addTargetPart(altChunkPart);

            // 创建 CTAltChunk 元素
            CTAltChunk ctAltChunk = new CTAltChunk();
            ctAltChunk.setId(altChunkRel.getId());
            mainPart.addObject(ctAltChunk);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            wordMLPackage.save(outputStream);

            LOGGER.info("Markdown 转 DOCX 成功, 输入 {} 字符, 输出 {} bytes",
                    markdownContent.length(), outputStream.size());
            return outputStream.toByteArray();
        } catch (Exception e) {
            LOGGER.error("Markdown 转 DOCX 失败, 内容长度: {}", markdownContent.length(), e);
            throw new BusinessException(ErrorCodeEnum.CONVERT_ERROR);
        }
    }
}
