package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.scheduler.Scheduler;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
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
	@Nullable
	public static Scheduler<MinecraftServer> serverScheduler;

	@Override
	public void onInitialize() {
		LOGGER.info("TimelessLib Sever Initialising");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("TimelessLib Schedulers Initialised for Server");
			serverScheduler = new Scheduler<>(() -> server);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server ->  {
			LOGGER.info("TimelessLib Schedulers Stopped for Server");
			serverScheduler = null;
		});
	}

	public static boolean isServerInitialized() {
		return serverScheduler != null;
	}

	public static Scheduler<MinecraftServer> getServerScheduler() throws IllegalStateException {
		if (serverScheduler == null) throw new IllegalStateException("Server Scheduler is null! This likely happened due to the server not being initialized.");
		return serverScheduler;
	}
}