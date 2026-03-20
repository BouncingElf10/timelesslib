package com.bouncingelf10.timelesslib.api.cooldown;

import com.bouncingelf10.timelesslib.api.clock.TimeSource;
import com.bouncingelf10.timelesslib.api.countdown.Countdown;
import com.bouncingelf10.timelesslib.api.time.Duration;

import java.util.UUID;
import java.util.function.Supplier;
/**
 * A Cooldown manager that tracks cooldowns for the local client
 * @see AbstractCooldownManager
 */
public class ClientCooldownManager<T> extends AbstractCooldownManager<T> {
    private final UUID localClient;

    public ClientCooldownManager(Supplier<T> ctx, UUID localClient) {
        super(ctx);
        this.localClient = localClient;
    }

    @Override
    protected UUID normalizeOwner(UUID owner) {
        return localClient;
    }

    public Countdown start(String key, Duration duration) {
        return super.start(localClient, key, duration);
    }

    public Countdown startRealtime(String key, Duration duration) {
        return super.startRealtime(localClient, key, duration);
    }

    public Countdown startIfAbsent(String key, Duration duration, TimeSource timeSource) {
        return super.startIfAbsent(localClient, key, duration, timeSource);
    }

    public boolean isReady(String key) {
        return super.isReady(localClient, key);
    }

    public Duration remaining(String key) {
        return super.remaining(localClient, key);
    }

    public void reset(String key) {
        super.reset(localClient, key);
    }

    public void resetAll() {
        super.resetAll(localClient);
    }

    public UUID getLocalClient() { return localClient; }
}

