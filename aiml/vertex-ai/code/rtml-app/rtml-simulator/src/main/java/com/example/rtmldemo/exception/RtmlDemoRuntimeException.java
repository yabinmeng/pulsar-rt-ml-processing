package com.example.rtmldemo.exception;

public class RtmlDemoRuntimeException extends RuntimeException {
    public RtmlDemoRuntimeException(String paramName, String errDesc) {
        super("Invalid setting for parameter (" + paramName + "): " + errDesc);
        this.printStackTrace();
    }

    public RtmlDemoRuntimeException(String fullErrDesc) {
        super(fullErrDesc);
    }
}