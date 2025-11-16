package dev.bouncingelf10.timelesslib.api.time;

import dev.bouncingelf10.timelesslib.TimelessClock;

/**
 * A time measurement utility that captures a point in time and measures elapsed duration.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Respects game pause
 * TimeAnchor gameTimer = TimeAnchor.create();
 *
 * // Always runs, even when game is paused
 * TimeAnchor realTimer = TimeAnchor.createRealTime();
 *
 * // Check if 5 seconds have passed
 * if (timer.hasElapsed(5, DurationUnit.SECONDS)) {
 *     // Do something
 * }
 *
 * // Get elapsed time in different units
 * long ticks = timer.elapsedTicks();
 * long millis = timer.elapsed(DurationUnit.MILLISECONDS);
 *
 * // Pause and resume
 * timer.pause();
 * // ... some time passes ...
 * timer.resume();
 *
 * // Reset to current time
 * timer.reset();
 * }</pre>
 *
 * @see DurationUnit
 */
public class TimeAnchor {
    private long startNano;
    private long accumulatedNanos;
    private long pausedAtNanos;
    private boolean isPaused;
    private final boolean respectsPlatformPause;

    /**
     * Creates a new TimeAnchor starting at the current time.
     * By default, respects the platform's pause state (e.g., game pause).
     */
    public TimeAnchor() {
        this(true);
    }

    /**
     * Creates a new TimeAnchor with specified pause behavior.
     *
     * @param respectsPlatformPause if true, uses TimelessClock (respects game pause);
     *                              if false, uses System.nanoTime() (always runs)
     */
    public TimeAnchor(boolean respectsPlatformPause) {
        this.respectsPlatformPause = respectsPlatformPause;
        this.startNano = getCurrentTime();
        this.accumulatedNanos = 0;
        this.pausedAtNanos = 0;
        this.isPaused = false;
    }

    /**
     * Gets the current time based on this anchor's pause behavior.
     *
     * @return current time in nanoseconds
     */
    private long getCurrentTime() {
        return respectsPlatformPause ? TimelessClock.now() : System.nanoTime();
    }

    /**
     * Returns the elapsed time in nanoseconds since this anchor was created or last reset,
     * excluding any time spent paused.
     * <p>
     * Note: If this anchor respects platform pause, time does not advance when
     * the TimelessClock is paused (e.g., when the game is paused).
     * </p>
     *
     * @return the elapsed time in nanoseconds
     */
    public long elapsedNanos() {
        if (isPaused) {
            return accumulatedNanos;
        }
        return accumulatedNanos + (getCurrentTime() - startNano);
    }

    /**
     * Returns the elapsed time in the specified duration unit.
     * <p>
     * For example, {@code elapsed(DurationUnit.SECONDS)} returns the elapsed seconds.
     * </p>
     *
     * @param unit the unit to convert the elapsed time to
     * @return the elapsed time in the specified unit
     */
    public double elapsed(DurationUnit unit) {
        return unit.from(elapsedNanos());
    }

    /**
     * Returns the elapsed time in Minecraft game ticks (20 ticks per second).
     *
     * @return the elapsed time in ticks
     */
    public int elapsedTicks() {
        return (int) elapsed(DurationUnit.TICKS);
    }

    /**
     * Returns the elapsed time in milliseconds.
     *
     * @return the elapsed time in milliseconds
     */
    public double elapsedMillis() {
        return elapsed(DurationUnit.MILLISECONDS);
    }

    /**
     * Returns the elapsed time in seconds.
     *
     * @return the elapsed time in seconds
     */
    public double elapsedSeconds() {
        return elapsed(DurationUnit.SECONDS);
    }

    /**
     * Returns the elapsed time in minutes.
     *
     * @return the elapsed time in minutes
     */
    public double elapsedMinutes() {
        return elapsed(DurationUnit.MINUTES);
    }

    /**
     * Checks if the specified amount of time has elapsed.
     * <p>
     * For example, {@code hasElapsed(5, DurationUnit.SECONDS)} returns true if
     * 5 or more seconds have passed.
     * </p>
     *
     * @param amount the amount of time to check
     * @param unit the unit of time
     * @return true if the specified duration has elapsed, false otherwise
     */
    public boolean hasElapsed(long amount, DurationUnit unit) {
        return elapsedNanos() >= unit.toNanos(amount);
    }

    /**
     * Checks if the specified number of ticks has elapsed.
     *
     * @param ticks the number of Minecraft ticks to check
     * @return true if the specified ticks have elapsed, false otherwise
     */
    public boolean hasElapsedTicks(long ticks) {
        return hasElapsed(ticks, DurationUnit.TICKS);
    }

    /**
     * Checks if the specified number of seconds has elapsed.
     *
     * @param seconds the number of seconds to check
     * @return true if the specified seconds have elapsed, false otherwise
     */
    public boolean hasElapsedSeconds(long seconds) {
        return hasElapsed(seconds, DurationUnit.SECONDS);
    }

