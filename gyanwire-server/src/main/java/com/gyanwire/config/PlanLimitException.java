package com.gyanwire.config;

import java.util.Map;

public class PlanLimitException extends RuntimeException {
    private final String errorCode;
    private final Map<String, Object> data;

    public PlanLimitException(String message, String errorCode, Map<String, Object> data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public String getErrorCode() { return errorCode; }
    public Map<String, Object> getData() { return data; }
}
