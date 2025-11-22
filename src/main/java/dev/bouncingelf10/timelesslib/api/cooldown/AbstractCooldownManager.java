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
    private final Map<UUID, Map<String, Countdown>> active = new ConcurrentHashMap<>();

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
        Map<String, Countdown> ownerMap = active.computeIfAbsent(owner, o -> new ConcurrentHashMap<>());
        UUID finalOwner = owner;
        return ownerMap.computeIfAbsent(key, k -> startCooldownInternal(finalOwner, key, duration, TimelessClock.TimeSources.GAME_TIME));
    }

    public boolean isReady(UUID owner, String key) {
        owner = normalizeOwner(owner);
        Map<String, Countdown> map = active.get(owner);
        return map == null || !map.containsKey(key);
    }

    public Duration remaining(UUID owner, String key) {
        owner = normalizeOwner(owner);
        Countdown countdown = active.getOrDefault(owner, Collections.emptyMap()).get(key);
        return countdown == null ? Duration.zero() : countdown.remaining();
    }

    public void reset(UUID owner, String key) {
        owner = normalizeOwner(owner);
        Map<String, Countdown> map = active.get(owner);
        if (map != null) {
            Countdown countdown = map.remove(key);
            if (countdown != null) countdown.cancel();
            if (map.isEmpty()) active.remove(owner);
        }
    }

    public void resetAll(UUID owner) {
        owner = normalizeOwner(owner);
        Map<String, Countdown> map = active.remove(owner);
        if (map != null) {
            map.values().forEach(Countdown::cancel);
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
            Map<String, Countdown> map = active.get(owner);
            if (map != null) {
                map.remove(key);
                if (map.isEmpty()) active.remove(owner);
            }
        });
        active.computeIfAbsent(owner, o -> new ConcurrentHashMap<>()).put(key, countdown);
        return countdown;
    }
}