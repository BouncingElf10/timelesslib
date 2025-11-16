package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.platform.TimelessPlatform;

public final class TimelessClock {
    private static long lastTime = System.nanoTime();
    private static long deltaNanos = 0;
    private static boolean paused = false;

    public static void update() {
        if (!TimelessPlatform.INSTANCE.shouldAdvanceTime()) {
            paused = true;
            deltaNanos = 0;
            return;
        }

        long now = System.nanoTime();
        deltaNanos = now - lastTime;
        lastTime = now;
        paused = false;
    }

    public static boolean isPaused() { return paused; }
    public static long deltaNanos() { return deltaNanos; }
    public static double deltaSeconds() { return deltaNanos / 1_000_000_000.0; }
    public static long now() { return lastTime; }
}
