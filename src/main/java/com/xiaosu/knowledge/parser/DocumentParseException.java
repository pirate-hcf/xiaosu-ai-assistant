package com.xiaosu.knowledge.parser;

public class DocumentParseException extends RuntimeException {

    private final ParseFailure failure;

    public DocumentParseException(ParseFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public DocumentParseException(ParseFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = failure;
    }

    public ParseFailure failure() {
        return failure;
    }
}
