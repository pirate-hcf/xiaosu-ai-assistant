package com.xiaosu.knowledge.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.xiaosu.knowledge.parser.MarkdownLocator;
import com.xiaosu.knowledge.parser.ParsedBlock;
import com.xiaosu.knowledge.parser.PdfLocator;

class ChunkerTest {

    @Test
    void longDocumentUsesDefaultFourHundredTokenWindowsWithSixtyTokenOverlap() {
        Chunker chunker = new Chunker(new ChunkingProperties(400, 60));
        MarkdownLocator locator = new MarkdownLocator("年假", 20, 80);
        ParsedBlock block = new ParsedBlock(tokens(900), locator);

        List<ChunkDraft> chunks = chunker.chunk(List.of(block));

        assertEquals(3, chunks.size());
        assertEquals(List.of(400, 400, 220), chunks.stream().map(ChunkDraft::estimatedTokens).toList());
        assertTrue(chunks.stream().allMatch(chunk -> chunk.estimatedTokens() <= 400));
        assertEquals(IntStream.range(0, chunks.size()).boxed().toList(),
                chunks.stream().map(ChunkDraft::chunkNo).toList());
        assertOverlap(chunks.get(0), chunks.get(1), 60);
        assertOverlap(chunks.get(1), chunks.get(2), 60);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.locator().equals(locator)));
    }

    @Test
    void chunksNeverMergeDifferentSourceLocators() {
        Chunker chunker = new Chunker(new ChunkingProperties(400, 60));
        MarkdownLocator markdownLocator = new MarkdownLocator("入职", 1, 4);
        PdfLocator pdfLocator = new PdfLocator(2);

        List<ChunkDraft> chunks = chunker.chunk(List.of(
                new ParsedBlock("入职账号由 IT 创建。", markdownLocator),
                new ParsedBlock("PAGE_TWO_ONLY_MARKER", pdfLocator)));

        assertEquals(2, chunks.size());
        assertSame(markdownLocator, chunks.get(0).locator());
        assertSame(pdfLocator, chunks.get(1).locator());
    }

    private static String tokens(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> "token" + String.format("%04d", index))
                .reduce((left, right) -> left + " " + right)
                .orElseThrow();
    }

    private static void assertOverlap(ChunkDraft left, ChunkDraft right, int overlap) {
        List<String> leftTokens = words(left.content());
        List<String> rightTokens = words(right.content());
        assertEquals(leftTokens.subList(leftTokens.size() - overlap, leftTokens.size()),
                rightTokens.subList(0, overlap));
    }

    private static List<String> words(String text) {
        return new ArrayList<>(Arrays.asList(text.split("\\s+")));
    }
}
