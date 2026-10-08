package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import kim.biryeong.semiontd.mixin.accessor.ServerGamePacketListenerAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.DerivedLevelData;

public final class ArenaCombatTicker {
    private static final Set<MinecraftServer> TICKING = Collections.newSetFromMap(new IdentityHashMap<>());

    private ArenaCombatTicker() {
    }

    public static boolean tick(MinecraftServer server, SemionGame game) {
        if (server == null || game == null || game.arena() == null || game.phase() != RoundPhase.LANE_WAVE
                || !server.tickRateManager().runsNormally()) {
            return false;
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException("Arena combat must tick on the server thread.");
        }
        if (!TICKING.add(server)) {
            return false;
        }
        try {
            int round = game.currentRound();
            Set<ServerLevel> requiredWorlds = Collections.newSetFromMap(new IdentityHashMap<>());
            for (TeamId team : TeamId.values()) {
                game.arena().teamArena(team).ifPresent(arena -> requiredWorlds.add(arena.world()));
            }
            if (requiredWorlds.isEmpty() || requiredWorlds.contains(null)) {
                return false;
            }
            List<ServerLevel> worlds = new ArrayList<>();
            for (ServerLevel world : server.getAllLevels()) {
                if (requiredWorlds.contains(world)) {
                    if (!canTick(server, world)) {
                        return false;
                    }
                    worlds.add(world);
                }
            }
            if (worlds.size() != requiredWorlds.size()) {
                return false;
            }
            for (ServerLevel world : worlds) {
                if (world.getLevelData() instanceof DerivedLevelData) {
                    ArenaCombatClock.advance(world);
                }
            }
            for (ServerLevel world : worlds) {
                if (!isCurrentRound(game, round) || !canTick(server, world)) {
                    return false;
                }
                world.tick(() -> true);
            }
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                if (!isCurrentRound(game, round)) {
                    return false;
                }
                ServerLevel world = player.level();
                if (requiredWorlds.contains(world) && canTick(server, world) && !player.isRemoved()
                        && !player.hasDisconnected() && !world.tickRateManager().isEntityFrozen(player)) {
                    tickPlayer(player);
                }
            }
            return isCurrentRound(game, round) && worlds.stream().allMatch(world -> canTick(server, world));
        } finally {
            TICKING.remove(server);
        }
    }

    static void tickPlayer(ServerPlayer player) {
        ServerLevel world = player.level();
        var connection = (ServerGamePacketListenerAccessor) player.connection;
        int teleport = connection.semiontd$awaitingTeleport();
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        player.xo = x;
        player.yo = y;
        player.zo = z;
        player.doTick();
        if (player.level() == world && !player.isRemoved() && connection.semiontd$awaitingTeleport() == teleport) {
            player.absSnapTo(x, y, z, player.getYRot(), player.getXRot());
        }
    }

    private static boolean canTick(MinecraftServer server, ServerLevel world) {
        return server.getLevel(world.dimension()) == world && server.isLevelEnabled(world)
                && world.tickRateManager().runsNormally() && !world.isHandlingTick();
    }

    private static boolean isCurrentRound(SemionGame game, int round) {
        return game.phase() == RoundPhase.LANE_WAVE && game.currentRound() == round;
    }
}
