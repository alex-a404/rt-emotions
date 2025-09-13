package flink_jobs;

import serde.Emotion;

public class EmotionsEvent {
    private final int id;
    private final Emotion emotion;
    public EmotionsEvent(int id, Emotion emotion) {
        this.id = id;
        this.emotion = emotion;
    }
    public Emotion getEmotion(){return emotion;}
    public int getId() {
        return id;
    }
}
