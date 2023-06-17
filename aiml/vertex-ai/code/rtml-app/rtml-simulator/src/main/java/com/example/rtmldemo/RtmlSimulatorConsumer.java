package com.example.rtmldemo;

import com.example.rtmldemo.exception.InvalidParamException;
import com.example.rtmldemo.exception.RtmlDemoRuntimeException;
import com.example.rtmldemo.util.RtmlDemoUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RtmlSimulatorConsumer extends PulsarCmdApp {

    static { System.setProperty("log_file_base_name", "RtmlSimulatorConsumer"); }
    private final static Logger logger = LoggerFactory.getLogger(RtmlSimulatorConsumer.class);

    private String subscriptionName;
    private SubscriptionType subscriptionType = SubscriptionType.Exclusive;
    private static Consumer<String> pulsarConsumer;

    public RtmlSimulatorConsumer(String appName, String[] inputParams) {
        super(appName, inputParams);
        addOptionalCommandLineOption("sbt","subType", true, "Pulsar subscription type.");
        addRequiredCommandLineOption("sbn", "subName", true, "Pulsar subscription name.");

        logger.info("Starting application: \"" + appName + "    \" ...");
    }

    public static void main(String[] args) {
        RtmlSimulatorConsumer simulatorClient = new RtmlSimulatorConsumer("RtmlSimulatorConsumer", args);
        int exitCode = simulatorClient.runCmdApp();
        System.exit(exitCode);
    }

    @Override
    public void processExtendedInputParams() throws InvalidParamException {
        // (Required) Pulsar subscription name
        subscriptionName = RtmlDemoUtil.processStringInputParam(commandLine, cliOptions, "sbn");
        if ( StringUtils.isBlank(subscriptionName) ) {
            throw new InvalidParamException("Must provide a subscription name for a consumer!");
        }

        // (Optional) Pulsar subscription type
        String subType = RtmlDemoUtil.processStringInputParam(commandLine, cliOptions, "sbt");
        if (!StringUtils.isBlank(subType)) {
            try {
                subscriptionType = SubscriptionType.valueOf(subType);
            }
            catch (IllegalArgumentException iae) {
                subscriptionType = SubscriptionType.Exclusive;
            }
        }
    }

    @Override
    public void execute() {
        try {
            if (pulsarClient == null) {
                pulsarClient = createNativePulsarClient();

                if (pulsarConsumer == null) {
                    ConsumerBuilder<String> consumerBuilder = pulsarClient.newConsumer(Schema.STRING);

                    String resultTopic = rtmlDemoMainProperty.getOutputTopic();
                    consumerBuilder.topic(resultTopic);
                    consumerBuilder.subscriptionName(subscriptionName);
                    consumerBuilder.subscriptionType(subscriptionType);
                    pulsarConsumer = consumerBuilder.subscribe();
                }
            }

            int msgRecvd = 0;

            while ((msgRecvd < totalRecordNum) || (totalRecordNum == -1)) {
                Message<String> message = pulsarConsumer.receive();
                logger.info("({}) Message received and acknowledged: " +
                                "key={}; properties={}; value={}",
                        pulsarConsumer.getConsumerName(),
                        message.getKey(),
                        message.getProperties(),
                        new String(message.getData()));
                pulsarConsumer.acknowledge(message);
                msgRecvd++;
            }

        }
        catch (PulsarClientException pce) {
            throw new RtmlDemoRuntimeException("Unexpected error when consuming Pulsar messages: " + pce.getMessage());
        }
    }

    @Override
    public void termCmdApp() {
        try {
            if (pulsarConsumer != null) {
                pulsarConsumer.close();
            }

            if (pulsarClient != null) {
                pulsarClient.close();
            }
        }
        catch (PulsarClientException pce) {
            throw new RtmlDemoRuntimeException("Failed to terminate Pulsar consumer or client!");
        }
        finally {
            logger.info("Terminating application: \"" + appName + "\" ...");
        }
    }
}
