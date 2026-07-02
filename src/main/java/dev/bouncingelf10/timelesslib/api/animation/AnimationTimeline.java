package dev.bouncingelf10.timelesslib.api.animation;

import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.clock.TimeSources;
import dev.bouncingelf10.timelesslib.api.clock.TimelessClock;
import dev.bouncingelf10.timelesslib.api.animation.keyframes.KeyframeDouble;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

public class AnimationTimeline {
    private ResourceLocation timelineId;

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

    private final List<Runnable> onStartCallbacks = new ArrayList<>();
    private final List<Runnable> onLoopCallbacks = new ArrayList<>();
    private final List<Runnable> onFinishCallbacks = new ArrayList<>();

    private double cachedDurationSeconds = 0.0;
    private boolean durationDirty = true;

    private TimeSource timeSource = TimeSources.GAME_TIME;
    private long lastTimelineNanoTime = timeSource.now();

    AnimationTimeline(ResourceLocation timelineId) {
        this.timelineId = Objects.requireNonNull(timelineId);
    }

    public ResourceLocation id() { return timelineId; }

    public AnimationTimeline loop(boolean enabled) { this.loop = enabled; return this; }
    public AnimationTimeline pingPong(boolean enabled) { this.pingPong = enabled; return this; }
    public AnimationTimeline speed(double speed) { this.playbackSpeed = speed; return this; }

    /**
     * Sets the default interpolation for the timeline. <br>
     * Note: Channels can override these settings and follow a hierarchy: <br>
     * {@link AnimationTimeline} > {@link ChannelDouble} > {@link KeyframeDouble} (same for Vec3)
     */
    public AnimationTimeline defaultInterpolation(Interpolation interpolation) { this.defaultInterpolation = interpolation; return this; }
    /**
     * Sets the default easing for the timeline. <br>
     * Note: Channels can override these settings and follow a hierarchy: <br>
     * {@link AnimationTimeline} > {@link ChannelDouble} > {@link KeyframeDouble} (same for Vec3)
     */
    public AnimationTimeline defaultEasing(Easing easing) { this.defaultEasing = easing; return this; }

    /**
     * Adds a callback to be executed when the timeline starts playing.
     * @param callback Callback to execute
     */
    public AnimationTimeline onStart(Runnable callback) { this.onStartCallbacks.add(callback); return this; }

    /**
     * Adds a callback to be executed when the timeline loops.
     * @param callback Callback to execute
     */
    public AnimationTimeline onLoop(Runnable callback) { this.onLoopCallbacks.add(callback); return this; }

    /**
     * Adds a callback to be executed when the timeline finishes playing.
     * @param callback Callback to execute
     */
    public AnimationTimeline onFinish(Runnable callback) { this.onFinishCallbacks.add(callback); return this; }

    public boolean isPlaying() { return isPlaying; }
    public boolean isFinished() { return isFinished; }

