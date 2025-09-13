package serde;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.flink.annotation.VisibleForTesting;

public class Person {
    private int inFrameId;
    private Emotion emotion;
    private int[] fingersList;

    @JsonCreator
    public Person(
            @JsonProperty("id") int inFrameId,
            @JsonProperty("emotion") Emotion emotion,
            @JsonProperty("fingers") int[] fingersList
    ) {
        this.inFrameId = inFrameId;
        this.emotion = emotion;
        this.fingersList = fingersList;
    }

    public Person() { }

    public int getInFrameId() { return inFrameId; }
    public void setInFrameId(int inFrameId) { this.inFrameId = inFrameId; }

    public Emotion getEmotion() { return emotion; }
    public void setEmotion(Emotion emotion) { this.emotion = emotion; }

    public int[] getFingersList() { return fingersList; }
    public void setFingersList(int[] fingersList) {
        this.fingersList = fingersList;
    }

    @Override
    public String toString() {
        return "Person{" + "inFrameId=" + inFrameId + ", emotion=" + emotion + '}';
    }
}
