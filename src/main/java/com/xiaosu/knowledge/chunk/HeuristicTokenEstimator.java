package com.xiaosu.knowledge.chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HeuristicTokenEstimator {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\p{IsHan}|[\\p{L}\\p{N}_]+|[^\\s]");

    List<TokenSpan> spans(String text) {
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        List<TokenSpan> spans = new ArrayList<>();
        while (matcher.find()) {
            spans.add(new TokenSpan(matcher.start(), matcher.end()));
        }
        return List.copyOf(spans);
    }

    record TokenSpan(int start, int end) {
    }
}
