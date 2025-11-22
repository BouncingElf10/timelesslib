package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;

public final class TimelessClock {
    private static long lastTime = System.nanoTime();
    private static long deltaNanos = 0;
    private static boolean paused = false;
    private static TimeSource timeSource = System::nanoTime;

    public static void update() {
        long now = timeSource.now();

        if (!TimelessFabricHelper.shouldAdvanceTime()) {
            paused = true;
            deltaNanos = 0;

            lastTime = now;
            return;
        }

        deltaNanos = now - lastTime;
        lastTime = now;
        paused = false;
    }

    public static boolean isPaused() { return paused; }
    public static long deltaNanos() { return deltaNanos; }
    public static double deltaSeconds() { return deltaNanos / 1_000_000_000.0; }

    public static long gameTime() { return lastTime; }
    public static long realTime() { return System.nanoTime(); }

    public static void reset() {
        lastTime = timeSource.now();
        deltaNanos = 0;
        paused = false;
    }

    public static void setTimeSource(TimeSource source) {
        if (source != null) {
            timeSource = source;
            reset();
        }
    }

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
