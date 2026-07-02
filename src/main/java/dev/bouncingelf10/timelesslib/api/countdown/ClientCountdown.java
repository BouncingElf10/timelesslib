package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.time.TimeFormat;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * A {@link Countdown} running against the local {@link Minecraft} client context, with chat-display convenience methods.
 * @see ClientCountdownManager#start(Duration)
 */
public final class ClientCountdown extends Countdown<Minecraft, ClientCountdown> {
    ClientCountdown(CountdownManager<Minecraft> manager, ResourceLocation id, Duration totalDuration, Duration tickInterval, TimeSource timeSource) {
        super(manager, id, totalDuration, tickInterval, timeSource);
    }

    public ClientCountdown displayToUser() {
        return every(Duration.TICK, client -> TimelessFabricHelper.clientDisplayToUser(remaining().toNanos()));
    }

    public ClientCountdown displayToUser(TimeFormat format, String prefix, String suffix) {
        return every(Duration.TICK, client -> TimelessFabricHelper.clientDisplayToUser(remaining().toNanos(), format, prefix, suffix));
    }
}
