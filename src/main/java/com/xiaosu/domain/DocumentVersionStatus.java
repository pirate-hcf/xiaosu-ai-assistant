package com.xiaosu.domain;

public enum DocumentVersionStatus {
    PENDING("pending"),
    INDEXED("indexed"),
    FAILED("failed");

    private final String databaseValue;

    DocumentVersionStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static DocumentVersionStatus fromDatabase(String value) {
        for (DocumentVersionStatus status : values()) {
            if (status.databaseValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown document version status: " + value);
    }
}
