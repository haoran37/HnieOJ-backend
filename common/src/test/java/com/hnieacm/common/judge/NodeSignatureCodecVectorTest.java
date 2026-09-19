package com.hnieacm.common.judge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用冻结公开向量校验规范编码与 Ed25519 验签逻辑。
 *
 * <p>向量文件 protocol-vectors.json 为跨语言公开测试向量，任何字节偏差都视为协议不兼容。</p>
 *
 * @author Codex
 */
class NodeSignatureCodecVectorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void canonicalEncodingAndSignatureMatchFrozenVectors() throws Exception {
        JsonNode root = loadVectors();
        String publicKey = root.get("publicKey").asText();
        JsonNode vectors = root.get("vectors");
        assertTrue(vectors.isArray() && vectors.size() == 5, "应包含 5 个公开向量");

        for (JsonNode vector : vectors) {
            List<String> fields = new ArrayList<>();
            vector.get("fields").forEach(node -> fields.add(node.asText()));
            byte[] canonical = NodeSignatureCodec.canonical(fields.toArray(new String[0]));

            assertEquals(vector.get("canonicalHex").asText(), HexFormat.of().formatHex(canonical),
                    "canonicalHex 不一致: " + fields.get(0));
            assertTrue(NodeSignatureCodec.verify(publicKey, canonical, vector.get("signature").asText()),
                    "签名校验失败: " + fields.get(0));

            // 篡改任一字段后必须验签失败。
            List<String> tampered = new ArrayList<>(fields);
            tampered.set(fields.size() - 1, fields.get(fields.size() - 1) + "x");
            byte[] tamperedBytes = NodeSignatureCodec.canonical(tampered.toArray(new String[0]));
            assertFalse(NodeSignatureCodec.verify(publicKey, tamperedBytes, vector.get("signature").asText()),
                    "篡改后不应通过: " + fields.get(0));
        }
    }

    @Test
    void unicodeFieldsUseUtf8LengthPrefix() {
        byte[] canonical = NodeSignatureCodec.canonical("节点一");
        // 4 字节长度前缀 + 9 字节 UTF-8 内容（每个汉字 3 字节）。
        assertEquals(13, canonical.length);
        assertEquals("00000009e88a82e782b9e4b880", HexFormat.of().formatHex(canonical));
    }

    @Test
    void wrongKeyAndTamperedSignatureAreRejected() throws Exception {
        JsonNode root = loadVectors();
        JsonNode first = root.get("vectors").get(0);
        List<String> fields = new ArrayList<>();
        first.get("fields").forEach(node -> fields.add(node.asText()));
        byte[] canonical = NodeSignatureCodec.canonical(fields.toArray(new String[0]));

        // 使用另一把合法的 32 字节 Ed25519 公钥也无法通过（wrong key）。
        String otherKey = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        assertFalse(NodeSignatureCodec.verify(otherKey, canonical, first.get("signature").asText()));

        // 长度非法的签名字节直接拒绝。
        assertFalse(NodeSignatureCodec.verify(root.get("publicKey").asText(), canonical, "AAAA"));
    }

    private JsonNode loadVectors() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("protocol-vectors.json")) {
            if (in == null) {
                throw new IllegalStateException("protocol-vectors.json 未随测试资源打包");
            }
            return MAPPER.readTree(in);
        }
    }
}