    public void play() {
        if (!isPlaying) {
            isPlaying = true;
            isFinished = false;
            lastTimelineNanoTime = timeSource.now();
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
    }

    public void playOrReset() {
        if (this.isPlaying()) {
            this.stop();
            this.play();
        } else {
            this.play();
        }
    }

    /**
     * Toggles between playing and paused.
     */
    public void pauseOrUnpause() {
        if (this.isPlaying()) {
            this.pause();
        } else {
            this.play();
        }
    }

    /**
     * Seeks to the specified time in seconds.
     * Note: "Seeking" to a time means that the timeline will jump to that time instantly.
     */
    public void seek(double seconds) {
        currentTimeSeconds = Math.max(0.0, Math.min(getDurationSeconds(), seconds));
    }

    /**
     * Seeks to the specified duration.
     * Note: "Seeking" to a time means that the timeline will jump to that time instantly.
     */
    public void seek(Duration duration) {
        seek(duration.toNanos() / 1e9);
    }

    /**
     * Adds a new double channel to the timeline.
     * @param name Channel name
     * @return {@link ChannelDouble}
     */
    public ChannelDouble channelDouble(String name) {
        markDurationDirty();
        return doubleChannels.computeIfAbsent(name, ChannelDouble::new);
    }

    /**
     * Adds a new Vec3 channel to the timeline.
     * @param name Channel name
     * @return {@link ChannelVec3}
     */
    public ChannelVec3 channelVec3(String name) {
        markDurationDirty();
        return vec3Channels.computeIfAbsent(name, ChannelVec3::new);
    }

    public Collection<ChannelDouble> getDoubleChannels() { return Collections.unmodifiableCollection(doubleChannels.values()); }
    public Collection<ChannelVec3> getVec3Channels() { return Collections.unmodifiableCollection(vec3Channels.values()); }

    /**
     * You don't need to call this. Its taken care of by the {@link AnimationManager}.
     */
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

    private void evaluateAll(double timeSeconds) {
        for (ChannelDouble ch : doubleChannels.values())
            ch.evaluateAt(timeSeconds, defaultInterpolation, defaultEasing);
        for (ChannelVec3 ch : vec3Channels.values())
            ch.evaluateAt(timeSeconds, defaultInterpolation, defaultEasing);
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

    double getDeltaSeconds() {
        if (!isPlaying) return 0.0;
        if (timeSource == TimeSources.GAME_TIME && TimelessClock.isPaused()) return 0.0;

        long now = timeSource.now();
        double delta = (now - lastTimelineNanoTime) / 1e9;
        lastTimelineNanoTime = now;
        return delta * playbackSpeed;
    }

    /**
     * Sets the default {@link TimeSource} for the timeline. <br>
     * Note: Channels can override these settings and follow a hierarchy: <br>
     * {@link AnimationTimeline} > {@link ChannelDouble} > {@link KeyframeDouble} (same for Vec3)
     */
    public AnimationTimeline setTimeSource(TimeSource source) {
        this.timeSource = Objects.requireNonNull(source);
        this.lastTimelineNanoTime = source.now();
        return this;
    }

    private void markDurationDirty() { durationDirty = true; }

    /**
     * Binds a double consumer to the specified channel. <br>
     * Normally you should call this in the channel itself {@link ChannelDouble#bind(Consumer)} and I advise you to not use this method.
     */
    public AnimationTimeline bindDouble(String channelName, Consumer<Double> consumer) {
        channelDouble(channelName).bind(consumer);
        markDurationDirty();
        return this;
    }
    /**
     * Binds a Vec3 consumer to the specified channel. <br>
     * Normally you should call this in the channel itself {@link ChannelVec3#bind(Consumer)} and I advise you to not use this method.
     */
    public AnimationTimeline bindVec3(String channelName, Consumer<Vec3> consumer) {
        channelVec3(channelName).bind(consumer);
        markDurationDirty();
        return this;
    }

    /**
     * Creates a detached copy of this timeline, not registered with any {@link AnimationManager}. <br>
     * Combine with {@link #randomiseId()} and {@link AnimationManager#addTimeline(AnimationTimeline)} to spawn procedural instances of a template timeline.
     */
    public AnimationTimeline copy() {
        AnimationTimeline copy = new AnimationTimeline(this.timelineId);
        copy.loop = this.loop;
        copy.pingPong = this.pingPong;
        copy.playbackSpeed = this.playbackSpeed;
        copy.defaultInterpolation = this.defaultInterpolation;
        copy.defaultEasing = this.defaultEasing;
        copy.timeSource = this.timeSource;
        for (var entry : this.doubleChannels.entrySet()) copy.doubleChannels.put(entry.getKey(), entry.getValue().copy());
        for (var entry : this.vec3Channels.entrySet()) copy.vec3Channels.put(entry.getKey(), entry.getValue().copy());
        copy.markDurationDirty();
        return copy;
    }

    /**
     * Randomizes the ID of this timeline by appending a random suffix to its path, keeping the same namespace. <br>
     * Useful for procedural timelines - see {@link #copy()}.
     */
    public AnimationTimeline randomiseId() {
        this.timelineId = ResourceLocation.fromNamespaceAndPath(this.timelineId.getNamespace(), this.timelineId.getPath() + "-" + UUID.randomUUID());
        return this;
    }
}