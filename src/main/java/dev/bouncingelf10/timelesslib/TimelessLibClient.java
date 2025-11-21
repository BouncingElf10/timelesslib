package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.cooldown.ClientCooldownManager;
import dev.bouncingelf10.timelesslib.api.countdown.CountdownManager;
import dev.bouncingelf10.timelesslib.api.scheduler.Scheduler;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import static com.mojang.text2speech.Narrator.LOGGER;

public class TimelessLibClient implements ClientModInitializer {
	public static final Scheduler<Minecraft> clientScheduler = new Scheduler<>(TimelessFabricHelper::getClient);
	public static final CountdownManager<Minecraft> clientCountdownManager = new CountdownManager<>(TimelessFabricHelper::getClient);
	public static final ClientCooldownManager<Minecraft> clientCooldownManager = new ClientCooldownManager<>(TimelessFabricHelper::getClient, TimelessFabricHelper.getPlayerUuid());

	@Override
	public void onInitializeClient() {
		LOGGER.info("TimelessLib Client Initialising");

		ClientTickEvents.END_CLIENT_TICK.register(client -> TimelessClock.update());
	}

	public static Scheduler<Minecraft> getClientScheduler() {
		if (TimelessFabricHelper.isServer()) throw new IllegalStateException("You are calling a client function on server code!");
		return clientScheduler;
	}
	public static CountdownManager<Minecraft> getClientCountdownManager() {
		if (TimelessFabricHelper.isServer()) throw new IllegalStateException("You are calling a client function on server code!");
		return clientCountdownManager;
	}
	public static CountdownManager<Minecraft> getClientCooldownManager() {
		if (TimelessFabricHelper.isServer()) throw new IllegalStateException("You are calling a client function on server code!");
		return clientCooldownManager;
	}
}