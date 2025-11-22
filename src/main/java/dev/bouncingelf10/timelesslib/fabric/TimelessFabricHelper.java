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

import java.util.UUID;

public class TimelessFabricHelper {

    // ===================== COMMON ==================

    public static boolean isClient() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    public static boolean isServer() {
        return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
    }

    // ===================== CLIENT ==================

    public static @Nullable Minecraft getClient() {
        return isClient() ? Minecraft.getInstance() : null;
    }

    public static @Nullable UUID getPlayerUuid() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;

        return client.player.getUUID();
    }

    public static void clientDisplayToUser(long nanosLeft) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        String msg = TimeFormatter.format(nanosLeft, TimeFormatter.TimeFormat.VERBOSE_SIMPLE) + " left";
        client.player.displayClientMessage(Component.literal(msg), true);
    }

    public static void clientDisplayToUser(long nanosLeft, TimeFormatter.TimeFormat fmt,
                                           String prefix, String suffix) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        String msg = prefix + TimeFormatter.format(nanosLeft, fmt) + suffix;
        client.player.displayClientMessage(Component.literal(msg), true);
    }

    public static boolean shouldAdvanceTime() {
        if (!isClient()) return true;

        Minecraft client = Minecraft.getInstance();
        return !client.isPaused() && client.isSingleplayer();
    }

    // ===================== SERVER ==================

    public static void serverDisplayToUser(long nanosLeft, ServerPlayer player) {
        if (!TimelessLib.isServerInitialized()) return;

        String msg = TimeFormatter.format(nanosLeft, TimeFormatter.TimeFormat.VERBOSE_SIMPLE) + " left";
        player.displayClientMessage(Component.literal(msg), true);
    }

    public static void serverDisplayToUser(long nanosLeft, ServerPlayer player, TimeFormatter.TimeFormat fmt, String prefix, String suffix) {
        if (!TimelessLib.isServerInitialized()) return;

        String msg = prefix + TimeFormatter.format(nanosLeft, fmt) + suffix;
        player.displayClientMessage(Component.literal(msg), true);
    }

    public static void serverDisplayNearbyUsers(long nanosLeft, Vec3 pos, float radius) {
        if (!TimelessLib.isServerInitialized()) return;

        PlayerList list = TimelessLib.getServer().getPlayerList();

        for (ServerPlayer player : list.getPlayers()) {
            if (player.distanceToSqr(pos) <= radius * radius) {
                serverDisplayToUser(nanosLeft, player);
            }
        }
    }

    public static void serverDisplayNearbyUsers(long nanosLeft, Vec3 pos, float radius, TimeFormatter.TimeFormat fmt, String prefix, String suffix) {
        if (!TimelessLib.isServerInitialized()) return;

        PlayerList list = TimelessLib.getServer().getPlayerList();

        for (ServerPlayer player : list.getPlayers()) {
            if (player.distanceToSqr(pos) <= radius * radius) {
                serverDisplayToUser(nanosLeft, player, fmt, prefix, suffix);
            }
        }
    }

    public static void serverDisplayAllUsers(long nanosLeft) {
        if (!TimelessLib.isServerInitialized()) return;

        PlayerList list = TimelessLib.getServer().getPlayerList();
        for (ServerPlayer player : list.getPlayers()) {
            serverDisplayToUser(nanosLeft, player);
        }
    }

    public static void serverDisplayAllUsers(long nanosLeft, TimeFormatter.TimeFormat fmt, String prefix, String suffix) {
        if (!TimelessLib.isServerInitialized()) return;

        PlayerList list = TimelessLib.getServer().getPlayerList();
        for (ServerPlayer player : list.getPlayers()) {
            serverDisplayToUser(nanosLeft, player, fmt, prefix, suffix);
        }
    }
}