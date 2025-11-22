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
        TimeComponents tc = new TimeComponents(nanos);
        StringBuilder sb = new StringBuilder();

        if (tc.days > 0) sb.append(tc.days).append("d ");
        if (tc.hours > 0) sb.append(tc.hours).append("h ");
        if (tc.minutes > 0) sb.append(tc.minutes).append("m ");
        if (tc.seconds > 0) sb.append(tc.seconds).append("s ");
        if (tc.millis > 0) sb.append(tc.millis).append("ms ");

        if (sb.isEmpty()) {
            sb.append("0").append(getUnitSuffix(minUnit));
        }

        return sb.toString().trim();
    }

    public static String formatVerbose(long nanos, String conjunction) {
        TimeComponents tc = new TimeComponents(nanos);
        List<String> parts = new ArrayList<>();

        if (tc.days > 0) parts.add(tc.days + (tc.days == 1 ? " day" : " days"));
        if (tc.hours > 0) parts.add(tc.hours + (tc.hours == 1 ? " hour" : " hours"));
        if (tc.minutes > 0) parts.add(tc.minutes + (tc.minutes == 1 ? " minute" : " minutes"));
        if (tc.seconds > 0) parts.add(tc.seconds + (tc.seconds == 1 ? " second" : " seconds"));
        if (parts.isEmpty()) return "0 seconds";

        if (parts.size() == 1) return parts.getFirst();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                if (i == parts.size() - 1 && !conjunction.isEmpty()) {
                    sb.append(" ").append(conjunction).append(" ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(parts.get(i));
        }

        return sb.toString();
    }

    public static String formatDigital(long nanos, boolean includeMillis) {
        TimeComponents tc = new TimeComponents(nanos);

        if (tc.days > 0) {
            return includeMillis
                    ? String.format("%dd %02d:%02d:%02d.%03d", tc.days, tc.hours, tc.minutes, tc.seconds, tc.millis)
                    : String.format("%dd %02d:%02d:%02d", tc.days, tc.hours, tc.minutes, tc.seconds);
        }
        if (tc.hours > 0) {
            return includeMillis
                    ? String.format("%02d:%02d:%02d.%03d", tc.hours, tc.minutes, tc.seconds, tc.millis)
                    : String.format("%02d:%02d:%02d", tc.hours, tc.minutes, tc.seconds);
        }
        return includeMillis
                ? String.format("%02d:%02d.%03d", tc.minutes, tc.seconds, tc.millis)
                : String.format("%02d:%02d", tc.minutes, tc.seconds);
    }

    public static String formatISO8601(long nanos) {
        TimeComponents tc = new TimeComponents(nanos);
        long days = tc.days;

        long hours = tc.hours;
        long minutes = tc.minutes;
        long seconds = tc.seconds;
        long millis = tc.millis;

        StringBuilder sb = new StringBuilder();
        sb.append("P");

        if (days > 0) sb.append(days).append("D");

        boolean hasTime = hours > 0 || minutes > 0 || seconds > 0 || millis > 0;

        if (hasTime) {
            sb.append("T");
            if (hours > 0) sb.append(hours).append("H");
            if (minutes > 0) sb.append(minutes).append("M");

            if (millis > 0) {
                sb.append(seconds).append(".");
                sb.append(String.format("%03d", millis)).append("S");
            } else if (seconds > 0) {
                sb.append(seconds).append("S");
            }
        }

        if (!hasTime && days == 0) {
            sb.append("T0S");
        }

        return sb.toString();
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

            long leftover = totalSeconds % 86400L;
            this.hours = (int) (leftover / 3600L);
            leftover %= 3600L;

            this.minutes = (int) (leftover / 60L);
            this.seconds = (int) (leftover % 60L);
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
                TimeComponents tc = new TimeComponents(nanos);
                if (tc.days > 0) return tc.days + "d";
                if (tc.hours > 0) return tc.hours + "h";
                if (tc.minutes > 0) return tc.minutes + "m";
                if (tc.seconds > 0) return tc.seconds + "s";
                return tc.millis + "ms";
            }
        },

        MINIMAL_TWO {
            @Override public String apply(long nanos) {
                TimeComponents tc = new TimeComponents(nanos);
                List<String> parts = new ArrayList<>();

                if (tc.days > 0) parts.add(tc.days + "d");
                if (tc.hours > 0) parts.add(tc.hours + "h");
                if (tc.minutes > 0) parts.add(tc.minutes + "m");
                if (tc.seconds > 0) parts.add(tc.seconds + "s");
                if (tc.millis > 0) parts.add(tc.millis + "ms");

                if (parts.isEmpty()) return "0ms";
                if (parts.size() == 1) return parts.get(0);
                return parts.get(0) + " " + parts.get(1);
            }
        },

        ISO_8601 {
            @Override public String apply(long nanos) {
                return formatISO8601(nanos);
            }
        },

        DEBUG {
            @Override public String apply(long nanos) {
                TimeComponents tc = new TimeComponents(nanos);
                long micros = (nanos % 1_000_000_000L) / 1_000L;
                long remNano = nanos % 1_000L;

                return String.format(
                        "Days: %d, Hours: %d, Minutes: %d, Seconds: %d, Milliseconds: %d, Microseconds: %d, Nanoseconds: %d",
                        tc.days, tc.hours, tc.minutes, tc.seconds, tc.millis, micros, remNano
                );
            }
        };

        public abstract String apply(long nanos);
    }
}
