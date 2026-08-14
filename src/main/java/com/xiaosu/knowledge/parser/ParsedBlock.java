package com.xiaosu.knowledge.parser;

import java.util.Objects;

public record ParsedBlock(String text, BlockLocator locator) {

    public ParsedBlock {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Parsed block text must not be blank");
        }
        text = text.strip();
        Objects.requireNonNull(locator, "locator");
    }
}
