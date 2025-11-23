package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.animation.KeyframeManager;
import dev.bouncingelf10.timelesslib.api.cooldown.ServerCooldownManager;
import dev.bouncingelf10.timelesslib.api.countdown.CountdownManager;
import dev.bouncingelf10.timelesslib.api.scheduler.Scheduler;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.time.TimeFormatter;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
    @Nullable private static KeyframeManager serverKeyframeManager;

	@Override
	public void onInitialize() {
		LOGGER.info("TimelessLib Sever Initialising");

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("TimelessLib Initialised for Server");
			TimelessLib.server = server;
			serverScheduler = new Scheduler<>(() -> server);
			serverCountdownManager = new CountdownManager<>(() -> server);
			serverCooldownManager = new ServerCooldownManager<>(() -> server);
            serverKeyframeManager = new KeyframeManager();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server ->  {
			LOGGER.info("TimelessLib Stopped for Server");
			TimelessLib.server = null;
			serverScheduler = null;
			serverCountdownManager = null;
			serverCooldownManager = null;
            serverKeyframeManager = null;
		});

        ServerTickEvents.END_SERVER_TICK.register(server -> TimelessClock.update());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (serverKeyframeManager == null) return;
            serverKeyframeManager.update(TimelessClock.deltaSeconds());
        });
	}

	public static boolean isServerInitialized() {
		return server != null;
	}

	public static MinecraftServer getServer() throws IllegalStateException {
        if (server == null) throw new IllegalStateException("Server is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return server;
	}

	public static Scheduler<MinecraftServer> getServerScheduler() throws IllegalStateException {
        if (serverScheduler == null) throw new IllegalStateException("Server Scheduler is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return serverScheduler;
	}

	public static CountdownManager<MinecraftServer> getServerCountdownManager() throws IllegalStateException {
        if (serverCountdownManager == null) throw new IllegalStateException("Server CountdownManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return serverCountdownManager;
	}

	public static ServerCooldownManager<MinecraftServer> getServerCooldownManager() throws IllegalStateException {
        if (serverCooldownManager == null) throw new IllegalStateException("Server CooldownManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
		return serverCooldownManager;
	}

    public static KeyframeManager getServerKeyframeManager() {
        if (serverKeyframeManager == null) throw new IllegalStateException("Server KeyframeManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
        return serverKeyframeManager;
    }
}