package com.hnieacm.common.judge;

import com.hnieacm.common.exception.BizException;
import com.hnieacm.common.result.ResultCode;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 节点协议 v1 签名字节编解码与 Ed25519 校验。
 *
 * <p>规范编码：每个字段先 UTF-8 编码，再前置 4 字节无符号大端长度，按协议顺序拼接。
 * 签名/公钥均为 RFC4648 标准 Base64。公钥为裸 32 字节，校验时补 X.509 前缀。</p>
 *
 * @author Codex
 */
public final class NodeSignatureCodec {

    /** Ed25519 SubjectPublicKeyInfo 前缀，用于把裸 32 字节公钥包装成 X.509。 */
    private static final byte[] ED25519_X509_PREFIX =
            HexFormat.of().parseHex("302a300506032b6570032100");

    private static final int RAW_PUBLIC_KEY_BYTES = 32;
    private static final int SIGNATURE_BYTES = 64;

    private NodeSignatureCodec() {
    }

    /**
     * 按协议拼接签名字节。
     *
     * @param fields 按签名域约定顺序排列的字段，必须非 null
     * @return 长度前缀编码后的字节
     */
    public static byte[] canonical(String... fields) {
        if (fields == null || fields.length == 0) {
            throw new IllegalArgumentException("canonical fields must not be empty");
        }
        int total = 0;
        byte[][] encoded = new byte[fields.length][];
        for (int i = 0; i < fields.length; i++) {
            if (fields[i] == null) {
                throw new IllegalArgumentException("canonical field " + i + " must not be null");
            }
            byte[] bytes = fields[i].getBytes(StandardCharsets.UTF_8);
            encoded[i] = bytes;
            total += 4 + bytes.length;
        }
        byte[] result = new byte[total];
        int offset = 0;
        for (byte[] bytes : encoded) {
            result[offset] = (byte) (bytes.length >>> 24);
            result[offset + 1] = (byte) (bytes.length >>> 16);
            result[offset + 2] = (byte) (bytes.length >>> 8);
            result[offset + 3] = (byte) bytes.length;
            System.arraycopy(bytes, 0, result, offset + 4, bytes.length);
            offset += 4 + bytes.length;
        }
        return result;
    }

    /**
     * 计算 SHA-256 小写十六进制。
     */
    public static String sha256Hex(byte[] input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 使用裸 Base64 公钥校验 Ed25519 签名。
     *
     * @param rawPublicKeyBase64 32 字节裸公钥的 Base64
     * @param canonicalBytes     规范签名字节
     * @param signatureBase64    64 字节签名的 Base64
     * @return 校验是否通过
     */
    public static boolean verify(String rawPublicKeyBase64, byte[] canonicalBytes, String signatureBase64) {
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            if (signatureBytes.length != SIGNATURE_BYTES) {
                return false;
            }
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(parseEd25519PublicKey(rawPublicKeyBase64));
            verifier.update(canonicalBytes);
            return verifier.verify(signatureBytes);
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            // 任何解析/算法异常都视为校验失败，避免把异常冒泡成 500 或绕过校验。
            return false;
        }
    }

    /**
     * 校验失败即抛出未授权异常，供 REST/WSS 直接复用。
     */
    public static void requireValid(String rawPublicKeyBase64, byte[] canonicalBytes, String signatureBase64) {
        if (!verify(rawPublicKeyBase64, canonicalBytes, signatureBase64)) {
            throw new BizException(ResultCode.UNAUTHORIZED, "节点签名校验失败");
        }
    }

    /**
     * 把裸 32 字节 Ed25519 公钥解析为 {@link PublicKey}。
     */
    public static PublicKey parseEd25519PublicKey(String rawPublicKeyBase64) {
        try {
            byte[] raw = Base64.getDecoder().decode(rawPublicKeyBase64);
            if (raw.length != RAW_PUBLIC_KEY_BYTES) {
                throw new BizException(ResultCode.BAD_REQUEST, "公钥必须为 32 字节 Ed25519 裸公钥");
            }
            byte[] x509 = new byte[ED25519_X509_PREFIX.length + raw.length];
            System.arraycopy(ED25519_X509_PREFIX, 0, x509, 0, ED25519_X509_PREFIX.length);
            System.arraycopy(raw, 0, x509, ED25519_X509_PREFIX.length, raw.length);
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException | java.security.GeneralSecurityException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "公钥格式非法");
        }
    }
}
