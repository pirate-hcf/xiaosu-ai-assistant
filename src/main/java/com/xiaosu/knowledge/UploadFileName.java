package com.xiaosu.knowledge;

import java.text.Normalizer;

record UploadFileName(String canonicalName, DocumentFileType type) {

    private static final int MAX_FILE_NAME_LENGTH = 255;

    static UploadFileName from(String submittedName, String contentType) {
        if (submittedName == null || submittedName.isBlank()) {
            throw DocumentUploadException.badRequest("INVALID_FILE_NAME", "文件名不能为空");
        }
        String fileName = submittedName.replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
        fileName = Normalizer.normalize(fileName, Normalizer.Form.NFKC)
                .replaceAll("[\\p{Cntrl}]", "")
                .trim()
                .replaceAll("\\s+", " ");
        if (fileName.isBlank() || fileName.equals(".") || fileName.equals("..")) {
            throw DocumentUploadException.badRequest("INVALID_FILE_NAME", "文件名无效");
        }
        if (fileName.length() > MAX_FILE_NAME_LENGTH) {
            throw DocumentUploadException.badRequest("INVALID_FILE_NAME", "文件名不能超过 255 个字符");
        }
        return new UploadFileName(fileName, DocumentFileType.detect(fileName, contentType));
    }
}
