package dev.bouncingelf10.timelesslib.api.time;

import dev.bouncingelf10.timelesslib.TimelessClock;

public class TimeAnchor {
    private long startNano;
    private long accumulatedNanos;
    private long pausedAtNanos;
    private boolean isPaused;
    private final TimelessClock.TimeSource timeSource;

    public TimeAnchor() {
        this(TimelessClock.TimeSources.GAME_TIME);
    }

    public TimeAnchor(TimelessClock.TimeSource timeSource) {
        this.timeSource = timeSource;
        this.startNano = getCurrentTime();
        this.accumulatedNanos = 0;
        this.pausedAtNanos = 0;
        this.isPaused = false;
    }

    private long getCurrentTime() {
        return timeSource.now();
    }

    public long elapsedNanos() {
        if (isPaused) {
            return accumulatedNanos;
        }
        return accumulatedNanos + (getCurrentTime() - startNano);
    }

    public Duration elapsed() {
        return Duration.ofNanos(elapsedNanos());
    }

    public double elapsed(DurationUnit unit) {
        return unit.from(elapsedNanos());
    }

    public int elapsedTicks() {
        return (int) elapsed(DurationUnit.TICKS);
    }

    public double elapsedMillis() {
        return elapsed(DurationUnit.MILLISECONDS);
    }

    public double elapsedSeconds() {
        return elapsed(DurationUnit.SECONDS);
    }

    public double elapsedMinutes() {
        return elapsed(DurationUnit.MINUTES);
    }

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

    public Duration remaining(Duration target) {
        long targetNanos = target.toNanos();
        long elapsed = elapsedNanos();
        return Duration.ofNanos(Math.max(0, targetNanos - elapsed));
    }

    public long remaining(long amount, DurationUnit unit) {
        return remaining(Duration.of(amount, unit)).toNanos();
    }

    public double remaining(long targetAmount, DurationUnit targetUnit, DurationUnit returnUnit) {
        return returnUnit.from(remaining(targetAmount, targetUnit));
    }

    public void pause() {
        if (!isPaused) {
            accumulatedNanos += getCurrentTime() - startNano;
            pausedAtNanos = getCurrentTime();
            isPaused = true;
        }
    }

    public void resume() {
        if (isPaused) {
            startNano = getCurrentTime();
            isPaused = false;
            pausedAtNanos = 0;
        }
    }

    public boolean isPaused() {
        return isPaused;
    }

    public TimelessClock.TimeSource getTimeSource() {
        return timeSource;
    }

    public void reset() {
        this.startNano = getCurrentTime();
        this.accumulatedNanos = 0;
        this.pausedAtNanos = 0;
        this.isPaused = false;
    }

    public void resetAndPause() {
        reset();
        pause();
    }

    public Duration snapshot() {
        return Duration.ofNanos(elapsedNanos());
    }

    public Duration sinceSnapshot(Duration previousSnapshot) {
        return elapsed().minus(previousSnapshot);
    }

    @Deprecated
    public long snapshotNanos() {
        return elapsedNanos();
    }

    @Deprecated
    public long sinceSnapshotNanos(long previousSnapshot) {
        return elapsedNanos() - previousSnapshot;
    }

    @Override
    public String toString() {
        return TimeFormatter.format(elapsedNanos(), TimeFormatter.TimeFormat.COMPACT);
    }

    public String toString(TimeFormatter.TimeFormat format) {
        return TimeFormatter.format(elapsedNanos(), format);
    }

    public static TimeAnchor create() {
        return new TimeAnchor(TimelessClock.TimeSources.GAME_TIME);
    }

    public static TimeAnchor createRealTime() {
        return new TimeAnchor(TimelessClock.TimeSources.REAL_TIME);
    }

    public static TimeAnchor createPaused() {
        return createPaused(TimelessClock.TimeSources.GAME_TIME);
    }

    public static TimeAnchor createPaused(TimelessClock.TimeSource source) {
        TimeAnchor anchor = new TimeAnchor(source);
        anchor.pause();
        return anchor;
    }
}