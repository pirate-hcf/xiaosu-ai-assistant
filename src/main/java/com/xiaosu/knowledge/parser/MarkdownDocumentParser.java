package com.xiaosu.knowledge.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

@Component
public class MarkdownDocumentParser implements DocumentParser {

    private final Parser parser = Parser.builder()
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();
    private final TextContentRenderer renderer = TextContentRenderer.builder().build();

    @Override
    public boolean supports(String mimeType) {
        return "text/markdown".equalsIgnoreCase(mimeType);
    }

    @Override
    public List<ParsedBlock> parse(InputStream inputStream) {
        String markdown = readUtf8(inputStream, "Markdown");
        Node document = parser.parse(markdown);
        List<ParsedBlock> blocks = new ArrayList<>();
        Section section = null;

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            String text = renderer.render(node).strip();
            if (node instanceof Heading) {
                if (section != null) {
                    section.addTo(blocks);
                }
                section = new Section(text, firstLine(node));
                section.add(text, lastLine(node));
            } else if (!text.isBlank()) {
                if (section == null) {
                    section = new Section(null, firstLine(node));
                }
                section.add(text, lastLine(node));
            }
        }
        if (section != null) {
            section.addTo(blocks);
        }
        if (blocks.isEmpty()) {
            throw new DocumentParseException(ParseFailure.EMPTY_DOCUMENT, "Markdown 文档没有可解析内容");
        }
        return List.copyOf(blocks);
    }

    private static int firstLine(Node node) {
        return node.getSourceSpans().stream()
                .mapToInt(SourceSpan::getLineIndex)
                .min()
                .orElse(0) + 1;
    }

    private static int lastLine(Node node) {
        return node.getSourceSpans().stream()
                .mapToInt(SourceSpan::getLineIndex)
                .max()
                .orElse(0) + 1;
    }

    private static String readUtf8(InputStream inputStream, String format) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new DocumentParseException(ParseFailure.INVALID_DOCUMENT, format + " 文件不是有效的 UTF-8 文本", exception);
        } catch (IOException exception) {
            throw new DocumentParseException(ParseFailure.INVALID_DOCUMENT, format + " 文件读取失败", exception);
        }
    }

    private static final class Section {

        private final String heading;
        private final int startLine;
        private final List<String> parts = new ArrayList<>();
        private int endLine;

        private Section(String heading, int startLine) {
            this.heading = heading;
            this.startLine = startLine;
            this.endLine = startLine;
        }

        private void add(String text, int lastLine) {
            if (!text.isBlank()) {
                parts.add(text);
                endLine = Math.max(endLine, lastLine);
            }
        }

        private void addTo(List<ParsedBlock> blocks) {
            if (!parts.isEmpty()) {
                blocks.add(new ParsedBlock(
                        String.join("\n", parts),
                        new MarkdownLocator(heading, startLine, endLine)));
            }
        }
    }
}
