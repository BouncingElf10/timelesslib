package dev.bouncingelf10.timelesslib.fabric;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

public class TimelessFabricHelper {
    public static Minecraft getClient() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return Minecraft.getInstance();
        }
        return null;
    }

    public static boolean shouldAdvanceTime() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            Minecraft client = Minecraft.getInstance();
            return !client.isPaused();
        }
        return true;
    }
}
