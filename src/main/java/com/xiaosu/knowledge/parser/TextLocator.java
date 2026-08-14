package com.xiaosu.knowledge.parser;

public record TextLocator(int startLine, int endLine) implements BlockLocator {

    public TextLocator {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Invalid text line range");
        }
    }

    @Override
    public String type() {
        return "text";
    }
}
