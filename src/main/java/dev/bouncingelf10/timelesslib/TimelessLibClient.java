package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.animation.AnimationManager;
import dev.bouncingelf10.timelesslib.api.clock.TimelessClock;
import dev.bouncingelf10.timelesslib.api.cooldown.ClientCooldownManager;
import dev.bouncingelf10.timelesslib.api.countdown.ClientCountdownManager;
import dev.bouncingelf10.timelesslib.api.scheduler.Scheduler;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.UUID;

public class TimelessLibClient implements ClientModInitializer {
	public static final Scheduler<Minecraft> clientScheduler = new Scheduler<>(TimelessFabricHelper::getClient);
	public static final ClientCountdownManager<Minecraft> clientCountdownManager = new ClientCountdownManager<>(TimelessFabricHelper::getClient);
	public static final ClientCooldownManager<Minecraft> clientCooldownManager = new ClientCooldownManager<>(TimelessFabricHelper::getClient, UUID.randomUUID());
    public static final AnimationManager clientAnimationManager = new AnimationManager();

	@Override
	public void onInitializeClient() {
		TimelessLib.LOGGER.info("TimelessLib Client Initialising");
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