package com.example.realtimeml;

import com.example.realtimeml.exception.InvalidParamException;
import org.apache.commons.lang3.StringUtils;

abstract public class SparkDemoCmdApp extends EShopRecCmdApp {

    protected final static String API_TYPE = "eShopRecSparkSS";

    // -1 means to process all available records (indefinitely)
    protected Integer recordNum;

    protected Integer clickNum;
    protected String eshopRawDataCsvFileDir;

    protected boolean streamingMode;

    public SparkDemoCmdApp(String appName, String[] inputParams) {
        super(appName, inputParams);

        addRequiredCommandLineOption(
                "rn","recordNum", true, "Number of records to output.");
        addRequiredCommandLineOption(
                "cn","clickNum", true, "Number of clicks included in one output.");
        addRequiredCommandLineOption(
                "cfd","csvFileDirectory", true, "IoT sensor data CSV file directory.");
        addOptionalCommandLineOption(
                "stm", "streamingMode", true, "Run the spark job in streaming mode");
    }

    @Override
    public void processExtendedInputParams() throws InvalidParamException {
        // (Required) CLI option for number of records to output (default: -1)
        recordNum = processIntegerInputParam("rn", -1);
        if ( (recordNum <= 0) && (recordNum != -1) ) {
            throw new InvalidParamException("Recorder number (\"-rn\") must be a positive integer or -1!");
        }

        // (Required) CLI option for number of clicks in one output (default: -1)
        clickNum = processIntegerInputParam("cn", -1);
        if ( clickNum <= 0 ) {
            throw new InvalidParamException("Click number (\"-cn\") must be a positive integer!");
        }

        // (Required) CLI option for IoT sensor source file
        eshopRawDataCsvFileDir = processStringInputParam("cfd");
        if ( StringUtils.isBlank(eshopRawDataCsvFileDir) ) {
            throw new InvalidParamException("Must provided a valid (raw) EShop data csv file pattern!");
        }

        // (Optional) Run spark job in streaming mode (default: false)
        streamingMode = processBooleanInputParam("stm", false);
    }
}
