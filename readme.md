
## Overview:
This is a project which consists of a data pipeline:
#### Current features:
- Reads frames from a camera feed, labels emotions and number of fingers and maps them to people in the frame.
- Produces the results into a given Kafka topic.

#### WIP (Roadmap):

- Launches a Flink job, deserializes data.
- Provides real-time analytics.

## Launch instructions:
1. To launch the app in standalone (local face/hand recognition only mode with no producing or analytics) mode: <br>
`docker compose up --build standalone`
<br>Then visit localhost:8501 and enjoy!


Remarks:
This is not an AI/ML project but rather a data engineering proof-of-concept, and as such you would be right to question the quality of the frame detection, which was mostly made by combining code from 2 repos and of course LLMs. However, feel free to contribute if you have any ideas on how to improve it
