package com.xiaosu.knowledge.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Profile("fake-embedding")
@Primary
public final class FakeEmbeddingGateway implements EmbeddingGateway {

    private static final int DIMENSIONS = 8;

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding text must not be blank");
        }
        byte[] digest = sha256(text);
        ByteBuffer buffer = ByteBuffer.wrap(digest).order(ByteOrder.BIG_ENDIAN);
        float[] vector = new float[DIMENSIONS];
        double squaredNorm = 0;
        for (int index = 0; index < vector.length; index++) {
            vector[index] = buffer.getInt() / (float) Integer.MAX_VALUE;
            squaredNorm += vector[index] * vector[index];
        }
        float norm = (float) Math.sqrt(squaredNorm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
        return vector;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not support SHA-256", exception);
        }
    }
}
