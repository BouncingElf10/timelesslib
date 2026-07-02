package dev.bouncingelf10.timelesslib.api.countdown;

import dev.bouncingelf10.timelesslib.api.clock.TimeSource;
import dev.bouncingelf10.timelesslib.api.time.Duration;
import dev.bouncingelf10.timelesslib.api.time.TimeFormat;
import dev.bouncingelf10.timelesslib.fabric.TimelessFabricHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

/**
 * A {@link Countdown} running against the {@link MinecraftServer} context, with chat-display convenience methods.
 * @see ServerCountdownManager#start(Duration)
 */
public final class ServerCountdown extends Countdown<MinecraftServer, ServerCountdown> {
    ServerCountdown(CountdownManager<MinecraftServer> manager, Identifier id, Duration totalDuration, Duration tickInterval, TimeSource timeSource) {
        super(manager, id, totalDuration, tickInterval, timeSource);
    }

    public ServerCountdown displayToUser(ServerPlayer player) {
        return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayToUser(remaining().toNanos(), player));
    }

    public ServerCountdown displayToUser(ServerPlayer player, TimeFormat timeFormat, String prefix, String suffix) {
        return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayToUser(remaining().toNanos(), player, timeFormat, prefix, suffix));
    }

    public ServerCountdown displayAllUsers() {
        return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayAllUsers(remaining().toNanos()));
    }

    public ServerCountdown displayAllUsers(TimeFormat timeFormat, String prefix, String suffix) {
        return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayAllUsers(remaining().toNanos(), timeFormat, prefix, suffix));
    }

    public ServerCountdown displayNearbyUsers(Vec3 position, float radius) {
        return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), position, radius));
    }

    public ServerCountdown displayNearbyUsers(Vec3 position, float radius, TimeFormat timeFormat, String prefix, String suffix) {
        return every(Duration.TICK, server -> TimelessFabricHelper.serverDisplayNearbyUsers(remaining().toNanos(), position, radius, timeFormat, prefix, suffix));
    }

    public ServerCountdown displayNearbyUsers(ServerPlayer player, float radius) {
        return displayNearbyUsers(player.position(), radius);
    }

    public ServerCountdown displayNearbyUsers(ServerPlayer player, float radius, TimeFormat timeFormat, String prefix, String suffix) {
        return displayNearbyUsers(player.position(), radius, timeFormat, prefix, suffix);
    }

    public ServerCountdown displayNearbyUsers(BlockPos blockPos, float radius) {
        return displayNearbyUsers(new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5), radius);
    }

    public ServerCountdown displayNearbyUsers(BlockPos blockPos, float radius, TimeFormat timeFormat, String prefix, String suffix) {
        return displayNearbyUsers(new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5), radius, timeFormat, prefix, suffix);
    }

    public ServerCountdown displayNearbyUsers(double x, double y, double z, float radius) {
        return displayNearbyUsers(new Vec3(x, y, z), radius);
    }

    public ServerCountdown displayNearbyUsers(double x, double y, double z, float radius, TimeFormat timeFormat, String prefix, String suffix) {
        return displayNearbyUsers(new Vec3(x, y, z), radius, timeFormat, prefix, suffix);
    }
}
