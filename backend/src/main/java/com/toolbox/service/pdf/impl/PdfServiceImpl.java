package com.toolbox.service.pdf.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.PdfService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PdfServiceImpl implements PdfService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfServiceImpl.class);

    @Override
    public byte[] splitPdf(byte[] pdfBytes, String originalFilename, String mode,
                           String pages, int everyN, boolean preserveMeta) {
        // 去掉原文件名的 .pdf 后缀
        String baseName = originalFilename;
        if (baseName.toLowerCase().endsWith(".pdf")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }

        try (PDDocument sourceDoc = Loader.loadPDF(pdfBytes)) {
            // 检查是否加密
            if (sourceDoc.isEncrypted()) {
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
            }

            int totalPages = sourceDoc.getNumberOfPages();
            LOGGER.info("PDF 切分请求: file={}, mode={}, totalPages={}", originalFilename, mode, totalPages);

            ByteArrayOutputStream zipBos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipBos)) {
                switch (mode) {
                    case "by-page" -> splitByPage(sourceDoc, baseName, preserveMeta, zos, totalPages);
                    case "by-range" -> splitByRange(sourceDoc, baseName, pages, preserveMeta, zos, totalPages);
                    case "by-n" -> splitByN(sourceDoc, baseName, everyN, preserveMeta, zos, totalPages);
                    default -> throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
                }
            }

            LOGGER.info("PDF 切分完成: file={}, mode={}", originalFilename, mode);
            return zipBos.toByteArray();
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            LOGGER.error("PDF 处理异常: file={}", originalFilename, e);
            throw new BusinessException(ErrorCodeEnum.PDF_PROCESS_ERROR);
        }
    }

    private void splitByPage(PDDocument source, String baseName, boolean preserveMeta,
                             ZipOutputStream zos, int totalPages) throws IOException {
        for (int i = 0; i < totalPages; i++) {
            String filename = baseName + "_p" + (i + 1) + ".pdf";
            writeSinglePagePdf(source, i, filename, preserveMeta, zos);
        }
    }

    private void splitByRange(PDDocument source, String baseName, String pages,
                              boolean preserveMeta, ZipOutputStream zos, int totalPages) throws IOException {
        // 解析页码范围，展开为区间列表
        List<int[]> ranges = parsePageRanges(pages, totalPages);
        for (int[] range : ranges) {
            String filename;
            if (range[0] == range[1]) {
                filename = baseName + "_p" + range[0] + ".pdf";
            } else {
                filename = baseName + "_p" + range[0] + "-" + range[1] + ".pdf";
            }
            writePageRangePdf(source, range[0] - 1, range[1] - 1, filename, preserveMeta, zos);
        }
    }

    private void splitByN(PDDocument source, String baseName, int everyN,
                          boolean preserveMeta, ZipOutputStream zos, int totalPages) throws IOException {
        if (everyN < 1) {
            throw new BusinessException(ErrorCodeEnum.PDF_EVERY_N_INVALID);
        }
        int partNum = 1;
        for (int start = 0; start < totalPages; start += everyN) {
            int end = Math.min(start + everyN - 1, totalPages - 1);
            String filename = baseName + "_part" + partNum + ".pdf";
            writePageRangePdf(source, start, end, filename, preserveMeta, zos);
            partNum++;
        }
    }

    /**
     * 解析页码范围字符串，展开为 (起始页, 结束页) 区间列表，并校验重复/重叠/越界
     */
    List<int[]> parsePageRanges(String pages, int totalPages) {
        if (pages == null || pages.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
        }

        // 格式校验：只允许数字、逗号、短横线、空格
        if (!pages.matches("[0-9,\\- ]+")) {
            throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
        }

        String[] parts = pages.split(",");
        List<int[]> ranges = new ArrayList<>();
        Set<Integer> seenPages = new HashSet<>();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
            }

            if (trimmed.contains("-")) {
                String[] pair = trimmed.split("-");
                if (pair.length != 2) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
                }
                int start, end;
                try {
                    start = Integer.parseInt(pair[0].trim());
                    end = Integer.parseInt(pair[1].trim());
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
                }
                if (start > end) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
                }
                // 越界检查
                if (start < 1 || end > totalPages) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE,
                            "（共 " + totalPages + " 页）");
                }
                // 重叠检查
                for (int p = start; p <= end; p++) {
                    if (!seenPages.add(p)) {
                        throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OVERLAP);
                    }
                }
                ranges.add(new int[]{start, end});
            } else {
                int page;
                try {
                    page = Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_FORMAT_ERROR);
                }
                if (page < 1 || page > totalPages) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE,
                            "（共 " + totalPages + " 页）");
                }
                if (!seenPages.add(page)) {
                    throw new BusinessException(ErrorCodeEnum.PDF_PAGE_OVERLAP);
                }
                ranges.add(new int[]{page, page});
            }
        }

        return ranges;
    }

    private void writeSinglePagePdf(PDDocument source, int pageIndex, String filename,
                                    boolean preserveMeta, ZipOutputStream zos) throws IOException {
        try (PDDocument newDoc = new PDDocument()) {
            // 逐页复制，确保内容不变
            PDPage copiedPage = new PDPage(source.getPage(pageIndex).getCOSObject());
            newDoc.addPage(copiedPage);

            if (preserveMeta) {
                copyMetadata(source, newDoc);
            }

            ZipEntry entry = new ZipEntry(filename);
            zos.putNextEntry(entry);
            newDoc.save(zos);
            zos.closeEntry();
        }
    }

    private void writePageRangePdf(PDDocument source, int startIndex, int endIndex,
                                   String filename, boolean preserveMeta, ZipOutputStream zos) throws IOException {
        try (PDDocument newDoc = new PDDocument()) {
            for (int i = startIndex; i <= endIndex; i++) {
                PDPage copiedPage = new PDPage(source.getPage(i).getCOSObject());
                newDoc.addPage(copiedPage);
            }

            if (preserveMeta) {
                copyMetadata(source, newDoc);
            }

            ZipEntry entry = new ZipEntry(filename);
            zos.putNextEntry(entry);
            newDoc.save(zos);
            zos.closeEntry();
        }
    }

    private void copyMetadata(PDDocument source, PDDocument target) {
        PDMetadata meta = source.getDocumentCatalog().getMetadata();
        if (meta != null) {
            target.getDocumentCatalog().setMetadata(meta);
        }
        // 逐字段复制文档信息（PDFBox 3.0 不支持直接 setDocumentInformation 传递引用）
        if (source.getDocumentInformation() != null) {
            var srcInfo = source.getDocumentInformation();
            var tgtInfo = target.getDocumentInformation();
            if (srcInfo.getTitle() != null) tgtInfo.setTitle(srcInfo.getTitle());
            if (srcInfo.getAuthor() != null) tgtInfo.setAuthor(srcInfo.getAuthor());
            if (srcInfo.getSubject() != null) tgtInfo.setSubject(srcInfo.getSubject());
            if (srcInfo.getKeywords() != null) tgtInfo.setKeywords(srcInfo.getKeywords());
            if (srcInfo.getCreator() != null) tgtInfo.setCreator(srcInfo.getCreator());
            if (srcInfo.getProducer() != null) tgtInfo.setProducer(srcInfo.getProducer());
        }
    }

    @Override
    public byte[] mergePdf(List<byte[]> pdfBytesList, boolean preserveMeta) {
        if (pdfBytesList == null || pdfBytesList.size() < 2) {
            throw new BusinessException(ErrorCodeEnum.PDF_MERGE_TOO_FEW);
        }
        if (pdfBytesList.size() > 10) {
            throw new BusinessException(ErrorCodeEnum.PDF_MERGE_TOO_MANY);
        }

        // 逐个加载 PDF 并检测加密，暂存文档对象
        List<PDDocument> documents = new ArrayList<>();
        try {
            for (int i = 0; i < pdfBytesList.size(); i++) {
                byte[] bytes = pdfBytesList.get(i);
                if (bytes == null || bytes.length == 0) {
                    throw new BusinessException(ErrorCodeEnum.PDF_FILE_EMPTY);
                }
                PDDocument doc = Loader.loadPDF(bytes);
                if (doc.isEncrypted()) {
                    throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPTED);
                }
                documents.add(doc);
            }

            try (PDDocument mergedDoc = new PDDocument()) {
                // 逐页复制
                for (PDDocument doc : documents) {
                    int pageCount = doc.getNumberOfPages();
                    for (int p = 0; p < pageCount; p++) {
                        PDPage copiedPage = new PDPage(doc.getPage(p).getCOSObject());
                        mergedDoc.addPage(copiedPage);
                    }
                }

                // 复制第一个文件的元数据
                if (preserveMeta && !documents.isEmpty()) {
                    copyMetadata(documents.get(0), mergedDoc);
                }

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                mergedDoc.save(bos);
                byte[] result = bos.toByteArray();
                LOGGER.info("PDF 合并完成: {} 个文件, {} 页, 输出 {} bytes",
                        pdfBytesList.size(), mergedDoc.getNumberOfPages(), result.length);
                return result;
            }

        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            LOGGER.error("PDF 合并异常", e);
            throw new BusinessException(ErrorCodeEnum.PDF_PROCESS_ERROR);
        } finally {
            for (PDDocument doc : documents) {
                try { doc.close(); } catch (IOException ignored) { }
            }
        }
    }
}
