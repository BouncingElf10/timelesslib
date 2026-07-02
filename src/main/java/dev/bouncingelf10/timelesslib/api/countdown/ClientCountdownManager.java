package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.InternalAccess;
import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.clock.TimeSources;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Countdown factory running against the local {@link Minecraft} client context. <br>
 * Obtain the shared instance through {@code TimelessLibClient.countdowns()} - this class cannot be constructed by other mods.
 */
public final class ClientCountdownManager extends CountdownManager<Minecraft> {
    public ClientCountdownManager(InternalAccess access, Supplier<Minecraft> contextProvider) {
        super(access, contextProvider);
    }

    public ClientCountdownManager(InternalAccess access, Supplier<Minecraft> contextProvider, int poolSize) {
        super(access, contextProvider, poolSize);
    }

    /**
     * Starts a countdown with a tick interval of 10ms and the game time source.
     * @param totalDuration Duration of the countdown.
     * @return {@link ClientCountdown}
     * @see TimeSources
     */
    public ClientCountdown start(Duration totalDuration) {
        return start(totalDuration, Duration.ofMillis(10), TimeSources.GAME_TIME);
    }

    /**
     * Starts a countdown with a tick interval of 10ms and the real time source.
     * @param totalDuration Duration of the countdown.
     * @return {@link ClientCountdown}
     * @see TimeSources
     */
    public ClientCountdown startRealtime(Duration totalDuration) {
        return start(totalDuration, Duration.ofMillis(10), TimeSources.REAL_TIME);
    }

    /**
     * Starts a countdown with the specified tick interval and time source.
     * @param totalDuration Duration of the countdown.
     * @param tickInterval Tick interval of the countdown.
     * @param timeSource Time source to use.
     * @return {@link ClientCountdown}
     * @see TimeSources
     */
    public ClientCountdown start(Duration totalDuration, Duration tickInterval, TimeSource timeSource) {
        Objects.requireNonNull(totalDuration);
        Objects.requireNonNull(tickInterval);
        Objects.requireNonNull(timeSource);

        ResourceLocation id = randomId();
        ClientCountdown countdown = new ClientCountdown(this, id, totalDuration, tickInterval, timeSource);
        countdowns.put(id, countdown);
        countdown.start();
        return countdown;
    }
}
