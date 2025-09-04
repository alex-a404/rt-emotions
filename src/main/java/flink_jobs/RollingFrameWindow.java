package flink_jobs;

import serde.FrameData;

import java.util.List;

public class RollingFrameWindow {
    private List<FrameData> frames;

    public void update(FrameData frame) {
        if (frame.getPeople().isEmpty()){
            frames.clear();
            return;
        }
        frames.add(frame);
    }

}
