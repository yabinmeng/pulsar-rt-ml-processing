package com.example.realtimeml;

import com.example.realtimeml.util.ClientConnConf;
import org.apache.commons.cli.*;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.example.realtimeml.exception.HelpExitException;
import com.example.realtimeml.exception.InvalidParamException;
import com.example.realtimeml.exception.UnexpectedRuntimException;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

abstract public class EShopCmdApp {

    protected final static String API_TYPE = "eShopRec";

    protected String[] rawCmdInputParams;

    protected final String appName;

    protected String topicName;
    protected File clientConnFile;
    protected boolean useAstraStreaming;

    protected ClientConnConf clientConnConf;

    protected CommandLine commandLine;
    protected final DefaultParser commandParser;
    protected final Options cliOptions = new Options();

    public abstract void runApp();
    public abstract void termApp();

    public EShopCmdApp(String appName, String[] inputParams) {
        this.appName = appName;
        this.rawCmdInputParams = inputParams;
        this.commandParser = new DefaultParser();

        addOptionalCommandLineOption("h", "help", false, "Displays the usage method.");
        addOptionalCommandLineOption("tp", "topic", true, "Pulsar topic name.");
        addOptionalCommandLineOption("pc","pulsarConnFile", true, "\"client.conf\" file path.");
        addOptionalCommandLineOption("as", "astra", false, "Whether to use Astra streaming.");
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

    protected static String getLogFileName(String apiType, String appName) {
        return apiType + "_" + appName;
    }

    public void processExtendedInputParams() throws InvalidParamException {
        // (Required) CLI option for Pulsar topic
        topicName = processStringInputParam("tp");

        // (Required) CLI option for client.conf file
        clientConnFile = processFileInputParam("pc");
        if (clientConnFile != null) {
            clientConnConf = new ClientConnConf(clientConnFile);
        }

        // (Optional) Whether to use Astra Streaming
        useAstraStreaming = processBooleanInputParam("as", true);
    };

    public int run() {
        int exitCode = 0;
        try {
            this.processInputParams();
            this.runApp();
        }
        catch (HelpExitException hee) {
            this.usage(appName);
            exitCode = 1;
        }
        catch (InvalidParamException ipe) {
            System.out.println("\n[ERROR] Invalid input value(s) detected!");
            ipe.printStackTrace();
            exitCode = 2;
        }
        catch (UnexpectedRuntimException ure) {
            System.out.println("\n[ERROR] Unexpected runtime error detected!");
            ure.printStackTrace();
            exitCode = 3;
        }
        finally {
            this.termApp();
        }
        
        return exitCode;
    }

    public void usage(String appNme) {
        PrintWriter printWriter = new PrintWriter(System.out, true);

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(printWriter, 150, appName,
                "Command Line Options:",
                cliOptions, 2, 1, "", true);

        System.out.println();
    }
    
    public void processInputParams() throws HelpExitException, InvalidParamException {

    	if (commandLine == null) {
            try {
                commandLine = commandParser.parse(cliOptions, rawCmdInputParams);
            } catch (ParseException e) {
                throw new InvalidParamException("Failed to parse application CLI input parameters: " + e.getMessage());
            }
    	}
    	
    	// CLI option for help messages
        if (commandLine.hasOption("h")) {
            throw new HelpExitException();
        }

        processExtendedInputParams();
    }

    public PulsarClient createNativePulsarClient() throws PulsarClientException {
        ClientBuilder clientBuilder = PulsarClient.builder();

        String pulsarSvcUrl = clientConnConf.getValue("brokerServiceUrl");
        clientBuilder.serviceUrl(pulsarSvcUrl);

        String authPluginClassName = clientConnConf.getValue("authPlugin");
        String authParams = clientConnConf.getValue("authParams");
        if ( !StringUtils.isAnyBlank(authPluginClassName, authParams) ) {
            clientBuilder.authentication(authPluginClassName, authParams);
        }

        // For Astra streaming, there is no need for this section.
        // But for Luna streaming, they're required if TLS is expected.
        if ( !useAstraStreaming && StringUtils.contains(pulsarSvcUrl, "pulsar+ssl") ) {
            boolean tlsHostnameVerificationEnable = BooleanUtils.toBoolean(
                    clientConnConf.getValue("tlsEnableHostnameVerification"));
            clientBuilder.enableTlsHostnameVerification(tlsHostnameVerificationEnable);

            String tlsTrustCertsFilePath =
                    clientConnConf.getValue("tlsTrustCertsFilePath");
            if (!StringUtils.isBlank(tlsTrustCertsFilePath)) {
                clientBuilder.tlsTrustCertsFilePath(tlsTrustCertsFilePath);
            }

            boolean tlsAllowInsecureConnection = BooleanUtils.toBoolean(
                    clientConnConf.getValue("tlsAllowInsecureConnection"));
            clientBuilder.allowTlsInsecureConnection(tlsAllowInsecureConnection);
        }

        return clientBuilder.build();
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
