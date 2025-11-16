package dev.bouncingelf10.timelesslib;

import dev.bouncingelf10.timelesslib.api.platform.TimelessPlatform;
import dev.bouncingelf10.timelesslib.api.time.DurationUnit;
import dev.bouncingelf10.timelesslib.api.time.TimeAnchor;
import dev.bouncingelf10.timelesslib.api.time.TimeFormatter;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimelessLib implements ModInitializer {
	public static final String MOD_ID = "timelesslib";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("TimelessLib Sever Initialising");
		TimelessPlatform.INSTANCE = TimelessFabricHelper::shouldAdvanceTime;

		ServerTickEvents.END_SERVER_TICK.register(server -> { TimelessClock.update(); });
	}
}