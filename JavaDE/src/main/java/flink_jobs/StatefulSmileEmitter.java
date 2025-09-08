package flink_jobs;

import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.util.Collector;
import serde.Emotion;
import serde.Person;

/**
 * Emits a SmileEvent only once a person was smiling for over n frames.
 */
public class StatefulSmileEmitter
        extends RichFlatMapFunction<Person, SmileEvent> {

    private transient ValueState<Integer> count;
    private transient ValueState<Boolean> smiled;

    @Override
    public void open(Configuration parameters) throws Exception {
        count = getRuntimeContext().getState(
                new ValueStateDescriptor<>("count", Integer.class)
        );
        smiled = getRuntimeContext().getState(
                new ValueStateDescriptor<>("smiled", Boolean.class)
        );
    }


    @Override
    public void flatMap(Person person, Collector<SmileEvent> collector) throws Exception {

        // Initialize from null
        Integer currentCount = count.value();
        if (currentCount == null) {
            currentCount = 0;
        }

        Boolean hasSmiled = smiled.value();
        if (hasSmiled == null) {
            hasSmiled = false;
        }

        // Update count state
        if (person.getEmotion() == Emotion.HAPPY) {
            currentCount += 1;
        } else {
            currentCount = 0;
        }
        count.update(currentCount);

        if (currentCount == 50 && !hasSmiled) {
            smiled.update(true);
            collector.collect(new SmileEvent(person.getInFrameId()));
        }

    }
}
