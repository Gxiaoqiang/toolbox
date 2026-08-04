package com.toolbox.service.pdf;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.impl.PdfEncryptServiceImpl;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PdfEncryptService 单元测试
 *
 * @author toolbox
 * @since 2026-07-19
 */
@DisplayName("PDF 加密服务")
class PdfEncryptServiceTest {

    private PdfEncryptService service;

    @BeforeEach
    void setUp() {
        service = new PdfEncryptServiceImpl();
    }

    // ===== 辅助方法 =====

    /** 创建一个简单的测试 PDF */
    private byte[] createTestPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    // ===== 密码校验测试 =====

    @Nested
    @DisplayName("密码校验")
    class PasswordValidation {

        @Test
        @DisplayName("两个密码都为空 → 抛 PASSWORD_EMPTY")
        void bothPasswordsEmpty_throwsPasswordEmpty() throws Exception {
            byte[] pdf = createTestPdf();
            assertThatThrownBy(() -> service.encrypt(pdf, "", "", true, true, true, true, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_EMPTY.getCode());
        }

        @Test
        @DisplayName("两个密码都为 null → 抛 PASSWORD_EMPTY")
        void bothPasswordsNull_throwsPasswordEmpty() throws Exception {
            byte[] pdf = createTestPdf();
            assertThatThrownBy(() -> service.encrypt(pdf, null, null, true, true, true, true, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_EMPTY.getCode());
        }

        @Test
        @DisplayName("两个密码相同 → 抛 PASSWORD_SAME")
        void samePasswords_throwsPasswordSame() throws Exception {
            byte[] pdf = createTestPdf();
            assertThatThrownBy(() -> service.encrypt(pdf, "abc123", "abc123", true, true, true, true, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_SAME.getCode());
        }

        @Test
        @DisplayName("用户密码太短（<6位）→ 抛 PASSWORD_WEAK")
        void userPasswordTooShort_throwsPasswordWeak() throws Exception {
            byte[] pdf = createTestPdf();
            assertThatThrownBy(() -> service.encrypt(pdf, "a1", "", true, true, true, true, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_WEAK.getCode());
        }

        @Test
        @DisplayName("所有者密码缺少数字 → 抛 PASSWORD_WEAK")
        void ownerPasswordNoDigit_throwsPasswordWeak() throws Exception {
            byte[] pdf = createTestPdf();
            assertThatThrownBy(() -> service.encrypt(pdf, "", "abcdef", true, true, true, true, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_WEAK.getCode());
        }

        @Test
        @DisplayName("所有者密码缺少字母 → 抛 PASSWORD_WEAK")
        void ownerPasswordNoLetter_throwsPasswordWeak() throws Exception {
            byte[] pdf = createTestPdf();
            assertThatThrownBy(() -> service.encrypt(pdf, "", "123456", true, true, true, true, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_WEAK.getCode());
        }
    }

    // ===== 权限校验测试 =====

    @Nested
    @DisplayName("权限校验")
    class PermissionValidation {

        @Test
        @DisplayName("所有者密码存在 + 权限全开 → 允许加密（无需关闭任何权限）")
        void ownerPasswordWithAllPermissionsOpen_succeeds() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] result = service.encrypt(pdf, "", "owner123", true, true, true, true, true);
            assertThat(result).isNotNull();
            assertThat(result.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("所有者密码存在 + 关闭一个权限 → 成功")
        void ownerPasswordWithOnePermissionClosed_succeeds() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] result = service.encrypt(pdf, "", "owner123", false, true, true, true, true);
            assertThat(result).isNotNull();
            assertThat(result.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("无所有者密码 + 权限全开 → 成功（权限不生效）")
        void noOwnerPassword_allPermissionsOpen_succeeds() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] result = service.encrypt(pdf, "user123", "", true, true, true, true, true);
            assertThat(result).isNotNull();
        }
    }

    // ===== 已加密 PDF 检测 =====

    @Nested
    @DisplayName("已加密 PDF 检测")
    class EncryptedDetection {

        @Test
        @DisplayName("已加密的 PDF → 抛 ALREADY_ENCRYPTED")
        void encryptedPdf_throwsAlreadyEncrypted() throws Exception {
            // 先加密一个 PDF
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user123", "owner123", false, true, true, true, true);

            // 再次加密应该报错
            assertThatThrownBy(() -> service.encrypt(encrypted, "user456", "owner456", false, true, true, true, true))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ErrorCodeEnum.PDF_ENCRYPT_ALREADY_ENCRYPTED.getCode());
        }
    }

    // ===== 加密功能测试 =====

    @Nested
    @DisplayName("加密功能")
    class EncryptionFunction {

        @Test
        @DisplayName("仅用户密码 + 权限全开 → 加密成功，需要密码打开")
        void userPasswordOnly_allPermissions_encryptsSuccessfully() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user123", "", true, true, true, true, true);

            assertThat(encrypted).isNotNull();
            assertThat(encrypted.length).isGreaterThan(0);

            // 不带密码打开应该失败
            assertThatThrownBy(() -> Loader.loadPDF(encrypted))
                    .isInstanceOf(InvalidPasswordException.class);

            // 用用户密码打开应该成功
            try (PDDocument doc = Loader.loadPDF(encrypted, "user123")) {
                assertThat(doc.getNumberOfPages()).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("仅所有者密码 + 关闭打印 → 不需密码打开，打印权限受限")
        void ownerPasswordOnly_restrictPrint_opensWithoutPassword() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "", "owner123", false, true, true, true, true);

            // 不带密码应该能打开（无用户密码）
            try (PDDocument doc = Loader.loadPDF(encrypted)) {
                AccessPermission perm = doc.getCurrentAccessPermission();
                assertThat(perm.canPrint()).isFalse();
                assertThat(perm.canExtractContent()).isTrue();
                assertThat(perm.canModify()).isTrue();
                assertThat(perm.isOwnerPermission()).isFalse();
            }

            // 用所有者密码打开应该有完全权限
            try (PDDocument doc = Loader.loadPDF(encrypted, "owner123")) {
                AccessPermission perm = doc.getCurrentAccessPermission();
                assertThat(perm.isOwnerPermission()).isTrue();
            }
        }

        @Test
        @DisplayName("双密码 + 全部权限关闭 → 用户模式权限正确")
        void dualPassword_allRestricted_userPermissionsCorrect() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user123", "owner456", false, false, false, false, false);

            // 用用户密码打开
            try (PDDocument doc = Loader.loadPDF(encrypted, "user123")) {
                AccessPermission perm = doc.getCurrentAccessPermission();
                assertThat(perm.canPrint()).isFalse();
                assertThat(perm.canExtractContent()).isFalse();
                assertThat(perm.canModify()).isFalse();
                assertThat(perm.canModifyAnnotations()).isFalse();
                assertThat(perm.canFillInForm()).isFalse();
                assertThat(perm.canAssembleDocument()).isFalse();
                assertThat(perm.isOwnerPermission()).isFalse();
            }

            // 用所有者密码打开应该有完全权限
            try (PDDocument doc = Loader.loadPDF(encrypted, "owner456")) {
                assertThat(doc.getCurrentAccessPermission().isOwnerPermission()).isTrue();
            }
        }

        @Test
        @DisplayName("256-bit AES 加密 → 正常工作")
        void aes256Encryption_works() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user789", "owner789", false, false, true, true, true);

            try (PDDocument doc = Loader.loadPDF(encrypted, "user789")) {
                assertThat(doc.getNumberOfPages()).isEqualTo(2);
            }
        }

