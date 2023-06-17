package com.example.rtmldemo;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.TypedMessageBuilder;
import org.apache.pulsar.functions.api.Context;
import org.apache.pulsar.functions.api.Function;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;

public class FuelEfficiencyMlPredSvcFunc implements Function<String, Void> {

    private static Logger logger;

    private static TypedMessageBuilder<String> outputMessageBuilder;

    // common user-config settings
    private static boolean useDsrsRestApi;
    private static String remoteSvcEndpoint;
    private static String logLevelStr;

    // google vertex ai specific user-config settings
    private static String projectId;
    private static String endpointId;
    private static String location;
    private static String accessToken;


    private static HttpClient httpClient;

    @Override
    public void initialize(Context context) throws PulsarClientException {
        String outputTopic = context.getOutputTopic();
        outputMessageBuilder = context.newOutputMessage(outputTopic, Schema.STRING);
        logger = context.getLogger();

        Optional<Object> svcEndPointOpt = context.getUserConfigValue("restEndpoint");
        if (svcEndPointOpt.isEmpty()) {
            throw new PulsarClientException("The remote service endpoint config 'restEndpoint' can NOT be empty!");
        }
        remoteSvcEndpoint = (String) svcEndPointOpt.get();

        Optional<Object> dsrsOpt = context.getUserConfigValue("useDsrsRestApi");
        useDsrsRestApi = dsrsOpt.filter(o -> BooleanUtils.toBoolean((String) o)).isPresent();

        Optional<Object> logLevelOpt = context.getUserConfigValue("logLevel");
        if (logLevelOpt.isEmpty()) {
            logLevelStr = "info";
        }
        else {
            logLevelStr = (String) logLevelOpt.get();
        }

        Optional<Object> projectIdOpt = context.getUserConfigValue("projectId");
        projectIdOpt.ifPresent(o -> projectId = (String) o);

        Optional<Object> endpointIdOpt = context.getUserConfigValue("endpointId");
        endpointIdOpt.ifPresent(o -> endpointId = (String) o);

        Optional<Object> locationOpt = context.getUserConfigValue("location");
        locationOpt.ifPresent(o -> location = (String) o);

        Optional<Object> accessTokenOpt = context.getUserConfigValue("accessToken");
        accessTokenOpt.ifPresent(o -> accessToken = (String) o);

        logger.info("user-config -- logLevel:{}, useDsrsRestApi:{}, restEndpoint:{}",
                logLevelStr, useDsrsRestApi, remoteSvcEndpoint);

        if (!useDsrsRestApi) {
            if (StringUtils.isAnyBlank(projectId, endpointId, location, accessToken)) {
                throw new PulsarClientException(
                        "The provided user config parameters 'projectId', 'endpointId', 'location', or 'accessToken' can NOT be empty!");
            }

            // The provided service end-point must match the provided location
            if (!StringUtils.containsIgnoreCase(remoteSvcEndpoint, location)) {
                throw new PulsarClientException(
                        "The user config parameter 'restEndpoint' doesn't match the provided 'location' parameter!");
            }

            logger.info("user-config -- projectId:{}, endpointId:{}, accessToken:{}",
                    projectId, endpointId, "*****");
        }

        httpClient = HttpClient.newHttpClient();
    }

    // 1. The message payload of the input topic is a list of double values as in the following format:
    //      { number1, number2, number3, ..., numberN }.
    //    The result of the remote rest service call is sent to the output topic as its message payload.
    //
    // 2. For the testing 'DSRS' rest API service, the number of the input values in the list can bey any.
    //    The response value is a JSON string that lists the raw input values as well as the average of them.
    //
    //    An example is as below:
    //    * input: {1,2,3,4}
    //    * response: {"id":1,"input_values":[1.0,2.0,3.0,4.0],"average":2.5}
    //
    // 3. For the 'Fuel Efficiency ML Prediction' service (Google Vertex API), the number of the input values in the
    //    list must be 9.
    //    The response value is a double value that represents the predicated car fuel efficiency value.
    //
    //    An example is as below:
    //    * input: { 1.4838871833555929, 1.8659883497083019, 2.234620276849616, 1.0187816540094903, -2.530890710602246,
    //               -1.6046416850441676, -0.4651483719733302, -0.4952254087173721, 0.7746763768735953] }
    //    * response: 16.1574936
    @Override
    public Void process(String input, Context context) {

        HttpRequest request = null;
        HttpResponse<String> response;

        String expInputValListStr = StringUtils.strip(StringUtils.strip(input, "{}"), null);

        double[] doubleVals =
                Arrays.stream(expInputValListStr.split(","))
                        .filter(s -> {
                            try {
                                Double.parseDouble(s);
                                return true;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        })
                        .mapToDouble(Double::parseDouble)
                        .toArray();

        // Make sure the "baseUrl" ends without '/'
        String baseUrl = StringUtils.stripEnd(remoteSvcEndpoint, "/");
        String restUrl = baseUrl;

        boolean validInput = true;

        // For 'Fuel Efficiency ML Prediction' service, it must have 9 double values as the input
        if (!useDsrsRestApi) {
            if (doubleVals.length != 9) {
                logger.warn("For 'fuel efficiency prediction' service, must have 9 double values as the input. " +
                        "Skip processing the current record: {} ({})", expInputValListStr, doubleVals);
                validInput = false;
            }
            else {
                // The "baseUrl" is something like:
                //    https://${LOCATION}-aiplatform.googleapis.com/v1/
                //
                // The target post url is something like:
                //    https://${LOCATION}-aiplatform.googleapis.com/v1/projects/${PROJECT_ID}/locations/${LOCATION}/endpoints/${ENDPOINT_ID}:predict

                restUrl = restUrl +
                        "/projects/" + projectId +
                        "/locations/" + location +
                        "/endpoints/" + endpointId +
                        ":predict";
                String bodyJsonStr = "{ \"instances\": [ [" + expInputValListStr + "] ] }";

                request = HttpRequest.newBuilder(URI.create(restUrl))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + accessToken)
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJsonStr))
                        .build();


            }
        }
        // 'DSRS' service - for testing purpose
        else {
            restUrl = restUrl + "/" + expInputValListStr;
            request = HttpRequest.newBuilder(URI.create(restUrl)).build();
        }

        if (validInput) {
            assert (request != null);

            try {
                if (StringUtils.equalsIgnoreCase(logLevelStr, "debug")) {
                    logger.info("useDsrsRestApi={}, restUrl=\"{}\"", useDsrsRestApi, restUrl);
                }

                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String apiResult = response.body();

                outputMessageBuilder.value(apiResult);
                MessageId messageId = outputMessageBuilder.send();

                if (StringUtils.equalsAnyIgnoreCase(logLevelStr, "debug")) {
                    logger.info("The remote service endpoint result \"{}\" is sent to the target topic via message {}",
                            apiResult,
                            messageId);
                }
            } catch (IOException | InterruptedException ex) {
                if (StringUtils.equalsAnyIgnoreCase(logLevelStr, "debug")) {
                    logger.info("Failed to call the following remote rest api service: {}, {}", restUrl, ex.getMessage());
                }
                ex.printStackTrace();
            }
        }

        return null;
    }

    @Override
    public void close() {
    }
}