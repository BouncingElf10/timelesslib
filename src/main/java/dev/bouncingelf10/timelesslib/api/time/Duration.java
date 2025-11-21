package dev.bouncingelf10.timelesslib.api.time;

public final class Duration implements Comparable<Duration> {
    private final long nanos;

    private Duration(long nanos) {
        this.nanos = nanos;
    }

    public static Duration of(long amount, DurationUnit unit) {
        return new Duration(unit.toNanos(amount));
    }

    public static Duration of(double amount, DurationUnit unit) {
        return new Duration((long) unit.toNanos(amount));
    }

    public static Duration ofNanos(long nanos) {
        return new Duration(nanos);
    }

    public static Duration ofMicros(long micros) {
        return of(micros, DurationUnit.MICROSECONDS);
    }

    public static Duration ofMillis(long millis) {
        return of(millis, DurationUnit.MILLISECONDS);
    }

    public static Duration ofTicks(long ticks) {
        return of(ticks, DurationUnit.TICKS);
    }

    public static Duration ofSeconds(long seconds) {
        return of(seconds, DurationUnit.SECONDS);
    }

    public static Duration ofSeconds(double seconds) {
        return of(seconds, DurationUnit.SECONDS);
    }

    public static Duration ofMinutes(long minutes) {
        return of(minutes, DurationUnit.MINUTES);
    }

    public static Duration ofHours(long hours) {
        return of(hours, DurationUnit.HOURS);
    }

    public static Duration ofDays(long days) {
        return of(days, DurationUnit.DAYS);
    }

    public static Duration zero() {
        return new Duration(0);
    }

    public long toNanos() {
        return nanos;
    }

    public double to(DurationUnit unit) {
        return unit.from(nanos);
    }

    public double toMicros() {
        return to(DurationUnit.MICROSECONDS);
    }

    public double toMillis() {
        return to(DurationUnit.MILLISECONDS);
    }

    public double toTicks() {
        return to(DurationUnit.TICKS);
    }

    public double toSeconds() {
        return to(DurationUnit.SECONDS);
    }

    public double toMinutes() {
        return to(DurationUnit.MINUTES);
    }

    public double toHours() {
        return to(DurationUnit.HOURS);
    }

    public double toDays() {
        return to(DurationUnit.DAYS);
    }

    public Duration plus(Duration other) {
        return new Duration(this.nanos + other.nanos);
    }

    public Duration plus(long amount, DurationUnit unit) {
        return plus(Duration.of(amount, unit));
    }

    public Duration minus(Duration other) {
        return new Duration(this.nanos - other.nanos);
    }

    public Duration minus(long amount, DurationUnit unit) {
        return minus(Duration.of(amount, unit));
    }

    public Duration multiply(long multiplier) {
        return new Duration(this.nanos * multiplier);
    }

    public Duration multiply(double multiplier) {
        return new Duration((long) (this.nanos * multiplier));
    }

    public Duration divide(long divisor) {
        return new Duration(this.nanos / divisor);
    }

    public Duration divide(double divisor) {
        return new Duration((long) (this.nanos / divisor));
    }

    public Duration abs() {
        return nanos < 0 ? new Duration(-nanos) : this;
    }

    public Duration negate() {
        return new Duration(-nanos);
    }

    public boolean isZero() {
        return nanos == 0;
    }

    public boolean isNegative() {
        return nanos < 0;
    }

    public boolean isPositive() {
        return nanos > 0;
    }

    public boolean isLongerThan(Duration other) {
        return this.nanos > other.nanos;
    }

    public boolean isShorterThan(Duration other) {
        return this.nanos < other.nanos;
    }

    public Duration min(Duration other) {
        return this.nanos <= other.nanos ? this : other;
    }

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

    public String toString(TimeFormatter.TimeFormat format) {
        return TimeFormatter.format(nanos, format);
    }
}