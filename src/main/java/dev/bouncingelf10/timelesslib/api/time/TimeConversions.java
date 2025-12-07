package dev.bouncingelf10.timelesslib.api.time;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeConversions {

    private TimeConversions() {}

    private static final String UNIT_PATTERN =
            "ns|μs|us|ms|s|sec|secs|second|seconds|"
                    + "m|min|mins|minute|minutes|"
                    + "h|hr|hrs|hour|hours|"
                    + "d|day|days|"
                    + "t|tick|ticks";

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(" + UNIT_PATTERN + ")",
            Pattern.CASE_INSENSITIVE
    );

    public static Duration parse(String durationStr) throws IllegalArgumentException {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be null or empty.");
        }

        String normalized = durationStr.trim().toLowerCase();
        Matcher matcher = DURATION_PATTERN.matcher(normalized);

        long totalNanos = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            foundAny = true;
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            DurationUnit durationUnit = parseUnit(unit).orElseThrow(() -> new IllegalArgumentException("Unknown time unit: " + unit));
            totalNanos += (long) durationUnit.toNanos(value);
        }

        if (!foundAny) {
            throw new IllegalArgumentException(
                    "Could not parse duration string: \"" + durationStr + "\""
            );
        }

        return Duration.ofNanos(totalNanos);
    }
    
    private static Optional<DurationUnit> parseUnit(String unit) {
        return Optional.ofNullable(
                switch (unit) {
                    case "ns" -> DurationUnit.NANOSECONDS;
                    case "μs", "us" -> DurationUnit.MICROSECONDS;
                    case "ms" -> DurationUnit.MILLISECONDS;
                    case "s", "sec", "secs", "second", "seconds" -> DurationUnit.SECONDS;
                    case "m", "min", "mins", "minute", "minutes" -> DurationUnit.MINUTES;
                    case "h", "hr", "hrs", "hour", "hours" -> DurationUnit.HOURS;
                    case "d", "day", "days" -> DurationUnit.DAYS;
                    case "t", "tick", "ticks" -> DurationUnit.TICKS;
                    default -> null;
                }
        );
    }
    
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