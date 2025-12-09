package dev.bouncingelf10.timelesslib.api.animation;

import dev.bouncingelf10.timelesslib.api.animation.channels.ChannelDouble;

public enum Interpolation {
    /**
     * Step interpolation, i.e., the value at the start of the keyframe is returned.
     */
    STEP,
    /**
     * Linear interpolation, i.e., the value is interpolated linearly between the keyframe values.
     */
    LINEAR,
    /**
     * Easing interpolation, i.e., the value is interpolated using an easing function.
     * @see Easing
     * @see ChannelDouble#defaultEasing(Easing)
     */
    EASE,
    /**
     * Catmull-Rom spline interpolation creating smooth curves with c1 continuity.
     */
    CATMULL
}