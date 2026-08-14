package com.xiaosu.knowledge.parser;

public sealed interface BlockLocator permits MarkdownLocator, TextLocator, PdfLocator {

    String type();
}
