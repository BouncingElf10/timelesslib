package com.bouncingelf10.timelesslib.neoforge;

import com.bouncingelf10.timelesslib.TimelessLib;
import com.bouncingelf10.timelesslib.api.time.TimeFormat;
import com.bouncingelf10.timelesslib.api.time.TimeFormatter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLEnvironment;
import org.jetbrains.annotations.Nullable;

public class TimelessNeoforgeHelper {

    // ===================== COMMON ==================

    public static boolean isClient() {
        return FMLEnvironment.dist.isClient();
    }

    public static boolean isServer() {
        return FMLEnvironment.dist.isDedicatedServer();
    }

    // ===================== CLIENT ==================

    public static @Nullable Minecraft getClient() {
        return isClient() ? Minecraft.getInstance() : null;
    }

    public static void clientDisplayToUser(long nanosLeft) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        String msg = TimeFormatter.format(nanosLeft, TimeFormat.VERBOSE_SIMPLE) + " left";
        client.player.displayClientMessage(Component.literal(msg), true);
    }

    public static void clientDisplayToUser(long nanosLeft, TimeFormat fmt,
                                           String prefix, String suffix) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        String msg = prefix + TimeFormatter.format(nanosLeft, fmt) + suffix;
        client.player.displayClientMessage(Component.literal(msg), true);
    }

    public static boolean shouldAdvanceTime() {
        if (!isClient()) return true;

        Minecraft client = Minecraft.getInstance();
        // isPaused() + isSingleplayer() is the same check as in Fabric
        return !(client.isPaused() && client.isSingleplayer());
    }

    // ===================== SERVER ==================

    public static void serverDisplayToUser(long nanosLeft, ServerPlayer player) {
        if (!TimelessLib.isServerInitialized()) return;

        String msg = TimeFormatter.format(nanosLeft, TimeFormat.VERBOSE_SIMPLE) + " left";
        player.displayClientMessage(Component.literal(msg), true);
    }

    public static void serverDisplayToUser(long nanosLeft, ServerPlayer player,
                                           TimeFormat fmt, String prefix, String suffix) {
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

    public static void serverDisplayNearbyUsers(long nanosLeft, Vec3 pos, float radius,
                                                TimeFormat fmt, String prefix, String suffix) {
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

    public static void serverDisplayAllUsers(long nanosLeft, TimeFormat fmt,
                                             String prefix, String suffix) {
        if (!TimelessLib.isServerInitialized()) return;

        PlayerList list = TimelessLib.getServer().getPlayerList();
        for (ServerPlayer player : list.getPlayers()) {
            serverDisplayToUser(nanosLeft, player, fmt, prefix, suffix);
        }
    }
}