package com.toolbox.service.pdf.impl;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import com.toolbox.service.pdf.PdfEncryptService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * PDF 加密服务实现 — 基于 PDFBox StandardProtectionPolicy + AccessPermission
 *
 * @author toolbox
 * @since 2026-07-19
 */
@Service
public class PdfEncryptServiceImpl implements PdfEncryptService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfEncryptServiceImpl.class);

    /** 密码强度正则：至少6位，包含至少一个数字和一个字母 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[0-9])(?=.*[a-zA-Z]).{6,}$");

    @Override
    public byte[] encrypt(byte[] pdfBytes, String userPassword, String ownerPassword,
                          boolean canPrint, boolean canCopy, boolean canModify,
                          boolean canAnnotate, boolean canAssemble) {
        // 1. 参数校验
        validatePasswords(userPassword, ownerPassword);

        LOGGER.info("[PdfEncryptServiceImpl#encrypt] start: userPwd={}, ownerPwd={}, print={}, copy={}, modify={}, annotate={}, assemble={}",
                mask(userPassword), mask(ownerPassword), canPrint, canCopy, canModify, canAnnotate, canAssemble);

        // 2. 加载 PDF 并加密
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            // 检查是否已加密
            if (doc.isEncrypted()) {
                throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_ALREADY_ENCRYPTED);
            }

            // 3. 创建权限
            AccessPermission permission = new AccessPermission();
            permission.setCanPrint(canPrint);
            permission.setCanPrintFaithful(canPrint);
            permission.setCanExtractContent(canCopy);
            permission.setCanModify(canModify);
            permission.setCanModifyAnnotations(canAnnotate);
            permission.setCanFillInForm(canAnnotate);
            permission.setCanAssembleDocument(canAssemble);

            // 4. 创建加密策略
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    ownerPassword, userPassword, permission);
            policy.setEncryptionKeyLength(256);

            // 5. 应用加密
            doc.protect(policy);

            // 6. 保存
            byte[] encryptedData;
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                doc.save(bos);
                encryptedData = bos.toByteArray();
            }

            LOGGER.info("[PdfEncryptServiceImpl#encrypt] done: {} -> {} bytes",
                    pdfBytes.length, encryptedData.length);
            return encryptedData;

        } catch (BusinessException e) {
            throw e;
        } catch (InvalidPasswordException e) {
            LOGGER.warn("[PdfEncryptServiceImpl#encrypt] PDF already encrypted");
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_ALREADY_ENCRYPTED);
        } catch (IOException e) {
            LOGGER.error("[PdfEncryptServiceImpl#encrypt] error", e);
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PROCESS_ERROR);
        }
    }

    /**
     * 校验密码：非空、强度、不相同
     */
    private void validatePasswords(String userPassword, String ownerPassword) {
        boolean userEmpty = isBlank(userPassword);
        boolean ownerEmpty = isBlank(ownerPassword);

        // 至少填写一个密码
        if (userEmpty && ownerEmpty) {
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_EMPTY);
        }

        // 两个密码不能相同
        if (!userEmpty && !ownerEmpty && userPassword.equals(ownerPassword)) {
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_SAME);
        }

        // 密码强度校验
        if (!userEmpty && !PASSWORD_PATTERN.matcher(userPassword).matches()) {
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_WEAK);
        }
        if (!ownerEmpty && !PASSWORD_PATTERN.matcher(ownerPassword).matches()) {
            throw new BusinessException(ErrorCodeEnum.PDF_ENCRYPT_PASSWORD_WEAK);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String mask(String s) {
        if (isBlank(s)) return "(空)";
        return "*".repeat(s.length());
    }
}
