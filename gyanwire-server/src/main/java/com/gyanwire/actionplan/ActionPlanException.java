package com.gyanwire.actionplan;

import java.util.Map;

public class ActionPlanException extends RuntimeException {
    private final String errorCode;
    private final int status;
    private final Map<String, Object> data;

    public ActionPlanException(String message, String errorCode, int status, Map<String, Object> data) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
