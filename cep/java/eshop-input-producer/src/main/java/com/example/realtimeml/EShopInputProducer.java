package com.example.realtimeml;

import com.example.realtimeml.exception.InvalidParamException;
import com.example.realtimeml.exception.UnexpectedRuntimException;
import com.example.realtimeml.pojo.EShopInputData;
import com.example.realtimeml.pojo.EShopDataUtils;
import com.example.realtimeml.util.CsvFileLineScanner;
import org.apache.pulsar.client.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class EShopInputProducer extends EShopPulsarClientApp {

    // Must be set before initializing the "logger" object.
    private final static String APP_NAME = "EShopInputProducer";
    static { System.setProperty("log_file_base_name", getLogFileName(API_TYPE, APP_NAME)); }

    private final static Logger logger = LoggerFactory.getLogger(EShopInputProducer.class);

    private static File eshopRawDataCsvFile;
    private static PulsarClient pulsarClient;
    private static Producer<EShopInputData> pulsarProducer;

    public EShopInputProducer(String appName, String[] inputParams) {
        super(appName, inputParams);

        addOptionalCommandLineOption("csv","csvFile", true, "IoT sensor data CSV file.");

        logger.info("Starting application: \"" + appName + "\" ...");
    }

    public static void main(String[] args) {
        EShopCmdApp workshopApp = new EShopInputProducer(APP_NAME, args);
        int exitCode = workshopApp.run();
        System.exit(exitCode);
    }

    public void processExtendedInputParams() throws InvalidParamException {
        super.processExtendedInputParams();

        // (Required) CLI option for IoT sensor source file
        eshopRawDataCsvFile = processFileInputParam("csv");
        if ( eshopRawDataCsvFile == null) {
            throw new InvalidParamException("Must provided a valid (raw) EShop data csv file!");
        }
    }

    @Override
    public void runApp() throws UnexpectedRuntimException {
        try {
            if (pulsarClient == null ) {
                pulsarClient = createNativePulsarClient();
                if (pulsarProducer == null) {
                    ProducerBuilder<EShopInputData> producerBuilder = pulsarClient.newProducer(Schema.AVRO(EShopInputData.class));
                    pulsarProducer = producerBuilder.topic(topicName).create();
                }
            }

            assert (eshopRawDataCsvFile != null);

            CsvFileLineScanner csvFileLineScanner = new CsvFileLineScanner(eshopRawDataCsvFile);
            TypedMessageBuilder<EShopInputData> messageBuilder = pulsarProducer.newMessage();

            boolean isTitleLine = true;
            String titleLine = "";
            int msgSent = 0;

            while (csvFileLineScanner.hasNextLine()) {
                String csvLine = csvFileLineScanner.getNextLine();
                // Skip the first line which is a title line
                if (!isTitleLine) {
                    if ( (numMsg == -1) || (msgSent < numMsg) ) {
                        // Send message once per 0.1 second
                        try {
                            Thread.sleep(100);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        EShopInputData data = EShopDataUtils.csvToPojo(csvLine);
                        MessageId messageId = messageBuilder
                                .value(data)
                                .send();
                        if (logger.isDebugEnabled()) {
                            logger.debug("Published a message with eshop data: [{}] {}",
                                    messageId,
                                    data.toString());
                        }

                        msgSent++;
                    }
                } else {
                    isTitleLine = false;
                    titleLine = csvLine;
                }
            }

        } catch (PulsarClientException pce) {
        	pce.printStackTrace();
            throw new UnexpectedRuntimException("Unexpected error when producing Pulsar messages: " + pce.getMessage());
        } catch (IOException ioException) {
            throw new UnexpectedRuntimException("Failed to read from the workload data source file: " + ioException.getMessage());
        }
    }

    @Override
    public void termApp() {
        try {
            if (pulsarProducer != null) {
                pulsarProducer.close();
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
