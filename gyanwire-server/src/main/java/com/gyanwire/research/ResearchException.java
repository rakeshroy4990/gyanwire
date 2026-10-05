package com.gyanwire.research;

public class ResearchException extends RuntimeException {
    private final String errorCode;
    private final int status;

    public ResearchException(String message, String errorCode, int status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public int getStatus() { return status; }
}
