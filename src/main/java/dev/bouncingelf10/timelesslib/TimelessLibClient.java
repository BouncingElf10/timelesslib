package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.animation.KeyframeManager;
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
    public static final KeyframeManager clientKeyframeManager = new KeyframeManager();

	@Override
	public void onInitializeClient() {
		LOGGER.info("TimelessLib Client Initialising");

		ClientTickEvents.END_CLIENT_TICK.register(client -> TimelessClock.update());
        ClientTickEvents.END_CLIENT_TICK.register(client -> clientKeyframeManager.update(TimelessClock.deltaSeconds()));
    }

	public static Scheduler<Minecraft> getClientScheduler() {
		return clientScheduler;
	}
	public static CountdownManager<Minecraft> getClientCountdownManager() {
		return clientCountdownManager;
	}
	public static CountdownManager<Minecraft> getClientCooldownManager() {
		return clientCooldownManager;
	}
    public static KeyframeManager getClientKeyframeManager() {
        return clientKeyframeManager;
    }
}