package dev.bouncingelf10.timelesslib.fabric;

import dev.bouncingelf10.timelesslib.TimelessLib;
import dev.bouncingelf10.timelesslib.api.time.TimeFormatter;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public class TimelessFabricHelper {
    // ============ CLIENT ===========
    public static void clientDisplayToUser(long nanosLeft) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        Objects.requireNonNull(Objects.requireNonNull(getClient()).player).displayClientMessage(Component.nullToEmpty(
                TimeFormatter.format(nanosLeft, TimeFormatter.TimeFormat.VERBOSE_SIMPLE) + " left"), false);
    }

    public static void clientDisplayToUser(long nanosLeft, TimeFormatter.TimeFormat format, String prefix, String suffix) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) return;
        Objects.requireNonNull(Objects.requireNonNull(getClient()).player).displayClientMessage(Component.nullToEmpty(
                prefix + TimeFormatter.format(nanosLeft, format) + suffix), false);
    }


    public static Minecraft getClient() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return Minecraft.getInstance();
        }
        return null;
    }

    public static UUID getPlayerUuid() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            return Objects.requireNonNull(Objects.requireNonNull(getClient()).player).getUUID();
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
    // ============== SERVER ===============
    public static void serverDisplayToUser(long nanosLeft, ServerPlayer player) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        Objects.requireNonNull(Objects.requireNonNull(getClient()).player).displayClientMessage(Component.nullToEmpty(
                TimeFormatter.format(nanosLeft, TimeFormatter.TimeFormat.VERBOSE_SIMPLE) + " left"), false);
    }

    public static void serverDisplayToUser(long nanosLeft, ServerPlayer player, TimeFormatter.TimeFormat format, String prefix, String suffix) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        Objects.requireNonNull(Objects.requireNonNull(getClient()).player).displayClientMessage(Component.nullToEmpty(
                prefix + TimeFormatter.format(nanosLeft, format) + suffix), false);
    }

    public static void serverDisplayNearbyUsers(long nanosLeft, Vec3 pos, float radius) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        PlayerList list = TimelessLib.getServer().getPlayerList();
        for (ServerPlayer player : list.getPlayers()) {
            if (player.distanceToSqr(pos) <= radius * radius) {
                serverDisplayToUser(nanosLeft, player);
            }
        }
    }

    public static void serverDisplayNearbyUsers(long nanosLeft, Vec3 pos, float radius, TimeFormatter.TimeFormat format, String prefix, String suffix) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        PlayerList list = TimelessLib.getServer().getPlayerList();
        for (ServerPlayer player : list.getPlayers()) {
            if (player.distanceToSqr(pos) <= radius * radius) {
                serverDisplayToUser(nanosLeft, player, format, prefix, suffix);
            }
        }
    }

    public static void serverDisplayAllUsers(long nanosLeft) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        PlayerList list = TimelessLib.getServer().getPlayerList();
        for (ServerPlayer player : list.getPlayers()) {
            serverDisplayToUser(nanosLeft, player);
        }
    }

    public static void serverDisplayAllUsers(long nanosLeft, TimeFormatter.TimeFormat format, String prefix, String suffix) {
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER) return;
        PlayerList list = TimelessLib.getServer().getPlayerList();
        for (ServerPlayer player : list.getPlayers()) {
            serverDisplayToUser(nanosLeft, player, format, prefix, suffix);
        }
    }
}
