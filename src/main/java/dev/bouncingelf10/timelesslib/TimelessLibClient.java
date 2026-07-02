package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.animation.AnimationManager;
import dev.bouncingelf10.timelesslib.api.cooldown.ClientCooldownManager;
import dev.bouncingelf10.timelesslib.api.countdown.ClientCountdownManager;
import dev.bouncingelf10.timelesslib.api.scheduler.ClientScheduler;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.fabricmc.api.ClientModInitializer;

import java.util.UUID;

public class TimelessLibClient implements ClientModInitializer {
	private static final ClientScheduler CLIENT_SCHEDULER = new ClientScheduler(InternalAccess.issue(), TimelessFabricHelper::getClient);
	private static final ClientCountdownManager CLIENT_COUNTDOWNS = new ClientCountdownManager(InternalAccess.issue(), TimelessFabricHelper::getClient);
	private static final ClientCooldownManager CLIENT_COOLDOWNS = new ClientCooldownManager(InternalAccess.issue(), CLIENT_COUNTDOWNS, UUID.randomUUID());
    private static final AnimationManager CLIENT_ANIMATIONS = new AnimationManager(InternalAccess.issue());

	@Override
	public void onInitializeClient() {
		TimelessLib.LOGGER.info("TimelessLib Client Initialising");
    }

	public static ClientScheduler scheduler() {
		return CLIENT_SCHEDULER;
	}
	public static ClientCountdownManager countdowns() {
		return CLIENT_COUNTDOWNS;
	}
	public static ClientCooldownManager cooldowns() {
		return CLIENT_COOLDOWNS;
	}
    public static AnimationManager animations() {
        return CLIENT_ANIMATIONS;
    }
}
