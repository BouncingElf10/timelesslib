package com.bouncingelf10.timelesslib.api.time;

import com.bouncingelf10.timelesslib.api.clock.TimeSource;
import com.bouncingelf10.timelesslib.api.clock.TimeSources;

import java.util.Objects;

public class TimeAnchor {

    private long startNano;
    private long accumulatedNanos;
    private boolean paused;
    private final TimeSource timeSource;

    /**
     * Creates a new TimeAnchor that uses the game time.
     * @see TimeSources
     */
    public TimeAnchor() {
        this(TimeSources.GAME_TIME);
    }

    /**
     * Creates a new TimeAnchor that uses the specified time source.
     * @param timeSource Time source to use
     * @see TimeSources
     */
    public TimeAnchor(TimeSource timeSource) {
        this.timeSource = Objects.requireNonNull(timeSource, "TimeSource cannot be null");
        this.startNano = timeSource.now();
        this.accumulatedNanos = 0;
        this.paused = false;
    }

    private long currentNano() {
        return timeSource.now();
    }

    public long elapsedNanos() {
        return paused ? accumulatedNanos : accumulatedNanos + (currentNano() - startNano);
    }

    public Duration elapsed() {
        return Duration.ofNanos(elapsedNanos());
    }

    /**
     * Returns the elapsed time in the specified unit.
     */
    public double elapsed(DurationUnit unit) {
        return unit.from(elapsedNanos());
    }

    public long elapsedTicks() { return (long) elapsed(DurationUnit.TICKS); }
    public double elapsedMillis() { return elapsed(DurationUnit.MILLISECONDS); }
    public double elapsedSeconds() { return elapsed(DurationUnit.SECONDS); }
    public double elapsedMinutes() { return elapsed(DurationUnit.MINUTES); }

    /**
     * Returns true if the specified duration has elapsed.
     */
    public boolean hasElapsed(Duration duration) {
        return elapsedNanos() >= duration.toNanos();
    }

    public boolean hasElapsed(long amount, DurationUnit unit) {
        return hasElapsed(Duration.of(amount, unit));
    }

    public boolean hasElapsedTicks(long ticks) {
        return hasElapsed(Duration.ofTicks(ticks));
    }

    public boolean hasElapsedSeconds(long seconds) {
        return hasElapsed(Duration.ofSeconds(seconds));
    }

    /**
     * Returns the remaining time until the specified target.
     */
    public Duration remaining(Duration target) {
        return Duration.ofNanos(Math.max(0, target.toNanos() - elapsedNanos()));
    }

    public long remaining(long amount, DurationUnit unit) {
        return remaining(Duration.of(amount, unit)).toNanos();
    }

    public double remaining(long targetAmount, DurationUnit targetUnit, DurationUnit returnUnit) {
        return returnUnit.from(remaining(targetAmount, targetUnit));
    }

    /**
     * Pauses the anchor.<br>
     * Note: If the anchor is already paused, this method does nothing.
     */
    public void pause() {
        if (!paused) {
            accumulatedNanos += currentNano() - startNano;
            paused = true;
        }
    }

    /**
     * Resumes the anchor.<br>
     * Note: If the anchor is not paused, this method does nothing.
     */
    public void resume() {
        if (paused) {
            startNano = currentNano();
            paused = false;
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public void reset() {
        startNano = currentNano();
        accumulatedNanos = 0;
        paused = false;
    }

    public void resetAndPause() {
        reset();
        pause();
    }

    public void playOrReset() {
        if (this.isPaused()) {
            this.resume();
        } else {
            this.reset();
        }
    }

    public void pauseOrUnpause() {
        if (!this.isPaused()) {
            this.pause();
        } else {
            this.resume();
        }
    }

    /**
     * Creates a snapshot of the current time.
     */
    public Duration snapshot() {
        return Duration.ofNanos(elapsedNanos());
    }

    public Duration elapsedSince(Duration snapshot) {
        return elapsed().minus(snapshot);
    }

    public TimeSource getTimeSource() {
        return timeSource;
    }

    @Override
    public String toString() {
        return TimeFormatter.format(elapsedNanos(), TimeFormat.COMPACT);
    }

    public String toString(TimeFormat format) {
        return TimeFormatter.format(elapsedNanos(), format);
    }

    /**
     * Creates a new TimeAnchor that uses the game time.
     * @see TimeSources
     */
    public static TimeAnchor createGameTime() {
        return new TimeAnchor(TimeSources.GAME_TIME);
    }

    /**
     * Creates a new TimeAnchor that uses the real time.
     * @see TimeSources
     */
    public static TimeAnchor createRealTime() {
        return new TimeAnchor(TimeSources.REAL_TIME);
    }

    /**
     * Creates a new TimeAnchor that is paused and uses the game time.
     * @see TimeSources
     */
    public static TimeAnchor createPaused() {
        return createPaused(TimeSources.GAME_TIME);
    }

    /**
     * Creates a new TimeAnchor that is paused and uses the specified time source.
     * @see TimeSources
     */
    public static TimeAnchor createPaused(TimeSource source) {
        TimeAnchor anchor = new TimeAnchor(source);
        anchor.pause();
        return anchor;
    }
}
