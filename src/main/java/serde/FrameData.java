package serde;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class FrameData {
    private int key;
    private List<Person> people;

    @JsonCreator
    public FrameData(
            @JsonProperty("key") int key,
            @JsonProperty("people") List<Person> people
    ) {
        this.key = key;
        this.people = people;
    }

    public FrameData() {}

    public int getKey() { return key; }
    public void setKey(int key) { this.key = key; }

    public List<Person> getPeople() { return people; }
    public void setPeople(List<Person> people) { this.people = people; }

    @Override
    public String toString() {
        return "FrameData{" + "key=" + key + ", people=" + people + '}';
    }
}
