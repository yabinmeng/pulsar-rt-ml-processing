package com.example.realtimeml.exception;

public class UnexpectedRuntimException extends RuntimeException {
    public UnexpectedRuntimException(String paramName, String errDesc) {
        super("Invalid setting for parameter (" + paramName + "): " + errDesc);
        this.printStackTrace();
    }

    public UnexpectedRuntimException(String fullErrDesc) {
        super(fullErrDesc);
    }
}
