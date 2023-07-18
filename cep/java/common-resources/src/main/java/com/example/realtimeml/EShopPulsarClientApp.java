package com.example.realtimeml;

import com.example.realtimeml.exception.InvalidParamException;
import com.example.realtimeml.util.ClientConnConf;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import java.io.File;

abstract public class EShopPulsarClientApp extends EShopRecCmdApp {

    protected final static String API_TYPE = "eShopRec";

    // -1 means to process all available messages (indefinitely)
    protected Integer numMsg;
    public abstract void runApp();
    public abstract void termApp();

    public EShopPulsarClientApp(String appName, String[] inputParams) {
        super(appName, inputParams);

        addRequiredCommandLineOption("n","numMsg", true, "Number of messages to process.");
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