        @Test
        @DisplayName("各权限独立生效 — 打印")
        void canPrintPermission_independent() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user123", "owner123", false, true, true, true, true);

            try (PDDocument doc = Loader.loadPDF(encrypted, "user123")) {
                AccessPermission perm = doc.getCurrentAccessPermission();
                assertThat(perm.canPrint()).isFalse();
                assertThat(perm.canPrintFaithful()).isFalse();
                assertThat(perm.canExtractContent()).isTrue();
            }
        }

        @Test
        @DisplayName("各权限独立生效 — 复制")
        void canCopyPermission_independent() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user123", "owner123", true, false, true, true, true);

            try (PDDocument doc = Loader.loadPDF(encrypted, "user123")) {
                AccessPermission perm = doc.getCurrentAccessPermission();
                assertThat(perm.canPrint()).isTrue();
                assertThat(perm.canExtractContent()).isFalse();
            }
        }

        @Test
        @DisplayName("各权限独立生效 — 注释和表单")
        void canAnnotatePermission_independent() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user123", "owner123", true, true, true, false, true);

            try (PDDocument doc = Loader.loadPDF(encrypted, "user123")) {
                AccessPermission perm = doc.getCurrentAccessPermission();
                assertThat(perm.canModifyAnnotations()).isFalse();
                assertThat(perm.canFillInForm()).isFalse();
                assertThat(perm.canModify()).isTrue();
            }
        }

        @Test
        @DisplayName("各权限独立生效 — 页面组装")
        void canAssemblePermission_independent() throws Exception {
            byte[] pdf = createTestPdf();
            byte[] encrypted = service.encrypt(pdf, "user123", "owner123", true, true, true, true, false);

            try (PDDocument doc = Loader.loadPDF(encrypted, "user123")) {
                AccessPermission perm = doc.getCurrentAccessPermission();
                assertThat(perm.canAssembleDocument()).isFalse();
                assertThat(perm.canPrint()).isTrue();
            }
        }
    }
}
