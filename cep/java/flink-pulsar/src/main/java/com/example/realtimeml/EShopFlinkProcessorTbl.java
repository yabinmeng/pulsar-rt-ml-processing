//package com.example.realtimeml;
//
//import com.example.realtimeml.exception.InvalidParamException;
//import com.example.realtimeml.exception.UnexpectedRuntimException;
//import com.example.realtimeml.pojo.EShopInputData;
//import com.example.realtimeml.pojo.EShopInputDataProjected;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.apache.commons.lang3.BooleanUtils;
//import org.apache.commons.lang3.RandomStringUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.apache.commons.lang3.math.NumberUtils;
//import org.apache.flink.api.common.JobExecutionResult;
//import org.apache.flink.api.common.RuntimeExecutionMode;
//import org.apache.flink.api.common.eventtime.WatermarkStrategy;
//import org.apache.flink.api.common.functions.AggregateFunction;
//import org.apache.flink.api.common.functions.MapFunction;
//import org.apache.flink.configuration.ConfigOption;
//import org.apache.flink.configuration.ConfigOptions;
//import org.apache.flink.connector.base.DeliveryGuarantee;
//import org.apache.flink.connector.pulsar.sink.PulsarSink;
//import org.apache.flink.connector.pulsar.sink.PulsarSinkBuilder;
//import org.apache.flink.connector.pulsar.sink.writer.serializer.PulsarSerializationSchema;
//import org.apache.flink.connector.pulsar.source.PulsarSource;
//import org.apache.flink.connector.pulsar.source.PulsarSourceBuilder;
//import org.apache.flink.connector.pulsar.source.enumerator.cursor.StartCursor;
//import org.apache.flink.connector.pulsar.source.reader.deserializer.PulsarDeserializationSchema;
//import org.apache.flink.connector.pulsar.table.catalog.PulsarCatalog;
//import org.apache.flink.core.execution.JobClient;
//import org.apache.flink.streaming.api.datastream.DataStream;
//import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
//import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
//import org.apache.flink.streaming.api.windowing.assigners.TumblingProcessingTimeWindows;
//import org.apache.flink.streaming.api.windowing.time.Time;
//import org.apache.flink.table.api.Table;
//import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
//import org.apache.flink.table.catalog.Catalog;
//import org.apache.pulsar.client.api.Schema;
//import org.apache.pulsar.client.api.SubscriptionType;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.time.Duration;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//// EShop data preparer for the recommendation AI/ML model
//public class EShopFlinkProcessorTbl extends EShopCmdApp {
//    private final static String APP_NAME = "EShopFlinkProcessor";
//    static { System.setProperty("log_file_base_name", getLogFileName(API_TYPE, APP_NAME)); }
//
//    private final static Logger logger = LoggerFactory.getLogger(EShopFlinkProcessorTbl.class);
//
//    private final static String WINDOW_TYPE_EVENT_TIME ="etime";
//    private final static String WINDOW_TYPE_COUNT ="count";
//    private final static String WINDOW_TYPE_PROCESS_TIME ="ptime";
//
//    // Flink server types: 'embed', 'local', or 'remote'
//    // - default to 'embed' if not specified
//    private String flinkSrvType;
//    // Flink server host
//    // - default to 'localhost' if not specified
//    // - only applicable when 'flinkSrvType' is 'remote'
//    private String flinkSrvHost;
//    // Flink server port
//    // - default to '8081' if not specified
//    // - only applicable when 'flinkSrvType' is 'remote'
//    private int flinkSrvPort;
//
//    private String sinkPulsarTopic;
//    private String windowType;
//
//    // Use to indicate whether Flink DataStream API or Table API
//    private String apiType;
//
//    // > When 'windowType' is 'count',
//    //   * 'windowSize' and 'slideSize' represents the count of the events,
//    //   * the input parameter has no unit and must be an integer
//    // > Otherwise if 'windowType' is 'etime' (event_time) or 'ptime' (processing_time)
//    //   * 'windowSize' and 'slideSize' refer to the number of seconds
//    //   * the input parameter must be in the format of '<integer>[s|m|h|d]'
//    //     - unit 's': second
//    //     - unit 'm': minute
//    //     - unit 'h': hour
//    //     - unit 'd': day
//
//    private long windowSize;
//    private long slideSize;
//
//    private int lastNCnt;
//
//    private PulsarSource<EShopInputData> eShopPulsarDsInputSource;
//    private PulsarSink<String> eshopPulsarDsOutputSink;
//
//    private StreamExecutionEnvironment dsEnv;
//
//    private StreamTableEnvironment tblEnv;
//
//    public EShopFlinkProcessorTbl(String appName, String[] inputParams) {
//        super(appName, inputParams);
//
//        addOptionalCommandLineOption("fsrv","flinkServer", true,
//                "The flink server address. Must be in format of [embed|local|remote::<host>:<port>] (default: embed).");
//        addOptionalCommandLineOption("snktp","sinkTopic", true,
//                "The sink Pulsar topic where the processed output data is sent to.");
//        addOptionalCommandLineOption("wndt","windowType", true,
//                "Window type [etime(event_time) - default, count, or ptime(processing_time)].");
//        addOptionalCommandLineOption("wnds","windowSize", true,
//                "The window size of the click numbers in the click stream.");
//        addOptionalCommandLineOption("slds","slideSize", true,
//                "The slide size of the click numbers (within a window) in the click stream.");
//        addOptionalCommandLineOption("lnc","lastNCount", true,
//                "The last N count of the click numbers in the specified click stream window.");
//    }
//
//    public static void main(String[] args) {
//        EShopCmdApp workshopApp = new EShopFlinkProcessorTbl(APP_NAME, args);
//        int exitCode = workshopApp.run();
//        System.exit(exitCode);
//    }
//
//
//    private long getSizeByWindowType(String sizeInputStr, String windowType) throws InvalidParamException {
//        // -1 means there is an error
//        long sizeToRtn = -1;
//
//        if (StringUtils.equalsIgnoreCase(windowType, WINDOW_TYPE_COUNT)) {
//            // expecting the input size string is an integer with no unit
//            if (NumberUtils.isDigits(sizeInputStr)) {
//                sizeToRtn = Integer.parseInt(sizeInputStr);
//            }
//            else {
//                throw new InvalidParamException("Size must be an integer when 'windowType' is count based");
//            }
//        }
//        else {
//            // expecting the input size string is an integer with unit 's', 'm', 'h', 'd'
//            String unit = StringUtils.right(sizeInputStr, 1);
//            String sizeStrNoUnit = StringUtils.substringBeforeLast(sizeInputStr, unit);
//
//            if ( StringUtils.equalsAny(unit, "s", "m", "h", "d") &&
//                 NumberUtils.isDigits(sizeStrNoUnit) ) {
//                if (StringUtils.equals(unit,"s")) {
//                    sizeToRtn = Integer.parseInt(sizeStrNoUnit);
//                }
//                else if (StringUtils.equals(unit,"m")) {
//                    sizeToRtn = Long.parseLong(sizeStrNoUnit) * 60;
//                }
//                else if (StringUtils.equals(unit,"h")) {
//                    sizeToRtn = Long.parseLong(sizeStrNoUnit) * 60 * 60;
//                }
//                else if (StringUtils.equals(unit,"d")) {
//                    sizeToRtn = Long.parseLong(sizeStrNoUnit) * 60 * 60 * 24;
//                }
//            }
//            else {
//                throw new InvalidParamException(
//                        "Size must be an integer ending with a unit ('s','m','h','d') when 'windowType' is time based");
//            }
//        }
//
//        return sizeToRtn;
//    }
//
//    public void processExtendedInputParams() throws InvalidParamException {
//        super.processExtendedInputParams();
//
//        // Optional - flink server type and host
//        String flinkSrvTypeHostStr = processStringInputParam("fsrv");
//        if (StringUtils.isBlank(flinkSrvTypeHostStr)) {
//            flinkSrvType = "embed";
//        }
//        else if (StringUtils.equalsIgnoreCase(flinkSrvTypeHostStr, "embed") ||
//                 StringUtils.equalsIgnoreCase(flinkSrvTypeHostStr, "local") ) {
//            flinkSrvType = flinkSrvTypeHostStr;
//        }
//        else if (StringUtils.startsWithIgnoreCase(flinkSrvTypeHostStr, "remote::")) {
//            flinkSrvType = "remote";
//
//            String[] flinkSrvHostPortArr =
//                    StringUtils.split(StringUtils.substringAfter(flinkSrvTypeHostStr, "::"), ":");
//            if (flinkSrvHostPortArr.length == 1) {
//                flinkSrvHost = flinkSrvHostPortArr[0];
//                flinkSrvPort = 8081;
//            }
//            else if (flinkSrvHostPortArr.length == 2) {
//                flinkSrvHost = flinkSrvHostPortArr[0];
//                flinkSrvPort = Integer.parseInt(flinkSrvHostPortArr[1]);
//            }
//            else {
//                throw new InvalidParamException("Invalid flink server host and port: " + flinkSrvTypeHostStr);
//            }
//        }
//        else {
//            throw new InvalidParamException("Invalid flink server type and host: " + flinkSrvTypeHostStr);
//        }
//
//        // Required
//        sinkPulsarTopic = processStringInputParam("snktp");
//        if (StringUtils.isBlank(sinkPulsarTopic)) {
//            throw new InvalidParamException("Invalid sink Pulsar topic parameter (\"-st\")!");
//        }
//
//        // Optional - Window type (default to \"etime\" (event_time))
//        windowType = processStringInputParam("wndt", WINDOW_TYPE_EVENT_TIME);
//        if (!StringUtils.equalsAnyIgnoreCase(windowType, WINDOW_TYPE_EVENT_TIME, WINDOW_TYPE_COUNT, WINDOW_TYPE_PROCESS_TIME)) {
//            throw new InvalidParamException(
//                    "The window type (\"wndt\") must be one of the following values: " +
//                            "\"" + WINDOW_TYPE_EVENT_TIME + "\" (default), " +
//                            "\"" + WINDOW_TYPE_COUNT + "\", " +
//                            "\"" + WINDOW_TYPE_PROCESS_TIME + "\", !");
//        }
//
//        // Required - window size
//        windowSize = getSizeByWindowType(processStringInputParam("wnds"), windowType);
//        if (windowSize <= 0) {
//            throw new InvalidParamException("The window size (\"-wnds\") must be a positive integer!");
//        }
//
//        // Optional - default to the window size if not specified
//        String sSizeStr = processStringInputParam("slds");
//        if (StringUtils.isNotBlank(sSizeStr)) {
//            slideSize = getSizeByWindowType(sSizeStr, windowType);
//            if ((slideSize < 0) || (slideSize > windowSize)) {
//                throw new InvalidParamException(
//                        "The slide size (\"-slds\") must be a non-negative integer that is no greater than the window size!");
//            }
//        }
//
//        // Required - lastN count
//        lastNCnt = processIntegerInputParam("lnc");
//        if (lastNCnt <= 0) {
//            throw new InvalidParamException("The lastN count (\"-lnc\") must be a positive integer!");
//        }
//    }
//
//    @Override
//    public void runApp() throws UnexpectedRuntimException {
//        logger.info("Starting flink stream processing application: \"" + appName + "\" ...");
//        logger.info("- parameters: sinkTopic: {}, windowType: {}, api type: {}, " +
//                        "windowSize: {}, slideSize: {}, lastNCnt: {}",
//                sinkPulsarTopic,
//                windowType,
//                apiType,
//                windowSize,
//                slideSize,
//                lastNCnt);
//
//        createStreamExecEnvironment();
//
//        if (eShopPulsarDsInputSource == null) {
//            eShopPulsarDsInputSource = createFlinkPulsarDsSource();
//        }
//
//        if (eshopPulsarDsOutputSink == null) {
//            eshopPulsarDsOutputSink = createFlinkPulsarDsSink();
//        }
//
//
//        ////////////////
//        processWithTableAPI();
//    }
//
//    @Override
//    public void termApp() {
//
//        try {
//            if (dsEnv != null) {
//                dsEnv.close();
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        finally {
//            logger.info("Terminating application: \"" + appName + "\" ...");
//        }
//    }
//
//    private void createStreamExecEnvironment() {
//        if (dsEnv == null) {
//            if (StringUtils.equalsIgnoreCase(flinkSrvType, "embed")) {
//                dsEnv = StreamExecutionEnvironment.getExecutionEnvironment();
//            }
//            else if (StringUtils.equalsIgnoreCase(flinkSrvType, "local")) {
//                dsEnv = StreamExecutionEnvironment.createLocalEnvironment();
//            }
//            else if (StringUtils.equalsIgnoreCase(flinkSrvType, "remote")) {
//                dsEnv = StreamExecutionEnvironment.createRemoteEnvironment(flinkSrvHost, flinkSrvPort);
//            }
//
//            // Not necessary, as the default mode is STREAMING.
//            // -- just for clarity
//            dsEnv.setRuntimeMode(RuntimeExecutionMode.STREAMING);
//
//            // The default parallelism seems too high; and it may cause event_time based windowing
//            // not being triggered.
//            dsEnv.setParallelism(2);
//
//            if (tblEnv == null) {
//                tblEnv = StreamTableEnvironment.create(dsEnv);
//            }
//        }
//    }
//
//
//    /**
//     * DataStream API related code blocks
//     */
//    private PulsarSource<EShopInputData> createFlinkPulsarDsSource() {
//        PulsarSourceBuilder<EShopInputData> builder = PulsarSource.builder();
//        String pulsarSvcUrl = clientConnConf.getValue("brokerServiceUrl");
//        String pulsarWebUrl = clientConnConf.getValue("webServiceUrl");
//
//        builder.setTopics(topicName)
//                .setDeserializationSchema(
//                        PulsarDeserializationSchema.pulsarSchema(Schema.AVRO(EShopInputData.class), EShopInputData.class))
//                .setSubscriptionType(SubscriptionType.Exclusive)
//                .setSubscriptionName("eshop-flink-pulsar-source-" + RandomStringUtils.randomAlphanumeric(10))
//                //.setStartCursor(StartCursor.earliest())
//                .setStartCursor(StartCursor.latest())
//                .setServiceUrl(pulsarSvcUrl)
//                .setAdminUrl(pulsarWebUrl);
//
//        ConfigOption<String> strCfgOpt;
//        ConfigOption<Boolean> boolCfgOpt;
//
//        String authPlugin = clientConnConf.getValue("authPlugin");
//        String authParams = clientConnConf.getValue("authParams");
//        if ( !StringUtils.isAnyBlank(authPlugin, authParams) ) {
//            strCfgOpt = ConfigOptions.key("pulsar.client.authPluginClassName")
//                    .stringType().noDefaultValue();
//            builder.setConfig(strCfgOpt, authPlugin);
//
//            strCfgOpt = ConfigOptions.key("pulsar.client.authParams")
//                    .stringType().noDefaultValue();
//            builder.setConfig(strCfgOpt, authParams);
//        }
//
//        // For Astra streaming, there is no need for this section.
//        // But for Luna streaming, they're required if TLS is expected.
//        if ( !useAstraStreaming && StringUtils.contains(pulsarSvcUrl, "pulsar+ssl") ) {
//            boolean tlsHostnameVerificationEnable = BooleanUtils.toBoolean(
//                    clientConnConf.getValue("tlsEnableHostnameVerification"));
//
//            boolCfgOpt = ConfigOptions.key("pulsar.client.tlsHostnameVerificationEnable")
//                    .booleanType().noDefaultValue();
//            builder.setConfig(boolCfgOpt, tlsHostnameVerificationEnable);
//
//            String tlsTrustCertsFilePath =
//                    clientConnConf.getValue("tlsTrustCertsFilePath");
//            if (!StringUtils.isBlank(tlsTrustCertsFilePath)) {
//                strCfgOpt = ConfigOptions.key("pulsar.client.tlsTrustCertsFilePath")
//                        .stringType().noDefaultValue();
//                builder.setConfig(strCfgOpt, tlsTrustCertsFilePath);
//            }
//
//            boolean tlsAllowInsecureConnection = BooleanUtils.toBoolean(
//                    clientConnConf.getValue("tlsAllowInsecureConnection"));
//            boolCfgOpt = ConfigOptions.key("pulsar.client.tlsAllowInsecureConnection")
//                    .booleanType().noDefaultValue();
//            builder.setConfig(boolCfgOpt, tlsAllowInsecureConnection);
//        }
//
//        return builder.build();
//    }
//
//    private PulsarSink<String> createFlinkPulsarDsSink() {
//        String pulsarSvcUrl = clientConnConf.getValue("brokerServiceUrl");
//        String pulsarWebUrl = clientConnConf.getValue("webServiceUrl");
//
//
//        PulsarSinkBuilder<String> builder = PulsarSink.builder();
//        builder.setTopics(sinkPulsarTopic)
//                .setSerializationSchema(
//                        PulsarSerializationSchema.pulsarSchema(Schema.STRING, String.class))
//                .enableSchemaEvolution()
//                // at-least-once may cause data duplication
//                // exactly-once requires Flink savepoint and Pulsar transaction
//                .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
//                .setAdminUrl(pulsarWebUrl)
//                .setServiceUrl(pulsarSvcUrl);
//
//        ConfigOption<String> strCfgOpt;
//        ConfigOption<Boolean> boolCfgOpt;
//
//        String authPlugin = clientConnConf.getValue("authPlugin");
//        String authParams = clientConnConf.getValue("authParams");
//        if ( !StringUtils.isAnyBlank(authPlugin, authParams) ) {
//            strCfgOpt = ConfigOptions.key("pulsar.client.authPluginClassName")
//                    .stringType().noDefaultValue();
//            builder.setConfig(strCfgOpt, authPlugin);
//
//            strCfgOpt = ConfigOptions.key("pulsar.client.authParams")
//                    .stringType().noDefaultValue();
//            builder.setConfig(strCfgOpt, authParams);
//        }
//
//        // For Astra streaming, there is no need for this section.
//        // But for Luna streaming, they're required if TLS is expected.
//        if ( !useAstraStreaming && StringUtils.contains(pulsarSvcUrl, "pulsar+ssl") ) {
//            boolean tlsHostnameVerificationEnable = BooleanUtils.toBoolean(
//                    clientConnConf.getValue("tlsEnableHostnameVerification"));
//
//            boolCfgOpt = ConfigOptions.key("pulsar.client.tlsHostnameVerificationEnable")
//                    .booleanType().noDefaultValue();
//            builder.setConfig(boolCfgOpt, tlsHostnameVerificationEnable);
//
//            String tlsTrustCertsFilePath =
//                    clientConnConf.getValue("tlsTrustCertsFilePath");
//            if (!StringUtils.isBlank(tlsTrustCertsFilePath)) {
//                strCfgOpt = ConfigOptions.key("pulsar.client.tlsTrustCertsFilePath")
//                        .stringType().noDefaultValue();
//                builder.setConfig(strCfgOpt, tlsTrustCertsFilePath);
//            }
//
//            boolean tlsAllowInsecureConnection = BooleanUtils.toBoolean(
//                    clientConnConf.getValue("tlsAllowInsecureConnection"));
//            boolCfgOpt = ConfigOptions.key("pulsar.client.tlsAllowInsecureConnection")
//                    .booleanType().noDefaultValue();
//            builder.setConfig(boolCfgOpt, tlsAllowInsecureConnection);
//        }
//
//        return builder.build();
//    }
//
//    private void processWithDataStreamAPI() throws UnexpectedRuntimException {
//        DataStream<EShopInputData> eShopInputDataStream;
//
//        if (StringUtils.equalsIgnoreCase(windowType, WINDOW_TYPE_COUNT)) {
//            eShopInputDataStream = dsEnv.fromSource(
//                    eShopPulsarDsInputSource,
//                    WatermarkStrategy.noWatermarks(),
//                    "(Pulsar) E-Shop Count Window No Watermark Strategy");
//        }
//        else if (StringUtils.equalsIgnoreCase(windowType, WINDOW_TYPE_PROCESS_TIME)) {
//            eShopInputDataStream = dsEnv.fromSource(
//                    eShopPulsarDsInputSource,
//                    WatermarkStrategy.forBoundedOutOfOrderness(Duration.ofSeconds(5)),
//                    "(Pulsar) E-Shop Process Time Window with BoundedOutOfOrderness Watermark Strategy");
//        }
//        else {
//            eShopInputDataStream = dsEnv.fromSource(
//                    eShopPulsarDsInputSource,
//                    WatermarkStrategy.<EShopInputData>forBoundedOutOfOrderness(Duration.ofSeconds(5))
//                            .withTimestampAssigner((event, timestamp) -> event.getEventTime()),
//                    "(Pulsar) E-Shop Event Time Window with forBoundedOutOfOrderness Watermark Strategy");
//        }
//
//        DataStream<EShopInputDataProjected> eShopInputDataProjectedDataStream =
//                eShopInputDataStream.map((MapFunction<EShopInputData, EShopInputDataProjected>) eShopInputData ->
//                    new EShopInputDataProjected(
//                        eShopInputData.getSession(),
//                        eShopInputData.getOrder(),
//                        eShopInputData.getCategory(),
//                        eShopInputData.getModel(),
//                        eShopInputData.getColor(),
//                        eShopInputData.getEventTime()
//                ));
//
//        DataStream<String> outputDataStream;
//
//        if (StringUtils.equals(windowType, WINDOW_TYPE_COUNT)) {
//            outputDataStream = eShopInputDataProjectedDataStream
//                    .keyBy(EShopInputDataProjected::getSession)
//                    .countWindow(windowSize, slideSize)
//                    .aggregate(new EShopLastNAggregator(lastNCnt));
//        }
//        else if (StringUtils.equalsIgnoreCase(windowType, WINDOW_TYPE_PROCESS_TIME)) {
//            outputDataStream = eShopInputDataProjectedDataStream
//                    .keyBy(EShopInputDataProjected::getSession)
//                    .window(TumblingProcessingTimeWindows.of(Time.seconds(windowSize), Time.seconds(slideSize)))
//                    .aggregate(new EShopLastNAggregator(lastNCnt));
//        }
//        else {
//            outputDataStream = eShopInputDataProjectedDataStream
//                    .keyBy(EShopInputDataProjected::getSession)
//                    .window(TumblingEventTimeWindows.of(Time.seconds(windowSize), Time.seconds(slideSize)))
//                    .aggregate(new EShopLastNAggregator(lastNCnt));
//        }
//
//        outputDataStream.sinkTo(eshopPulsarDsOutputSink);
//
//        try {
//            JobClient jobClient = dsEnv.executeAsync();
//            JobExecutionResult jobExecutionResult =
//                    jobClient.getJobExecutionResult().get();
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//            throw new UnexpectedRuntimException("Unexpected flink job exception!");
//        }
//    }
//
//    private static class EShopLastNAggregator
//        implements AggregateFunction<EShopInputDataProjected, Map<Integer, List<EShopInputDataProjected>>, String> {
//        static ObjectMapper objectMapper = new ObjectMapper();
//        static int lastNCnt;
//
//        public EShopLastNAggregator(int cnt) {
//            lastNCnt = cnt;
//        }
//
//        @Override
//        public Map<Integer, List<EShopInputDataProjected>> createAccumulator() {
//            return new HashMap<>();
//        }
//
//        @Override
//        public Map<Integer, List<EShopInputDataProjected>> add(
//                EShopInputDataProjected event,
//                Map<Integer, List<EShopInputDataProjected>> accumulator) {
//            int sessionId = event.getSession();
//
//            List<EShopInputDataProjected> historyMetadata = accumulator.get(sessionId);
//            if (historyMetadata == null) {
//                historyMetadata = new ArrayList<>();
//            }
//            historyMetadata.add(event);
//
//            accumulator.put(sessionId, historyMetadata);
//            return  accumulator;
//        }
//
//        @Override
//        public String getResult(Map<Integer, List<EShopInputDataProjected>> accumulator) {
//            StringBuilder jsonStrBuilder = new StringBuilder();
//
//            jsonStrBuilder.append("[");
//
//            int idx = 0;
//            int totalSessionCnt = accumulator.keySet().size();
//            for (Integer sessionId : accumulator.keySet()) {
//                idx++;
//
//                jsonStrBuilder.append("{");
//                jsonStrBuilder.append("\"sessionId\":" + sessionId + ", ");
//
//                int idx2 = 0;
//                jsonStrBuilder.append("\"lastItems\": [");
//                for (EShopInputDataProjected modelData : accumulator.get(sessionId)) {
//                    // For each session, only includes lastN records
//                    if (idx2 == lastNCnt)
//                        break;
//
//                    idx2++;
//
//                    jsonStrBuilder.append("{");
//                    jsonStrBuilder.append("\"category\":\"" + modelData.getCategory() + "\",");
//                    jsonStrBuilder.append("\"clothingModel\":\"" + modelData.getModel() + "\",");
//                    jsonStrBuilder.append("\"color\":\"" + modelData.getColor() + "\"");
//
//                    jsonStrBuilder.append("},");
//                }
//
//                jsonStrBuilder.deleteCharAt(jsonStrBuilder.length() - 1);
//                jsonStrBuilder.append("]");
//
//                jsonStrBuilder.append("},");
//            }
//
//            jsonStrBuilder.deleteCharAt(jsonStrBuilder.length() - 1);
//            jsonStrBuilder.append("]");
//
//            return jsonStrBuilder.toString();
//        }
//
//        @Override
//        public Map<Integer, List<EShopInputDataProjected>> merge(
//                Map<Integer, List<EShopInputDataProjected>> acc1,
//                Map<Integer, List<EShopInputDataProjected>> acc2) {
//            Map<Integer, List<EShopInputDataProjected>> combinedMap = new HashMap<>(acc1);
//
//            for (Integer sessionId : acc2.keySet()) {
//                if (!acc1.containsKey(sessionId)) {
//                    combinedMap.put(sessionId, acc2.get(sessionId));
//                }
//                else {
//                    List<EShopInputDataProjected> combinedList = new ArrayList<>();
//                    combinedList.addAll(acc1.get(sessionId));
//                    combinedList.addAll(acc2.get(sessionId));
//                    combinedMap.put(sessionId, combinedList);
//                }
//            }
//
//            return combinedMap;
//        }
//    }
//
//
//    /**
//     * Table API related code blocks
//     */
//    private void createAndRegisterPulsarCatalog(String catalogName, String dftDataBase) {
//        assert (StringUtils.isNotBlank(dftDataBase));
//
//        Optional<Catalog> catalogOpt = tblEnv.getCatalog(catalogName);
//        if (!catalogOpt.isPresent()) {
//            // TBD: What if the Pulsar cluster has TLS enabled?
//            //      It looks like SN's pulsar sql connector API doesn't have a way to pass in TLS certificates
//            //         and corresponding TLS related settings
//            String pulsarSvcUrl = clientConnConf.getValue("brokerServiceUrl");
//            String pulsarWebUrl = clientConnConf.getValue("webServiceUrl");
//            String authPlugin = clientConnConf.getValue("authPlugin");
//            String authParams = clientConnConf.getValue("authParams");
//
//            Catalog catalog;
//            if (!StringUtils.isAnyBlank(authPlugin, authParams)) {
//                authPlugin = null;
//                authParams = null;
//            }
//
//            catalog = new PulsarCatalog(
//                    catalogName,
//                    pulsarWebUrl,
//                    pulsarSvcUrl,
//                    dftDataBase,            // default database (pulsar namespace)
//                    "__flink_catalog",      // catalog tenant
//                    authPlugin,
//                    authParams);
//
//            tblEnv.registerCatalog(catalogName, catalog);
//        }
//    }
//
//    private String getFlinkPulsarCrtTblSqlStr(String tableName) {
//
//        String sqlCrtTblBaseStr = "CREATE TABLE " + tableName + "\n" +
//                "(\n" +
//                "  `year`       INTEGER,\n" +
//                "  `month`      INTEGER,\n" +
//                "  `day`        INTEGER,\n" +
//                "  `order`      INTEGER,\n" +
//                "  `country`    INTEGER,\n" +
//                "  `session`    INTEGER,\n" +
//                "  `category`   INTEGER,\n" +
//                "  `model`      VARCHAR,\n" +
//                "  `color`      INTEGER,\n" +
//                "  `location`   INTEGER,\n" +
//                "  `modelPhoto` INTEGER,\n" +
//                "  `price`      INTEGER,\n" +
//                "  `priceInd`   INTEGER,\n" +
//                "  `page`       INTEGER,\n" +
//                "  `eventTime`  BIGINT\n" +
//                ")";
//
//        String pulsarSvcUrl = clientConnConf.getValue("brokerServiceUrl");
//        String pulsarWebUrl = clientConnConf.getValue("webServiceUrl");
//        String authPlugin = clientConnConf.getValue("authPlugin");
//        String authParams = clientConnConf.getValue("authParams");
//
//        String fullFlinkPulsarTblStr = sqlCrtTblBaseStr +
//                " WITH (\n" +
//                "    'connector' = 'pulsar',\n" +
//                "    'topics' = '" + tableName + "',\n" +
//                "    'service-url' = '" + pulsarSvcUrl + "',\n" +
//                "    'admin-url' = '" + pulsarWebUrl + "',\n";
//
//        if (!StringUtils.isAnyBlank(authPlugin, authParams)) {
//            fullFlinkPulsarTblStr = fullFlinkPulsarTblStr +
//                    "    'auth-plugin' = '" + authPlugin + "',\n" +
//                    "    'authParams' = '" + authParams + "',\n";
//        }
//
//        fullFlinkPulsarTblStr = fullFlinkPulsarTblStr +
//                "    'value.format' = 'json',\n" +
//                "    'value.json.fail-on-missing-field' = 'false'\n" +
//                ");";
//
//        return fullFlinkPulsarTblStr;
//    }
//
//    private void processWithTableAPI() throws UnexpectedRuntimException {
//
//        String catalogNameSrc = "pulsar";
//        String sourceName = topicName;
//        if (StringUtils.contains(topicName, "://")) {
//            sourceName = StringUtils.substringAfter(topicName, "://");
//        }
//
//        ///////////////////////////////////////////
//        // NOTE: Can't really make it work with Flink Pulsar SQL Connector
//        //
//        //       Can't create an explicit table. Run into the following error consistently.
//        //       Also, the corresponding Pulsar metadata topic is not created.
//        //
//        //       Flink SQL> CREATE TABLE eshopInputData
//        //       (
//        //          `year`   	INTEGER,
//        //          `month`  	INTEGER,
//        //          … …
//        //          … …
//        //          `eventTime`  BIGINT
//        //       ) WITH (
//        //	        'connector' = 'pulsar',
//        //	        'topics' = 'persistent://public/default/eshop_input',
//        //	        'format' = 'avro'
//        //       );
//        //       [ERROR] Could not execute SQL statement. Reason:
//        //       java.lang.ClassNotFoundException: org.apache.flink.table.runtime.util.JsonUtils
//        //	            ... 20 more
//        //
//        //       End of exception on server side>]
//        ///////////////////////////////////////////
//
//        String defaultDatabase =
//                StringUtils.substringBeforeLast(topicName, "/");
//        createAndRegisterPulsarCatalog(catalogNameSrc, defaultDatabase);
//
//        tblEnv.useCatalog(catalogNameSrc);
//        String[] databases = tblEnv.listDatabases();
//        String[] tables = tblEnv.listTables();
//
//        String eShopInputTblName = "eShopInputData";
//        String sqlStr = getFlinkPulsarCrtTblSqlStr(eShopInputTblName);
//        tblEnv.executeSql(sqlStr);
//
//        Table eShopInputTbl = tblEnv.sqlQuery("SELECT * FROM `eshop_input`");// + eShopInputTblName);
//        eShopInputTbl.fetch(5).execute();
//
//        eShopInputTbl.printSchema();
//
//
//        String sinkName = sinkPulsarTopic;
//        if (StringUtils.contains(sinkPulsarTopic, "://")) {
//            sinkName = StringUtils.substringAfter(sinkPulsarTopic, "://");
//        }
//        Catalog eShopPulsarTblCatalogSink = createAndRegisterPulsarCatalog(catalogNameSrc, sinkName);
//    }
//}
