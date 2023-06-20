# Apache Pulsar for Real-Time Messaging, Streaming, and ML Processing

## Summary

This GitHub repository serves as an introductory guide and demonstration of utilizing Apache Pulsar for real-time messaging, streaming, and machine learning (ML) processing. The repository showcases various components and examples that highlight the capabilities and features of Apache Pulsar in these domains. It aims to provide developers and enthusiasts with practical insights into leveraging Pulsar for building robust, scalable, and efficient real-time data pipelines in the following areas:

1. Real-Time Messaging: Demonstrations and sample code illustrating how to implement real-time messaging solutions using Pulsar's publish-subscribe model and features like topics, producers, and consumers.
2. Streaming Processing: Examples showcasing Pulsar's streaming capabilities through the usage of Pulsar Functions and Apache Flink integration for stream processing tasks.
3. ML Processing: Guidance on integrating ML models into Pulsar workflows, including deploying ML models as Pulsar functions, interacting with ML model endpoints, and performing real-time predictions using Pulsar's messaging capabilities.
4. Integration and Ecosystem: Insights into integrating Apache Pulsar with other technologies and frameworks, such as Apache Kafka, Kubernetes, and cloud platforms like AWS and Google Cloud.

## Repository Contents

This repository encompasses a collection of comprehensive, end-to-end scenarios that demonstrate the capabilities of Apache Pulsar for real-time messaging, streaming, and ML processing. Each scenario is self-contained, independent, and accompanied by complete documentation, programs, and scripts. These scenarios offer a holistic understanding of different use cases and provide practical insights into leveraging Apache Pulsar effectively.

| Scenario Folder | Scenario Name | Scenario Description |
| --------------- | ------------- | -------------------- |
| [aiml/vertex-ai](aiml/vertex-ai/) | Real-time Online Prediction Service | This scenario demonstrates using Pulsar Functions to create a real-time prediction service with a custom ML model built using TensorFlow. The ML model is trained and deployed on Google Vertex AI. By integrating the Pulsar function into data pipelines, developers can enable dynamic and on-the-fly predictions for incoming data streams. This scenario provides detailed documentation, code samples, and instructions for implementing and deploying the Pulsar function for real-time predictions using TensorFlow and Google Vertex AI. |

## Usage and Licensing

This repository provides step-by-step guides, sample code, and ready-to-use scripts to facilitate the exploration and utilization of Apache Pulsar for real-time messaging, streaming, and ML processing. Users can follow the provided examples, documentation, and resources to gain hands-on experience with Pulsar's capabilities and learn how to incorporate it into their own projects.

The repository is released under the Apache License 2.0, ensuring an open and permissive licensing framework for users to utilize, modify, and distribute the code. For more information, refer to the repository documentation and the respective license files.