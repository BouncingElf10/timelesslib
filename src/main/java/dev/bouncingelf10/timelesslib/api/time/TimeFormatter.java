package dev.bouncingelf10.timelesslib.api.time;

public final class TimeFormatter {
    private TimeFormatter() {}

    public static String format(long nanos, TimeFormat format) {
        return format.format(nanos);
    }

    public static String format(long amount, DurationUnit unit, TimeFormat format) {
        return format.format(unit.toNanos(amount));
    }

    public static String format(TimeAnchor anchor, TimeFormat format) {
        return format.format(anchor.elapsedNanos());
    }

    public static String formatCompact(long nanos, DurationUnit minUnit) {
        TimeComponents tc = new TimeComponents(nanos);
        StringBuilder sb = new StringBuilder();

        if (tc.days > 0) sb.append(tc.days).append("d ");
        if (tc.hours > 0) sb.append(tc.hours).append("h ");
        if (tc.minutes > 0 && minUnit.toNanos() <= DurationUnit.MINUTES.toNanos())
            sb.append(tc.minutes).append("m ");
        if (tc.seconds > 0 && minUnit.toNanos() <= DurationUnit.SECONDS.toNanos())
            sb.append(tc.seconds).append("s");
        if (minUnit == DurationUnit.MILLISECONDS && tc.millis > 0)
            sb.append(tc.millis).append("ms");

        return sb.length() > 0 ? sb.toString().trim() : "0" + getUnitSuffix(minUnit);
    }

    public static String formatVerbose(long nanos, String conjunction) {
        TimeComponents tc = new TimeComponents(nanos);
        StringBuilder sb = new StringBuilder();
        int componentCount = 0;

        if (tc.days > 0) {
            sb.append(tc.days).append(tc.days == 1 ? " day" : " days");
            componentCount++;
        }
        if (tc.hours > 0) {
            if (componentCount > 0) sb.append(", ");
            sb.append(tc.hours).append(tc.hours == 1 ? " hour" : " hours");
            componentCount++;
        }
        if (tc.minutes > 0) {
            if (componentCount > 0) {
                if (tc.seconds == 0 && !conjunction.isEmpty()) {
                    sb.append(" ").append(conjunction).append(" ");
                } else {
                    sb.append(", ");
                }
            }
            sb.append(tc.minutes).append(tc.minutes == 1 ? " minute" : " minutes");
            componentCount++;
        }
        if (tc.seconds > 0) {
            if (componentCount > 0 && !conjunction.isEmpty()) {
                sb.append(" ").append(conjunction).append(" ");
            } else if (componentCount > 0) {
                sb.append(", ");
            }
            sb.append(tc.seconds).append(tc.seconds == 1 ? " second" : " seconds");
            componentCount++;
        }

        return componentCount > 0 ? sb.toString() : "0 seconds";
    }

    public static String formatDigital(long nanos, boolean includeMillis) {
        TimeComponents tc = new TimeComponents(nanos);

        if (tc.days > 0) {
            String format = includeMillis ? "%dd %02d:%02d:%02d.%03d" : "%dd %02d:%02d:%02d";
            return includeMillis
                    ? String.format(format, tc.days, tc.hours, tc.minutes, tc.seconds, tc.millis)
                    : String.format(format, tc.days, tc.hours, tc.minutes, tc.seconds);
        } else if (tc.hours > 0) {
            String format = includeMillis ? "%02d:%02d:%02d.%03d" : "%02d:%02d:%02d";
            return includeMillis
                    ? String.format(format, tc.hours, tc.minutes, tc.seconds, tc.millis)
                    : String.format(format, tc.hours, tc.minutes, tc.seconds);
        } else {
            String format = includeMillis ? "%02d:%02d.%03d" : "%02d:%02d";
            return includeMillis
                    ? String.format(format, tc.minutes, tc.seconds, tc.millis)
                    : String.format(format, tc.minutes, tc.seconds);
        }
    }

