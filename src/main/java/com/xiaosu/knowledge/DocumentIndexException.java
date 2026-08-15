package com.xiaosu.knowledge;

import org.springframework.http.HttpStatus;

public class DocumentIndexException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DocumentIndexException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DocumentIndexException notFound() {
        return new DocumentIndexException(HttpStatus.NOT_FOUND, "DOCUMENT_NOT_FOUND", "文档不存在");
    }

    public static DocumentIndexException notRetryable() {
        return new DocumentIndexException(HttpStatus.CONFLICT, "DOCUMENT_NOT_RETRYABLE", "只有索引失败的文档可以重试");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
