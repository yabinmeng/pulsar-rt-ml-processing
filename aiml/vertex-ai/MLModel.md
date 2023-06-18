- [1. Overview](#1-overview)
- [2. Prerequisites](#2-prerequisites)
- [3. Train the Model](#3-train-the-model)
- [4. Deploy the Model on Google Vertex AI](#4-deploy-the-model-on-google-vertex-ai)
  - [4.1. Create and Publish the Docker Container](#41-create-and-publish-the-docker-container)
  - [4.2. Train the Model in Vertex AI](#42-train-the-model-in-vertex-ai)
  - [4.3. Create an Online Service Endpoint](#43-create-an-online-service-endpoint)

---

# 1. Overview

This document provides a step-by-step procedure to train and deploy a machine learning (ML) model on Google's Vertex AI platform. The ML model used in this demo follows the example from the TensorFlow documentation [here](https://www.tensorflow.org/tutorials/keras/regression#get_the_data).

# 2. Prerequisites

Before proceeding with training and deployment, ensure the following prerequisites are met:

- Enable the following Google Cloud Platform (GCP) enterprise APIs:
  - Compute Engine API
  - Vertex AI API
  - Container Registry API

- Create a Google Cloud Storage (GCS) bucket, e.g., `gs://temp-pulsar-ml-prediction-bucket`, to store the trained ML model. Update the bucket name in the provided model training program [ml-model/trainer/train.py]:
```
# TODO: replace `your-gcs-bucket` with the name of the Storage bucket you created earlier
BUCKET = 'gs://temp-pulsar-ml-prediction-bucket'
```

- Install the `tensorflow` Python library to test the model training locally:
```
$ pip install tensorflow
```

# 3. Train the Model

To test the model training locally, run the following command:
```
$ cd ml-model
$ python trainer/train.py
```

Upon successful completion, the trained model will be saved in the `model` subfolder of the GCS bucket created earlier.

# 4. Deploy the Model on Google Vertex AI

## 4.1. Create and Publish the Docker Container

To make the model training code available in Google Vertex AI, perform the following tasks:

1. Containerize the training code using the provided [ml-model/Dockerfile](ml-model/Dockerfile) and bash script [ml-model/buildDockerContainer.sh].

2. Upload and publish the Docker container to the Google Container Registry.

Ensure you run the following two commands before executing the bash script [ml-model/buildDockerContainer.sh]:
```
$ gcloud auth login
$ gcloud auth configure-docker
```

If the script runs without any issues, a Docker image named `fueleffpred:latest` will be published to the Google Container Registry. You can verify the published Docker image by running the following command:

```
$ gcloud container images list --project=$(gcloud config get-value project)
```

To view the available published image versions/tags, use the following command:
```
$ gcloud container images list-tags <image_name>
```

## 4.2. Train the Model in Vertex AI

This step is currently performed through the Google Cloud Console UI.

- Go to "Vertex AI" -> "Model Development" -> "Training" and click the "Create" button. Ensure the following options are selected:
  - Datasets: `No managed dataset`
  - Model training method: `Custom training (advanced)`
  
<img src="./resources/vertex-ai-training-model.png"  width="600" height="320">

- Follow the UI instructions. In the "Training container" step:
  - Select the `Custom container` type
  - Choose the recently published Docker image
  - Specify the GCS bucket model output location created earlier

<img src="./resources/vertex-ai-training-container.png"  width="500" height="320">

## 4.3. Create an Online Service Endpoint

Once the model is trained in Vertex AI, you can create an endpoint to use it for online predictions.

- Go to "Vertex AI" -> "Deploy and Use" -> "Online prediction" and click the "CREATE" button. 
  - Follow the instructions in the UI. 
  - In the "Model settings" step, choose the deployed model name from the previous step.

<img src="./resources/vertex-ai-training-endpoint.png"  width="500" height="250">

At this point, the ML model is ready for external REST API.