package com.example.realtimeml;

import com.example.realtimeml.exception.HelpExitException;
import com.example.realtimeml.exception.InvalidParamException;
import com.example.realtimeml.exception.UnexpectedRuntimException;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.File;
import java.io.IOException;

abstract public class SparkDemoCmdApp {
    protected String[] rawCmdInputParams;

    // -1 means to process all available records (indefinitely)
    protected Integer recordNum;

    protected Integer clickNum;
    protected String eshopRawDataCsvFileDir;

    protected boolean streamingMode;

    protected final String appName;

    protected CommandLine commandLine;
    protected final DefaultParser commandParser;
    protected final Options cliOptions = new Options();

    public abstract void processExtendedInputParams() throws InvalidParamException;
    public abstract void runApp();
    public abstract void termApp();

    public SparkDemoCmdApp(String appName, String[] inputParams) {
        this.appName = appName;
        this.rawCmdInputParams = inputParams;
        this.commandParser = new DefaultParser();

        addRequiredCommandLineOption("rn","recordNum", true, "Number of records to output.");
        addRequiredCommandLineOption("cn","clickNum", true, "Number of clicks included in one output.");
        addRequiredCommandLineOption("cfd","csvFileDirectory", true, "IoT sensor data CSV file directory.");
        addOptionalCommandLineOption("stm", "streamingMode", true, "Run the spark job in streaming mode");
    }

    protected void addRequiredCommandLineOption(String option, String longOption, boolean hasArg, String description) {
        Option opt = new Option(option, longOption, hasArg, description);
        opt.setRequired(true);
    	cliOptions.addOption(opt);
    }

    protected void addOptionalCommandLineOption(String option, String longOption, boolean hasArg, String description) {
        Option opt = new Option(option, longOption, hasArg, description);
        opt.setRequired(false);
        cliOptions.addOption(opt);
    }

    public int run() {
        int exitCode = 0;
        try {
            this.processInputParams();
            this.runApp();
        }
        catch (InvalidParamException ipe) {
            System.out.println("\n[ERROR] Invalid input value(s) detected!");
            ipe.printStackTrace();
            exitCode = 1;
        }
        catch (UnexpectedRuntimException ure) {
            System.out.println("\n[ERROR] Unexpected runtime error detected!");
            ure.printStackTrace();
            exitCode = 2;
        }
        finally {
            this.termApp();
        }
        
        return exitCode;
    }
    
    public void processInputParams() throws HelpExitException, InvalidParamException {

    	if (commandLine == null) {
            try {
                commandLine = commandParser.parse(cliOptions, rawCmdInputParams);
            } catch (ParseException e) {
                throw new InvalidParamException("Failed to parse application CLI input parameters: " + e.getMessage());
            }
    	}

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

        processExtendedInputParams();
    }

    public boolean processBooleanInputParam(String optionName) {
        return processBooleanInputParam(optionName, false);
    }
    public boolean processBooleanInputParam(String optionName, boolean dftValue) {
        Option option = cliOptions.getOption(optionName);

        // Default value if not present on command line
        boolean boolVal = dftValue;
        String value = commandLine.getOptionValue(option.getOpt());

        if (option.isRequired()) {
            if (StringUtils.isBlank(value)) {
                throw new InvalidParamException("Empty value for argument '" + optionName + "'");
            }
        }

        if (StringUtils.isNotBlank(value)) {
            boolVal=BooleanUtils.toBoolean(value);
        }

        return boolVal;
    }

    public int processIntegerInputParam(String optionName) {
        return processIntegerInputParam(optionName, 0);
    }
    public int processIntegerInputParam(String optionName, int dftValue) {
        Option option = cliOptions.getOption(optionName);

        // Default value if not present on command line
        int intVal = dftValue;
        String value = commandLine.getOptionValue(option.getOpt());

        if (option.isRequired()) {
            if (StringUtils.isBlank(value)) {
                throw new InvalidParamException("Empty value for argument '" + optionName + "'");
            }
        }

        if (StringUtils.isNotBlank(value)) {
            intVal = NumberUtils.toInt(value);
        }

        return intVal;
    }

    public String processStringInputParam(String optionName) {
        return processStringInputParam(optionName, null);
    }
    public String processStringInputParam(String optionName, String dftValue) {
    	Option option = cliOptions.getOption(optionName);

        String strVal = dftValue;
        String value = commandLine.getOptionValue(option);

        if (option.isRequired()) {
            if (StringUtils.isBlank(value)) {
                throw new InvalidParamException("Empty value for argument '" + optionName + "'");
            }
        }

        if (StringUtils.isNotBlank(value)) {
            strVal = value;
        }

        return strVal;
    }
    
    public File processFileInputParam(String optionName) {
        Option option = cliOptions.getOption(optionName);

        File file = null;

        if (option.isRequired()) {
            String path = commandLine.getOptionValue(option.getOpt());
            try {
                file = new File(path);
                file.getCanonicalPath();
            } catch (IOException ex) {
                throw new InvalidParamException("Invalid file path for param '" + optionName + "': " + path);
            }
        }

        return file;
    }
}
