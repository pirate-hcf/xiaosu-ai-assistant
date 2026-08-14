package com.xiaosu.knowledge;

import org.springframework.http.HttpStatus;

public class DocumentUploadException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public DocumentUploadException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public DocumentUploadException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static DocumentUploadException badRequest(String code, String message) {
        return new DocumentUploadException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static DocumentUploadException unsupported(String message) {
        return new DocumentUploadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", message);
    }

    public static DocumentUploadException tooLarge(long maxBytes) {
        return new DocumentUploadException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "FILE_TOO_LARGE",
                "文件大小不能超过 " + maxBytes + " 字节");
    }

    public static DocumentUploadException conflict(String canonicalName) {
        return new DocumentUploadException(
                HttpStatus.CONFLICT,
                "DOCUMENT_ALREADY_EXISTS",
                "文档已存在：" + canonicalName);
    }

    public static DocumentUploadException storageFailure(Throwable cause) {
        return new DocumentUploadException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "FILE_STORAGE_FAILED",
                "文件保存失败",
                cause);
    }
}
