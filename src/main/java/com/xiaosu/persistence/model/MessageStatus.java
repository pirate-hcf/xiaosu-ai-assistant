package com.xiaosu.persistence.model;

public enum MessageStatus {
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");

    private final String databaseValue;

    MessageStatus(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static MessageStatus fromDatabase(String value) {
        for (MessageStatus status : values()) {
            if (status.databaseValue.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown message status: " + value);
    }
}
