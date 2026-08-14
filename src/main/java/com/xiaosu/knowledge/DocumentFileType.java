package com.xiaosu.knowledge;

import java.util.Locale;
import java.util.Set;

enum DocumentFileType {
    MARKDOWN(Set.of("md", "markdown"), Set.of("text/markdown", "text/plain", "application/octet-stream"),
            "text/markdown"),
    TEXT(Set.of("txt"), Set.of("text/plain", "application/octet-stream"), "text/plain"),
    PDF(Set.of("pdf"), Set.of("application/pdf", "application/octet-stream"), "application/pdf");

    private final Set<String> extensions;
    private final Set<String> acceptedContentTypes;
    private final String canonicalMimeType;

    DocumentFileType(Set<String> extensions, Set<String> acceptedContentTypes, String canonicalMimeType) {
        this.extensions = extensions;
        this.acceptedContentTypes = acceptedContentTypes;
        this.canonicalMimeType = canonicalMimeType;
    }

    public String canonicalMimeType() {
        return canonicalMimeType;
    }

    public void validateContent(byte[] prefix) {
        if (this == PDF && !startsWithPdfSignature(prefix)) {
            throw DocumentUploadException.unsupported("PDF 文件缺少有效的 PDF 签名");
        }
        if (this != PDF && containsNullByte(prefix)) {
            throw DocumentUploadException.unsupported("文本文件包含不支持的二进制内容");
        }
    }

    public static DocumentFileType detect(String canonicalName, String declaredContentType) {
        int separator = canonicalName.lastIndexOf('.');
        if (separator < 1 || separator == canonicalName.length() - 1) {
            throw DocumentUploadException.unsupported("仅支持 Markdown、TXT 和 PDF 文件");
        }
        String extension = canonicalName.substring(separator + 1).toLowerCase(Locale.ROOT);
        DocumentFileType type = null;
        for (DocumentFileType candidate : values()) {
            if (candidate.extensions.contains(extension)) {
                type = candidate;
                break;
            }
        }
        if (type == null) {
            throw DocumentUploadException.unsupported("仅支持 Markdown、TXT 和 PDF 文件");
        }

        String normalizedContentType = normalizeContentType(declaredContentType);
        if (!type.acceptedContentTypes.contains(normalizedContentType)) {
            throw DocumentUploadException.unsupported("文件扩展名与 Content-Type 不匹配");
        }
        return type;
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private static boolean startsWithPdfSignature(byte[] prefix) {
        byte[] signature = {'%', 'P', 'D', 'F', '-'};
        if (prefix.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (prefix[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsNullByte(byte[] prefix) {
        for (byte value : prefix) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }
}
