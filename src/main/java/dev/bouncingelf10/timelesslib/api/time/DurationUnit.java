package dev.bouncingelf10.timelesslib.api.time;

/**
 * Represents time duration units with conversion capabilities between different time scales.
 * <p>
 * Each unit maintains an internal nanosecond representation, allowing precise conversions
 * between all supported time units. The TICKS unit represents Minecraft game ticks (20 ticks per second).
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Convert 5 seconds to nanoseconds
 * long nanos = DurationUnit.SECONDS.toNanos(5);
 *
 * // Convert with decimal precision: 823 seconds = 13.7167 minutes
 * double minutes = DurationUnit.SECONDS.to(823, DurationUnit.MINUTES);
 *
 * // Convert from nanoseconds to any unit
 * double seconds = DurationUnit.SECONDS.from(nanos);
 * }</pre>
 * @see TimeConversions
 */
public enum DurationUnit {
    /** Nanoseconds - the base unit (1 nanosecond = 1 billionth of a second) */
    NANOSECONDS(1),

    /** Microseconds - 1,000 nanoseconds */
    MICROSECONDS(1_000),

    /** Milliseconds - 1,000,000 nanoseconds */
    MILLISECONDS(1_000_000),

    /** Minecraft game ticks - 50,000,000 nanoseconds (20 ticks per second) */
    TICKS(50_000_000),

    /** Seconds - 1,000,000,000 nanoseconds */
    SECONDS(1_000_000_000),

    /** Minutes - 60 seconds */
    MINUTES(60L * 1_000_000_000),

    /** Hours - 60 minutes */
    HOURS(60L * 60 * 1_000_000_000),

    /** Days - 24 hours */
    DAYS(24L * 60 * 60 * 1_000_000_000);

    private final long nanos;

    DurationUnit(long nanos) {
        this.nanos = nanos;
    }

    /**
     * Returns the nanosecond value of one unit of this duration.
     *
     * @return the number of nanoseconds in one unit of this duration
     */
    public long toNanos() {
        return this.nanos;
    }

    /**
     * Converts the specified amount of this unit to nanoseconds.
     *
     * @param amount the amount of this duration unit to convert
     * @return the equivalent duration in nanoseconds
     */
    public long toNanos(long amount) {
        return amount * this.nanos;
    }

    /**
     * Converts the specified amount of this unit to nanoseconds.
     *
     * @param amount the amount of this duration unit to convert
     * @return the equivalent duration in nanoseconds
     */
    public double toNanos(double amount) {
        return amount * this.nanos;
    }

    /**
     * Converts the specified nanoseconds to this unit with full precision.
     * <p>
     * For example, {@code DurationUnit.MINUTES.from(823_000_000_000L)} returns 13.716666...
     * </p>
     *
     * @param ns the duration in nanoseconds to convert
     * @return the equivalent duration in this unit
     */
    public double from(long ns) {
        return (double) ns / this.nanos;
    }

    /**
     * Converts from this unit to another unit with full precision.
     * <p>
     * For example, {@code DurationUnit.SECONDS.to(823, DurationUnit.MINUTES)} returns 13.716666...
     * </p>
     *
     * @param amount the amount in this unit
     * @param target the target unit to convert to
     * @return the equivalent duration in the target unit
     */
    public double to(double amount, DurationUnit target) {
        return (amount * this.nanos) / target.nanos;
    }
}