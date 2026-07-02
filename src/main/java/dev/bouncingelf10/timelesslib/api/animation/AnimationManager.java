package dev.bouncingelf10.timelesslib.api.animation;

import dev.bouncingelf10.timelesslib.InternalAccess;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns and drives every {@link AnimationTimeline}. <br>
 * Obtain the shared instance through {@code TimelessLib.animations()} / {@code TimelessLibClient.animations()} -
 * this class cannot be constructed by other mods.
 */
public class AnimationManager {
    private final Map<ResourceLocation, AnimationTimeline> timelines = new ConcurrentHashMap<>();

    public AnimationManager(InternalAccess access) {
        Objects.requireNonNull(access, "Managers can only be constructed by TimelessLib");
    }

    public AnimationTimeline createTimeline(ResourceLocation id) {
        Objects.requireNonNull(id);
        AnimationTimeline timeline = new AnimationTimeline(id);
        return addTimeline(timeline);
    }

    /**
     * Gets the existing timeline for the given ID, or creates and registers a new one if absent.
     * @param id Timeline ID
     * @return {@link AnimationTimeline}
     */
    public AnimationTimeline getOrCreateTimeline(ResourceLocation id) {
        return getTimeline(id).orElseGet(() -> createTimeline(id));
    }

    public AnimationTimeline addTimeline(AnimationTimeline timeline) {
        Objects.requireNonNull(timeline);
        timelines.put(timeline.id(), timeline);
        return timeline;
    }

    /**
     * Gets a timeline by its ID. <br>
     * Note: Returns an empty Optional if the timeline does not exist.
     * @param id Timeline ID
     * @return {@link AnimationTimeline} or empty
     */
    public Optional<AnimationTimeline> getTimeline(ResourceLocation id) {
        return Optional.ofNullable(timelines.get(id));
    }

    public boolean removeTimeline(ResourceLocation id) { return timelines.remove(id) != null; }

    /**
     * Pauses every registered timeline.
     */
    public void pauseAll() {
        timelines.values().forEach(AnimationTimeline::pause);
    }

    /**
     * Stops every registered timeline.
     */
    public void stopAll() {
        timelines.values().forEach(AnimationTimeline::stop);
    }

    /**
     * DO NOT CALL THIS, it is already handled for you.
     */
     public void update() {
        for (AnimationTimeline timeline : timelines.values()) {
            double delta = timeline.getDeltaSeconds();
            timeline.update(delta);
        }
    }
}
