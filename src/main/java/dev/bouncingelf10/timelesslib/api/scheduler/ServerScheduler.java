package dev.bouncingelf10.timelesslib.api.scheduler;

import dev.bouncingelf10.timelesslib.InternalAccess;
import net.minecraft.server.MinecraftServer;

import java.util.function.Supplier;

/**
 * General-purpose task scheduler running against the {@link MinecraftServer} context - one-off delays,
 * repeating tasks, and step sequences. For counting down to zero with rich tick/finish/threshold events,
 * see {@code ServerCountdownManager} instead. <br>
 * Obtain the shared instance through {@code TimelessLib.scheduler()} - this class cannot be constructed by other mods.
 */
public final class ServerScheduler extends Scheduler<MinecraftServer> {
    public ServerScheduler(InternalAccess access, Supplier<MinecraftServer> contextProvider) {
        super(access, contextProvider);
    }

    public ServerScheduler(InternalAccess access, Supplier<MinecraftServer> contextProvider, Config config) {
        super(access, contextProvider, config);
    }
}
