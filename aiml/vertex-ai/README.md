- [1. Overview](#1-overview)
- [2. Introduction to the Prediction ML Model](#2-introduction-to-the-prediction-ml-model)
  - [2.1. Make the Rest API call to the Model Endpoint](#21-make-the-rest-api-call-to-the-model-endpoint)
  - [2.2. Endpoint Access Token](#22-endpoint-access-token)
- [3. Set up the Environment and Run the Simulation](#3-set-up-the-environment-and-run-the-simulation)
  - [3.1. Set up a Pulsar Cluster](#31-set-up-a-pulsar-cluster)
  - [3.2. Global Configuration Parameters](#32-global-configuration-parameters)
  - [3.3. Deploy the Prediction Pulsar Function](#33-deploy-the-prediction-pulsar-function)
  - [3.4. Prediction Simulation Applications](#34-prediction-simulation-applications)
    - [3.4.1. Build the Applications](#341-build-the-applications)
    - [3.4.2. Run the Applications](#342-run-the-applications)
      - [3.4.2.1. Simulation Producer](#3421-simulation-producer)
      - [3.4.2.2. Simulation Consumer](#3422-simulation-consumer)
- [4. Appendix A: DSRS (dead-simple-rest-service) Rest API server](#4-appendix-a-dsrs-dead-simple-rest-service-rest-api-server)


---


# 1. Overview

This repository showcases an example of how to use Apache Pulsar to help facilitate making the real-time ML model prediction service. Please note that the ML model is pre-created, pre-trained and pre-deployed on an ML platform like Google Vertex AI. This part is not real-time. However, utilizing the pre-trained ML services to make predictions can be real time and this is where Apache Pulsar is good at. 

The overall architecture of this demo is like below:
![deployment](resources/deployment.png)

In the above diagram, there are several Pulsar components involved:
1. A Pulsar producer that generates a series of messages whose payload contains the required ML model service inputs.
2. A Pulsar function reads the ML model service inputs from an "input" topic and then makes service calls to the remotely deployed ML model service. The returned prediction result for each input will be sent as message payloads to another "result" topic 
3. A Pulsar consumer retrieves the prediction results from the "result" topic.


# 2. Introduction to the Prediction ML Model

**`NOTE`**: For a more detailed, step-by-step description of how to create, train, and deploy the required ML model, please see the [Deploy a Simple Prediction ML Model.md](./MLModel.md) document.


In this demo, the prediction ML model we're using is a basic regression model as outlined in this [TensorFlow doc](https://www.tensorflow.org/tutorials/keras/regression) for automobile fuel efficiency prediction. This model will be trained and deployed on [Google's Vertex AI](https://cloud.google.com/vertex-ai) platform as a custom training model. Note that for this simple demo it is probably easier to use Google's AutoML training model ([doc](https://cloud.google.com/vertex-ai/docs/beginner/beginners-guide)) but it has a requirement of at least 1000 records in the training dataset that this demo doesn't satisfy.

Once the model is trained, it needs to be deployed as an **Endpoint**(https://cloud.google.com/vertex-ai/docs/general/deployment) for online predication. If the endpoint is successfully deployed, the complete endpoint URL would be something like below:
```
https://<GCP_LOCATION>-aiplatform.googleapis.com/v1/projects/<PROJECT_ID>/locations/us-central1/endpoints/<ENDPOINT_ID>:predict
```

Please **NOTE** in the above model endpoint URL
1. *<GCP_LOCATION>* is the GCP region name, e.g. us-central1
2. *<PROJECT_ID>* is the GCP project ID (project number) 
3. *<ENDPOINT_ID>* is the model endpoint ID assigned to the deployed ML model

## 2.1. Make the Rest API call to the Model Endpoint

The prediction model in this demo takes a list of 9 double values as the input and the returned predication result is a double value. For example, providing the following inputs will return a predication result of `16.0958195`
```
1.48,1.86,2.23,1.01,-2.53,-1.60,-0.46,-0.49,0.77
```

In order to pass the above input values to the endpoint (Rest API) URL, they need to be embedded in a JSON string with certain format.
```
$ cat request.json
{
    "instances": [
        [1.48,1.86,2.23,1.01,-2.53,-1.60,-0.46,-0.49,0.77]
    ]
}
```

Run the following `curl` command to make the rest API call to the model's endpoint.
```
$ curl -X POST \
       -H "Authorization: Bearer <GCP_ACCESS_TOKEN>" \
       -H "Content-Type: application/json; charset=utf-8" \
       https://${LOCATION}-aiplatform.googleapis.com/v1/projects/${PROJECT_ID}/locations/${LOCATION}/endpoints/${ENDPOINT_ID}:predict \
       -d @/Users/yabinmeng/Temp/request.json
```

The following result response will be returned. The `predictions` includes the prediction result that is calculated by the ML model. Other information about the deployed model is also returned.
```
{
  "predictions": [
    [
      16.0958195
    ]
  ],
  "deployedModelId": "<deployed_model_id>",
  "model": "projects/<porject_ID>/locations/<GCP_LOCATION>/models/<model_id>",
  "modelDisplayName": "fuel-efficiency-prediction",
  "modelVersionId": "1"
}
```

Please *NOTE* that you can provide a list of input values and making one single rest API call to the model endpoint will return a list of prediction results.

## 2.2. Endpoint Access Token

The ML model endpoint is securely protected. In order to access it, we can provide the [Application Default Credentials (ADC)](https://cloud.google.com/docs/authentication/provide-credentials-adc). If ADC is set up properly, you can get it by running the following command:
```
$ gcloud auth application-default print-access-token
```

# 3. Set up the Environment and Run the Simulation

*The detail of creating and deploying the ML model on Google Vertex AI can be found in the document of [Deploy a Simple Prediction ML Model.md](./MLModel.md)*.

---

## 3.1. Set up a Pulsar Cluster

A Pulsar cluster is needed to run this demo. Any Pulsar cluster can be used as long as the deployed Pulsar function is able to access the remote prediction ML model endpoint. 

For simplicity purpose, this demo also includes scripts to set up a Pulsar cluster in a K8s cluster (e.g. Docker Desktop's built-in K8s cluster), using the DataStax Pulsar helm chart. 

An example helm chart values.yaml file is provided in this demo at: [conf/helm/values.yaml](conf/helm/values.yaml). This example sets up a simple Pulsar cluster without security feature enabled.

To simplify the Pulsar cluster deployment process, the following bash scripts are provided in this demo:
* `bash/infra/deploy_pulsar_cluster.sh`: deploy a Pulsar cluster using the provided helm chart example to a specific K8s namespace
* `bash/infra/teardown_pulsar_cluster.sh`: tear down the Pulsar cluster using the above deployment script

Please ***NOTE*** that the above deployment script will also port-forward the default Pulsar cluster listening ports 6650 and 8080 to the localhost.

## 3.2. Global Configuration Parameters

In order to run the end-to-end prediction simulation workflow, a few global configuration parameters (see below) are needed; and a default properties file is provided for this purpose: [conf/cfg.properties](conf/cfg.properties).
```
clientConf=./conf/conn/client.conf
inputTopic=public/default/input
outputTopic=public/default/result
useDsrsService=false
```

Among these parameters,
* `clientConf` is the "client.conf" file to connect to the deployed Pulsar cluster. 
* `inputTopic` is the Pulsar topic for the required input values of the prediction ML model
* `outputTopic` is the Pulsar topic that has the returned prediction result/response of the prediction ML model
* `useDsrsservice` indicates whether to call the *DSRS* rest API or the prediction ML model rest API. This is only for testing purpose of the Pulsar function. The default value is false which means the Pulsar function will call the prediction ML model rest API by default. Pleas see [Appendix A](#4-appendix-a-dsrs-dead-simple-rest-service-rest-api-server) for more details of the *DSRS* rest API.

## 3.3. Deploy the Prediction Pulsar Function

The prediction Pulsar function, `rtmlsvccaller`, needs to be deployed in the Pulsar cluster before we can run the end-to-end prediction simulation. A bash script [bash/deployRtmlSvcFunction.sh](bash/deployRtmlSvcFunction.sh) is used to do this job.

Behind the scene, this script relies on the `pulsar-shell` client utility to deploy the Pulsar function as below. So this requires you to install *pulsar-shell* on your PC (pleas see Apache Pulsar [doc](bash/deployRtmlSvcFunction.sh) for more details).
```
pulsar-shell -e "admin functions create \
  --name rtmlsvccaller \
  --auto-ack true \
  --tenant public \
  --namespace default \
  --jar ${funcPkgFile} \
  --classname com.example.rtmldemo.FuelEfficiencyMlPredSvcFunc \
  --inputs public/default/input \
  --output public/default/result \
  --user-config '{ \"useDsrsRestApi\": \"${useDsrsRestApi}\", \"restEndpoint\": \"${REST_ENDPOINT}\", \"projectId\": \"${PROJECT_ID}\", \"location\": \"${LOCATION}\", \"endpointId\": \"${ENDPOINT_ID}\", \"accessToken\": \"${ACCESS_TOKEN}\"  }'
" --fail-on-error
```

Please **NOTE** that this function requires user-defined configuration parameters as below:
```
{
    useDsrsRestApi: [true|false], 
    restEndpoint: ${REST_ENDPOINT}, 
    projectId: ${PROJECT_ID}, 
    location: ${LOCATION},
    endpointId: ${ENDPOINT_ID}, 
    accessToken: ${ACCESS_TOKEN}
}
```

Among these parameters, 
* `useDsrsRestApi` (default to *false* and we already covered this earlier)
* `restEndpoint` is the URL either 
   * the *DSRS* rest API (when *useDsrsRestApi* is *true*), or 
   * the URL of the prediction ML model endpoint rest API (when *useDsrsRestApi* is *false*) [default]
* `projectId`, `location`, `endpointId`, and `accessToken` are required for a successful rest API call to the prediction ML model endpoint. 

Please **`NOTE`** that when *useDsrsRestApi* is *false* (which means the function is calling the prediction ML model endpoint), the `restEndpoint` parameter is a **base URL** (see below) which is only a part of the complete rest API URL as needed. The complete URL will be automatically constructed by the function using other provided parameters: `projectId`, `location`, `endpointId`, and `accessToken`
```
https://${LOCATION}-aiplatform.googleapis.com/v1
```

## 3.4. Prediction Simulation Applications

Once we have the ML model endpoint and the Pulsar function deployed, we can start an end-to-end prediction simulation using 2 simulation client applications:

* `RtmlSimulatorProducer` is a Pulsar producer client application that generates a given number of messages. The payload of each message is a series of double values (which will be used as the inputs for the prediction service). 
* `RtmlSimulatorConsumer` is a Pulsar consumer client application that consumes a given number of messages. The payload of each message is a JSON string that contains the prediction service response.

### 3.4.1. Build the Applications

The programs in this demo are Java based, and we can build them using the following bash script: [bash/build.sh](bash/build.sh).

### 3.4.2. Run the Applications

#### 3.4.2.1. Simulation Producer

Run the bash script [bash/startSimulatorProducer.sh](bash/startSimulatorProducer.sh) to kick off the simulation producer application.
```
Usage: startSimulatorProducer.sh [-h]
                                 [-n <record_num>]
       -n : (Optional) The number of the records to publish.
                       default to -1, which means to receive indefinitely.
```

Below is an example log of running this application with 2 messages published (*-n 2*).
```
17:17:04.596 [main] INFO  c.e.rtmldemo.RtmlSimulatorProducer - Published a message with raw value: [0] {10.48,7.98,16.71,20.55,5.58,8.13,1.06,24.93,25.88}
17:17:04.625 [main] INFO  c.e.rtmldemo.RtmlSimulatorProducer - Published a message with raw value: [1] {2.74,24.3,29.07,7.86,5.41,0.21,11.1,6.49,8.61}
17:06:48.819 [main] INFO  c.e.rtmldemo.RtmlSimulatorProducer - Terminating application: "RtmlSimulatorProducer" ...
```

#### 3.4.2.2. Simulation Consumer

Run the bash script [bash/startSimulatorConsumer.sh](bash/startSimulatorConsumer.sh) to kick off the simulation consumer application.
```
Usage: startSimulatorConsumer.sh [-h]
                                 [-n <record_num>]
       -n : (Optional) The number of the records to receive.
                       default to -1, which means to receive indefinitely.
```

Below is an example log file of running this application and receiving 2 prediction results:
```
17:16:58.529 [main] INFO  c.e.rtmldemo.RtmlSimulatorConsumer - Starting application: "RtmlSimulatorConsumer    " ...
17:17:05.212 [main] INFO  c.e.rtmldemo.RtmlSimulatorConsumer - (510a8) Message received and acknowledged: key=null; properties={}; value={
  "predictions": [
    [
      119.655304
    ]
  ],
  "deployedModelId": "5339177886945378304",
  "model": "projects/958416962465/locations/us-central1/models/6003889840540614656",
  "modelDisplayName": "fuel-efficiency-prediction",
  "modelVersionId": "1"
}

17:17:05.269 [main] INFO  c.e.rtmldemo.RtmlSimulatorConsumer - (510a8) Message received and acknowledged: key=null; properties={}; value={
  "predictions": [
    [
      19.8436813
    ]
  ],
  "deployedModelId": "5339177886945378304",
  "model": "projects/958416962465/locations/us-central1/models/6003889840540614656",
  "modelDisplayName": "fuel-efficiency-prediction",
  "modelVersionId": "1"
}
17:10:35.936 [main] INFO  c.e.rtmldemo.RtmlSimulatorConsumer - Terminating application: "RtmlSimulatorConsumer" ...
```

# 4. Appendix A: DSRS (dead-simple-rest-service) Rest API server 

This demo also includes a simple rest API server. Using this sever, we can easily test the end-to-end simulation process without first having to create and deploy a complex ML model. 

Run the following bash scripts to start and stop the *DSRS* rest API server:
* [bash/startDsrsRestSrv.sh](bash/startDsrsRestSrv.sh)
* [bash/stopDsrsRestSrv.sh](bash/stopDsrsRestSrv.sh)

Once started, the *DSRS* server will run locally on port *8090* (which is chosen to avoid port conflict with Pulsar port 8080). This sever only has one API endpoint, `/avg`, which is used to calculate the average of a series of input double values. Below is an example of calling this rest API:
```
$ curl http://localhost:8090/avg/10,20,30,40,50
{"id":1,"input_values":[10.0,20.0,30.0,40.0,50.0],"average":30.0}
```