package dev.bouncingelf10.timelesslib.api.animation.channels;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.api.animation.*;
import dev.bouncingelf10.timelesslib.api.animation.keyframes.KeyframeDouble;
import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.*;
import java.util.function.Consumer;

public class ChannelDouble {
    private final String name;
    private final List<KeyframeDouble> keys = new ArrayList<>();
    private Interpolation defaultInterpolation = Interpolation.EASE; // timeline default typically overrides
    private Easing defaultEasing = Easing.LINEAR;
    private Consumer<Double> bound = d -> {};
    private TimelessClock.TimeSource timeSource = TimelessClock.TimeSources.GAME_TIME;
    private boolean useTimelineTime = true;

    private double tension = 0.0;
    private double continuity = 0.0;
    private double bias = 0.0;

    private boolean tangentsDirty = true;

    public ChannelDouble(String name) { this.name = Objects.requireNonNull(name); }

    public String name() { return name; }

    public ChannelDouble keyframe(double timeSeconds, double value) {
        return keyframe(KeyframeDouble.of(timeSeconds, value));
    }
    public ChannelDouble keyframe(double timeSeconds, double value, Easing easing) {
        return keyframe(KeyframeDouble.of(timeSeconds, value, easing));
    }
    public ChannelDouble keyframe(double timeSeconds, double value, Interpolation interp) {
        return keyframe(KeyframeDouble.of(timeSeconds, value, interp));
    }
    public ChannelDouble keyframe(double timeSeconds, double value, Interpolation interp, Easing easing) {
        return keyframe(KeyframeDouble.of(timeSeconds, value, easing, interp));
    }

    public ChannelDouble keyframe(Duration d, double value) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value);
    }
    public ChannelDouble keyframe(Duration d, double value, Easing easing) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value, easing);
    }
    public ChannelDouble keyframe(Duration d, double value, Interpolation interp) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value, interp);
    }
    public ChannelDouble keyframe(Duration d, double value, Easing easing, Interpolation interp) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value, interp, easing);
    }

    public ChannelDouble keyframe(KeyframeDouble k) {
        keys.add(k);
        keys.sort(Comparator.comparingDouble(kf -> kf.timeSeconds));
        tangentsDirty = true;
        return this;
    }

    public ChannelDouble timeSource(TimelessClock.TimeSource src) {
        this.useTimelineTime = false;
        this.timeSource = src;
        return this;
    }

    public ChannelDouble defaultInterpolation(Interpolation i) { this.defaultInterpolation = Objects.requireNonNull(i); return this; }
    public ChannelDouble defaultEasing(Easing e) { this.defaultEasing = Objects.requireNonNull(e); return this; }
    public ChannelDouble bind(Consumer<Double> c) { this.bound = Objects.requireNonNull(c); return this; }

    public ChannelDouble setTCB(double tension, double continuity, double bias) {
        this.tension = tension; this.continuity = continuity; this.bias = bias;
        tangentsDirty = true;
        return this;
    }

    public double computeDurationSeconds() {
        if (keys.isEmpty()) return 0.0;
        return keys.getLast().timeSeconds;
    }

    public void computeTangentsIfNeeded() {
        if (!tangentsDirty) return;
        tangentsDirty = false;
        int n = keys.size();
        if (n == 0) return;
        if (n == 1) { keys.getFirst().tangent = 0.0; return; }

        for (int i = 0; i < n; i++) {
            KeyframeDouble prev = (i > 0) ? keys.get(i - 1) : keys.get(i);
            KeyframeDouble cur  = keys.get(i);
            KeyframeDouble next = (i < n - 1) ? keys.get(i + 1) : keys.get(i);

            double dt = next.timeSeconds - prev.timeSeconds;
            if (dt == 0) {
                cur.tangent = 0.0;
                continue;
            }

            double dv = next.value - prev.value;

            if (tension == 0.0 && continuity == 0.0 && bias == 0.0) {
                cur.tangent = 0.5 * (dv / dt);
            } else {
                double dt1 = cur.timeSeconds - prev.timeSeconds;
                double dt2 = next.timeSeconds - cur.timeSeconds;
                double dv1 = cur.value - prev.value;
                double dv2 = next.value - cur.value;

                double d1 = dt1 > 0 ? dv1 / dt1 : 0.0;
                double d2 = dt2 > 0 ? dv2 / dt2 : 0.0;

                double t = tension, c = continuity, b = bias;

                cur.tangent = (1 - t) * ( (1 + c) * (1 + b) * d1 / 2.0 + (1 - c) * (1 - b) * d2 / 2.0 );
            }
        }
    }

    public void evaluateAt(double timeSeconds, Interpolation timelineDefaultInterp, Easing timelineDefaultEasing, boolean timelineComputeTangents) {
        double effectiveTime = useTimelineTime ? timeSeconds : (timeSource.now() / 1e9);

        if (keys.isEmpty()) {
            bound.accept(0.0);
            return;
        }
        if (effectiveTime <= keys.getFirst().timeSeconds) {
            bound.accept(keys.getFirst().value);
            return;
        }
        if (effectiveTime >= keys.getLast().timeSeconds) {
            bound.accept(keys.getLast().value);
            return;
        }

        KeyframeDouble left = keys.getFirst(), right = keys.getLast();
        for (int i = 0; i < keys.size()-1; i++) {
            KeyframeDouble a = keys.get(i);
            KeyframeDouble b = keys.get(i+1);
            if (effectiveTime >= a.timeSeconds && effectiveTime <= b.timeSeconds) { left = a; right = b; break; }
        }

        double span = right.timeSeconds - left.timeSeconds;
        double t = span == 0 ? 0.0 : (effectiveTime - left.timeSeconds) / span;

        Interpolation segmentInterp = left.interpolation != null ? left.interpolation : (defaultInterpolation != null ? defaultInterpolation : timelineDefaultInterp);
        Easing easing = left.easing != null ? left.easing : (defaultEasing != null ? defaultEasing : timelineDefaultEasing);

        double out;
        switch (segmentInterp) {
            case STEP:
                out = left.value;
                break;
            case LINEAR:
                out = lerp(left.value, right.value, t);
                break;
            case EASE:
                double e = easing == null ? Easing.LINEAR.apply(t) : easing.apply(t);
                out = lerp(left.value, right.value, e);
                break;
            case HERMITE:
            default:
                if (timelineComputeTangents) computeTangentsIfNeeded();
                double m0 = left.tangent * span;
                double m1 = right.tangent * span;
                double h00 = 2*t*t*t - 3*t*t + 1;
                double h10 = t*t*t - 2*t*t + t;
                double h01 = -2*t*t*t + 3*t*t;
                double h11 = t*t*t - t*t;
                out = h00*left.value + h10*m0 + h01*right.value + h11*m1;
                break;
        }
        bound.accept(out);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}