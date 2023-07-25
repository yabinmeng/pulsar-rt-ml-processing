package com.example.realtimeml;

import com.example.realtimeml.exception.InvalidParamException;
import com.example.realtimeml.exception.UnexpectedRuntimException;
import com.example.realtimeml.pojo.EShopInputData;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EShopModelConsumer extends EShopPulsarClientApp {

    // Must be set before initializing the "logger" object.
    private final static String APP_NAME = "EShopModelConsumer";
    static { System.setProperty("log_file_base_name", getLogFileName(API_TYPE, APP_NAME)); }

    private final static Logger logger = LoggerFactory.getLogger(EShopModelConsumer.class);

    private String subscriptionName;
    private SubscriptionType subscriptionType = SubscriptionType.Exclusive;
    
    private static PulsarClient pulsarClient;
    private static Consumer<EShopInputData> pulsarConsumer;

    public EShopModelConsumer(String appName, String[] inputParams) {
        super(appName, inputParams);

        addOptionalCommandLineOption("sbt","subType", true, "Pulsar subscription type.");
        addRequiredCommandLineOption("sbn", "subName", true, "Pulsar subscription name.");

        logger.info("Starting application: \"" + appName + "\" ...");
    }

    public static void main(String[] args) {
        EShopCmdApp workshopApp = new EShopModelConsumer(APP_NAME, args);
        int exitCode = workshopApp.run();
        System.exit(exitCode);
    }

    @Override
    public void processExtendedInputParams() throws InvalidParamException {
        super.processExtendedInputParams();

        // (Required) Pulsar subscription name
        subscriptionName = processStringInputParam("sbn");
        if ( StringUtils.isBlank(subscriptionName) ) {
            throw new InvalidParamException("Must provide a subscription name for a consumer!");
        }

        // (Optional) Pulsar subscription type
        String subType = processStringInputParam("sbt");
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
    public void runApp() throws UnexpectedRuntimException {
        try {
            if (pulsarClient == null ) {
                pulsarClient = createNativePulsarClient();
                if (pulsarConsumer == null) {
                    ConsumerBuilder<EShopInputData> consumerBuilder = pulsarClient.newConsumer(Schema.AVRO(EShopInputData.class));
                    consumerBuilder.topic(topicName);
                    consumerBuilder.subscriptionName(subscriptionName);
                    consumerBuilder.subscriptionType(subscriptionType);
                    pulsarConsumer = consumerBuilder.subscribe();
                }
            }

            int msgRecvd = 0;
            if (numMsg == -1) {
                numMsg = Integer.MAX_VALUE;
            }

            while (msgRecvd < numMsg) {
                Message<EShopInputData> message = pulsarConsumer.receive();
                pulsarConsumer.acknowledge(message);

                if (logger.isDebugEnabled()) {
                    logger.debug("({}) Message received and acknowledged: " +
                                    "key={}; properties={}; value={}",
                            pulsarConsumer.getConsumerName(),
                            message.getKey(),
                            message.getProperties(),
                            message.getValue());
                }

                msgRecvd++;
            }

        } catch (PulsarClientException pce) {
        	pce.printStackTrace();
            throw new UnexpectedRuntimException("Unexpected error when producing Pulsar messages: " + pce.getMessage());
        }
    }

    @Override
    public void termApp() {
        try {
            if (pulsarConsumer != null) {
                pulsarConsumer.close();
            }

            if (pulsarClient != null) {
                pulsarClient.close();
            }
        }
        catch (PulsarClientException pce) {
            throw new UnexpectedRuntimException("Failed to terminate Pulsar producer or client!");
        }
        finally {
            logger.info("Terminating application: \"" + appName + "\" ...");
        }
    }
}
