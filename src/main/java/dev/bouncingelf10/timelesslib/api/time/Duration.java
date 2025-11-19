package dev.bouncingelf10.timelesslib.api.time;

/**
 * Represents an immutable duration of time with a specific unit.
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * // Create durations
 * Duration fiveSeconds = Duration.of(5, DurationUnit.SECONDS);
 * Duration thirtyTicks = Duration.ofTicks(30);
 * Duration twoMinutes = Duration.ofMinutes(2);
 *
 * // Convert between units
 * // 5000 ms
 * double inMillis = fiveSeconds.to(DurationUnit.MILLISECONDS);
 *
 * // Arithmetic operations
 * Duration combined = fiveSeconds.plus(twoMinutes);
 * Duration difference = twoMinutes.minus(fiveSeconds);
 * Duration doubled = fiveSeconds.multiply(2);
 *
 * // Comparisons
 * if (fiveSeconds.isLongerThan(thirtyTicks)) {
 *     // ...
 * }
 *
 * // Use with TimeAnchor
 * TimeAnchor timer = TimeAnchor.create();
 * if (timer.hasElapsed(fiveSeconds)) {
 *     // ...
 * }
 * }</pre>
 */
public final class Duration implements Comparable<Duration> {
    private final long nanos;

    private Duration(long nanos) {
        this.nanos = nanos;
    }

    /**
     * Creates a Duration from the specified amount and unit.
     *
     * @param amount the amount of time
     * @param unit the unit of time
     * @return a new Duration instance
     */
    public static Duration of(long amount, DurationUnit unit) {
        return new Duration(unit.toNanos(amount));
    }

    /**
     * Creates a Duration from the specified amount and unit.
     *
     * @param amount the amount of time (supports fractional values)
     * @param unit the unit of time
     * @return a new Duration instance
     */
    public static Duration of(double amount, DurationUnit unit) {
        return new Duration((long) unit.toNanos(amount));
    }

    /**
     * Creates a Duration from nanoseconds.
     *
     * @param nanos the duration in nanoseconds
     * @return a new Duration instance
     */
    public static Duration ofNanos(long nanos) {
        return new Duration(nanos);
    }

    /**
     * Creates a Duration from microseconds.
     *
     * @param micros the duration in microseconds
     * @return a new Duration instance
     */
    public static Duration ofMicros(long micros) {
        return of(micros, DurationUnit.MICROSECONDS);
    }

    /**
     * Creates a Duration from milliseconds.
     *
     * @param millis the duration in milliseconds
     * @return a new Duration instance
     */
    public static Duration ofMillis(long millis) {
        return of(millis, DurationUnit.MILLISECONDS);
    }

    /**
     * Creates a Duration from Minecraft ticks (20 ticks per second).
     *
     * @param ticks the duration in ticks
     * @return a new Duration instance
     */
    public static Duration ofTicks(long ticks) {
        return of(ticks, DurationUnit.TICKS);
    }

    /**
     * Creates a Duration from seconds.
     *
     * @param seconds the duration in seconds
     * @return a new Duration instance
     */
    public static Duration ofSeconds(long seconds) {
        return of(seconds, DurationUnit.SECONDS);
    }

    /**
     * Creates a Duration from seconds with fractional precision.
     *
     * @param seconds the duration in seconds
     * @return a new Duration instance
     */
    public static Duration ofSeconds(double seconds) {
        return of(seconds, DurationUnit.SECONDS);
    }

    /**
     * Creates a Duration from minutes.
     *
     * @param minutes the duration in minutes
     * @return a new Duration instance
     */
    public static Duration ofMinutes(long minutes) {
        return of(minutes, DurationUnit.MINUTES);
    }

    /**
     * Creates a Duration from hours.
     *
     * @param hours the duration in hours
     * @return a new Duration instance
     */
    public static Duration ofHours(long hours) {
        return of(hours, DurationUnit.HOURS);
    }

    /**
     * Creates a Duration from days.
     *
     * @param days the duration in days
     * @return a new Duration instance
     */
    public static Duration ofDays(long days) {
        return of(days, DurationUnit.DAYS);
    }

    /**
     * Returns a Duration representing zero time.
     *
     * @return a zero-length Duration
     */
    public static Duration zero() {
        return new Duration(0);
    }

    /**
     * Returns the duration in nanoseconds.
     *
     * @return the duration in nanoseconds
     */
    public long toNanos() {
        return nanos;
    }

    /**
     * Returns the duration in the specified unit.
     *
     * @param unit the target unit
     * @return the duration in the specified unit
     */
    public double to(DurationUnit unit) {
        return unit.from(nanos);
    }

    /**
     * Returns the duration in microseconds.
     *
     * @return the duration in microseconds
     */
    public double toMicros() {
        return to(DurationUnit.MICROSECONDS);
    }

    /**
     * Returns the duration in milliseconds.
     *
     * @return the duration in milliseconds
     */
    public double toMillis() {
        return to(DurationUnit.MILLISECONDS);
    }

    /**
     * Returns the duration in Minecraft ticks.
     *
     * @return the duration in ticks
     */
    public double toTicks() {
        return to(DurationUnit.TICKS);
    }

