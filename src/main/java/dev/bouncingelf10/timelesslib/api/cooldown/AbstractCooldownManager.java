package dev.bouncingelf10.timelesslib.api.cooldown;

import dev.bouncingelf10.timelesslib.TimelessClock;
import dev.bouncingelf10.timelesslib.api.countdown.CountdownManager;
import dev.bouncingelf10.timelesslib.api.time.Duration;

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

    private Countdown startCooldown(UUID owner, String key, Duration duration, TimelessClock.TimeSource timeSource) {
        owner = normalizeOwner(owner);
        reset(owner, key);

        Countdown cd = start(duration, Duration.ofMillis(10), timeSource);

        UUID finalOwner = owner;
        cd.onFinish(ctx -> reset(finalOwner, key));

        active.computeIfAbsent(owner, o -> new ConcurrentHashMap<>()).put(key, cd);

        return cd;
    }

    public boolean isReady(UUID owner, String key) {
        owner = normalizeOwner(owner);
        return !active.containsKey(owner) || !active.get(owner).containsKey(key);
    }

    public Duration remaining(UUID owner, String key) {
        owner = normalizeOwner(owner);
        CountdownManager<T>.Countdown cd = active.getOrDefault(owner, Map.of()).get(key);
        return cd == null ? Duration.zero() : cd.remaining();
    }

    public void reset(UUID owner, String key) {
        owner = normalizeOwner(owner);

        Countdown cd = active
                .getOrDefault(owner, Map.of())
                .remove(key);

        if (cd != null) cd.cancel();
    }

    public void resetAll(UUID owner) {
        owner = normalizeOwner(owner);
        Map<String, Countdown> map = active.remove(owner);
        if (map != null) {
            map.values().forEach(Countdown::cancel);
        }
    }
}

