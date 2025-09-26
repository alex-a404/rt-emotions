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

import java.util.Arrays;

public class StatefulWaveEmitter
        extends RichFlatMapFunction<Person, WaveEvent> {

    private final int FRAMES_THRESHOLD = 10;
    private transient ValueState<Integer> happyFrameCount;
    private transient ValueState<Integer>  fingersFrameCount;

    @Override
    public void open(Configuration parameters) throws Exception {

        // ValueState descriptor for happy frames
        ValueStateDescriptor<Integer> happyDesc =
                new ValueStateDescriptor<>(
                        "happyFrameCount",         // state name
                        Integer.class              // type info
                );
        happyFrameCount = getRuntimeContext().getState(happyDesc);

        // ValueState descriptor for fingers frames
        ValueStateDescriptor<Integer> fingersDesc =
                new ValueStateDescriptor<>(
                        "fingersFrameCount",
                        Integer.class
                );
        fingersFrameCount = getRuntimeContext().getState(fingersDesc);
    }

    @Override
    public void flatMap(Person person, Collector<WaveEvent> out) throws Exception {

        Emotion emotion = person.getEmotion();
        int nFingers = Arrays.stream(person.getFingersList()).max().orElse(0);

        // Get current happy count or start at 0 if not present
        Integer happyForFrames = happyFrameCount.value();
        if (happyForFrames == null) {
            happyForFrames = 0;
        }

        if (emotion == Emotion.HAPPY) {
            happyForFrames++;
        } else {
            happyForFrames=0;
        }

        // Get current number of fingers or start at 0
        Integer fFingersForFrames = fingersFrameCount.value();
        if (fFingersForFrames == null) {
            fFingersForFrames = 0;
        }

        if (nFingers == 5) {
            fFingersForFrames++;
        }  else {
            fFingersForFrames=0;
        }

        if (fFingersForFrames>FRAMES_THRESHOLD && happyForFrames>FRAMES_THRESHOLD) {
            out.collect(new WaveEvent(person.getInFrameId()));
        }

        happyFrameCount.update(happyForFrames);
        fingersFrameCount.update(fFingersForFrames);

    }

}
