package dev.bouncingelf10.timelesslib.api.time;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for time conversions, parsing, and quick calculations.
 * <p>
 * Provides convenience methods for common time conversions and parsing duration strings.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Parse duration strings
 * Duration d1 = TimeConversions.parse("5s");           // 5 seconds
 * Duration d2 = TimeConversions.parse("2m 30s");       // 2 minutes 30 seconds
 * Duration d3 = TimeConversions.parse("1h 15m 30s");   // 1 hour 15 minutes 30 seconds
 * Duration d4 = TimeConversions.parse("3.5s");         // 3.5 seconds
 * Duration d5 = TimeConversions.parse("100t");         // 100 ticks
 *
 * // Quick conversions
 * double seconds = TimeConversions.ticksToSeconds(100);
 * double ticks = TimeConversions.secondsToTicks(5.0);
 * double millisPerTick = TimeConversions.millisPerTick();
 *
 * // Minecraft-specific
 * Duration oneMinecraftDay = TimeConversions.minecraftDay();
 * Duration thirtyTicks = TimeConversions.ticks(30);
 * }</pre>
 */
public final class TimeConversions {
    private TimeConversions() {}

    // Regex pattern for parsing duration strings like "1h 30m 45s" or "5s" or "2.5m"
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*([a-zA-Zμ]+)"
    );

    /**
     * Parses a duration string and returns a Duration instance.
     * <p>
     * Supported formats:
     * <ul>
     * <li>"5s" - 5 seconds</li>
     * <li>"2m 30s" - 2 minutes 30 seconds</li>
     * <li>"1h 15m 30s" - 1 hour 15 minutes 30 seconds</li>
     * <li>"3.5s" - 3.5 seconds (fractional values supported)</li>
     * <li>"100t" - 100 ticks</li>
     * <li>"1d 12h" - 1 day 12 hours</li>
     * <li>"500ms" - 500 milliseconds</li>
     * </ul>
     * </p>
     * <p>
     * Supported unit suffixes: ns, μs/us, ms, t/tick/ticks, s/sec/second/seconds,
     * m/min/minute/minutes, h/hr/hour/hours, d/day/days
     * </p>
     *
     * @param durationStr the duration string to parse
     * @return the parsed Duration
     * @throws IllegalArgumentException if the string cannot be parsed
     */
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

    /**
     * Converts between time units.
     * <p>
     * For example: {@code convert(823, DurationUnit.SECONDS, DurationUnit.MINUTES)} returns 13.716666...
     * </p>
     *
     * @param amount the amount to convert
     * @param from the source unit
     * @param to the target unit
     * @return the converted value
     */
    public static double convert(double amount, DurationUnit from, DurationUnit to) {
        return from.to(amount, to);
    }

    /**
     * Converts nanoseconds to ticks.
     *
     * @param ns nanoseconds
     * @return ticks
     */
    public static double nanosToTicks(long ns) {
        return DurationUnit.TICKS.from(ns);
    }

    /**
     * Converts ticks to nanoseconds.
     *
     * @param ticks ticks
     * @return nanoseconds
     */
    public static double ticksToNanos(double ticks) {
        return DurationUnit.TICKS.toNanos(ticks);
    }

    /**
     * Converts milliseconds to ticks.
     *
     * @param ms milliseconds
     * @return ticks
     */
    public static double millisToTicks(long ms) {
        return DurationUnit.TICKS.from(ms * 1_000_000);
    }

    /**
     * Converts ticks to milliseconds.
     *
     * @param ticks ticks
     * @return milliseconds
     */
    public static double ticksToMillis(double ticks) {
        return DurationUnit.TICKS.toNanos(ticks) / 1_000_000;
    }

    /**
     * Converts nanoseconds to milliseconds.
     *
     * @param ns nanoseconds
     * @return milliseconds
     */
    public static double nanosToMillis(long ns) {
        return (double) ns / 1_000_000;
    }

    /**
     * Converts milliseconds to nanoseconds.
     *
     * @param ms milliseconds
     * @return nanoseconds
     */
    public static double millisToNanos(double ms) {
        return ms * 1_000_000;
    }

    /**
     * Converts nanoseconds to seconds.
     *
     * @param ns nanoseconds
     * @return seconds
     */
    public static double nanosToSeconds(long ns) {
        return DurationUnit.SECONDS.from(ns);
    }

    /**
     * Converts seconds to nanoseconds.
     *
     * @param sec seconds
     * @return nanoseconds
     */
    public static double secondsToNanos(double sec) {
        return DurationUnit.SECONDS.toNanos(sec);
    }

    /**
     * Converts ticks to seconds.
     *
     * @param ticks ticks
     * @return seconds
     */
    public static double ticksToSeconds(double ticks) {
        return ticks / 20.0;
    }

    /**
     * Converts seconds to ticks.
     *
     * @param seconds seconds
     * @return ticks
     */
    public static double secondsToTicks(double seconds) {
        return seconds * 20.0;
    }

    /**
     * Converts minutes to ticks.
     *
     * @param minutes minutes
     * @return ticks
     */
    public static double minutesToTicks(double minutes) {
        return minutes * 1200.0; // 60 seconds * 20 ticks
    }

    /**
     * Converts ticks to minutes.
     *
     * @param ticks ticks
     * @return minutes
     */
    public static double ticksToMinutes(double ticks) {
        return ticks / 1200.0;
    }

    /**
     * Returns the number of milliseconds per Minecraft tick (50ms).
     *
     * @return milliseconds per tick
     */
    public static double millisPerTick() {
        return 50.0;
    }

    /**
     * Returns the number of ticks per second in Minecraft (20).
     *
     * @return ticks per second
     */
    public static int ticksPerSecond() {
        return 20;
    }

    /**
     * Creates a Duration representing the specified number of Minecraft ticks.
     *
     * @param ticks number of ticks
     * @return Duration representing the ticks
     */
    public static Duration ticks(long ticks) {
        return Duration.ofTicks(ticks);
    }

    /**
     * Returns a Duration representing one Minecraft day (24000 ticks = 20 minutes).
     *
     * @return Duration of one Minecraft day
     */
    public static Duration minecraftDay() {
        return Duration.ofTicks(24000);
    }

    /**
     * Returns a Duration representing one Minecraft hour (1000 ticks = 50 seconds).
     *
     * @return Duration of one Minecraft hour
     */
    public static Duration minecraftHour() {
        return Duration.ofTicks(1000);
    }

    /**
     * Returns a Duration representing one Minecraft minute (~16.67 ticks).
     *
     * @return Duration of one Minecraft minute
     */
    public static Duration minecraftMinute() {
        return Duration.of(1000.0 / 60.0, DurationUnit.TICKS);
    }

    /**
     * Returns a Duration representing one real-world second.
     *
     * @return Duration of one second
     */
    public static Duration oneSecond() {
        return Duration.ofSeconds(1);
    }

    /**
     * Returns a Duration representing one real-world minute.
     *
     * @return Duration of one minute
     */
    public static Duration oneMinute() {
        return Duration.ofMinutes(1);
    }

    /**
     * Returns a Duration representing one real-world hour.
     *
     * @return Duration of one hour
     */
    public static Duration oneHour() {
        return Duration.ofHours(1);
    }

    /**
     * Returns a Duration representing one real-world day.
     *
     * @return Duration of one day
     */
    public static Duration oneDay() {
        return Duration.ofDays(1);
    }
}