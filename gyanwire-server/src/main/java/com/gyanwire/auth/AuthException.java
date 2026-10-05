package com.gyanwire.auth;

public class AuthException extends RuntimeException {
    private final String errorCode;
    private final int status;

    public AuthException(String message, String errorCode, int status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() { return errorCode; }
    public int getStatus() { return status; }
}
