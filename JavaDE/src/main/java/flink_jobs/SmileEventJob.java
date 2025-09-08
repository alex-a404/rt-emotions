package flink_jobs;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;


import org.apache.flink.util.Collector;
import serde.Person;
import serde.PersonListDeserializationSchema;

import java.util.List;


/**
 * This job takes a stream of List of Person. It emits smile events into a topic.
 * A smile event is produced when a person smiles for more than 10
 * consecutive frames.
 */
public class SmileEventJob {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        // Get initial data source, which is a list of Person
        KafkaSource<List<Person>> source =
                KafkaSource.<List<Person>>builder()
                        .setBootstrapServers("localhost:9092")
                        .setTopics("emotions.rt.v2")
                        .setGroupId("payment-consumer-group")
                        .setValueOnlyDeserializer(new PersonListDeserializationSchema())
                        .setStartingOffsets(OffsetsInitializer.earliest())
                        .build();


        env.fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .flatMap(new FlatMapFunction<List<Person>, Person>() {
                    @Override
                    public void flatMap(List<Person> people, Collector<Person> collector) throws Exception {
                        for  (Person person : people) collector.collect(person);
                    }
                }).keyBy(new KeySelector<Person, Integer>() {
                    @Override
                    public Integer getKey(Person person) throws Exception {
                        return person.getInFrameId();
                    }
                });
    }

}
