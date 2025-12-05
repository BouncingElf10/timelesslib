package dev.bouncingelf10.timelesslib.api.animation.channels;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.api.animation.*;
import dev.bouncingelf10.timelesslib.api.animation.keyframes.KeyframeVec3;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Consumer;

public class ChannelVec3 {
    private final String name;
    private final List<KeyframeVec3> keyframes = new ArrayList<>();
    private Interpolation defaultInterpolation = Interpolation.EASE;
    private Easing defaultEasing = Easing.LINEAR;

    private TangentMode tangentMode = TangentMode.ZERO;

    private Consumer<Vec3> boundConsumer = vec -> {};
    private boolean tangentsDirty = true;

    private double tension = 0.0;
    private double continuity = 0.0;
    private double bias = 0.0;

    public ChannelVec3(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String name() {
        return name;
    }

    public ChannelVec3 keyframe(double timeSeconds, Vec3 value) {
        return addKeyframe(KeyframeVec3.of(timeSeconds, value));
    }

    public ChannelVec3 keyframe(double timeSeconds, Vec3 value, Easing easing) {
        return addKeyframe(KeyframeVec3.of(timeSeconds, value, easing));
    }

    public ChannelVec3 keyframe(double timeSeconds, Vec3 value, Interpolation interpolation) {
        return addKeyframe(KeyframeVec3.of(timeSeconds, value, interpolation));
    }

    public ChannelVec3 keyframe(double timeSeconds, Vec3 value, Interpolation interpolation, Easing easing) {
        return addKeyframe(KeyframeVec3.of(timeSeconds, value, easing, interpolation));
    }

    public ChannelVec3 keyframe(Duration duration, Vec3 value) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value);
    }

    public ChannelVec3 keyframe(Duration duration, Vec3 value, Easing easing) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value, easing);
    }

    public ChannelVec3 keyframe(Duration duration, Vec3 value, Interpolation interpolation) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value, interpolation);
    }

    public ChannelVec3 keyframe(Duration duration, Vec3 value, Easing easing, Interpolation interpolation) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value, interpolation, easing);
    }

    public ChannelVec3 addKeyframe(KeyframeVec3 keyframe) {
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingDouble(k -> k.timeSeconds));
        tangentsDirty = true;
        return this;
    }

    public ChannelVec3 defaultInterpolation(Interpolation interpolation) {
        this.defaultInterpolation = Objects.requireNonNull(interpolation);
        return this;
    }

    public ChannelVec3 defaultEasing(Easing easing) {
        this.defaultEasing = Objects.requireNonNull(easing);
        return this;
    }

    public ChannelVec3 setTangentMode(TangentMode mode) {
        this.tangentMode = mode;
        this.tangentsDirty = true;
        return this;
    }

    public ChannelVec3 bind(Consumer<Vec3> consumer) {
        this.boundConsumer = Objects.requireNonNull(consumer);
        return this;
    }

    public ChannelVec3 setTCB(double tension, double continuity, double bias) {
        this.tension = tension;
        this.continuity = continuity;
        this.bias = bias;
        tangentsDirty = true;
        return this;
    }

    public double computeDurationSeconds() {
        if (keyframes.isEmpty()) return 0.0;
        return keyframes.getLast().timeSeconds;
    }

    public void computeTangentsIfNeeded() {
        if (!tangentsDirty) return;
        tangentsDirty = false;

        int frameCount = keyframes.size();
        if (frameCount == 0) return;
        if (frameCount == 1) {
            keyframes.getFirst().tangent = Vec3.ZERO;
            return;
        }

        switch (tangentMode) {

            case ZERO -> {
                for (KeyframeVec3 kf : keyframes)
                    kf.tangent = Vec3.ZERO;
            }

            case CATMULL_ROM -> {
                for (int i = 0; i < frameCount; i++) {
                    KeyframeVec3 prev = (i > 0) ? keyframes.get(i - 1) : keyframes.get(i);
                    KeyframeVec3 next = (i < frameCount - 1) ? keyframes.get(i + 1) : keyframes.get(i);

                    double dt = next.timeSeconds - prev.timeSeconds;
                    if (dt == 0) {
                        keyframes.get(i).tangent = Vec3.ZERO;
                    } else {
                        Vec3 delta = next.value.subtract(prev.value).scale(0.5 / dt);
                        keyframes.get(i).tangent = delta;
                    }
                }
            }

            case TCB -> {
                for (int i = 0; i < frameCount; i++) {
                    KeyframeVec3 previous = (i > 0) ? keyframes.get(i - 1) : keyframes.get(i);
                    KeyframeVec3 current = keyframes.get(i);
                    KeyframeVec3 next = (i < frameCount - 1) ? keyframes.get(i + 1) : keyframes.get(i);

                    double deltaTime = next.timeSeconds - previous.timeSeconds;
                    if (deltaTime == 0.0) {
                        current.tangent = Vec3.ZERO;
                        continue;
                    }

                    Vec3 deltaValue = new Vec3(
                            next.value.x - previous.value.x,
                            next.value.y - previous.value.y,
                            next.value.z - previous.value.z
                    );

                    if (tension == 0.0 && continuity == 0.0 && bias == 0.0) {
                        current.tangent = new Vec3(deltaValue.x / 2.0 / deltaTime, deltaValue.y / 2.0 / deltaTime, deltaValue.z / 2.0 / deltaTime);
                    } else {
                        double dtPrev = current.timeSeconds - previous.timeSeconds;
                        double dtNext = next.timeSeconds - current.timeSeconds;

                        Vec3 derivativePrev = dtPrev > 0
                                ? new Vec3((current.value.x - previous.value.x) / dtPrev,
                                (current.value.y - previous.value.y) / dtPrev,
                                (current.value.z - previous.value.z) / dtPrev)
                                : Vec3.ZERO;

                        Vec3 derivativeNext = dtNext > 0
                                ? new Vec3((next.value.x - current.value.x) / dtNext,
                                (next.value.y - current.value.y) / dtNext,
                                (next.value.z - current.value.z) / dtNext)
                                : Vec3.ZERO;

                        double k1 = (1 - tension) * (1 + continuity) * (1 + bias) / 2.0;
                        double k2 = (1 - tension) * (1 - continuity) * (1 - bias) / 2.0;

                        current.tangent = new Vec3(
                                k1 * derivativePrev.x + k2 * derivativeNext.x,
                                k1 * derivativePrev.y + k2 * derivativeNext.y,
                                k1 * derivativePrev.z + k2 * derivativeNext.z
                        );
                    }
                }
            }
        }
    }


    public void evaluateAt(double timeSeconds, Interpolation timelineDefaultInterpolation, Easing timelineDefaultEasing, boolean computeTangentsForTimeline) {
        if (keyframes.isEmpty()) {
            boundConsumer.accept(new Vec3(0.0, 0.0, 0.0));
            return;
        }

        if (timeSeconds <= keyframes.getFirst().timeSeconds) {
            boundConsumer.accept(keyframes.getFirst().value);
            return;
        }

        if (timeSeconds >= keyframes.getLast().timeSeconds) {
            boundConsumer.accept(keyframes.getLast().value);
            return;
        }

        KeyframeVec3 leftFrame = keyframes.getFirst();
        KeyframeVec3 rightFrame = keyframes.getLast();
        for (int i = 0; i < keyframes.size() - 1; i++) {
            KeyframeVec3 frameA = keyframes.get(i);
            KeyframeVec3 frameB = keyframes.get(i + 1);
            if (timeSeconds >= frameA.timeSeconds && timeSeconds <= frameB.timeSeconds) {
                leftFrame = frameA;
                rightFrame = frameB;
                break;
            }
        }

        double span = rightFrame.timeSeconds - leftFrame.timeSeconds;
        double t = span == 0.0 ? 0.0 : (timeSeconds - leftFrame.timeSeconds) / span;

        Interpolation segmentInterpolation = leftFrame.interpolation != null ? leftFrame.interpolation : (defaultInterpolation != null ? defaultInterpolation : timelineDefaultInterpolation);
        Easing easing = leftFrame.easing != null ? leftFrame.easing : (defaultEasing != null ? defaultEasing : timelineDefaultEasing);

        Vec3 output;
        switch (segmentInterpolation) {
            case STEP -> output = leftFrame.value;
            case LINEAR -> output = lerp(leftFrame.value, rightFrame.value, t);
            case EASE -> {
                double easedT = easing == null ? Easing.LINEAR.apply(t) : easing.apply(t);
                output = lerp(leftFrame.value, rightFrame.value, easedT);
            }
            case HERMITE -> {
                if (computeTangentsForTimeline) computeTangentsIfNeeded();
                Vec3 m0 = leftFrame.tangent.scale(span);
                Vec3 m1 = rightFrame.tangent.scale(span);

                double h00 = 2 * t * t * t - 3 * t * t + 1;
                double h10 = t * t * t - 2 * t * t + t;
                double h01 = -2 * t * t * t + 3 * t * t;
                double h11 = t * t * t - t * t;

                output = new Vec3(
                        h00 * leftFrame.value.x + h10 * m0.x + h01 * rightFrame.value.x + h11 * m1.x,
                        h00 * leftFrame.value.y + h10 * m0.y + h01 * rightFrame.value.y + h11 * m1.y,
                        h00 * leftFrame.value.z + h10 * m0.z + h01 * rightFrame.value.z + h11 * m1.z
                );
            }

            case null, default -> throw new IllegalStateException("Invalid interpolation type: " + segmentInterpolation);
        }

        boundConsumer.accept(output);
    }

    private static Vec3 lerp(Vec3 start, Vec3 end, double t) {
        return new Vec3(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t,
                start.z + (end.z - start.z) * t
        );
    }
}