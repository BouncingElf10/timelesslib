package dev.bouncingelf10.timelesslib.api.time;

import dev.bouncingelf10.timelesslib.TimelessClock;
import java.util.Objects;

public class TimeAnchor {

    private long startNano;
    private long accumulatedNanos;
    private boolean paused;
    private final TimelessClock.TimeSource timeSource;

    public TimeAnchor() {
        this(TimelessClock.TimeSources.GAME_TIME);
    }

    public TimeAnchor(TimelessClock.TimeSource timeSource) {
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

    public double elapsed(DurationUnit unit) {
        return unit.from(elapsedNanos());
    }

    public long elapsedTicks() { return (long) elapsed(DurationUnit.TICKS); }
    public double elapsedMillis() { return elapsed(DurationUnit.MILLISECONDS); }
    public double elapsedSeconds() { return elapsed(DurationUnit.SECONDS); }
    public double elapsedMinutes() { return elapsed(DurationUnit.MINUTES); }

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
        return Duration.ofNanos(Math.max(0, target.toNanos() - elapsedNanos()));
    }

    public long remaining(long amount, DurationUnit unit) {
        return remaining(Duration.of(amount, unit)).toNanos();
    }

    public double remaining(long targetAmount, DurationUnit targetUnit, DurationUnit returnUnit) {
        return returnUnit.from(remaining(targetAmount, targetUnit));
    }

    public void pause() {
        if (!paused) {
            accumulatedNanos += currentNano() - startNano;
            paused = true;
        }
    }

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

    public Duration snapshot() {
        return Duration.ofNanos(elapsedNanos());
    }

    public Duration elapsedSince(Duration snapshot) {
        return elapsed().minus(snapshot);
    }

    public TimelessClock.TimeSource getTimeSource() {
        return timeSource;
    }

    @Override
    public String toString() {
        return TimeFormatter.format(elapsedNanos(), TimeFormatter.TimeFormat.COMPACT);
    }

    public String toString(TimeFormatter.TimeFormat format) {
        return TimeFormatter.format(elapsedNanos(), format);
    }

    public static TimeAnchor createGameTime() {
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
