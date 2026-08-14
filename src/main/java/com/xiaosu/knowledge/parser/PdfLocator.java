package com.xiaosu.knowledge.parser;

public record PdfLocator(int page) implements BlockLocator {

    public PdfLocator {
        if (page < 1) {
            throw new IllegalArgumentException("PDF page must be positive");
        }
    }

    @Override
    public String type() {
        return "pdf";
    }
}