    private static String getUnitSuffix(DurationUnit unit) {
        switch (unit) {
            case DAYS: return "d";
            case HOURS: return "h";
            case MINUTES: return "m";
            case SECONDS: return "s";
            case MILLISECONDS: return "ms";
            case MICROSECONDS: return "μs";
            case NANOSECONDS: return "ns";
            case TICKS: return "t";
            default: return "";
        }
    }

    private static class TimeComponents {
        final long days;
        final int hours;
        final int minutes;
        final int seconds;
        final int millis;

        TimeComponents(long nanos) {
            long totalSeconds = nanos / 1_000_000_000;
            this.days = totalSeconds / 86400;
            this.hours = (int) ((totalSeconds % 86400) / 3600);
            this.minutes = (int) ((totalSeconds % 3600) / 60);
            this.seconds = (int) (totalSeconds % 60);
            this.millis = (int) ((nanos % 1_000_000_000) / 1_000_000);
        }
    }


    public enum TimeFormat {
        COMPACT {
            @Override
            public String format(long nanos) {
                return formatCompact(nanos, DurationUnit.SECONDS);
            }
        },

        COMPACT_MILLIS {
            @Override
            public String format(long nanos) {
                return formatCompact(nanos, DurationUnit.MILLISECONDS);
            }
        },

        VERBOSE {
            @Override
            public String format(long nanos) {
                return formatVerbose(nanos, "and");
            }
        },

        VERBOSE_SIMPLE {
            @Override
            public String format(long nanos) {
                return formatVerbose(nanos, "");
            }
        },

        DIGITAL {
            @Override
            public String format(long nanos) {
                return formatDigital(nanos, false);
            }
        },

        DIGITAL_MILLIS {
            @Override
            public String format(long nanos) {
                return formatDigital(nanos, true);
            }
        },

        MINIMAL {
            @Override
            public String format(long nanos) {
                TimeComponents tc = new TimeComponents(nanos);
                if (tc.days > 0) return tc.days + "d";
                if (tc.hours > 0) return tc.hours + "h";
                if (tc.minutes > 0) return tc.minutes + "m";
                if (tc.seconds > 0) return tc.seconds + "s";
                return tc.millis + "ms";
            }
        },

        MINIMAL_TWO {
            @Override
            public String format(long nanos) {
                TimeComponents tc = new TimeComponents(nanos);
                StringBuilder sb = new StringBuilder();
                int count = 0;

                if (tc.days > 0) { sb.append(tc.days).append("d "); count++; }
                if (tc.hours > 0 && count < 2) { sb.append(tc.hours).append("h "); count++; }
                if (tc.minutes > 0 && count < 2) { sb.append(tc.minutes).append("m "); count++; }
                if (tc.seconds > 0 && count < 2) { sb.append(tc.seconds).append("s "); count++; }
                if (tc.millis > 0 && count < 2) { sb.append(tc.millis).append("ms"); count++; }

                return sb.length() > 0 ? sb.toString().trim() : "0s";
            }
        },

        ISO_8601 {
            @Override
            public String format(long nanos) {
                TimeComponents tc = new TimeComponents(nanos);
                StringBuilder sb = new StringBuilder("PT");

                if (tc.days > 0) sb.append(tc.days * 24 + tc.hours).append("H");
                else if (tc.hours > 0) sb.append(tc.hours).append("H");

                if (tc.minutes > 0) sb.append(tc.minutes).append("M");
                if (tc.seconds > 0 || sb.length() == 2) sb.append(tc.seconds).append("S");

                return sb.toString();
            }
        },

        DEBUG {
            @Override
            public String format(long nanos) {
                TimeComponents tc = new TimeComponents(nanos);
                int micros = (int) ((nanos % 1_000_000_000) / 1_000);
                return String.format("Days: %d, Hours: %d, Minutes: %d, Seconds: %d, Milliseconds: %d, Microseconds: %d, Nanoseconds: %d",
                        tc.days, tc.hours, tc.minutes, tc.seconds,
                        tc.millis, micros, nanos % 1_000);
            }
        };

        public abstract String format(long nanos);
    }
}