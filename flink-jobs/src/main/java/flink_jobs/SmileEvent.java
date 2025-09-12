package flink_jobs;

public class SmileEvent {
    private final int id;
    public SmileEvent(int id) {
        this.id = id;
    }
    public int getId() {
        return id;
    }
}
