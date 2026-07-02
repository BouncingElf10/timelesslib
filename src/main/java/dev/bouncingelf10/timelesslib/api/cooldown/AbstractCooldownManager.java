package dev.bouncingelf10.timelesslib.api.cooldown;

import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.clock.TimeSources;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractCooldownManager {
    private final Map<UUID, Map<Identifier, Cooldown>> activeCooldowns = new ConcurrentHashMap<>();

    protected abstract UUID normalizeOwner(UUID owner);

    /**
     * Constructs the underlying countdown-backed cooldown. Implementations must invoke {@code onFinishCleanup}
     * exactly once, whether the cooldown expires naturally or {@link Cooldown#reset()} is called early -
     * this is what keeps the owner/key bookkeeping in sync (see {@link #reset(UUID, Identifier)}).
     */
    protected abstract Cooldown newCooldown(Duration duration, Duration tickInterval, TimeSource timeSource, Runnable onFinishCleanup);

    /**
     * Starts a cooldown for the given owner.
     * @param owner Owner of the cooldown.
     * @param key Cooldown key.
     * @param duration Cooldown duration.
     * @return {@link Cooldown}
     * @see TimeSources
     */
    public Cooldown start(UUID owner, Identifier key, Duration duration) {
        return startCooldown(owner, key, duration, TimeSources.GAME_TIME);
    }

    /**
     * Starts a realtime cooldown for the given owner.
     * @param owner Owner of the cooldown.
     * @param key Cooldown key.
     * @param duration Cooldown duration.
     * @return {@link Cooldown}
     * @see TimeSources
     */
    public Cooldown startRealtime(UUID owner, Identifier key, Duration duration) {
        return startCooldown(owner, key, duration, TimeSources.REAL_TIME);
    }

    /**
     * Starts a cooldown for the given owner if it does not exist yet. If it does exist, it is returned instead.
     * @param owner Owner of the cooldown.
     * @param key Cooldown key.
     * @param duration Cooldown duration.
     * @param timeSource Time source to use for the cooldown.
     * @return {@link Cooldown}
     * @see TimeSources
     */
    public Cooldown startIfAbsent(UUID owner, Identifier key, Duration duration, TimeSource timeSource) {
        UUID normalized = normalizeOwner(owner);
        return activeCooldowns
                .computeIfAbsent(normalized, o -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, k -> startCooldownInternal(normalized, key, duration, timeSource));
    }

    /**
     * Checks if a cooldown is ready for the given owner and key.<br>
     * Note: "Ready" means that the cooldown has finished (or was never started).
     * @param owner Owner of the cooldown.
     * @param key Cooldown key.
     * @return true if the cooldown is ready, false otherwise.
     */
    public boolean isReady(UUID owner, Identifier key) {
        UUID normalized = normalizeOwner(owner);
        Map<Identifier, Cooldown> ownerCooldowns = activeCooldowns.get(normalized);
        return ownerCooldowns == null || !ownerCooldowns.containsKey(key);
    }

    public Duration remaining(UUID owner, Identifier key) {
        UUID normalized = normalizeOwner(owner);
        Cooldown cooldown = activeCooldowns.getOrDefault(normalized, Collections.emptyMap()).get(key);
        return cooldown == null ? Duration.zero() : cooldown.remaining();
    }

    /**
     * Cancels the cooldown for the given owner and key, making it immediately ready. <br>
     * This is the one true cancellation path - it always keeps the owner/key bookkeeping in sync,
     * whether called directly or via {@link Cooldown#reset()}.
     */
    public void reset(UUID owner, Identifier key) {
        UUID normalized = normalizeOwner(owner);
        Map<Identifier, Cooldown> ownerCooldowns = activeCooldowns.get(normalized);
        if (ownerCooldowns != null) {
            Cooldown cooldown = ownerCooldowns.remove(key);
            if (cooldown != null) cooldown.reset();
            if (ownerCooldowns.isEmpty()) activeCooldowns.remove(normalized);
        }
    }

    /**
     * You should probably use {@link #reset(UUID, Identifier)} instead. Especially if you're NOT using a custom key scheme.
     */
    public void resetAll(UUID owner) {
        UUID normalized = normalizeOwner(owner);
        Map<Identifier, Cooldown> ownerCooldowns = activeCooldowns.remove(normalized);
        if (ownerCooldowns != null) {
            ownerCooldowns.values().forEach(Cooldown::reset);
        }
    }

    private Cooldown startCooldown(UUID owner, Identifier key, Duration duration, TimeSource timeSource) {
        UUID normalized = normalizeOwner(owner);
        reset(normalized, key);
        return startCooldownInternal(normalized, key, duration, timeSource);
    }

    private Cooldown startCooldownInternal(UUID owner, Identifier key, Duration duration, TimeSource timeSource) {
        Runnable cleanup = () -> {
            Map<Identifier, Cooldown> ownerCooldowns = activeCooldowns.get(owner);
            if (ownerCooldowns != null) {
                ownerCooldowns.remove(key);
                if (ownerCooldowns.isEmpty()) activeCooldowns.remove(owner);
            }
        };

        Cooldown cooldown = newCooldown(duration, Duration.TICK, timeSource, cleanup);
        activeCooldowns.computeIfAbsent(owner, o -> new ConcurrentHashMap<>()).put(key, cooldown);
        return cooldown;
    }
}
