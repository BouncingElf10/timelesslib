package dev.bouncingelf10.timelesslib.api.animation;

import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.animation.channels.ChannelDouble;
import dev.bouncingelf10.timelesslib.api.animation.channels.ChannelVec3;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

public class AnimationTimeline {
    private final String timelineId;

    private final Map<String, ChannelDouble> doubleChannels = new LinkedHashMap<>();
    private final Map<String, ChannelVec3> vec3Channels = new LinkedHashMap<>();

    private boolean loop = false;
    private boolean pingPong = false;
    private double playbackSpeed = 1.0;
    private boolean isPlaying = false;
    private boolean isFinished = false;
    private double currentTimeSeconds = 0.0;
    private int playDirection = 1;

    private Interpolation defaultInterpolation = Interpolation.EASE;
    private Easing defaultEasing = Easing.LINEAR;
    private boolean computeTangents = false;

    private final List<Runnable> onStartCallbacks = new ArrayList<>();
    private final List<Runnable> onLoopCallbacks = new ArrayList<>();
    private final List<Runnable> onFinishCallbacks = new ArrayList<>();

    private double cachedDurationSeconds = 0.0;
    private boolean durationDirty = true;

    public AnimationTimeline(String timelineId) {
        this.timelineId = Objects.requireNonNull(timelineId);
    }

    public String id() { return timelineId; }

    public AnimationTimeline loop(boolean enabled) { this.loop = enabled; return this; }
    public AnimationTimeline pingPong(boolean enabled) { this.pingPong = enabled; return this; }
    public AnimationTimeline speed(double speed) { this.playbackSpeed = speed; return this; }

    public AnimationTimeline defaultInterpolation(Interpolation interpolation) { this.defaultInterpolation = interpolation; return this; }
    public AnimationTimeline defaultEasing(Easing easing) { this.defaultEasing = easing; return this; }
    public AnimationTimeline computeTangents(boolean enabled) { this.computeTangents = enabled; return this; }

    public AnimationTimeline onStart(Runnable callback) { this.onStartCallbacks.add(callback); return this; }
    public AnimationTimeline onLoop(Runnable callback) { this.onLoopCallbacks.add(callback); return this; }
    public AnimationTimeline onFinish(Runnable callback) { this.onFinishCallbacks.add(callback); return this; }

    public boolean isPlaying() { return isPlaying; }
    public boolean isFinished() { return isFinished; }

    public void play() {
        if (!isPlaying) {
            isPlaying = true;
            isFinished = false;
            onStartCallbacks.forEach(Runnable::run);
        }
    }

    public void pause() { isPlaying = false; }

    public void stop() {
        isPlaying = false;
        currentTimeSeconds = 0.0;
        playDirection = 1;
        isFinished = false;
        markDurationDirty();
        recomputeAllTangents();
    }

    public void seek(double seconds) {
        currentTimeSeconds = Math.max(0.0, Math.min(getDurationSeconds(), seconds));
    }

    public void seek(Duration duration) {
        seek(duration.toNanos() / 1e9);
    }

    public ChannelDouble channelDouble(String name) {
        markDurationDirty();
        return doubleChannels.computeIfAbsent(name, ChannelDouble::new);
    }

    public ChannelVec3 channelVec3(String name) {
        markDurationDirty();
        return vec3Channels.computeIfAbsent(name, ChannelVec3::new);
    }

    public Collection<ChannelDouble> doubleChannels() { return Collections.unmodifiableCollection(doubleChannels.values()); }
    public Collection<ChannelVec3> vec3Channels() { return Collections.unmodifiableCollection(vec3Channels.values()); }

    public void update(double deltaSeconds) {
        if (!isPlaying || isFinished || deltaSeconds <= 0) return;

        double stepSeconds = Math.max(1e-9, Math.abs(playbackSpeed) * deltaSeconds) * playDirection;
        double newTime = currentTimeSeconds + stepSeconds;

        double timelineDuration = getDurationSeconds();
        if (timelineDuration <= 0.0) {
            currentTimeSeconds = 0.0;
            isPlaying = false;
            isFinished = true;
            onFinishCallbacks.forEach(Runnable::run);
            return;
        }

        if (newTime < 0.0 || newTime > timelineDuration) {
            if (loop) {
                if (pingPong) {
                    double overshoot = Math.abs(newTime - (newTime < 0.0 ? 0.0 : timelineDuration));
                    playDirection *= -1;
                    newTime = (playDirection == 1) ? Math.min(timelineDuration, overshoot) : Math.max(0.0, timelineDuration - overshoot);
                    onLoopCallbacks.forEach(Runnable::run);
                } else {
                    newTime = ((newTime % timelineDuration) + timelineDuration) % timelineDuration;
                    onLoopCallbacks.forEach(Runnable::run);
                }
            } else {
                newTime = Math.max(0.0, Math.min(timelineDuration, newTime));
                currentTimeSeconds = newTime;
                evaluateAll(currentTimeSeconds);
                isPlaying = false;
                isFinished = true;
                onFinishCallbacks.forEach(Runnable::run);
                return;
            }
        }

        currentTimeSeconds = newTime;
        evaluateAll(currentTimeSeconds);
    }

    public void update(Duration duration) {
        update(duration.toNanos() / 1e9);
    }

    private void evaluateAll(double timeSeconds) {
        recomputeAllTangents();
        for (ChannelDouble ch : doubleChannels.values())
            ch.evaluateAt(timeSeconds, defaultInterpolation, defaultEasing, computeTangents);
        for (ChannelVec3 ch : vec3Channels.values())
            ch.evaluateAt(timeSeconds, defaultInterpolation, defaultEasing, computeTangents);
    }

    private void recomputeAllTangents() {
        if (!computeTangents) return;
        doubleChannels.values().forEach(ChannelDouble::computeTangentsIfNeeded);
        vec3Channels.values().forEach(ChannelVec3::computeTangentsIfNeeded);
    }

    public double getDurationSeconds() {
        if (!durationDirty) return cachedDurationSeconds;

        double maxDuration = 0.0;
        for (ChannelDouble ch : doubleChannels.values()) maxDuration = Math.max(maxDuration, ch.computeDurationSeconds());
        for (ChannelVec3 ch : vec3Channels.values()) maxDuration = Math.max(maxDuration, ch.computeDurationSeconds());

        cachedDurationSeconds = maxDuration;
        durationDirty = false;
        return cachedDurationSeconds;
    }

    private void markDurationDirty() { durationDirty = true; }

    private void markDurationDirtyPublic() { markDurationDirty(); }

    public AnimationTimeline bindDouble(String channelName, Consumer<Double> consumer) {
        channelDouble(channelName).bind(consumer);
        markDurationDirty();
        return this;
    }

    public AnimationTimeline bindVec3(String channelName, Consumer<Vec3> consumer) {
        channelVec3(channelName).bind(consumer);
        markDurationDirty();
        return this;
    }
}