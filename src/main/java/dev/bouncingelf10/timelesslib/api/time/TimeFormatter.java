package dev.bouncingelf10.timelesslib.api.time;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class TimeFormatter {
    private TimeFormatter() {}

    public static String format(long nanos, TimeFormat format) {
        return format.apply(nanos);
    }

    public static String format(long amount, DurationUnit unit, TimeFormat format) {
        return format.apply(unit.toNanos(amount));
    }

    public static String format(long amount, TimeUnit unit, TimeFormat format) {
        return format.apply(unit.toNanos(amount));
    }

    public static String format(Duration duration, TimeFormat format) {
        return format.apply(duration.toNanos());
    }

    public static String format(TimeAnchor anchor, TimeFormat format) {
        return format.apply(anchor.elapsedNanos());
    }

    public static String formatCompact(long nanos, DurationUnit minUnit) {
        TimeComponents components = new TimeComponents(nanos);
        StringBuilder output = new StringBuilder();

        if (components.days > 0) output.append(components.days).append("d ");
        if (components.hours > 0) output.append(components.hours).append("h ");
        if (components.minutes > 0) output.append(components.minutes).append("m ");
        if (components.seconds > 0) output.append(components.seconds).append("s ");
        if (components.millis > 0) output.append(components.millis).append("ms ");

        if (output.isEmpty()) {
            output.append("0").append(getUnitSuffix(minUnit));
        }

        return output.toString().trim();
    }

    public static String formatVerbose(long nanos, String conjunction) {
        TimeComponents components = new TimeComponents(nanos);
        List<String> timeParts = new ArrayList<>();

        if (components.days > 0) timeParts.add(components.days + (components.days == 1 ? " day" : " days"));
        if (components.hours > 0) timeParts.add(components.hours + (components.hours == 1 ? " hour" : " hours"));
        if (components.minutes > 0) timeParts.add(components.minutes + (components.minutes == 1 ? " minute" : " minutes"));
        if (components.seconds > 0) timeParts.add(components.seconds + (components.seconds == 1 ? " second" : " seconds"));

        if (timeParts.isEmpty()) return "0 seconds";
        if (timeParts.size() == 1) return timeParts.get(0);

        StringBuilder output = new StringBuilder();
        for (int i = 0; i < timeParts.size(); i++) {
            if (i > 0) {
                if (i == timeParts.size() - 1 && !conjunction.isEmpty()) {
                    output.append(" ").append(conjunction).append(" ");
                } else {
                    output.append(", ");
                }
            }
            output.append(timeParts.get(i));
        }

        return output.toString();
    }

    public static String formatDigital(long nanos, boolean includeMillis) {
        TimeComponents components = new TimeComponents(nanos);

        if (components.days > 0) {
            return includeMillis
                    ? String.format("%dd %02d:%02d:%02d.%03d", components.days, components.hours, components.minutes, components.seconds, components.millis)
                    : String.format("%dd %02d:%02d:%02d", components.days, components.hours, components.minutes, components.seconds);
        }
        if (components.hours > 0) {
            return includeMillis
                    ? String.format("%02d:%02d:%02d.%03d", components.hours, components.minutes, components.seconds, components.millis)
                    : String.format("%02d:%02d:%02d", components.hours, components.minutes, components.seconds);
        }
        return includeMillis
                ? String.format("%02d:%02d.%03d", components.minutes, components.seconds, components.millis)
                : String.format("%02d:%02d", components.minutes, components.seconds);
    }

    public static String formatISO8601(long nanos) {
        TimeComponents components = new TimeComponents(nanos);

        StringBuilder isoOutput = new StringBuilder("P");
        if (components.days > 0) isoOutput.append(components.days).append("D");

        boolean hasTime = components.hours > 0 || components.minutes > 0 || components.seconds > 0 || components.millis > 0;
        if (hasTime) {
            isoOutput.append("T");
            if (components.hours > 0) isoOutput.append(components.hours).append("H");
            if (components.minutes > 0) isoOutput.append(components.minutes).append("M");
            if (components.millis > 0) {
                isoOutput.append(components.seconds).append(".");
                isoOutput.append(String.format("%03d", components.millis)).append("S");
            } else if (components.seconds > 0) {
                isoOutput.append(components.seconds).append("S");
            }
        }

        if (!hasTime && components.days == 0) {
            isoOutput.append("T0S");
        }

        return isoOutput.toString();
    }

    private static String getUnitSuffix(DurationUnit unit) {
        return switch (unit) {
            case DAYS -> "d";
            case HOURS -> "h";
            case MINUTES -> "m";
            case SECONDS -> "s";
            case MILLISECONDS -> "ms";
            case MICROSECONDS -> "μs";
            case NANOSECONDS -> "ns";
            case TICKS -> "t";
        };
    }

    private static class TimeComponents {
        final long days;
        final int hours;
        final int minutes;
        final int seconds;
        final int millis;

        TimeComponents(long nanos) {
            long totalSeconds = nanos / 1_000_000_000L;
            this.days = totalSeconds / 86400L;

            long remainingSeconds = totalSeconds % 86400L;
            this.hours = (int) (remainingSeconds / 3600L);
            remainingSeconds %= 3600L;

            this.minutes = (int) (remainingSeconds / 60L);
            this.seconds = (int) (remainingSeconds % 60L);
            this.millis = (int) ((nanos % 1_000_000_000L) / 1_000_000L);
        }
    }

    public enum TimeFormat {
        COMPACT {
            @Override public String apply(long nanos) {
                return formatCompact(nanos, DurationUnit.SECONDS);
            }
        },
        COMPACT_MILLIS {
            @Override public String apply(long nanos) {
                return formatCompact(nanos, DurationUnit.MILLISECONDS);
            }
        },
        VERBOSE {
            @Override public String apply(long nanos) {
                return formatVerbose(nanos, "and");
            }
        },
        VERBOSE_SIMPLE {
            @Override public String apply(long nanos) {
                return formatVerbose(nanos, "");
            }
        },
        DIGITAL {
            @Override public String apply(long nanos) {
                return formatDigital(nanos, false);
            }
        },
        DIGITAL_MILLIS {
            @Override public String apply(long nanos) {
                return formatDigital(nanos, true);
            }
        },
        MINIMAL {
            @Override public String apply(long nanos) {
                TimeComponents components = new TimeComponents(nanos);
                if (components.days > 0) return components.days + "d";
                if (components.hours > 0) return components.hours + "h";
                if (components.minutes > 0) return components.minutes + "m";
                if (components.seconds > 0) return components.seconds + "s";
                return components.millis + "ms";
            }
        },
        MINIMAL_TWO {
            @Override public String apply(long nanos) {
                TimeComponents components = new TimeComponents(nanos);
                List<String> firstTwoParts = new ArrayList<>();

                if (components.days > 0) firstTwoParts.add(components.days + "d");
                if (components.hours > 0) firstTwoParts.add(components.hours + "h");
                if (components.minutes > 0) firstTwoParts.add(components.minutes + "m");
                if (components.seconds > 0) firstTwoParts.add(components.seconds + "s");
                if (components.millis > 0) firstTwoParts.add(components.millis + "ms");

                if (firstTwoParts.isEmpty()) return "0ms";
                if (firstTwoParts.size() == 1) return firstTwoParts.get(0);
                return firstTwoParts.get(0) + " " + firstTwoParts.get(1);
            }
        },
        ISO_8601 {
            @Override public String apply(long nanos) {
                return formatISO8601(nanos);
            }
        },
        DEBUG {
            @Override public String apply(long nanos) {
                TimeComponents components = new TimeComponents(nanos);
                long microseconds = (nanos % 1_000_000_000L) / 1_000L;
                long remainingNanos = nanos % 1_000L;

                return String.format(
                        "Days: %d, Hours: %d, Minutes: %d, Seconds: %d, Milliseconds: %d, Microseconds: %d, Nanoseconds: %d",
                        components.days, components.hours, components.minutes, components.seconds,
                        components.millis, microseconds, remainingNanos
                );
            }
        };

        public abstract String apply(long nanos);
    }
}