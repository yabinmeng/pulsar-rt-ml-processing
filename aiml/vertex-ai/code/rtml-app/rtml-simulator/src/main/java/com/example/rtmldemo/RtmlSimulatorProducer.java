package com.example.rtmldemo;

import com.example.rtmldemo.exception.InvalidParamException;
import com.example.rtmldemo.exception.RtmlDemoRuntimeException;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class RtmlSimulatorProducer extends PulsarCmdApp {

    static { System.setProperty("log_file_base_name", "RtmlSimulatorProducer"); }
    private final static Logger logger = LoggerFactory.getLogger(RtmlSimulatorProducer.class);
    private static Producer<String> pulsarProducer;

    public RtmlSimulatorProducer(String appName, String[] inputParams) {
        super(appName, inputParams);
        logger.info("Starting application: \"" + appName + "    \" ...");
    }

    public static void main(String[] args) {
        RtmlSimulatorProducer simulatorClient = new RtmlSimulatorProducer("RtmlSimulatorProducer", args);
        int exitCode = simulatorClient.runCmdApp();
        System.exit(exitCode);
    }

    @Override
    public void processExtendedInputParams() throws InvalidParamException {
        // No extra parameters
    }

    @Override
    public void execute() {
        try {
            if (pulsarClient == null ) {
                pulsarClient = createNativePulsarClient();

                if (pulsarProducer == null) {
                    ProducerBuilder<String> producerBuilder = pulsarClient.newProducer(Schema.STRING);

                    String inputTopic = rtmlDemoMainProperty.getInputTopic();
                    pulsarProducer = producerBuilder.topic(inputTopic).create();
                }
            }

            TypedMessageBuilder<String> messageBuilder = pulsarProducer.newMessage();
            int msgSent = 0;

            while ((msgSent < totalRecordNum) || (totalRecordNum == -1)) {

                double[] randomDoubles;
                boolean useDsrsService = rtmlDemoMainProperty.getUseDsrsService();
                if (useDsrsService) {
                    // a random value between 5 and 15, inclusive
                    int randIntVal = RandomUtils.nextInt(5, 16);
                    randomDoubles = genRandomDoubles(randIntVal);
                }
                else {
                    randomDoubles = genRandomDoubles(9);
                }

                String[] strArray = new String[randomDoubles.length];
                for (int i = 0; i < randomDoubles.length; i++) {
                    strArray[i] = String.valueOf(randomDoubles[i]);
                }

                String msgPayload = "{" + StringUtils.join(strArray, ",") + "}";
                MessageId messageId = messageBuilder
                        .value(msgPayload)
                        .send();
                logger.info("Published a message with raw value: [{}] {}",
                        msgSent,
                        msgPayload);

                msgSent++;
            }

        } catch (PulsarClientException pce) {
            pce.printStackTrace();
            throw new RtmlDemoRuntimeException("Unexpected error when producing Pulsar messages: " + pce.getMessage());
        }
    }

    @Override
    public void termCmdApp() {
        try {
            if (pulsarProducer != null) {
                pulsarProducer.close();
            }

            if (pulsarClient != null) {
                pulsarClient.close();
            }
        }
        catch (PulsarClientException pce) {
            throw new RtmlDemoRuntimeException("Failed to terminate Pulsar producer or client!");
        }
        finally {
            logger.info("Terminating application: \"" + appName + "\" ...");
        }
    }

    // Generate a random double number array
    // - each double value is between [0,30)
    // - each double value is limited with 2 decimals
    double[] genRandomDoubles(int num) {
        assert (num > 0);

        ArrayList<Double> doubleArrayList = new ArrayList<>();
        for (int i=0; i<num; i++) {
            double val = Math.round(RandomUtils.nextDouble(0, 30) * 100.0) / 100.0;
            doubleArrayList.add(val);
        }

        return doubleArrayList.stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
    }
}
