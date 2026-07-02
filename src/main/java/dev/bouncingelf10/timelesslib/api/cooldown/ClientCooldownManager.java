package dev.bouncingelf10.timelesslib.api.cooldown;

import dev.bouncingelf10.timelesslib.InternalAccess;
import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.countdown.ClientCountdown;
import dev.bouncingelf10.timelesslib.api.countdown.ClientCountdownManager;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks cooldowns for the local client, keyed by a namespaced key. <br>
 * Obtain the shared instance through {@code TimelessLibClient.cooldowns()} - this class cannot be constructed by other mods.
 * @see AbstractCooldownManager
 */
public final class ClientCooldownManager extends AbstractCooldownManager {
    private final ClientCountdownManager countdowns;
    private final UUID localClient;

    public ClientCooldownManager(InternalAccess access, ClientCountdownManager countdowns, UUID localClient) {
        Objects.requireNonNull(access, "Managers can only be constructed by TimelessLib");
        this.countdowns = Objects.requireNonNull(countdowns);
        this.localClient = Objects.requireNonNull(localClient);
    }

    @Override
    protected UUID normalizeOwner(UUID owner) {
        return localClient;
    }

    @Override
    protected Cooldown newCooldown(Duration duration, Duration tickInterval, TimeSource timeSource, Runnable onFinishCleanup) {
        ClientCountdown underlying = countdowns.start(duration, tickInterval, timeSource);

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

    public Cooldown start(ResourceLocation key, Duration duration) {
        return super.start(localClient, key, duration);
    }

    public Cooldown startRealtime(ResourceLocation key, Duration duration) {
        return super.startRealtime(localClient, key, duration);
    }

    public Cooldown startIfAbsent(ResourceLocation key, Duration duration, TimeSource timeSource) {
        return super.startIfAbsent(localClient, key, duration, timeSource);
    }

    public boolean isReady(ResourceLocation key) {
        return super.isReady(localClient, key);
    }

    public Duration remaining(ResourceLocation key) {
        return super.remaining(localClient, key);
    }

    public void reset(ResourceLocation key) {
        super.reset(localClient, key);
    }

    public void resetAll() {
        super.resetAll(localClient);
    }

    public UUID getLocalClient() { return localClient; }
}
