package dev.bouncingelf10.timelesslib.api.cooldown;

import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.UUID;
import java.util.function.Supplier;

public class ClientCooldownManager<T> extends AbstractCooldownManager<T> {

    private final UUID localPlayer;

    public ClientCooldownManager(Supplier<T> ctx, UUID localPlayer) {
        super(ctx);
        this.localPlayer = localPlayer;
    }

    @Override
    protected UUID normalizeOwner(UUID owner) {
        return localPlayer;
    }

    public Countdown start(String key, Duration duration) {
        return super.start(localPlayer, key, duration);
    }

    public Countdown startRealtime(String key, Duration duration) {
        return super.startRealtime(localPlayer, key, duration);
    }

    public boolean isReady(String key) {
        return super.isReady(localPlayer, key);
    }

    public Duration remaining(String key) {
        return super.remaining(localPlayer, key);
    }

    public void reset(String key) {
        super.reset(localPlayer, key);
    }

    public void resetAll() {
        super.resetAll(localPlayer);
    }
}

