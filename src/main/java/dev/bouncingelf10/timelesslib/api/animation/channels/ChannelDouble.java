package dev.bouncingelf10.timelesslib.api.animation.channels;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.api.animation.*;
import dev.bouncingelf10.timelesslib.api.animation.keyframes.KeyframeDouble;
import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.*;
import java.util.function.Consumer;

public class ChannelDouble {
    private final String name;
    private final List<KeyframeDouble> keyframes = new ArrayList<>();
    private Interpolation defaultInterpolation = Interpolation.EASE;
    private Easing defaultEasing = Easing.LINEAR;
    private Consumer<Double> boundConsumer = value -> {};
    private TimelessClock.TimeSource timeSource = TimelessClock.TimeSources.GAME_TIME;
    private boolean useTimelineTime = true;

    private double tension = 0.0;
    private double continuity = 0.0;
    private double bias = 0.0;

    private boolean tangentsDirty = true;

    public ChannelDouble(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String name() {
        return name;
    }

    public ChannelDouble keyframe(double timeSeconds, double value) {
        return addKeyframe(KeyframeDouble.of(timeSeconds, value));
    }

    public ChannelDouble keyframe(double timeSeconds, double value, Easing easing) {
        return addKeyframe(KeyframeDouble.of(timeSeconds, value, easing));
    }

    public ChannelDouble keyframe(double timeSeconds, double value, Interpolation interpolation) {
        return addKeyframe(KeyframeDouble.of(timeSeconds, value, interpolation));
    }

    public ChannelDouble keyframe(double timeSeconds, double value, Interpolation interpolation, Easing easing) {
        return addKeyframe(KeyframeDouble.of(timeSeconds, value, easing, interpolation));
    }

    public ChannelDouble keyframe(Duration duration, double value) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value);
    }

    public ChannelDouble keyframe(Duration duration, double value, Easing easing) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value, easing);
    }

    public ChannelDouble keyframe(Duration duration, double value, Interpolation interpolation) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value, interpolation);
    }

    public ChannelDouble keyframe(Duration duration, double value, Easing easing, Interpolation interpolation) {
        double seconds = duration.toNanos() / 1e9;
        return keyframe(seconds, value, interpolation, easing);
    }

    public ChannelDouble addKeyframe(KeyframeDouble keyframe) {
        keyframes.add(keyframe);
        keyframes.sort(Comparator.comparingDouble(k -> k.timeSeconds));
        tangentsDirty = true;
        return this;
    }

    public ChannelDouble timeSource(TimelessClock.TimeSource source) {
        this.useTimelineTime = false;
        this.timeSource = source;
        return this;
    }

    public ChannelDouble defaultInterpolation(Interpolation interpolation) {
        this.defaultInterpolation = Objects.requireNonNull(interpolation);
        return this;
    }

    public ChannelDouble defaultEasing(Easing easing) {
        this.defaultEasing = Objects.requireNonNull(easing);
        return this;
    }

    public ChannelDouble bind(Consumer<Double> consumer) {
        this.boundConsumer = Objects.requireNonNull(consumer);
        return this;
    }

    public ChannelDouble setTCB(double tension, double continuity, double bias) {
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
            keyframes.getFirst().tangent = 0.0;
            return;
        }

        for (int i = 0; i < frameCount; i++) {
            KeyframeDouble previous = (i > 0) ? keyframes.get(i - 1) : keyframes.get(i);
            KeyframeDouble current = keyframes.get(i);
            KeyframeDouble next = (i < frameCount - 1) ? keyframes.get(i + 1) : keyframes.get(i);

            double deltaTime = next.timeSeconds - previous.timeSeconds;
            if (deltaTime == 0.0) {
                current.tangent = 0.0;
                continue;
            }

            double deltaValue = next.value - previous.value;

            if (tension == 0.0 && continuity == 0.0 && bias == 0.0) {
                current.tangent = 0.5 * (deltaValue / deltaTime);
            } else {
                double deltaTimePrev = current.timeSeconds - previous.timeSeconds;
                double deltaTimeNext = next.timeSeconds - current.timeSeconds;
                double deltaValuePrev = current.value - previous.value;
                double deltaValueNext = next.value - current.value;

                double derivativePrev = deltaTimePrev > 0 ? deltaValuePrev / deltaTimePrev : 0.0;
                double derivativeNext = deltaTimeNext > 0 ? deltaValueNext / deltaTimeNext : 0.0;

                current.tangent = (1 - tension) * ((1 + continuity) * (1 + bias) * derivativePrev / 2.0
                        + (1 - continuity) * (1 - bias) * derivativeNext / 2.0);
            }
        }
    }

    public void evaluateAt(double timeSeconds, Interpolation timelineDefaultInterpolation, Easing timelineDefaultEasing, boolean computeTangentsForTimeline) {
        double effectiveTime = useTimelineTime ? timeSeconds : (timeSource.now() / 1e9);

        if (keyframes.isEmpty()) {
            boundConsumer.accept(0.0);
            return;
        }

        if (effectiveTime <= keyframes.getFirst().timeSeconds) {
            boundConsumer.accept(keyframes.getFirst().value);
            return;
        }

        if (effectiveTime >= keyframes.getLast().timeSeconds) {
            boundConsumer.accept(keyframes.getLast().value);
            return;
        }

        KeyframeDouble leftFrame = keyframes.getFirst();
        KeyframeDouble rightFrame = keyframes.getLast();
        for (int i = 0; i < keyframes.size() - 1; i++) {
            KeyframeDouble frameA = keyframes.get(i);
            KeyframeDouble frameB = keyframes.get(i + 1);
            if (effectiveTime >= frameA.timeSeconds && effectiveTime <= frameB.timeSeconds) {
                leftFrame = frameA;
                rightFrame = frameB;
                break;
            }
        }

        double span = rightFrame.timeSeconds - leftFrame.timeSeconds;
        double t = span == 0.0 ? 0.0 : (effectiveTime - leftFrame.timeSeconds) / span;

        Interpolation segmentInterpolation = leftFrame.interpolation != null ? leftFrame.interpolation : (defaultInterpolation != null ? defaultInterpolation : timelineDefaultInterpolation);
        Easing easing = leftFrame.easing != null ? leftFrame.easing : (defaultEasing != null ? defaultEasing : timelineDefaultEasing);

        double outputValue;
        switch (segmentInterpolation) {
            case STEP -> outputValue = leftFrame.value;
            case LINEAR -> outputValue = lerp(leftFrame.value, rightFrame.value, t);
            case EASE -> {
                double easedT = easing == null ? Easing.LINEAR.apply(t) : easing.apply(t);
                outputValue = lerp(leftFrame.value, rightFrame.value, easedT);
            }
            case HERMITE -> {
                if (computeTangentsForTimeline) computeTangentsIfNeeded();
                double m0 = leftFrame.tangent * span;
                double m1 = rightFrame.tangent * span;

                double h00 = 2 * t * t * t - 3 * t * t + 1;
                double h10 = t * t * t - 2 * t * t + t;
                double h01 = -2 * t * t * t + 3 * t * t;
                double h11 = t * t * t - t * t;

                outputValue = h00 * leftFrame.value + h10 * m0 + h01 * rightFrame.value + h11 * m1;
            }
            case null, default -> throw new IllegalStateException("Invalid interpolation type: " + segmentInterpolation);
        }

        boundConsumer.accept(outputValue);
    }

    private static double lerp(double start, double end, double t) {
        return start + (end - start) * t;
    }
}