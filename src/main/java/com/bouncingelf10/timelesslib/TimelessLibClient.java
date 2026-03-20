package com.bouncingelf10.timelesslib;

import com.bouncingelf10.timelesslib.api.animation.AnimationManager;
import com.bouncingelf10.timelesslib.api.clock.TimelessClock;
import com.bouncingelf10.timelesslib.api.cooldown.ClientCooldownManager;
import com.bouncingelf10.timelesslib.api.countdown.ClientCountdownManager;
import com.bouncingelf10.timelesslib.api.scheduler.Scheduler;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

@Mod(value = TimelessLib.MOD_ID, dist = Dist.CLIENT)
public class TimelessLibClient {
    public static final Scheduler<Minecraft> clientScheduler = new Scheduler<>(Minecraft::getInstance);
    public static final ClientCountdownManager<Minecraft> clientCountdownManager = new ClientCountdownManager<>(Minecraft::getInstance);
    public static final ClientCooldownManager<Minecraft> clientCooldownManager = new ClientCooldownManager<>(Minecraft::getInstance, UUID.randomUUID());
    public static final AnimationManager clientAnimationManager = new AnimationManager();

    public TimelessLibClient(IEventBus modEventBus, ModContainer modContainer) {
        TimelessLib.LOGGER.info("TimelessLib Client Initialising");
        NeoForge.EVENT_BUS.register(this);
    }

    /**
     * Replaces the Fabric mixin on Minecraft#runTick.
     * ClientTickEvent.Post fires at the end of each client tick, equivalent
     * to the mixin's @At("TAIL") injection on runTick(Z)V.
     */
    @SubscribeEvent
    public void onClientTickEnd(ClientTickEvent.Post event) {
        TimelessClock.update();
        clientAnimationManager.update();
    }

    public static Scheduler<Minecraft> getClientScheduler() {
        return clientScheduler;
    }

    public static ClientCountdownManager<Minecraft> getClientCountdownManager() {
        return clientCountdownManager;
    }

    public static ClientCooldownManager<Minecraft> getClientCooldownManager() {
        return clientCooldownManager;
    }

    public static AnimationManager getClientAnimationManager() {
        return clientAnimationManager;
    }
}