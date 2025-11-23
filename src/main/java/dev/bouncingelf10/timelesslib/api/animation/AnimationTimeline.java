package dev.bouncingelf10.timelesslib.api.animation;

import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.animation.channels.ChannelDouble;
import dev.bouncingelf10.timelesslib.api.animation.channels.ChannelVec3;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

/**
 * AnimationTimeline aggregates channels (double + vec3), provides playback controls,
 * automatic duration computation (max key time), looping/ping-pong, speed, and update().
 *
 * Time units: seconds (double). Accepts Duration overloads.
 */
public class AnimationTimeline {
    private final String id;

    // channels
    private final Map<String, ChannelDouble> doubleChannels = new LinkedHashMap<>();
    private final Map<String, ChannelVec3> vecChannels = new LinkedHashMap<>();

    // playback
    private boolean loop = false;
    private boolean pingPong = false;
    private double speed = 1.0;
    private boolean playing = false;
    private boolean finished = false;
    private double cursorSeconds = 0.0;
    private int direction = 1; // 1 forward, -1 backward

    // defaults
    private Interpolation defaultInterpolation = Interpolation.EASE;
    private Easing defaultEasing = Easing.LINEAR;
    private boolean computeTangents = false; // timeline-level opt-in for Hermite tangents

    private final List<Runnable> onStart = new ArrayList<>();
    private final List<Runnable> onLoop = new ArrayList<>();
    private final List<Runnable> onFinish = new ArrayList<>();

    private double cachedDuration = 0.0;
    private boolean durationDirty = true;

    public AnimationTimeline(String id) { this.id = Objects.requireNonNull(id); }

    public String id() { return id; }

    public AnimationTimeline loop(boolean v) { this.loop = v; return this; }
    public AnimationTimeline pingPong(boolean v) { this.pingPong = v; return this; }
    public AnimationTimeline speed(double s) { this.speed = s; return this; }

    public AnimationTimeline defaultInterpolation(Interpolation i) { this.defaultInterpolation = i; return this; }
    public AnimationTimeline defaultEasing(Easing e) { this.defaultEasing = e; return this; }
    public AnimationTimeline computeTangents(boolean v) { this.computeTangents = v; return this; }

    public AnimationTimeline onStart(Runnable r) { this.onStart.add(r); return this; }
    public AnimationTimeline onLoop(Runnable r) { this.onLoop.add(r); return this; }
    public AnimationTimeline onFinish(Runnable r) { this.onFinish.add(r); return this; }

    public boolean isPlaying() { return playing; }
    public boolean isFinished() { return finished; }

    public void play() {
        if (!playing) {
            playing = true;
            finished = false;
            onStart.forEach(Runnable::run);
        }
    }

    public void pause() { playing = false; }
    public void stop() { playing = false; cursorSeconds = 0.0; direction = 1; finished = false; markDurationDirty(); recomputeAllTangents(); }

    public void seek(double seconds) {
        cursorSeconds = Math.max(0.0, Math.min(getDurationSeconds(), seconds));
    }

    public void seek(Duration d) {
        double s = d.toNanos() / 1e9;
        seek(s);
    }

    public ChannelDouble channelDouble(String name) { markDurationDirty(); return doubleChannels.computeIfAbsent(name, ChannelDouble::new); }
    public ChannelVec3 channelVec3(String name) { markDurationDirty(); return vecChannels.computeIfAbsent(name, ChannelVec3::new); }

    public Collection<ChannelDouble> doubleChannels() { return Collections.unmodifiableCollection(doubleChannels.values()); }
    public Collection<ChannelVec3> vecChannels() { return Collections.unmodifiableCollection(vecChannels.values()); }

    public void update(double deltaSeconds) {
        if (!playing || finished) return;
        if (deltaSeconds <= 0) return;

        double effective = Math.max(1e-9, Math.abs(speed) * deltaSeconds);
        double step = direction * effective;
        double newCursor = cursorSeconds + step;

        double dur = getDurationSeconds();
        if (dur <= 0.0) {
            cursorSeconds = 0.0;
            playing = false;
            finished = true;
            onFinish.forEach(Runnable::run);
            return;
        }

        if (newCursor < 0.0 || newCursor > dur) {
            if (loop) {
                if (pingPong) {
                    double over = Math.abs(newCursor - (newCursor < 0.0 ? 0.0 : dur));
                    direction *= -1;
                    newCursor = (direction == 1) ? Math.min(dur, over) : Math.max(0.0, dur - over);
                    onLoop.forEach(Runnable::run);
                } else {
                    newCursor = ((newCursor % dur) + dur) % dur;
                    onLoop.forEach(Runnable::run);
                }
            } else {
                newCursor = Math.max(0.0, Math.min(dur, newCursor));
                cursorSeconds = newCursor;
                evaluateAll(cursorSeconds);
                playing = false;
                finished = true;
                onFinish.forEach(Runnable::run);
                return;
            }
        }

        cursorSeconds = newCursor;
        evaluateAll(cursorSeconds);
    }

    public void update(Duration d) {
        double s = d.toNanos() / 1e9;
        update(s);
    }

    private void evaluateAll(double timeSeconds) {
        recomputeAllTangents();
        for (ChannelDouble ch : doubleChannels.values()) ch.evaluateAt(timeSeconds, defaultInterpolation, defaultEasing, computeTangents);
        for (ChannelVec3 ch : vecChannels.values()) ch.evaluateAt(timeSeconds, defaultInterpolation, defaultEasing, computeTangents);
    }

    private void recomputeAllTangents() {
        if (!computeTangents) return;
        for (ChannelDouble ch : doubleChannels.values()) ch.computeTangentsIfNeeded();
        for (ChannelVec3 ch : vecChannels.values()) ch.computeTangentsIfNeeded();
    }

    public double getDurationSeconds() {
        if (!durationDirty) return cachedDuration;
        double max = 0.0;
        for (ChannelDouble ch : doubleChannels.values()) max = Math.max(max, ch.computeDurationSeconds());
        for (ChannelVec3 ch : vecChannels.values()) max = Math.max(max, ch.computeDurationSeconds());
        cachedDuration = max;
        durationDirty = false;
        return cachedDuration;
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