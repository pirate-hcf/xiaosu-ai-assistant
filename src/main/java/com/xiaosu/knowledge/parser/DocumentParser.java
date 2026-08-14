package com.xiaosu.knowledge.parser;

import java.io.InputStream;
import java.util.List;

public interface DocumentParser {

    boolean supports(String mimeType);

    List<ParsedBlock> parse(InputStream inputStream);
}
