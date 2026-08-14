package com.xiaosu.knowledge.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xiaosu.knowledge.parser.ParsedBlock;

@Component
public class Chunker {

    private final int maxTokens;
    private final int overlapTokens;
    private final HeuristicTokenEstimator tokenEstimator = new HeuristicTokenEstimator();

    public Chunker(ChunkingProperties properties) {
        this.maxTokens = properties.maxTokens();
        this.overlapTokens = properties.overlapTokens();
    }

    public List<ChunkDraft> chunk(List<ParsedBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        List<ChunkDraft> chunks = new ArrayList<>();
        for (ParsedBlock block : blocks) {
            Objects.requireNonNull(block, "block");
            addBlockChunks(block, chunks);
        }
        return List.copyOf(chunks);
    }

    private void addBlockChunks(ParsedBlock block, List<ChunkDraft> chunks) {
        List<HeuristicTokenEstimator.TokenSpan> tokens = tokenEstimator.spans(block.text());
        int startToken = 0;
        while (startToken < tokens.size()) {
            int endToken = Math.min(startToken + maxTokens, tokens.size());
            int startCharacter = tokens.get(startToken).start();
            int endCharacter = tokens.get(endToken - 1).end();
            String content = block.text().substring(startCharacter, endCharacter).strip();
            chunks.add(new ChunkDraft(
                    chunks.size(),
                    content,
                    block.locator(),
                    endToken - startToken));

            if (endToken == tokens.size()) {
                break;
            }
            startToken = endToken - overlapTokens;
        }
    }
}
