package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.scheduler.CountdownManager;
import dev.bouncingelf10.timelesslib.api.scheduler.Scheduler;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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

	@Override
	public void onInitialize() {
		LOGGER.info("TimelessLib Sever Initialising");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("TimelessLib Scheduler Initialised for Server");
			TimelessLib.server = server;
			serverScheduler = new Scheduler<>(() -> server);
			serverCountdownManager = new CountdownManager<>(() -> server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server ->  {
			LOGGER.info("TimelessLib Scheduler Stopped for Server");
			TimelessLib.server = null;
			serverScheduler = null;
			serverCountdownManager = null;
		});
	}

	public static boolean isServerInitialized() {
		return serverScheduler != null;
	}

	public static MinecraftServer getServer() throws IllegalStateException {
		if (server == null) throw new IllegalStateException("Server is null! This likely happened due to the server not being initialized.");
		return server;
	}

	public static Scheduler<MinecraftServer> getServerScheduler() throws IllegalStateException {
		if (serverScheduler == null) throw new IllegalStateException("Server Scheduler is null! This likely happened due to the server not being initialized.");
		return serverScheduler;
	}

	public static CountdownManager<MinecraftServer> getServerCountdownManager() throws IllegalStateException {
		if (serverCountdownManager == null) throw new IllegalStateException("Server Countdown Manager is null! This likely happened due to the server not being initialized.");
		return serverCountdownManager;
	}
}