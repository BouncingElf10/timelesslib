package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.animation.AnimationManager;
import dev.bouncingelf10.timelesslib.api.clock.TimelessClock;
import dev.bouncingelf10.timelesslib.api.cooldown.ServerCooldownManager;
import dev.bouncingelf10.timelesslib.api.countdown.ServerCountdownManager;
import dev.bouncingelf10.timelesslib.api.scheduler.ServerScheduler;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimelessLib implements ModInitializer {
	public static final String MOD_ID = "timelesslib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	@Nullable private static MinecraftServer server;
	@Nullable private static ServerScheduler scheduler;
	@Nullable private static ServerCountdownManager countdowns;
	@Nullable private static ServerCooldownManager cooldowns;
    @Nullable private static AnimationManager animations;

	@Override
	public void onInitialize() {
		LOGGER.info("TimelessLib Initialising");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("TimelessLib Initialised for Server");
			TimelessLib.server = server;
			scheduler = new ServerScheduler(InternalAccess.issue(), () -> server);
			countdowns = new ServerCountdownManager(InternalAccess.issue(), () -> server);
			cooldowns = new ServerCooldownManager(InternalAccess.issue(), countdowns);
            animations = new AnimationManager(InternalAccess.issue());
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server ->  {
			LOGGER.info("TimelessLib Stopped for Server");
			TimelessLib.server = null;
			scheduler = null;
			countdowns = null;
			cooldowns = null;
            animations = null;
		});

        ServerTickEvents.END_SERVER_TICK.register(server -> TimelessClock.update());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (animations == null) return;
            animations.update();
        });
	}

    /**
     * Checks if the server is initialized.
     */
	public static boolean isServerInitialized() {
		return server != null;
	}

    /**
     * Gets the server instance. Will throw an exception if the server is not initialized.
     * @return Server instance
     * @throws IllegalStateException
     */
	public static MinecraftServer getServer() throws IllegalStateException {
        if (server == null) throw new IllegalStateException("Server is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return server;
	}

    /**
     * Gets the server task scheduler - for one-off delays, repeating tasks, and step sequences. Will throw an exception if the server is not initialized.
     * @return Server scheduler
     * @throws IllegalStateException
     */
	public static ServerScheduler scheduler() throws IllegalStateException {
        if (scheduler == null) throw new IllegalStateException("Server Scheduler is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return scheduler;
	}

    /**
     * Gets the server countdown manager. Will throw an exception if the server is not initialized.
     * @return Server countdown manager
     * @throws IllegalStateException
     */
	public static ServerCountdownManager countdowns() throws IllegalStateException {
        if (countdowns == null) throw new IllegalStateException("Server CountdownManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return countdowns;
	}

    /**
     * Gets the server cooldown manager. Will throw an exception if the server is not initialized.
     * @return Server cooldown manager
     * @throws IllegalStateException
     */
	public static ServerCooldownManager cooldowns() throws IllegalStateException {
        if (cooldowns == null) throw new IllegalStateException("Server CooldownManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return cooldowns;
	}

    /**
     * Gets the server animation manager. Will throw an exception if the server is not initialized.
     * @return Server animation manager
     * @throws IllegalStateException
     */
    public static AnimationManager animations() throws IllegalStateException {
        if (animations == null) throw new IllegalStateException("Server AnimationManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
        return animations;
    }
}
