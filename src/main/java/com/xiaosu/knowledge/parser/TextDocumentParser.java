package com.xiaosu.knowledge.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TextDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String mimeType) {
        return "text/plain".equalsIgnoreCase(mimeType);
    }

    @Override
    public List<ParsedBlock> parse(InputStream inputStream) {
        String text = readUtf8(inputStream);
        String[] lines = text.split("\\r\\n|\\n|\\r", -1);
        List<ParsedBlock> blocks = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();
        int startLine = 0;

        for (int index = 0; index <= lines.length; index++) {
            boolean atEnd = index == lines.length;
            String line = atEnd ? "" : lines[index];
            if (!atEnd && !line.isBlank()) {
                if (paragraph.isEmpty()) {
                    startLine = index + 1;
                }
                paragraph.add(line.stripTrailing());
            } else if (!paragraph.isEmpty()) {
                int endLine = index;
                blocks.add(new ParsedBlock(
                        String.join("\n", paragraph).strip(),
                        new TextLocator(startLine, endLine)));
                paragraph.clear();
            }
        }

        if (blocks.isEmpty()) {
            throw new DocumentParseException(ParseFailure.EMPTY_DOCUMENT, "TXT 文档没有可解析内容");
        }
        return List.copyOf(blocks);
    }

    private static String readUtf8(InputStream inputStream) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(inputStream.readAllBytes()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new DocumentParseException(ParseFailure.INVALID_DOCUMENT, "TXT 文件不是有效的 UTF-8 文本", exception);
        } catch (IOException exception) {
            throw new DocumentParseException(ParseFailure.INVALID_DOCUMENT, "TXT 文件读取失败", exception);
        }
    }
}
