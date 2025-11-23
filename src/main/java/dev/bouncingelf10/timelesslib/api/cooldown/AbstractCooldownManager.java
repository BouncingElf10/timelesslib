package dev.bouncingelf10.timelesslib.api.cooldown;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.api.countdown.CountdownManager;
import dev.bouncingelf10.timelesslib.api.time.Duration;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public abstract class AbstractCooldownManager<T> extends CountdownManager<T> {
    private final Map<UUID, Map<String, Countdown>> activeCooldowns = new ConcurrentHashMap<>();

    protected AbstractCooldownManager(Supplier<T> context) {
        super(context);
    }

    protected AbstractCooldownManager(Supplier<T> context, int poolSize) {
        super(context, poolSize);
    }

    protected abstract UUID normalizeOwner(UUID owner);

    public Countdown start(UUID owner, String key, Duration duration) {
        return startCooldown(owner, key, duration, TimelessClock.TimeSources.GAME_TIME);
    }

    public Countdown startRealtime(UUID owner, String key, Duration duration) {
        return startCooldown(owner, key, duration, TimelessClock.TimeSources.REAL_TIME);
    }

    public Countdown startIfAbsent(UUID owner, String key, Duration duration) {
        owner = normalizeOwner(owner);

        UUID finalOwner = owner;
        return activeCooldowns
                .computeIfAbsent(owner, o -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, k -> startCooldownInternal(finalOwner, key, duration, TimelessClock.TimeSources.GAME_TIME));
    }

    public boolean isReady(UUID owner, String key) {
        owner = normalizeOwner(owner);
        Map<String, Countdown> ownerCooldowns = activeCooldowns.get(owner);
        return ownerCooldowns == null || !ownerCooldowns.containsKey(key);
    }

    public Duration remaining(UUID owner, String key) {
        owner = normalizeOwner(owner);
        Countdown countdown = activeCooldowns.getOrDefault(owner, Collections.emptyMap()).get(key);
        return countdown == null ? Duration.zero() : countdown.remaining();
    }

    public void reset(UUID owner, String key) {
        owner = normalizeOwner(owner);
        Map<String, Countdown> ownerCooldowns = activeCooldowns.get(owner);
        if (ownerCooldowns != null) {
            Countdown countdown = ownerCooldowns.remove(key);
            if (countdown != null) countdown.cancel();
            if (ownerCooldowns.isEmpty()) activeCooldowns.remove(owner);
        }
    }

    public void resetAll(UUID owner) {
        owner = normalizeOwner(owner);
        Map<String, Countdown> ownerCooldowns = activeCooldowns.remove(owner);
        if (ownerCooldowns != null) {
            ownerCooldowns.values().forEach(Countdown::cancel);
        }
    }

    private Countdown startCooldown(UUID owner, String key, Duration duration, TimelessClock.TimeSource timeSource) {
        owner = normalizeOwner(owner);
        reset(owner, key);
        return startCooldownInternal(owner, key, duration, timeSource);
    }

    private Countdown startCooldownInternal(UUID owner, String key, Duration duration, TimelessClock.TimeSource timeSource) {
        Countdown countdown = start(duration, Duration.TICK, timeSource);
        countdown.onFinish(ctx -> {
            Map<String, Countdown> ownerCooldowns = activeCooldowns.get(owner);
            if (ownerCooldowns != null) {
                ownerCooldowns.remove(key);
                if (ownerCooldowns.isEmpty()) activeCooldowns.remove(owner);
            }
        });

        activeCooldowns.computeIfAbsent(owner, o -> new ConcurrentHashMap<>()).put(key, countdown);
        return countdown;
    }
}