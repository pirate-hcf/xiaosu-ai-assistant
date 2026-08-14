package com.xiaosu.knowledge.parser;

public record MarkdownLocator(String heading, int startLine, int endLine) implements BlockLocator {

    public MarkdownLocator {
        if (startLine < 1 || endLine < startLine) {
            throw new IllegalArgumentException("Invalid Markdown line range");
        }
        if (heading != null) {
            heading = heading.strip();
            if (heading.isEmpty()) {
                heading = null;
            }
        }
    }

    @Override
    public String type() {
        return "markdown";
    }
}
