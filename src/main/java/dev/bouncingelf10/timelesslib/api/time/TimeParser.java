package dev.bouncingelf10.timelesslib.api.time;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeParser {
    private TimeParser() {}

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

    /**
     * Parses a duration string into a {@link Duration}.<br>
     * Eg. "5h 30m 10s": durUnit = 1530000000000 nanoseconds.
     * @param durationStr Duration string to parse
     * @return Parsed duration
     * @throws IllegalArgumentException when the duration string is invalid
     */
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
        DurationUnit durUnit;
        switch (unit) {
            case "ns":
                durUnit = DurationUnit.NANOSECONDS;
                break;
            case "μs":
            case "us":
                durUnit = DurationUnit.MICROSECONDS;
                break;
            case "ms":
                durUnit = DurationUnit.MILLISECONDS;
                break;
            case "s":
            case "sec":
            case "secs":
            case "second":
            case "seconds":
                durUnit = DurationUnit.SECONDS;
                break;
            case "m":
            case "min":
            case "mins":
            case "minute":
            case "minutes":
                durUnit = DurationUnit.MINUTES;
                break;
            case "h":
            case "hr":
            case "hrs":
            case "hour":
            case "hours":
                durUnit = DurationUnit.HOURS;
                break;
            case "d":
            case "day":
            case "days":
                durUnit = DurationUnit.DAYS;
                break;
            case "t":
            case "tick":
            case "ticks":
                durUnit = DurationUnit.TICKS;
                break;
            default:
                durUnit = null;
                break;
        }
        return Optional.ofNullable(durUnit);
    }
}
