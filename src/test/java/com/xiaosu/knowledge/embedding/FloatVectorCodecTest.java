package com.xiaosu.knowledge.embedding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteOrder;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class FloatVectorCodecTest {

    @Test
    void randomFloatVectorRoundTripsWithoutPrecisionLoss() {
        SplittableRandom random = new SplittableRandom(20260815L);
        float[] vector = new float[257];
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) random.nextDouble(-10_000.0, 10_000.0);
        }

        byte[] encoded = FloatVectorCodec.encode(vector);
        float[] decoded = FloatVectorCodec.decode(encoded);

        assertEquals(vector.length * Float.BYTES, encoded.length);
        assertArrayEquals(vector, decoded);
    }

    @Test
    void encodingUsesDocumentedBigEndianByteOrder() {
        assertEquals(ByteOrder.BIG_ENDIAN, FloatVectorCodec.BYTE_ORDER);
        assertArrayEquals(
                new byte[] {0x3f, (byte) 0x80, 0x00, 0x00, (byte) 0xc0, 0x20, 0x00, 0x00},
                FloatVectorCodec.encode(new float[] {1.0f, -2.5f}));
    }

    @Test
    void malformedBlobIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> FloatVectorCodec.decode(new byte[3]));
        assertThrows(IllegalArgumentException.class, () -> FloatVectorCodec.decode(new byte[0]));
    }
}
