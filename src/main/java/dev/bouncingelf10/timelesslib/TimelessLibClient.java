package dev.bouncingelf10.timelesslib;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import static com.mojang.text2speech.Narrator.LOGGER;

public class TimelessLibClient implements ClientModInitializer {

	@Override
	public void onInitializeClient() {
		LOGGER.info("TimelessLib Client Initialising");
		ClientTickEvents.END_CLIENT_TICK.register(client -> { TimelessClock.update(); });
	}
}