package com.toolbox.controller.convert;

import com.toolbox.model.common.R;
import com.toolbox.model.dto.ConvertResultDTO;
import com.toolbox.service.convert.ConvertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 文件转换接口
 *
 * @author toolbox
 * @since 2026-07-01
 */
@RestController
@RequestMapping("/api/convert")
public class ConvertController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConvertController.class);
    private final ConvertService convertService;

    public ConvertController(ConvertService convertService) {
        this.convertService = convertService;
    }

    /**
     * Markdown 转 DOCX：接受 Markdown 文本，返回 DOCX 文件下载
     */
    @PostMapping("/md-to-docx")
    public ResponseEntity<Resource> convertMdToDocx(@RequestParam("content") String markdownContent,
                                                     @RequestParam(value = "filename", defaultValue = "output") String filename) {
        LOGGER.info("Markdown 转 DOCX 请求, 内容长度: {}", markdownContent.length());
        byte[] docxBytes = convertService.convertMarkdownToDocx(markdownContent);
        ByteArrayResource resource = new ByteArrayResource(docxBytes);
        String encodedFilename = URLEncoder.encode(filename + ".docx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .body(resource);
    }
}
