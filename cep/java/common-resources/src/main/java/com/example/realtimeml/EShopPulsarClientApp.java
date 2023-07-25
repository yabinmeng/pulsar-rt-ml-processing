package com.example.realtimeml;

import com.example.realtimeml.exception.InvalidParamException;

abstract public class EShopPulsarClientApp extends EShopCmdApp {

    protected final static String API_TYPE = "eShopRec";

    // -1 means to process all available messages (indefinitely)
    protected Integer numMsg;
    public abstract void runApp();
    public abstract void termApp();

    public EShopPulsarClientApp(String appName, String[] inputParams) {
        super(appName, inputParams);

        addOptionalCommandLineOption("n","numMsg", true, "Number of messages to process.");
    }

    public void processExtendedInputParams() throws InvalidParamException {
        super.processExtendedInputParams();

        // (Required) CLI option for number of messages
        numMsg = processIntegerInputParam("n");
        if ( (numMsg <= 0) && (numMsg != -1) ) {
            throw new InvalidParamException("Message number must be a positive integer or -1 (all available raw input)!");
        }
    }
}
