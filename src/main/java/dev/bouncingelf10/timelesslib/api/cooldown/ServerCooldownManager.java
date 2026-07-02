package dev.bouncingelf10.timelesslib.api.cooldown;

import dev.bouncingelf10.timelesslib.InternalAccess;
import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.countdown.ServerCountdown;
import dev.bouncingelf10.timelesslib.api.countdown.ServerCountdownManager;
import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks per-player cooldowns on the server, keyed by owner UUID and a namespaced key. <br>
 * Obtain the shared instance through {@code TimelessLib.cooldowns()} - this class cannot be constructed by other mods.
 * @see AbstractCooldownManager
 */
public final class ServerCooldownManager extends AbstractCooldownManager {
    private final ServerCountdownManager countdowns;

    public ServerCooldownManager(InternalAccess access, ServerCountdownManager countdowns) {
        Objects.requireNonNull(access, "Managers can only be constructed by TimelessLib");
        this.countdowns = Objects.requireNonNull(countdowns);
    }

    @Override
    protected UUID normalizeOwner(UUID owner) {
        return owner;
    }

    @Override
    protected Cooldown newCooldown(Duration duration, Duration tickInterval, TimeSource timeSource, Runnable onFinishCleanup) {
        ServerCountdown underlying = countdowns.start(duration, tickInterval, timeSource);

        List<Runnable> readyHandlers = new CopyOnWriteArrayList<>();
        AtomicBoolean fired = new AtomicBoolean(false);
        Runnable fireReady = () -> {
            if (fired.compareAndSet(false, true)) {
                onFinishCleanup.run();
                readyHandlers.forEach(Runnable::run);
            }
        };
        underlying.onFinish(fireReady);

        return new Cooldown(
                () -> underlying.isFinished() || underlying.isCancelled(),
                underlying::remaining,
                underlying::progress,
                () -> { underlying.cancel(); fireReady.run(); },
                readyHandlers::add
        );
    }
}
