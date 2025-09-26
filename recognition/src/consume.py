from confluent_kafka import Consumer


def read_config(path):
    # reads the client configuration from client.properties
    # and returns it as a key-value map
    config = {}
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if len(line) != 0 and line[0] != "#":
                parameter, value = line.strip().split("=", 1)
                config[parameter] = value.strip()
    return config


def consume(topic, config):
    # sets the consumer group ID and offset
    config["group.id"] = "python-group-1"
    config["auto.offset.reset"] = "latest"

    # creates a new consumer instance
    consumer = Consumer(config)

    # subscribes to the specified topic
    consumer.subscribe([topic])

    try:
        while True:
            # consumer polls the topic and prints any incoming messages
            msg = consumer.poll(1.0)
            if msg is not None and msg.error() is None:
                value = msg.value().decode("utf-8")
                print(f"{value:12}")
    except KeyboardInterrupt:
        pass
    finally:
        # closes the consumer connection
        consumer.close()


config = read_config(
    "/home/alex/Desktop/facerec/recognition/src/camera_feed.properties"
)
consume("rt.smile-events.v1", config)
