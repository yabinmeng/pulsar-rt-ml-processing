package com.example.rtmldemo.exception;

public class InvalidParamException extends RuntimeException {

    private String errorDescription;

    public InvalidParamException(String paramName, String errDesc) {
        super("Invalid setting for parameter (" + paramName + "): " + errDesc);
        this.errorDescription = "Invalid setting for parameter (" + paramName + "): " + errDesc;
    }

    public InvalidParamException(String fullErrDesc) {
        super(fullErrDesc);
        this.errorDescription = fullErrDesc;
    }

    public String getErrorDescription() { return errorDescription; }

}