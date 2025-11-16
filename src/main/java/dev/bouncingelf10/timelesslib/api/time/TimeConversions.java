package dev.bouncingelf10.timelesslib.api.time;

public final class TimeConversions {
    private TimeConversions() {}

    /**
     * Converts between time units.
     * <p>
     * For example: {@code convert(823, DurationUnit.SECONDS, DurationUnit.MINUTES)} returns 13.716666...
     * </p>
     */
    public static double convert(double amount, DurationUnit from, DurationUnit to) {
        return from.to(amount, to);
    }

    public static double nanosToTicks(long ns) { return DurationUnit.TICKS.from(ns); }
    public static double ticksToNanos(double ticks) { return DurationUnit.TICKS.toNanos(ticks); }
    public static double millisToTicks(long ms) { return DurationUnit.TICKS.from(ms * 1_000_000); }
    public static double ticksToMillis(double ticks) { return DurationUnit.TICKS.toNanos(ticks) / 1_000_000; }
    public static double nanosToMillis(long ns) { return (double) ns / 1_000_000; }
    public static double millisToNanos(double ms) { return ms * 1_000_000; }
    public static double nanosToSeconds(long ns) { return DurationUnit.SECONDS.from(ns); }
    public static double secondsToNanos(double sec) { return DurationUnit.SECONDS.toNanos(sec); }
}