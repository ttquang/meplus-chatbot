package com.ttq.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** What the product and category embeddings have in common. */
final class EmbeddingSupport {

    private EmbeddingSupport() {
    }

    /** Hashes the model with the content, so switching model re-embeds everything. */
    static String hash(String model, String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((model + '\n' + content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }

    /** Cosine similarity, from -1 to 1; vectors of different sizes are never similar. */
    static double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return Double.NEGATIVE_INFINITY;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return normA == 0 || normB == 0 ? 0 : dot / Math.sqrt(normA * normB);
    }
}
