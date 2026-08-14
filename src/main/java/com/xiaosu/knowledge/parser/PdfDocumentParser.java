package com.xiaosu.knowledge.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String mimeType) {
        return "application/pdf".equalsIgnoreCase(mimeType);
    }

    @Override
    public List<ParsedBlock> parse(InputStream inputStream) {
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            if (document.getNumberOfPages() == 0) {
                throw new DocumentParseException(ParseFailure.EMPTY_DOCUMENT, "PDF 文档没有页面");
            }
            List<ParsedBlock> blocks = new ArrayList<>();
            PDFTextStripper textStripper = new PDFTextStripper();
            textStripper.setSortByPosition(true);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                textStripper.setStartPage(page);
                textStripper.setEndPage(page);
                String text = normalize(textStripper.getText(document));
                if (!text.isBlank()) {
                    blocks.add(new ParsedBlock(text, new PdfLocator(page)));
                }
            }
            if (blocks.isEmpty()) {
                throw new DocumentParseException(
                        ParseFailure.NO_EXTRACTABLE_TEXT,
                        "PDF 未提取到文本，暂不支持扫描 PDF/OCR");
            }
            return List.copyOf(blocks);
        } catch (DocumentParseException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DocumentParseException(ParseFailure.INVALID_DOCUMENT, "PDF 文件无效或读取失败", exception);
        }
    }

    private static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map(String::stripTrailing)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("")
                .strip();
    }
}
