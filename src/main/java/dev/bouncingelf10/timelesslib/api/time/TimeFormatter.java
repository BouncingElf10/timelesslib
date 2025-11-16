package dev.bouncingelf10.timelesslib.api.time;

/**
 * Utility class for formatting time durations into human-readable strings.
 * <p>
 * Supports multiple format styles including compact, verbose, digital clock formats,
 * and customizable precision levels.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * TimeAnchor timer = TimeAnchor.create();
 * // ... some time passes ...
 *
 * String compact = TimeFormatter.format(timer.elapsedNanos(), TimeFormat.COMPACT);
 * // Output: "2h 15m 30s"
 *
 * String verbose = TimeFormatter.format(timer.elapsedNanos(), TimeFormat.VERBOSE);
 * // Output: "2 hours, 15 minutes, 30 seconds"
 *
 * String clock = TimeFormatter.format(timer.elapsedNanos(), TimeFormat.DIGITAL);
 * // Output: "02:15:30"
 * }</pre>
 */
public final class TimeFormatter {
    private TimeFormatter() {}

    /**
     * Formats a duration in nanoseconds using the specified format style.
     *
     * @param nanos the duration in nanoseconds
     * @param format the format style to use
     * @return formatted time string
     */
    public static String format(long nanos, TimeFormat format) {
        return format.format(nanos);
    }

    /**
     * Formats a duration using the specified format style and precision.
     *
     * @param amount the duration amount
     * @param unit the unit of the duration
     * @param format the format style to use
     * @return formatted time string
     */
    public static String format(long amount, DurationUnit unit, TimeFormat format) {
        return format.format(unit.toNanos(amount));
    }

    /**
     * Formats a TimeAnchor's elapsed time using the specified format style.
     *
     * @param anchor the TimeAnchor to format
     * @param format the format style to use
     * @return formatted time string
     */
    public static String format(TimeAnchor anchor, TimeFormat format) {
        return format.format(anchor.elapsedNanos());
    }

    /**
     * Formats a duration in a compact style with custom precision.
     * Only includes non-zero units down to the specified minimum unit.
     *
     * @param nanos the duration in nanoseconds
     * @param minUnit the smallest unit to display
     * @return formatted time string (e.g., "2h 15m 30s")
     */
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

    /**
     * Formats a duration in a verbose, fully spelled-out style.
     *
     * @param nanos the duration in nanoseconds
     * @param conjunction the word to use between the last two components (e.g., "and", "")
     * @return formatted time string (e.g., "2 hours, 15 minutes and 30 seconds")
     */
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

    /**
     * Formats a duration as a digital clock display.
     *
     * @param nanos the duration in nanoseconds
     * @param includeMillis whether to include milliseconds
     * @return formatted time string (e.g., "02:15:30" or "02:15:30.500")
     */
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

    /**
     * Internal class to break down nanoseconds into time components.
     */
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


    /**
     * Predefined time format styles.
     */
    public enum TimeFormat {
        /**
         * Compact format with abbreviated units.
         * <p>Examples: "2h 15m 30s", "45m 12s", "3s"</p>
         */
        COMPACT {
            @Override
            public String format(long nanos) {
                return formatCompact(nanos, DurationUnit.SECONDS);
            }
        },

        /**
         * Compact format including milliseconds.
         * <p>Examples: "2h 15m 30s 500ms", "3s 250ms"</p>
         */
        COMPACT_MILLIS {
            @Override
            public String format(long nanos) {
                return formatCompact(nanos, DurationUnit.MILLISECONDS);
            }
        },

        /**
         * Verbose format with full unit names.
         * <p>Examples: "2 hours, 15 minutes, 30 seconds", "45 minutes, 12 seconds"</p>
         */
        VERBOSE {
            @Override
            public String format(long nanos) {
                return formatVerbose(nanos, "and");
            }
        },

        /**
         * Verbose format without conjunction.
         * <p>Examples: "2 hours, 15 minutes, 30 seconds", "45 minutes, 12 seconds"</p>
         */
        VERBOSE_SIMPLE {
            @Override
            public String format(long nanos) {
                return formatVerbose(nanos, "");
            }
        },

        /**
         * Digital clock format (HH:MM:SS).
         * <p>Examples: "02:15:30", "00:45:12", "12:00:03"</p>
         */
        DIGITAL {
            @Override
            public String format(long nanos) {
                return formatDigital(nanos, false);
            }
        },

        /**
         * Digital clock format with milliseconds (HH:MM:SS.mmm).
         * <p>Examples: "02:15:30.500", "00:45:12.250"</p>
         */
        DIGITAL_MILLIS {
            @Override
            public String format(long nanos) {
                return formatDigital(nanos, true);
            }
        },

        /**
         * Minimal format showing only the largest non-zero unit.
         * <p>Examples: "2h", "45m", "30s"</p>
         */
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

        /**
         * Minimal format showing the two largest non-zero units.
         * <p>Examples: "2h 15m", "45m 12s", "30s 500ms"</p>
         */
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

        /**
         * ISO 8601 duration format.
         * <p>Examples: "PT2H15M30S", "PT45M12S", "PT3S"</p>
         */
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
        /**
         * Debug duration format.
         * <p>Example: Days: 0, Hours: 0, Minutes: 2, Seconds: 3, Milliseconds: 42, Microseconds: 6, Nanoseconds: 8895"</p>
         */
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

        /**
         * Formats the given nanoseconds duration according to this format style.
         *
         * @param nanos the duration in nanoseconds
         * @return formatted time string
         */
        public abstract String format(long nanos);
    }
}