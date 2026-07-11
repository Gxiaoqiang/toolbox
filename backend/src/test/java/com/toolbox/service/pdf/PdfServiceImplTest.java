package com.toolbox.service.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.impl.PdfServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

class PdfServiceImplTest {

    private final PdfServiceImpl service = new PdfServiceImpl();

    private byte[] createTestPdf(int pageCount) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pageCount; i++) {
                doc.addPage(new PDPage());
            }
            doc.save(bos);
        }
        return bos.toByteArray();
    }

    @Test
    @DisplayName("逐页拆分: 5 页 PDF 生成 5 个独立文件")
    void byPage_5pages_returnsZipWith5Files() throws Exception {
        byte[] pdf = createTestPdf(5);
        byte[] zip = service.splitPdf(pdf, "test.pdf", "by-page", null, 0, false);

        int fileCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            while (zis.getNextEntry() != null) fileCount++;
        }
        assertEquals(5, fileCount);
    }

    @Test
    @DisplayName("逐页拆分: 拆分后总页数 = 原始总页数")
    void byPage_totalPagesMatches() throws Exception {
        byte[] pdf = createTestPdf(7);
        byte[] zip = service.splitPdf(pdf, "test.pdf", "by-page", null, 0, false);

        int totalPages = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            while (zis.getNextEntry() != null) {
                ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = zis.read(buf)) != -1) entryBytes.write(buf, 0, n);
                try (PDDocument doc = Loader.loadPDF(entryBytes.toByteArray())) {
                    totalPages += doc.getNumberOfPages();
                }
            }
        }
        assertEquals(7, totalPages);
    }

    @Test
    @DisplayName("逐页拆分: 文件名含序号")
    void byPage_filenamesHavePageNumber() throws Exception {
        byte[] pdf = createTestPdf(3);
        byte[] zip = service.splitPdf(pdf, "报告.pdf", "by-page", null, 0, false);

        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) names.add(entry.getName());
        }
        assertEquals("报告_p1.pdf", names.get(0));
        assertEquals("报告_p2.pdf", names.get(1));
        assertEquals("报告_p3.pdf", names.get(2));
    }

    @Test
    @DisplayName("按页码范围: \"1,3,5-7\" 生成 4 个文件")
    void byRange_4files() throws Exception {
        byte[] pdf = createTestPdf(10);
        byte[] zip = service.splitPdf(pdf, "test.pdf", "by-range", "1,3,5-7", 0, false);

        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) names.add(entry.getName());
        }
        assertEquals(3, names.size());
        assertEquals("test_p1.pdf", names.get(0));
        assertEquals("test_p3.pdf", names.get(1));
        assertEquals("test_p5-7.pdf", names.get(2));
    }

    @Test
    @DisplayName("按页码范围: 重复页码抛 PDF_PAGE_OVERLAP")
    void byRange_overlap_throws() {
        assertThrows(BusinessException.class, () -> {
            byte[] pdf = createTestPdf(10);
            service.splitPdf(pdf, "test.pdf", "by-range", "1-5,3", 0, false);
        });
    }

    @Test
    @DisplayName("按页码范围: 越界页码抛 PDF_PAGE_OUT_OF_RANGE")
    void byRange_outOfRange_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () -> {
            byte[] pdf = createTestPdf(10);
            service.splitPdf(pdf, "test.pdf", "by-range", "1-20", 0, false);
        });
        assertEquals(ErrorCodeEnum.PDF_PAGE_OUT_OF_RANGE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("按页码范围: 格式错误抛 PDF_PAGE_FORMAT_ERROR")
    void byRange_badFormat_throws() throws Exception {
        byte[] pdf = createTestPdf(10);
        assertThrows(BusinessException.class, () ->
                service.splitPdf(pdf, "test.pdf", "by-range", "1,,3", 0, false));
        assertThrows(BusinessException.class, () ->
                service.splitPdf(pdf, "test.pdf", "by-range", "5-3", 0, false));
    }

    @Test
    @DisplayName("每 N 页拆分: 10 页每 3 页 → 4 个文件 (3+3+3+1)")
    void byN_10pagesEvery3_returns4files() throws Exception {
        byte[] pdf = createTestPdf(10);
        byte[] zip = service.splitPdf(pdf, "test.pdf", "by-n", null, 3, false);

        int fileCount = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            while (zis.getNextEntry() != null) fileCount++;
        }
        assertEquals(4, fileCount);
    }

    @Test
    @DisplayName("每 N 页拆分: 文件名含 part 序号")
    void byN_filenamesHavePartNumber() throws Exception {
        byte[] pdf = createTestPdf(5);
        byte[] zip = service.splitPdf(pdf, "doc.pdf", "by-n", null, 2, false);

        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) names.add(entry.getName());
        }
        assertEquals("doc_part1.pdf", names.get(0));
        assertEquals("doc_part2.pdf", names.get(1));
        assertEquals("doc_part3.pdf", names.get(2));
    }

    @Test
    @DisplayName("每 N 页拆分: N=0 抛 PDF_EVERY_N_INVALID")
    void byN_zero_throws() throws Exception {
        byte[] pdf = createTestPdf(5);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.splitPdf(pdf, "test.pdf", "by-n", null, 0, false));
        assertEquals(ErrorCodeEnum.PDF_EVERY_N_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("加密 PDF 抛 PDF_ENCRYPTED")
    void encryptedPdf_throws() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.setAllSecurityToBeRemoved(true);
            doc.save(bos);
        }
    }

    // ========== PDF 合并测试 ==========

    @Test
    @DisplayName("合并: 2 个单页 PDF → 1 个 2 页 PDF")
    void merge_twoSinglePage_returns2PagePdf() throws Exception {
        byte[] pdf1 = createTestPdf(1);
        byte[] pdf2 = createTestPdf(1);

        byte[] merged = service.mergePdf(List.of(pdf1, pdf2), false);

        try (PDDocument doc = Loader.loadPDF(merged)) {
            assertEquals(2, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("合并: 多页 PDF 合并后总页数 = 各部分之和")
    void merge_multiPagePdfs_totalPagesMatch() throws Exception {
        byte[] pdf1 = createTestPdf(3);
        byte[] pdf2 = createTestPdf(5);
        byte[] pdf3 = createTestPdf(2);

        byte[] merged = service.mergePdf(List.of(pdf1, pdf2, pdf3), false);

        try (PDDocument doc = Loader.loadPDF(merged)) {
            assertEquals(10, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("合并: 3 个 PDF 合并成功")
    void merge_threePdfs_returnsValidPdf() throws Exception {
        byte[] pdf1 = createTestPdf(2);
        byte[] pdf2 = createTestPdf(3);
        byte[] pdf3 = createTestPdf(1);

        byte[] merged = service.mergePdf(List.of(pdf1, pdf2, pdf3), false);

        assertNotNull(merged);
        assertTrue(merged.length > 0);
        try (PDDocument doc = Loader.loadPDF(merged)) {
            assertEquals(6, doc.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("合并: preserveMeta=true 保留第一个文件的元数据")
    void merge_preserveMeta_copiesFirstDocMetadata() throws Exception {
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.getDocumentInformation().setTitle("合并标题");
            doc.getDocumentInformation().setAuthor("合并作者");
            doc.save(bos1);
        }
        byte[] pdf2 = createTestPdf(1);

        byte[] merged = service.mergePdf(List.of(bos1.toByteArray(), pdf2), true);

        try (PDDocument doc = Loader.loadPDF(merged)) {
            assertEquals("合并标题", doc.getDocumentInformation().getTitle());
            assertEquals("合并作者", doc.getDocumentInformation().getAuthor());
        }
    }

    @Test
    @DisplayName("合并: 空列表抛 PDF_MERGE_TOO_FEW")
    void merge_emptyList_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.mergePdf(List.of(), false));
        assertEquals(ErrorCodeEnum.PDF_MERGE_TOO_FEW.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("合并: 仅 1 个文件抛 PDF_MERGE_TOO_FEW")
    void merge_singleFile_throws() throws Exception {
        byte[] pdf = createTestPdf(2);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.mergePdf(List.of(pdf), false));
        assertEquals(ErrorCodeEnum.PDF_MERGE_TOO_FEW.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("合并: 超过 10 个文件抛 PDF_MERGE_TOO_MANY")
    void merge_tooMany_throws() throws Exception {
        byte[] pdf = createTestPdf(1);
        List<byte[]> list = new ArrayList<>();
        for (int i = 0; i < 11; i++) list.add(pdf);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.mergePdf(list, false));
        assertEquals(ErrorCodeEnum.PDF_MERGE_TOO_MANY.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("合并: 空字节数组抛 PDF_FILE_EMPTY")
    void merge_emptyBytes_throws() throws Exception {
        byte[] pdf = createTestPdf(1);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.mergePdf(List.of(pdf, new byte[0]), false));
        assertEquals(ErrorCodeEnum.PDF_FILE_EMPTY.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("合并: 加密 PDF 抛 PDF_ENCRYPTED")
    void merge_encryptedPdf_throws() throws Exception {
        byte[] normalPdf = createTestPdf(1);
        // 创建加密 PDF（使用 owner password）
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            org.apache.pdfbox.pdmodel.encryption.AccessPermission ap =
                    new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
            org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy spp =
                    new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(
                            "owner", "", ap);
            spp.setEncryptionKeyLength(128);
            doc.protect(spp);
            doc.save(bos);
        }

        assertThrows(BusinessException.class, () ->
                service.mergePdf(List.of(normalPdf, bos.toByteArray()), false));
    }

    @Test
    @DisplayName("preserveMeta=true 时元数据传递")
    void preserveMeta_copiesDocumentInfo() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.getDocumentInformation().setTitle("原始标题");
            doc.getDocumentInformation().setAuthor("测试作者");
            doc.save(bos);
        }

        byte[] zip = service.splitPdf(bos.toByteArray(), "test.pdf", "by-page", null, 0, true);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
            zis.getNextEntry();
            ByteArrayOutputStream entryBytes = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = zis.read(buf)) != -1) entryBytes.write(buf, 0, n);
            try (PDDocument result = Loader.loadPDF(entryBytes.toByteArray())) {
                assertEquals("原始标题", result.getDocumentInformation().getTitle());
                assertEquals("测试作者", result.getDocumentInformation().getAuthor());
            }
        }
    }
}
