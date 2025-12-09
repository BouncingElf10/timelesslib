package dev.bouncingelf10.timelesslib.api.time;

public final class TimeConversions {

    private TimeConversions() {}

    /**
     * Convert between time units.
     * @param amount Amount to convert
     * @param from Source unit
     * @param to Target unit
     * @return Converted amount
     */
    public static double convert(double amount, DurationUnit from, DurationUnit to) {
        return from.to(amount, to);
    }

    public static double nanosToTicks(long nanos) { return DurationUnit.TICKS.from(nanos); }
    public static double ticksToNanos(double ticks) { return DurationUnit.TICKS.toNanos(ticks); }
    public static double millisToTicks(double ms) { return DurationUnit.TICKS.from((long) (ms * 1_000_000)); }
    public static double ticksToMillis(double ticks) { return DurationUnit.TICKS.toNanos(ticks) / 1_000_000; }
    public static double nanosToMillis(long nanos) { return nanos / 1_000_000.0; }
    public static double millisToNanos(double ms) { return ms * 1_000_000.0; }
    public static double nanosToSeconds(long nanos) { return DurationUnit.SECONDS.from(nanos); }
    public static double secondsToNanos(double sec) { return DurationUnit.SECONDS.toNanos(sec); }
    public static double ticksToSeconds(double ticks) { return ticks / 20.0; }
    public static double secondsToTicks(double seconds) { return seconds * 20.0; }
    public static double minutesToTicks(double minutes) { return minutes * 1200.0; }
    public static double ticksToMinutes(double ticks) { return ticks / 1200.0; }
    public static double millisPerTick() { return 50.0; }
    public static int ticksPerSecond() { return 20; }
    public static Duration ticks(long ticks) { return Duration.ofTicks(ticks); }
}