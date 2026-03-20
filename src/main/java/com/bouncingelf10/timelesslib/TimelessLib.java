package com.bouncingelf10.timelesslib;

import com.mojang.logging.LogUtils;
import com.bouncingelf10.timelesslib.api.animation.AnimationManager;
import com.bouncingelf10.timelesslib.api.clock.TimelessClock;
import com.bouncingelf10.timelesslib.api.cooldown.ServerCooldownManager;
import com.bouncingelf10.timelesslib.api.countdown.CountdownManager;
import com.bouncingelf10.timelesslib.api.scheduler.Scheduler;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Mod(TimelessLib.MOD_ID)
public class TimelessLib {
    public static final String MOD_ID = "timelesslib";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Nullable private static MinecraftServer server;
    @Nullable private static Scheduler<MinecraftServer> serverScheduler;
    @Nullable private static CountdownManager<MinecraftServer> serverCountdownManager;
    @Nullable private static ServerCooldownManager<MinecraftServer> serverCooldownManager;
    @Nullable private static AnimationManager serverAnimationManager;

    public TimelessLib(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("TimelessLib Initialising");
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("TimelessLib Initialised for Server");
        server = event.getServer();
        serverScheduler = new Scheduler<>(() -> server);
        serverCountdownManager = new CountdownManager<>(() -> server);
        serverCooldownManager = new ServerCooldownManager<>(() -> server);
        serverAnimationManager = new AnimationManager();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("TimelessLib Stopped for Server");
        server = null;
        serverScheduler = null;
        serverCountdownManager = null;
        serverCooldownManager = null;
        serverAnimationManager = null;
    }

    @SubscribeEvent
    public void onServerTickEnd(ServerTickEvent.Post event) {
        TimelessClock.update();
        if (serverAnimationManager != null) {
            serverAnimationManager.update();
        }
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

    public static AnimationManager getServerAnimationManager() throws IllegalStateException {
        if (serverAnimationManager == null) throw new IllegalStateException("Server AnimationManager is null! This likely happened due to the server not being initialized. See TimelessLib#isServerInitialized()");
        return serverAnimationManager;
    }
}