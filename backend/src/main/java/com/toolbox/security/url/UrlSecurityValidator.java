package com.toolbox.security.url;

import com.toolbox.exception.BusinessException;
import com.toolbox.exception.ErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

/**
 * URL 安全校验工具 — 防止 SSRF（Server-Side Request Forgery）攻击
 * <p>
 * 校验规则：
 * 1. 只允许 http/https 协议
 * 2. 禁止访问内网/私有 IP 范围
 * 3. 禁止访问云元数据地址（169.254.169.254）
 *
 * @author toolbox
 * @since 2026-07-30
 */
public final class UrlSecurityValidator {

    private static final Logger log = LoggerFactory.getLogger(UrlSecurityValidator.class);

    /** 禁止访问的 IPv4 地址范围（CIDR 表示） */
    private static final List<CidrBlock> BLOCKED_IPV4_RANGES = List.of(
            CidrBlock.of("10.0.0.0", 8),          // A 类私有
            CidrBlock.of("172.16.0.0", 12),       // B 类私有
            CidrBlock.of("192.168.0.0", 16),      // C 类私有
            CidrBlock.of("127.0.0.0", 8),         // 回环
            CidrBlock.of("169.254.0.0", 16),      // 链路本地（AWS/云元数据）
            CidrBlock.of("0.0.0.0", 8),           // 保留
            CidrBlock.of("224.0.0.0", 4)          // 组播
    );

    private UrlSecurityValidator() {
        // 工具类禁止实例化
    }

    /**
     * 校验 URL 安全性
     * <p>
     * 未通过校验时抛出 BusinessException(URL_SSRF_BLOCKED)
     *
     * @param url 待校验的 URL 字符串
     */
    public static void validate(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_EMPTY);
        }

        String trimmed = url.trim();

        // 1. 协议检查：只允许 http/https
        String lowerUrl = trimmed.toLowerCase();
        if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
            log.warn("[UrlSecurityValidator#validate] invalid protocol: {}", trimmed);
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_INVALID);
        }

        // 2. 提取主机名
        String host;
        try {
            URI uri = URI.create(trimmed);
            host = uri.getHost();
        } catch (Exception e) {
            log.warn("[UrlSecurityValidator#validate] invalid URI: {}", trimmed);
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_INVALID);
        }

        if (host == null || host.isBlank()) {
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_INVALID);
        }

        // 3. DNS 解析主机名
        InetAddress address;
        try {
            address = InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            log.warn("[UrlSecurityValidator#validate] unable to resolve host: {}", host);
            throw new BusinessException(ErrorCodeEnum.HTML_TO_PDF_URL_UNREACHABLE);
        }

        // 4. 检查 IP 是否在禁止范围内
        byte[] ipBytes = address.getAddress();

        if (ipBytes.length == 4) {
            // IPv4
            int ip = bytesToInt(ipBytes);
            if (isBlockedIpv4(ip)) {
                log.warn("[UrlSecurityValidator#validate] SSRF blocked: url={}, ip={}", trimmed,
                        address.getHostAddress());
                throw new BusinessException(ErrorCodeEnum.URL_SSRF_BLOCKED);
            }
        } else if (ipBytes.length == 16) {
            // IPv6: 检查回环地址
            if (address.isLoopbackAddress()) {
                log.warn("[UrlSecurityValidator#validate] SSRF blocked (IPv6 loopback): url={}", trimmed);
                throw new BusinessException(ErrorCodeEnum.URL_SSRF_BLOCKED);
            }
        }
    }

    /**
     * 检查 IPv4 地址是否在禁止范围内
     *
     * @param ip 32 位整数表示的 IP
     * @return true=被阻止
     */
    private static boolean isBlockedIpv4(int ip) {
        for (CidrBlock block : BLOCKED_IPV4_RANGES) {
            if (block.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将字节数组转换为 32 位无符号整数
     */
    private static int bytesToInt(byte[] bytes) {
        int result = 0;
        for (byte b : bytes) {
            result = (result << 8) | (b & 0xFF);
        }
        return result;
    }

    /**
     * CIDR 地址块
     */
    private static class CidrBlock {
        private final int network;
        private final int mask;

        CidrBlock(int network, int mask) {
            this.network = network;
            this.mask = mask;
        }

        static CidrBlock of(String base, int prefixLength) {
            String[] parts = base.split("\\.");
            int ip = 0;
            for (String part : parts) {
                ip = (ip << 8) | Integer.parseInt(part);
            }
            int mask = prefixLength == 0 ? 0 : (-1 << (32 - prefixLength));
            return new CidrBlock(ip & mask, mask);
        }

        boolean matches(int ip) {
            return (ip & mask) == network;
        }
    }
}
