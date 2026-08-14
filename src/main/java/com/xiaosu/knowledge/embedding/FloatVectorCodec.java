package com.xiaosu.knowledge.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FloatVectorCodec {

    public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

    private FloatVectorCodec() {
    }

    public static byte[] encode(float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("Vector must not be empty");
        }
        ByteBuffer buffer = ByteBuffer.allocate(Math.multiplyExact(vector.length, Float.BYTES)).order(BYTE_ORDER);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % Float.BYTES != 0) {
            throw new IllegalArgumentException("Encoded vector length must be a positive multiple of four");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(BYTE_ORDER);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = buffer.getFloat();
        }
        return vector;
    }
}
