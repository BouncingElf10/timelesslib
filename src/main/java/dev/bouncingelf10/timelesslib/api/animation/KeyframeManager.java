package dev.bouncingelf10.timelesslib.api.animation;

import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class KeyframeManager {
    private final Map<String, AnimationTimeline> timelines = new ConcurrentHashMap<>();

    public AnimationTimeline createTimeline(String id) {
        Objects.requireNonNull(id);
        AnimationTimeline timeline = new AnimationTimeline(id);
        timelines.put(id, timeline);
        return timeline;
    }

    public Optional<AnimationTimeline> getTimeline(String id) {
        return Optional.ofNullable(timelines.get(id));
    }

    public boolean removeTimeline(String id) { return timelines.remove(id) != null; }

    public void update(double deltaSeconds) {
        if (deltaSeconds <= 0) return;
        for (AnimationTimeline timeline : timelines.values()) timeline.update(deltaSeconds);
    }

    public void update(Duration duration) {
        double s = duration.toNanos() / 1e9;
        update(s);
    }
}