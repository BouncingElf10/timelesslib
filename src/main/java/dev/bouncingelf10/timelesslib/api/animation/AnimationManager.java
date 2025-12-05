package dev.bouncingelf10.timelesslib.api.animation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnimationManager {
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

    public void update() {
        for (AnimationTimeline timeline : timelines.values()) {
            double delta = timeline.getDeltaSeconds();
            timeline.update(delta);
        }
    }
}