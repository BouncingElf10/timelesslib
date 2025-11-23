package dev.bouncingelf10.timelesslib.api.animation;

import dev.bouncingelf10.timelesslib.TimelessClock;

public final class AnimationClock {
    private static boolean enabled = true;

    public static void enable(boolean v) {
        enabled = v;
    }

    public static double deltaSeconds() {
        return enabled ? TimelessClock.deltaSeconds() : 0.0;
    }
    public static boolean shouldAdvance() {
        return enabled && !TimelessClock.isPaused();
    }
}
