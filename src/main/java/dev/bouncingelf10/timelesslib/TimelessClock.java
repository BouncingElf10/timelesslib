package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;

public final class TimelessClock {
    private static long lastTime = System.nanoTime();
    private static long deltaNanos = 0;
    private static boolean paused = false;

    public static void update() {
        if (!TimelessFabricHelper.shouldAdvanceTime()) {
            paused = true;
            deltaNanos = 0;
            return;
        }

        long now = System.nanoTime();
        deltaNanos = now - lastTime;
        lastTime = now;
        paused = false;
    }

    /**
     * Returns whether the clock is currently paused.
     * The clock pauses when the platform indicates time should not advance.
     *
     * @return true if paused, false otherwise
     */
    public static boolean isPaused() { return paused; }

    /**
     * Returns the time elapsed since the last update in nanoseconds.
     * Returns 0 when the clock is paused.
     *
     * @return delta time in nanoseconds
     */
    public static long deltaNanos() { return deltaNanos; }

    /**
     * Returns the time elapsed since the last update in seconds.
     * Returns 0.0 when the clock is paused.
     *
     * @return delta time in seconds
     */
    public static double deltaSeconds() { return deltaNanos / 1_000_000_000.0; }

    /**
     * Returns the current game time in nanoseconds.
     * This time respects the platform's pause state and does not advance when paused.
     * <p>
     * @return current game time in nanoseconds
     */
    public static long now() { return lastTime; }

    /**
     * Returns the current real-world time in nanoseconds.
     * This time always advances, regardless of whether the game is paused.
     * @return current real-world time in nanoseconds
     */
    public static long realTime() { return System.nanoTime(); }
}