    /**
     * Returns the remaining time until the specified duration is reached.
     * If the duration has already elapsed, returns 0.
     *
     * @param amount the target duration amount
     * @param unit the unit of the target duration
     * @return the remaining time in nanoseconds, or 0 if the duration has elapsed
     */
    public long remaining(long amount, DurationUnit unit) {
        long targetNanos = unit.toNanos(amount);
        long elapsed = elapsedNanos();
        return Math.max(0, targetNanos - elapsed);
    }

    /**
     * Returns the remaining time in the specified unit until the target duration is reached.
     *
     * @param targetAmount the target duration amount
     * @param targetUnit the unit of the target duration
     * @param returnUnit the unit to return the remaining time in
     * @return the remaining time in the specified return unit, or 0 if elapsed
     */
    public double remaining(long targetAmount, DurationUnit targetUnit, DurationUnit returnUnit) {
        return returnUnit.from(remaining(targetAmount, targetUnit));
    }

    /**
     * Pauses the time measurement. While paused, elapsed time will not increase.
     * If already paused, this method has no effect.
     * <p>
     * Note: This is independent of the TimelessClock's paused state. This allows
     * you to pause individual timers even when the game is running.
     * </p>
     */
    public void pause() {
        if (!isPaused) {
            accumulatedNanos += getCurrentTime() - startNano;
            pausedAtNanos = getCurrentTime();
            isPaused = true;
        }
    }

    /**
     * Resumes time measurement after being paused.
     * If not currently paused, this method has no effect.
     */
    public void resume() {
        if (isPaused) {
            startNano = getCurrentTime();
            isPaused = false;
            pausedAtNanos = 0;
        }
    }

    /**
     * Returns whether this TimeAnchor is currently paused.
     *
     * @return true if paused, false otherwise
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * Returns whether this TimeAnchor respects the platform's pause state.
     *
     * @return true if respecting platform pause, false if using real-time
     */
    public boolean respectsPlatformPause() {
        return respectsPlatformPause;
    }

    /**
     * Resets this TimeAnchor to the current time, clearing all elapsed time and pause state.
     */
    public void reset() {
        this.startNano = getCurrentTime();
        this.accumulatedNanos = 0;
        this.pausedAtNanos = 0;
        this.isPaused = false;
    }

    /**
     * Resets this TimeAnchor and immediately pauses it.
     */
    public void resetAndPause() {
        reset();
        pause();
    }

    /**
     * Creates a snapshot of the current elapsed time that can be compared later.
     * This is useful for measuring time between specific events.
     *
     * @return the current elapsed time in nanoseconds
     */
    public long snapshot() {
        return elapsedNanos();
    }

    /**
     * Returns the elapsed time since a previous snapshot.
     *
     * @param previousSnapshot a snapshot value from {@link #snapshot()}
     * @return the elapsed time in nanoseconds since the snapshot
     */
    public long sinceSnapshot(long previousSnapshot) {
        return elapsedNanos() - previousSnapshot;
    }

    /**
     * Returns a string representation of the elapsed time since this anchor was created or last reset.
     * The elapsed time is formatted using a compact time format.
     *
     * @return a string representing the formatted elapsed time
     */
    @Override
    public String toString() {
        return TimeFormatter.format(elapsedNanos(), TimeFormatter.TimeFormat.COMPACT);
    }

    /**
     * Converts the elapsed time to a formatted string based on the specified format.
     *
     * @param format the desired format for representing the elapsed time
     * @return a string representing the elapsed time in the specified format
     */
    public String toString(TimeFormatter.TimeFormat format) {
        return TimeFormatter.format(elapsedNanos(), format);
    }

    /**
     * Creates and returns a new TimeAnchor starting at the current time.
     * This anchor respects the platform's pause state (e.g., game pause).
     *
     * @return a new TimeAnchor that respects platform pause
     */
    public static TimeAnchor create() {
        return new TimeAnchor(true);
    }

    /**
     * Creates and returns a new TimeAnchor that uses real-time measurement.
     * This anchor continues running even when the game is paused.
     * Useful for performance measurements, real-world timers, or UI animations.
     *
     * @return a new TimeAnchor that uses real-time
     */
    public static TimeAnchor createRealTime() {
        return new TimeAnchor(false);
    }

    /**
     * Creates and returns a new TimeAnchor that is immediately paused.
     * By default, respects the platform's pause state.
     *
     * @return a new paused TimeAnchor
     */
    public static TimeAnchor createPaused() {
        return createPaused(true);
    }

    /**
     * Creates and returns a new TimeAnchor that is immediately paused.
     *
     * @param respectsPlatformPause if true, respects game pause; if false, uses real-time
     * @return a new paused TimeAnchor
     */
    public static TimeAnchor createPaused(boolean respectsPlatformPause) {
        TimeAnchor anchor = new TimeAnchor(respectsPlatformPause);
        anchor.pause();
        return anchor;
    }
}