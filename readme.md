This is a project which consists of a data pipeline:

- Reads frames from a camera feed, labels emotions and number of fingers and maps them to people in the frame.
- Produces the results into a given Kafka topic.

- WIP (Roadmap):
- Launches a Flink job, deserializes data.
- Provides real-time analytics.

Remarks:
This is not an AI/ML project but rather a data engineering proof-of-concept, and as such you would be right to question the quality of the frame detection, which was mostly made by combining code from 2 repos and of course LLMs. However, feel free to contribute if you have any ideas on how to improve it
