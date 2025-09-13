package flink_jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.util.Collector;
import serde.Person;

import serde.PersonListDeserializationSchema;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

public class UniqueEmotionsEventsJob {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        ObjectMapper objectMapper = new ObjectMapper();
        // Load properties from resources folder
        Properties config = readConfig("analytics.properties");

        // Kafka source
        KafkaSource<List<Person>> source =
                KafkaSource.<List<Person>>builder()
                        .setBootstrapServers(config.getProperty("bootstrap.servers"))
                        .setTopics("emotions.rt.v2")
                        .setGroupId(config.getProperty("group.id"))
                        .setProperties(config)
                        .setValueOnlyDeserializer(new PersonListDeserializationSchema())
                        .setStartingOffsets(OffsetsInitializer.latest()) //get latest from Kafka topic
                        .build();

        // Kafka sink
        KafkaSink<EmotionsEvent> uniqueEmotionsSink = KafkaSink.<EmotionsEvent>builder()
                .setBootstrapServers(config.getProperty("bootstrap.servers"))
                .setKafkaProducerConfig(config)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic("rt.smile-events.v1")
                        .setValueSerializationSchema(
                                (SerializationSchema<EmotionsEvent>) emotionsEvent -> {
                                    try {
                                        // Convert EmotionEvent to JSON bytes
                                        return objectMapper.writeValueAsBytes(emotionsEvent);
                                    } catch (Exception e) {
                                        throw new RuntimeException("Failed to serialize EmotionEvent to JSON", e);
                                    }
                                }
                        )
                        .build()
                )
                .build();

        // Flink pipeline with explicit type hint for List<Person>
        DataStream<List<Person>> personListStream = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .returns(TypeInformation.of(new TypeHint<List<Person>>() {}));

        personListStream
                .flatMap(new FlatMapFunction<List<Person>, Person>() {
                    @Override
                    public void flatMap(List<Person> people, Collector<Person> collector) throws Exception {
                        for (Person p : people) collector.collect(p);
                    }
                }).returns(Person.class)
                .keyBy((KeySelector<Person, Integer>) Person::getInFrameId)
                .flatMap(new StatefulEmotionEmitter())
                .sinkTo(uniqueEmotionsSink);


        env.execute("UniqueEmotions Events Job");
    }

    // Load properties from resources
    public static Properties readConfig(final String configFile) throws IOException {
        final Properties config = new Properties();
        try (InputStream inputStream = UniqueEmotionsEventsJob.class.getClassLoader().getResourceAsStream(configFile)) {
            if (inputStream == null) {
                throw new IOException(configFile + " not found in resources.");
            }
            config.load(inputStream);
        }
        return config;
    }
}
