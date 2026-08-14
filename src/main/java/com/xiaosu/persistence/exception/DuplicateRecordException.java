package com.xiaosu.persistence.exception;

public class DuplicateRecordException extends RuntimeException {

    public DuplicateRecordException(String recordType, String uniqueKey, Throwable cause) {
        super("Duplicate " + recordType + " for unique key: " + uniqueKey, cause);
    }
}
