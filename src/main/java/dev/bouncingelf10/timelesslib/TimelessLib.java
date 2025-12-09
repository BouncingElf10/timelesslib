package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.animation.AnimationManager;
import dev.bouncingelf10.timelesslib.api.clock.TimelessClock;
import dev.bouncingelf10.timelesslib.api.cooldown.ServerCooldownManager;
import dev.bouncingelf10.timelesslib.api.countdown.CountdownManager;
import dev.bouncingelf10.timelesslib.api.scheduler.Scheduler;
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
	@Nullable private static Scheduler<MinecraftServer> serverScheduler;
	@Nullable private static CountdownManager<MinecraftServer> serverCountdownManager;
	@Nullable private static ServerCooldownManager<MinecraftServer> serverCooldownManager;
    @Nullable private static AnimationManager serverAnimationManager;

	@Override
	public void onInitialize() {
		LOGGER.info("TimelessLib Initialising");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("TimelessLib Initialised for Server");
			TimelessLib.server = server;
			serverScheduler = new Scheduler<>(() -> server);
			serverCountdownManager = new CountdownManager<>(() -> server);
			serverCooldownManager = new ServerCooldownManager<>(() -> server);
            serverAnimationManager = new AnimationManager();
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server ->  {
			LOGGER.info("TimelessLib Stopped for Server");
			TimelessLib.server = null;
			serverScheduler = null;
			serverCountdownManager = null;
			serverCooldownManager = null;
            serverAnimationManager = null;
		});

        ServerTickEvents.END_SERVER_TICK.register(server -> TimelessClock.update());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (serverAnimationManager == null) return;
            serverAnimationManager.update();
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
     * Gets the server scheduler. Will throw an exception if the server is not initialized.
     * @return Server scheduler
     * @throws IllegalStateException
     */
	public static Scheduler<MinecraftServer> getServerScheduler() throws IllegalStateException {
        if (serverScheduler == null) throw new IllegalStateException("Server Scheduler is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return serverScheduler;
	}

    /**
     * Gets the server countdown manager. Will throw an exception if the server is not initialized.
     * @return Server countdown manager
     * @throws IllegalStateException
     */
	public static CountdownManager<MinecraftServer> getServerCountdownManager() throws IllegalStateException {
        if (serverCountdownManager == null) throw new IllegalStateException("Server CountdownManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return serverCountdownManager;
	}

    /**
     * Gets the server cooldown manager. Will throw an exception if the server is not initialized.
     * @return Server cooldown manager
     * @throws IllegalStateException
     */
	public static ServerCooldownManager<MinecraftServer> getServerCooldownManager() throws IllegalStateException {
        if (serverCooldownManager == null) throw new IllegalStateException("Server CooldownManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return serverCooldownManager;
	}

    /**
     * Gets the server animation manager. Will throw an exception if the server is not initialized.
     * @return Server animation manager
     * @throws IllegalStateException
     */
    public static AnimationManager getServerAnimationManager() throws IllegalStateException {
        if (serverAnimationManager == null) throw new IllegalStateException("Server KeyframeManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
        return serverAnimationManager;
    }
}