package com.bouncingelf10.timelesslib.api.clock;

public final class TimeSources {
    /**
     * Returns the current game time in nanoseconds.<br>
     * Note: "Game time" is the time since the game started and will NOT change when the client is paused.
     *
     * @see TimelessClock#isPaused()
     */
    public static final TimeSource GAME_TIME = TimelessClock::gameTime;
    /**
     * Returns the current real time in nanoseconds.
     *
     * @see System#nanoTime()
     */
    public static final TimeSource REAL_TIME = TimelessClock::realTime;

    private TimeSources() {
    }
}
