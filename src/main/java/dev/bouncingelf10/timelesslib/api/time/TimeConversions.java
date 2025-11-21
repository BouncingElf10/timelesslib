package dev.bouncingelf10.timelesslib.api.time;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeConversions {
    private TimeConversions() {}

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*([a-zA-Zμ]+)"
    );

    public static Duration parse(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            throw new IllegalArgumentException("Duration string cannot be null or empty");
        }

        String normalized = durationStr.toLowerCase().trim();
        Matcher matcher = DURATION_PATTERN.matcher(normalized);

        long totalNanos = 0;
        boolean foundAny = false;

        while (matcher.find()) {
            foundAny = true;
            double value = Double.parseDouble(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            DurationUnit durationUnit = parseUnit(unit);
            totalNanos += (long) durationUnit.toNanos(value);
        }

        if (!foundAny) {
            throw new IllegalArgumentException("Could not parse duration string: " + durationStr);
        }

        return Duration.ofNanos(totalNanos);
    }

    private static DurationUnit parseUnit(String unit) {
        switch (unit) {
            case "ns":
            case "nano":
            case "nanos":
            case "nanosecond":
            case "nanoseconds":
                return DurationUnit.NANOSECONDS;

            case "μs":
            case "us":
            case "micro":
            case "micros":
            case "microsecond":
            case "microseconds":
                return DurationUnit.MICROSECONDS;

            case "ms":
            case "milli":
            case "millis":
            case "millisecond":
            case "milliseconds":
                return DurationUnit.MILLISECONDS;

            case "t":
            case "tick":
            case "ticks":
                return DurationUnit.TICKS;

            case "s":
            case "sec":
            case "secs":
            case "second":
            case "seconds":
                return DurationUnit.SECONDS;

            case "m":
            case "min":
            case "mins":
            case "minute":
            case "minutes":
                return DurationUnit.MINUTES;

            case "h":
            case "hr":
            case "hrs":
            case "hour":
            case "hours":
                return DurationUnit.HOURS;

            case "d":
            case "day":
            case "days":
                return DurationUnit.DAYS;

            default:
                throw new IllegalArgumentException("Unknown time unit: " + unit);
        }
    }

    public static double convert(double amount, DurationUnit from, DurationUnit to) {
        return from.to(amount, to);
    }

    public static double nanosToTicks(long ns) {
        return DurationUnit.TICKS.from(ns);
    }
    public static double ticksToNanos(double ticks) {
        return DurationUnit.TICKS.toNanos(ticks);
    }
    public static double millisToTicks(long ms) {
        return DurationUnit.TICKS.from(ms * 1_000_000);
    }
    public static double ticksToMillis(double ticks) {
        return DurationUnit.TICKS.toNanos(ticks) / 1_000_000;
    }
    public static double nanosToMillis(long ns) {
        return (double) ns / 1_000_000;
    }
    public static double millisToNanos(double ms) {
        return ms * 1_000_000;
    }
    public static double nanosToSeconds(long ns) {
        return DurationUnit.SECONDS.from(ns);
    }
    public static double secondsToNanos(double sec) {
        return DurationUnit.SECONDS.toNanos(sec);
    }
    public static double ticksToSeconds(double ticks) {
        return ticks / 20.0;
    }
    public static double secondsToTicks(double seconds) {
        return seconds * 20.0;
    }
    public static double minutesToTicks(double minutes) {
        return minutes * 1200.0; // 60 seconds * 20 ticks
    }
    public static double ticksToMinutes(double ticks) {
        return ticks / 1200.0;
    }
    public static double millisPerTick() {
        return 50.0;
    }

    public static int ticksPerSecond() {
        return 20;
    }
    public static Duration ticks(long ticks) {
        return Duration.ofTicks(ticks);
    }
    public static Duration minecraftDay() {
        return Duration.ofTicks(24000);
    }
    public static Duration minecraftHour() {
        return Duration.ofTicks(1000);
    }
    public static Duration minecraftMinute() {
        return Duration.of(1000.0 / 60.0, DurationUnit.TICKS);
    }
    public static Duration oneSecond() {
        return Duration.ofSeconds(1);
    }
    public static Duration oneMinute() {
        return Duration.ofMinutes(1);
    }
    public static Duration oneHour() {
        return Duration.ofHours(1);
    }
    public static Duration oneDay() {
        return Duration.ofDays(1);
    }
}