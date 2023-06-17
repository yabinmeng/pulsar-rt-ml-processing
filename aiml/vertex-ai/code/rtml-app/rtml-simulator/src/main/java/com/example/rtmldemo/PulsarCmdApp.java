package com.example.rtmldemo;

import com.example.rtmldemo.util.ClientConnConf;
import com.example.rtmldemo.util.RtmlDemoMainProperty;
import com.example.rtmldemo.util.RtmlDemoUtil;
import org.apache.commons.cli.*;

import com.example.rtmldemo.exception.HelpExitException;
import com.example.rtmldemo.exception.InvalidParamException;
import com.example.rtmldemo.exception.RtmlDemoRuntimeException;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;

import java.io.File;
import java.io.PrintWriter;

abstract public class PulsarCmdApp {

    protected String[] rawCmdInputParams;

    // -1 means to process all available messages (indefinitely)
    protected int totalRecordNum;

    protected File mainCfgPropFile;

    protected RtmlDemoMainProperty rtmlDemoMainProperty;

    protected final String appName;

    protected CommandLine commandLine;
    protected final DefaultParser commandParser;
    protected final Options cliOptions = new Options();

    protected static PulsarClient pulsarClient;

    public abstract void processExtendedInputParams() throws InvalidParamException;
    public abstract void execute();
    public abstract void termCmdApp();

    public PulsarCmdApp(String appName, String[] inputParams) {
        this.appName = appName;
        this.rawCmdInputParams = inputParams;
        this.commandParser = new DefaultParser();

        addOptionalCommandLineOption("h", "help", false, "Displays the usage method.");
        addRequiredCommandLineOption("n", "numRecord", true, "The number of records to process.");
        addRequiredCommandLineOption("f", "cfgProp", true, "Main configuration properties file.");
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

    public int runCmdApp() {
        int exitCode = 0;
        try {
            this.processInputParams();
            this.execute();
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
        catch (RtmlDemoRuntimeException wre) {
            System.out.println("\n[ERROR] Unexpected runtime error detected!");
            wre.printStackTrace();
            exitCode = 3;
        }
        finally {
            this.termCmdApp();
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

        // (Required) Total number of records to process
        totalRecordNum = RtmlDemoUtil.processIntegerInputParam(commandLine, cliOptions, "n", -1);
        if ( (totalRecordNum <= 0) && (totalRecordNum != -1) ) {
            throw new InvalidParamException("Record number must be a positive integer or -1 (all available messages)!");
        }

        // (Required) CLI option for client.conf file
        mainCfgPropFile = RtmlDemoUtil.processFileInputParam(commandLine, cliOptions, "f");
        if (mainCfgPropFile != null) {
            rtmlDemoMainProperty = new RtmlDemoMainProperty(mainCfgPropFile);
        }

        processExtendedInputParams();
    }

    public PulsarClient createNativePulsarClient() throws PulsarClientException {
        ClientConnConf clientConnConf = rtmlDemoMainProperty.getClientConnConf();

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
        boolean useAstraStreaming = rtmlDemoMainProperty.getUseAstraStreaming();
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
}
