package com.prwatech.skillama.exception;

import lombok.Getter;

/** Thrown when an uploaded CSV's content hash matches a dataset already active in the same course. */
@Getter
public class DuplicateDatasetException extends RuntimeException {
    private final String existingDatasetId;

    public DuplicateDatasetException(String existingDatasetId) {
        super("An identical file is already attached as dataset " + existingDatasetId);
        this.existingDatasetId = existingDatasetId;
    }
}
