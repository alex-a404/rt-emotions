package flink_jobs;

import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import serde.Emotion;
import serde.Person;

/**
 * Emits a EmotionEvent only once a person was showing this emotion for over n frames.
 */
public class StatefulEmotionEmitter
        extends RichFlatMapFunction<Person, EmotionsEvent> {

    private final int FRAMES_THRESHOLD = 10;
    private transient MapState<Emotion,Integer> emotionsFrameCount;

    @Override
    public void open(Configuration parameters) throws Exception {
        MapStateDescriptor<Emotion, Integer> descriptor =
                new MapStateDescriptor<>(
                        "emotionsFrameCount",   // state name (unique within this operator)
                        TypeInformation.of(Emotion.class),  // key type
                        TypeInformation.of(Integer.class)   // value type
                );

        emotionsFrameCount = getRuntimeContext().getMapState(descriptor);
    }


    @Override
    public void flatMap(Person person, Collector<EmotionsEvent> collector) throws Exception {

        Emotion emotion = person.getEmotion();

        // Get current count or start at 0 if not present
        Integer currentCount = emotionsFrameCount.get(emotion);
        if (currentCount == null) {
            currentCount = 0;
        }

        currentCount++;
        emotionsFrameCount.put(emotion, currentCount);

        // Collect event when threshold reached
        if (currentCount == FRAMES_THRESHOLD) {
            collector.collect(new EmotionsEvent(person.getInFrameId(), emotion));
        }
    }

}
