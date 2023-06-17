package com.example.rtmldemo;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.aiplatform.v1.*;
import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import org.apache.commons.lang3.RandomUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;


/**
 * This will fail with the following error message:
 *
 * <p><b>404.</b> <ins>That’s an error.</ins>
 * <p>The requested URL <code>/google.cloud.aiplatform.v1.PredictionService/Predict</code> was not found on this server.  <ins>That’s all we know.</ins>
 */


public class RtmlSimulatorVertexAIClient {

    // get the project number using the following command:
    // -----------------------------
    //      gcloud projects list \
    //          --filter="$(gcloud config get-value project)" \
    //          --format="value(PROJECT_NUMBER)"
    static final String PROJECT_ID = "<To_be_filled>";
    static final String LOCATION = "us-central1";

    // find the endpoint ID using the following command:
    // -----------------------------
    //      gcloud ai endpoints list --region us-central1
    static final String ENDPOINT = "1270679199941656576";

    // get the access token using the following command
    //      gcloud auth print-access-token
    // note: need to run the following command first
    //      gcloud auth login
    static final String TOKEN ="<To_be_filled>";

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException, TimeoutException {

        AccessToken accessToken = new AccessToken(TOKEN, null);
        GoogleCredentials credentials = GoogleCredentials.create(accessToken);

        ListValue.Builder listValueBuilder = ListValue.newBuilder();
        for (int i=0; i<9; i++) {
            listValueBuilder.addValues(
                    Value.newBuilder().setNumberValue(RandomUtils.nextDouble())
            );
        }

        Value instanceValue = Value.newBuilder()
                .setListValue(listValueBuilder.build())
                .build();

        PredictRequest predictRequest =
                PredictRequest.newBuilder()
                        .setEndpoint(EndpointName.of(PROJECT_ID, LOCATION, ENDPOINT).toString())
                        .addInstances(instanceValue)
                        .build();


        PredictionServiceClient predictionServiceClient =
                PredictionServiceClient.create(PredictionServiceSettings.newBuilder()
                        .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                        .build());

        // Call the predict method to get the prediction response
        PredictResponse predictResponse = predictionServiceClient.predict(predictRequest);

        // Process the prediction response
        Value prediction = predictResponse.getPredictions(0);
        System.out.println("Prediction result: "  + prediction.toString());
    }
}
