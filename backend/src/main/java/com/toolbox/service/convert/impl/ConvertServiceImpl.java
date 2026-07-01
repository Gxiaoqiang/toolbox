package com.toolbox.service.convert.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.convert.ConvertService;
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.html.HtmlRenderer;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

/**
 * 文件转换服务实现
 *
 * @author toolbox
 * @since 2026-07-01
 */
@Service
public class ConvertServiceImpl implements ConvertService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertServiceImpl.class);

    @Override
    public byte[] convertMarkdownToDocx(String markdownContent) {
        try {
            // Markdown → HTML
            Parser parser = Parser.builder().build();
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            String html = renderer.render(parser.parse(markdownContent));

            // HTML → DOCX
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.createPackage();
            MainDocumentPart mainDocumentPart = wordMLPackage.getMainDocumentPart();
            mainDocumentPart.addStyledParagraphOfText("Title", "Converted Document");
            mainDocumentPart.addParagraphOfText(html.replaceAll("<[^>]+>", ""));
            wordMLPackage.save(outputStream);

            LOGGER.info("Markdown 转 DOCX 成功, 输出大小: {} bytes", outputStream.size());
            return outputStream.toByteArray();
        } catch (Exception e) {
            LOGGER.error("Markdown 转 DOCX 失败, 内容长度: {}", markdownContent.length(), e);
            throw new BusinessException(ErrorCodeEnum.CONVERT_ERROR);
        }
    }
}
