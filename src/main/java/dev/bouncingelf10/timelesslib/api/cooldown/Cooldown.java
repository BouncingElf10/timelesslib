package dev.bouncingelf10.timelesslib.api.cooldown;

import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A single active cooldown, returned by {@code ServerCooldownManager}/{@code ClientCooldownManager}. <br>
 * Unlike a {@link dev.bouncingelf10.timelesslib.api.countdown.Countdown}, this is scoped to exactly what a
 * cooldown needs - readiness, remaining time, progress, and resetting early - it does not expose
 * tick/threshold/display machinery that doesn't make sense for a cooldown.
 */
public final class Cooldown {
    private final Supplier<Boolean> readySupplier;
    private final Supplier<Duration> remainingSupplier;
    private final Supplier<Double> progressSupplier;
    private final Runnable resetAction;
    private final Consumer<Runnable> onReadyRegistrar;

    Cooldown(Supplier<Boolean> readySupplier, Supplier<Duration> remainingSupplier, Supplier<Double> progressSupplier,
             Runnable resetAction, Consumer<Runnable> onReadyRegistrar) {
        this.readySupplier = readySupplier;
        this.remainingSupplier = remainingSupplier;
        this.progressSupplier = progressSupplier;
        this.resetAction = resetAction;
        this.onReadyRegistrar = onReadyRegistrar;
    }

    /**
     * @return true if this cooldown has finished or been reset.
     */
    public boolean isReady() { return readySupplier.get(); }

    /**
     * @return remaining time before this cooldown is ready. Zero if already ready.
     */
    public Duration remaining() { return remainingSupplier.get(); }

    /**
     * @return progress from 0.0 (just started) to 1.0 (ready).
     */
    public double progress() { return progressSupplier.get(); }

    /**
     * Cancels this cooldown early, making it immediately ready.
     */
    public void reset() { resetAction.run(); }

    /**
     * Registers a callback to run once this cooldown becomes ready (naturally or via {@link #reset()}).
     */
    public Cooldown onReady(Runnable handler) {
        onReadyRegistrar.accept(handler);
        return this;
    }
}
