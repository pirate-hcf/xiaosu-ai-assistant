package com.xiaosu.knowledge.embedding;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

class EmbeddingGatewayTest {

    @Test
    void fakeEmbeddingIsStableForSameTextAndDifferentForDifferentText() {
        EmbeddingGateway gateway = new FakeEmbeddingGateway();

        float[] first = gateway.embed("员工年假制度");
        float[] second = gateway.embed("员工年假制度");
        float[] different = gateway.embed("订单退款统计");

        assertArrayEquals(first, second);
        assertNotSame(first, second);
        assertFalse(java.util.Arrays.equals(first, different));
    }

    @Test
    void springAiAdapterDelegatesWithoutExposingModelOwnedArray() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        float[] modelVector = {0.1f, 0.2f, 0.3f};
        when(model.embed("hello")).thenReturn(modelVector);
        SpringAiEmbeddingGateway gateway = new SpringAiEmbeddingGateway(model);

        float[] result = gateway.embed("hello");

        assertArrayEquals(modelVector, result);
        assertNotSame(modelVector, result);
        verify(model).embed("hello");
    }
}
