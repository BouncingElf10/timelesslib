package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.InternalAccess;
import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.clock.TimeSources;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Countdown factory running against the {@link MinecraftServer} context. <br>
 * Obtain the shared instance through {@code TimelessLib.countdowns()} - this class cannot be constructed by other mods.
 */
public final class ServerCountdownManager extends CountdownManager<MinecraftServer> {
    public ServerCountdownManager(InternalAccess access, Supplier<MinecraftServer> contextProvider) {
        super(access, contextProvider);
    }

    public ServerCountdownManager(InternalAccess access, Supplier<MinecraftServer> contextProvider, int poolSize) {
        super(access, contextProvider, poolSize);
    }

    /**
     * Starts a countdown with a tick interval of 50ms and the game time source.
     * @param totalDuration Duration of the countdown.
     * @return {@link ServerCountdown}
     * @see TimeSources
     */
    public ServerCountdown start(Duration totalDuration) {
        return start(totalDuration, Duration.ofMillis(50), TimeSources.GAME_TIME);
    }

    /**
     * Starts a countdown with a tick interval of 50ms and the real time source.
     * @param totalDuration Duration of the countdown.
     * @return {@link ServerCountdown}
     * @see TimeSources
     */
    public ServerCountdown startRealtime(Duration totalDuration) {
        return start(totalDuration, Duration.ofMillis(50), TimeSources.REAL_TIME);
    }

    /**
     * Starts a countdown with the specified tick interval and time source.
     * @param totalDuration Duration of the countdown.
     * @param tickInterval Tick interval of the countdown.
     * @param timeSource Time source to use.
     * @return {@link ServerCountdown}
     * @see TimeSources
     */
    public ServerCountdown start(Duration totalDuration, Duration tickInterval, TimeSource timeSource) {
        Objects.requireNonNull(totalDuration);
        Objects.requireNonNull(tickInterval);
        Objects.requireNonNull(timeSource);

        ResourceLocation id = randomId();
        ServerCountdown countdown = new ServerCountdown(this, id, totalDuration, tickInterval, timeSource);
        countdowns.put(id, countdown);
        countdown.start();
        return countdown;
    }
}
