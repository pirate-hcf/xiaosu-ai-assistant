package com.xiaosu.knowledge.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;

class DocumentParserTest {

    private final MarkdownDocumentParser markdownParser = new MarkdownDocumentParser();
    private final TextDocumentParser textParser = new TextDocumentParser();
    private final PdfDocumentParser pdfParser = new PdfDocumentParser();

    @Test
    void markdownKeepsHeadingAndExpectedSourceLines() throws IOException {
        List<ParsedBlock> blocks;
        try (InputStream input = fixture("employee-handbook.md")) {
            blocks = markdownParser.parse(input);
        }

        ParsedBlock annualLeave = blocks.stream()
                .filter(block -> block.locator() instanceof MarkdownLocator locator
                        && "年假".equals(locator.heading()))
                .findFirst()
                .orElseThrow();
        MarkdownLocator locator = assertInstanceOf(MarkdownLocator.class, annualLeave.locator());
        assertEquals(7, locator.startLine());
        assertEquals(10, locator.endLine());
        assertEquals("年假\n员工转正后可以申请带薪年假。\n年假应提前申请并经负责人审批。", annualLeave.text());
    }

    @Test
    void textParagraphKeepsExpectedStartAndEndLines() throws IOException {
        List<ParsedBlock> blocks;
        try (InputStream input = fixture("faq.txt")) {
            blocks = textParser.parse(input);
        }

        ParsedBlock account = blocks.stream()
                .filter(block -> block.text().startsWith("账号申请"))
                .findFirst()
                .orElseThrow();
        TextLocator locator = assertInstanceOf(TextLocator.class, account.locator());
        assertEquals(4, locator.startLine());
        assertEquals(6, locator.endLine());
        assertEquals("账号申请\n新员工入职后由 IT 创建账号。\n账号问题请联系服务台。", account.text());
    }

    @Test
    void pdfMarkerAppearsOnlyInTheSecondPageBlock() throws IOException {
        List<ParsedBlock> blocks;
        try (InputStream input = fixture("two-page.pdf")) {
            blocks = pdfParser.parse(input);
        }

        assertEquals(2, blocks.size());
        ParsedBlock firstPage = blockAtPage(blocks, 1);
        ParsedBlock secondPage = blockAtPage(blocks, 2);
        assertTrue(firstPage.text().contains("PAGE_ONE_MARKER"));
        assertFalse(firstPage.text().contains("PAGE_TWO_ONLY_MARKER"));
        assertTrue(secondPage.text().contains("PAGE_TWO_ONLY_MARKER"));
        assertFalse(secondPage.text().contains("PAGE_ONE_MARKER"));
    }

    @Test
    void emptyPdfReturnsControlledFailure() throws IOException {
        byte[] emptyPdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.save(output);
            emptyPdf = output.toByteArray();
        }

        DocumentParseException exception = assertThrows(
                DocumentParseException.class,
                () -> pdfParser.parse(new ByteArrayInputStream(emptyPdf)));
        assertEquals(ParseFailure.EMPTY_DOCUMENT, exception.failure());
        assertEquals("PDF 文档没有页面", exception.getMessage());
    }

    @Test
    void scannedImagePdfReturnsControlledOcrFailure() throws IOException {
        byte[] scannedPdf = scannedPdf();

        DocumentParseException exception = assertThrows(
                DocumentParseException.class,
                () -> pdfParser.parse(new ByteArrayInputStream(scannedPdf)));
        assertEquals(ParseFailure.NO_EXTRACTABLE_TEXT, exception.failure());
        assertTrue(exception.getMessage().contains("暂不支持扫描 PDF/OCR"));
    }

    @Test
    void blankMarkdownAndTextReturnControlledFailure() {
        DocumentParseException markdownFailure = assertThrows(
                DocumentParseException.class,
                () -> markdownParser.parse(new ByteArrayInputStream(" \n\n".getBytes(StandardCharsets.UTF_8))));
        DocumentParseException textFailure = assertThrows(
                DocumentParseException.class,
                () -> textParser.parse(new ByteArrayInputStream(" \n\n".getBytes(StandardCharsets.UTF_8))));

        assertEquals(ParseFailure.EMPTY_DOCUMENT, markdownFailure.failure());
        assertEquals(ParseFailure.EMPTY_DOCUMENT, textFailure.failure());
    }

    private static ParsedBlock blockAtPage(List<ParsedBlock> blocks, int page) {
        return blocks.stream()
                .filter(block -> block.locator() instanceof PdfLocator locator && locator.page() == page)
                .findFirst()
                .orElseThrow();
    }

    private static InputStream fixture(String name) {
        InputStream input = DocumentParserTest.class.getResourceAsStream("/fixtures/knowledge/" + name);
        if (input == null) {
            throw new IllegalStateException("Missing fixture: " + name);
        }
        return input;
    }

    private static byte[] scannedPdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            BufferedImage image = new BufferedImage(120, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.setColor(Color.BLACK);
                graphics.drawString("SCANNED IMAGE ONLY", 5, 24);
            } finally {
                graphics.dispose();
            }
            PDImageXObject pdfImage = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(pdfImage, 72, 700, 240, 80);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
