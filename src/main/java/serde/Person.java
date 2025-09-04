package serde;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Person {
    private int inFrameId;
    private Emotion emotion;

    @JsonCreator
    public Person(
            @JsonProperty("inFrameId") int inFrameId,
            @JsonProperty("emotion") Emotion emotion
    ) {
        this.inFrameId = inFrameId;
        this.emotion = emotion;
    }

    public Person() { }

    public int getInFrameId() { return inFrameId; }
    public void setInFrameId(int inFrameId) { this.inFrameId = inFrameId; }

    public Emotion getEmotion() { return emotion; }
    public void setEmotion(Emotion emotion) { this.emotion = emotion; }

    @Override
    public String toString() {
        return "Person{" + "inFrameId=" + inFrameId + ", emotion=" + emotion + '}';
    }
}