    /**
     * Returns the duration in seconds.
     *
     * @return the duration in seconds
     */
    public double toSeconds() {
        return to(DurationUnit.SECONDS);
    }

    /**
     * Returns the duration in minutes.
     *
     * @return the duration in minutes
     */
    public double toMinutes() {
        return to(DurationUnit.MINUTES);
    }

    /**
     * Returns the duration in hours.
     *
     * @return the duration in hours
     */
    public double toHours() {
        return to(DurationUnit.HOURS);
    }

    /**
     * Returns the duration in days.
     *
     * @return the duration in days
     */
    public double toDays() {
        return to(DurationUnit.DAYS);
    }

    /**
     * Returns a new Duration that is the sum of this and the specified duration.
     *
     * @param other the duration to add
     * @return a new Duration representing the sum
     */
    public Duration plus(Duration other) {
        return new Duration(this.nanos + other.nanos);
    }

    /**
     * Returns a new Duration that is the sum of this and the specified amount.
     *
     * @param amount the amount to add
     * @param unit the unit of the amount
     * @return a new Duration representing the sum
     */
    public Duration plus(long amount, DurationUnit unit) {
        return plus(Duration.of(amount, unit));
    }

    /**
     * Returns a new Duration that is the difference of this and the specified duration.
     *
     * @param other the duration to subtract
     * @return a new Duration representing the difference
     */
    public Duration minus(Duration other) {
        return new Duration(this.nanos - other.nanos);
    }

    /**
     * Returns a new Duration that is the difference of this and the specified amount.
     *
     * @param amount the amount to subtract
     * @param unit the unit of the amount
     * @return a new Duration representing the difference
     */
    public Duration minus(long amount, DurationUnit unit) {
        return minus(Duration.of(amount, unit));
    }

    /**
     * Returns a new Duration that is this duration multiplied by the specified factor.
     *
     * @param multiplier the factor to multiply by
     * @return a new Duration representing the product
     */
    public Duration multiply(long multiplier) {
        return new Duration(this.nanos * multiplier);
    }

    /**
     * Returns a new Duration that is this duration multiplied by the specified factor.
     *
     * @param multiplier the factor to multiply by
     * @return a new Duration representing the product
     */
    public Duration multiply(double multiplier) {
        return new Duration((long) (this.nanos * multiplier));
    }

    /**
     * Returns a new Duration that is this duration divided by the specified divisor.
     *
     * @param divisor the divisor
     * @return a new Duration representing the quotient
     */
    public Duration divide(long divisor) {
        return new Duration(this.nanos / divisor);
    }

    /**
     * Returns a new Duration that is this duration divided by the specified divisor.
     *
     * @param divisor the divisor
     * @return a new Duration representing the quotient
     */
    public Duration divide(double divisor) {
        return new Duration((long) (this.nanos / divisor));
    }

    /**
     * Returns the absolute value of this duration.
     *
     * @return a new Duration representing the absolute value
     */
    public Duration abs() {
        return nanos < 0 ? new Duration(-nanos) : this;
    }

    /**
     * Returns the negation of this duration.
     *
     * @return a new Duration with the opposite sign
     */
    public Duration negate() {
        return new Duration(-nanos);
    }

    /**
     * Returns whether this duration is zero.
     *
     * @return true if the duration is zero, false otherwise
     */
    public boolean isZero() {
        return nanos == 0;
    }

    /**
     * Returns whether this duration is negative.
     *
     * @return true if the duration is negative, false otherwise
     */
    public boolean isNegative() {
        return nanos < 0;
    }

    /**
     * Returns whether this duration is positive.
     *
     * @return true if the duration is positive, false otherwise
     */
    public boolean isPositive() {
        return nanos > 0;
    }

    /**
     * Returns whether this duration is longer than the specified duration.
     *
     * @param other the duration to compare to
     * @return true if this duration is longer
     */
    public boolean isLongerThan(Duration other) {
        return this.nanos > other.nanos;
    }

    /**
     * Returns whether this duration is shorter than the specified duration.
     *
     * @param other the duration to compare to
     * @return true if this duration is shorter
     */
    public boolean isShorterThan(Duration other) {
        return this.nanos < other.nanos;
    }

    /**
     * Returns the minimum of this duration and the specified duration.
     *
     * @param other the duration to compare to
     * @return the shorter duration
     */
    public Duration min(Duration other) {
        return this.nanos <= other.nanos ? this : other;
    }

    /**
     * Returns the maximum of this duration and the specified duration.
     *
     * @param other the duration to compare to
     * @return the longer duration
     */
    public Duration max(Duration other) {
        return this.nanos >= other.nanos ? this : other;
    }

    @Override
    public int compareTo(Duration other) {
        return Long.compare(this.nanos, other.nanos);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Duration)) return false;
        Duration other = (Duration) obj;
        return this.nanos == other.nanos;
    }

    @Override
    public String toString() {
        return TimeFormatter.format(nanos, TimeFormatter.TimeFormat.COMPACT);
    }

    /**
     * Formats this duration using the specified format.
     *
     * @param format the format style to use
     * @return formatted time string
     */
    public String toString(TimeFormatter.TimeFormat format) {
        return TimeFormatter.format(nanos, format);
    }
}