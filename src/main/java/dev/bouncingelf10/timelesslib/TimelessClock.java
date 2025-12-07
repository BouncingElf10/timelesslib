package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;

public final class TimelessClock {
    private static long lastRealTime = System.nanoTime();
    private static long gameNanos = 0;
    private static long delta = 0;
    private static boolean paused = false;

    public static void update() {
        long now = System.nanoTime();

        if (!TimelessFabricHelper.shouldAdvanceTime()) {
            paused = true;
            lastRealTime = now;
            return;
        }

        delta = now - lastRealTime;
        lastRealTime = now;

        if (!paused) {
            gameNanos += delta;
        }

        paused = false;
    }

    public static boolean isPaused() { return paused; }
    public static long deltaNanos() { return delta; }
    public static double deltaSeconds() { return delta / 1_000_000_000.0; }

    public static long gameTime() { return gameNanos; }
    public static long realTime() { return System.nanoTime(); }

    @FunctionalInterface
    public interface TimeSource {
        long now();
    }

    public static final class TimeSources {
        public static final TimeSource GAME_TIME = TimelessClock::gameTime;
        public static final TimeSource REAL_TIME = TimelessClock::realTime;

        private TimeSources() {}
    }
}
