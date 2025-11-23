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
    private final List<KeyframeVec3> keys = new ArrayList<>();
    private Interpolation defaultInterpolation = Interpolation.EASE;
    private Easing defaultEasing = Easing.LINEAR;
    private Consumer<Vec3> bound = v -> {};
    private boolean tangentsDirty = true;
    private TimelessClock.TimeSource timeSource = TimelessClock.TimeSources.GAME_TIME;
    private boolean useTimelineTime = true;

    private double tension = 0.0;
    private double continuity = 0.0;
    private double bias = 0.0;

    public ChannelVec3(String name) { this.name = Objects.requireNonNull(name); }
    public String name() { return name; }

    public ChannelVec3 keyframe(double timeSeconds, Vec3 value) {
        return keyframe(KeyframeVec3.of(timeSeconds, value));
    }
    public ChannelVec3 keyframe(double timeSeconds, Vec3 value, Easing easing) {
        return keyframe(KeyframeVec3.of(timeSeconds, value, easing));
    }
    public ChannelVec3 keyframe(double timeSeconds, Vec3 value, Interpolation interp) {
        return keyframe(KeyframeVec3.of(timeSeconds, value, interp));
    }
    public ChannelVec3 keyframe(double timeSeconds, Vec3 value, Interpolation interp, Easing easing) {
        return keyframe(KeyframeVec3.of(timeSeconds, value, easing, interp));
    }

    public ChannelVec3 keyframe(Duration d, Vec3 value) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value);
    }
    public ChannelVec3 keyframe(Duration d, Vec3 value, Easing easing) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value, easing);
    }
    public ChannelVec3 keyframe(Duration d, Vec3 value, Interpolation interp) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value, interp);
    }
    public ChannelVec3 keyframe(Duration d, Vec3 value, Easing easing, Interpolation interp) {
        double s = d.toNanos() / 1e9;
        return keyframe(s, value, interp, easing);
    }

    public ChannelVec3 keyframe(KeyframeVec3 k) {
        keys.add(k);
        keys.sort(Comparator.comparingDouble(kf -> kf.timeSeconds));
        tangentsDirty = true;
        return this;
    }

    public ChannelVec3 timeSource(TimelessClock.TimeSource src) {
        this.useTimelineTime = false;
        this.timeSource = src;
        return this;
    }

    public ChannelVec3 defaultInterpolation(Interpolation i) { this.defaultInterpolation = Objects.requireNonNull(i); return this; }
    public ChannelVec3 defaultEasing(Easing e) { this.defaultEasing = Objects.requireNonNull(e); return this; }
    public ChannelVec3 bind(Consumer<Vec3> c) { this.bound = Objects.requireNonNull(c); return this; }
    public ChannelVec3 setTCB(double tension, double continuity, double bias) { this.tension = tension; this.continuity = continuity; this.bias = bias; tangentsDirty = true; return this; }

    public double computeDurationSeconds() {
        if (keys.isEmpty()) return 0.0;
        return keys.getLast().timeSeconds;
    }

    public void computeTangentsIfNeeded() {
        if (!tangentsDirty) return;
        tangentsDirty = false;
        int n = keys.size();
        if (n == 0) return;
        if (n == 1) { keys.getFirst().tangent = Vec3.ZERO; return; }

        for (int i = 0; i < n; i++) {
            KeyframeVec3 prev = (i > 0) ? keys.get(i - 1) : keys.get(i);
            KeyframeVec3 cur  = keys.get(i);
            KeyframeVec3 next = (i < n - 1) ? keys.get(i + 1) : keys.get(i);

            double dt = next.timeSeconds - prev.timeSeconds;
            if (dt == 0) { cur.tangent = Vec3.ZERO; continue; }

            Vec3 dv = new Vec3(next.value.x - prev.value.x, next.value.y - prev.value.y, next.value.z - prev.value.z);

            if (tension == 0.0 && continuity == 0.0 && bias == 0.0) {
                cur.tangent = new Vec3(0.5 * dv.x / dt, 0.5 * dv.y / dt, 0.5 * dv.z / dt);
            } else {
                double dt1 = cur.timeSeconds - prev.timeSeconds;
                double dt2 = next.timeSeconds - cur.timeSeconds;
                Vec3 d1 = dt1 > 0 ? new Vec3( (cur.value.x - prev.value.x)/dt1, (cur.value.y - prev.value.y)/dt1, (cur.value.z - prev.value.z)/dt1 ) : Vec3.ZERO;
                Vec3 d2 = dt2 > 0 ? new Vec3( (next.value.x - cur.value.x)/dt2, (next.value.y - cur.value.y)/dt2, (next.value.z - cur.value.z)/dt2 ) : Vec3.ZERO;

                double t = tension, c = continuity, b = bias;
                double k1 = (1 - t) * (1 + c) * (1 + b) / 2.0;
                double k2 = (1 - t) * (1 - c) * (1 - b) / 2.0;

                cur.tangent = new Vec3(k1 * d1.x + k2 * d2.x, k1 * d1.y + k2 * d2.y, k1 * d1.z + k2 * d2.z);
            }
        }
    }

    public void evaluateAt(double timeSeconds, Interpolation timelineDefaultInterp, Easing timelineDefaultEasing, boolean timelineComputeTangents) {
        if (keys.isEmpty()) { bound.accept(Vec3.ZERO); return; }
        if (timeSeconds <= keys.getFirst().timeSeconds) { bound.accept(keys.getFirst().value); return; }
        if (timeSeconds >= keys.getLast().timeSeconds) { bound.accept(keys.getLast().value); return; }

        KeyframeVec3 left = keys.getFirst(), right = keys.getLast();
        for (int i = 0; i < keys.size()-1; i++) {
            KeyframeVec3 a = keys.get(i);
            KeyframeVec3 b = keys.get(i+1);
            if (timeSeconds >= a.timeSeconds && timeSeconds <= b.timeSeconds) { left = a; right = b; break; }
        }

        double span = right.timeSeconds - left.timeSeconds;
        double t = span == 0 ? 0.0 : (timeSeconds - left.timeSeconds) / span;

        Interpolation segmentInterp = left.interpolation != null ? left.interpolation : (defaultInterpolation != null ? defaultInterpolation : timelineDefaultInterp);
        Easing easing = left.easing != null ? left.easing : (defaultEasing != null ? defaultEasing : timelineDefaultEasing);

        Vec3 out;
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
                Vec3 m0 = left.tangent.scale(span);
                Vec3 m1 = right.tangent.scale(span);
                double h00 = 2*t*t*t - 3*t*t + 1;
                double h10 = t*t*t - 2*t*t + t;
                double h01 = -2*t*t*t + 3*t*t;
                double h11 = t*t*t - t*t;
                out = new Vec3(
                        h00*left.value.x + h10*m0.x + h01*right.value.x + h11*m1.x,
                        h00*left.value.y + h10*m0.y + h01*right.value.y + h11*m1.y,
                        h00*left.value.z + h10*m0.z + h01*right.value.z + h11*m1.z
                );
                break;
        }
        bound.accept(out);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }
}