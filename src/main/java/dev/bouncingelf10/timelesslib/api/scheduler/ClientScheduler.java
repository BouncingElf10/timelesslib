package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.InternalAccess;
import net.minecraft.client.Minecraft;

import java.util.function.Supplier;

/**
 * General-purpose task scheduler running against the local {@link Minecraft} client context - one-off delays,
 * repeating tasks, and step sequences. For counting down to zero with rich tick/finish/threshold events,
 * see {@code ClientCountdownManager} instead. <br>
 * Obtain the shared instance through {@code TimelessLibClient.scheduler()} - this class cannot be constructed by other mods.
 */
public final class ClientScheduler extends Scheduler<Minecraft> {
    public ClientScheduler(InternalAccess access, Supplier<Minecraft> contextProvider) {
        super(access, contextProvider);
    }

    public ClientScheduler(InternalAccess access, Supplier<Minecraft> contextProvider, Config config) {
        super(access, contextProvider, config);
    }
}
