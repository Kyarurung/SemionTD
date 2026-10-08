package kim.biryeong.semiontd.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.DerivedLevelData;

public final class ArenaCombatTicker {
    private static final Set<MinecraftServer> TICKING = Collections.newSetFromMap(new IdentityHashMap<>());

    private ArenaCombatTicker() {
    }

    public static void tick(MinecraftServer server, SemionGame game) {
        if (server == null || game == null || game.arena() == null || game.phase() != RoundPhase.LANE_WAVE
                || !server.tickRateManager().runsNormally()) {
            return;
        }
        if (!server.isSameThread()) {
            throw new IllegalStateException("Arena combat must tick on the server thread.");
        }
        if (!TICKING.add(server)) {
            return;
        }
        try {
            int round = game.currentRound();
            List<ServerLevel> worlds = new ArrayList<>();
            for (ServerLevel world : server.getAllLevels()) {
                if (game.arena().containsWorld(world) && canTick(server, world)) {
                    worlds.add(world);
                }
            }
            for (ServerLevel world : worlds) {
                if (world.getLevelData() instanceof DerivedLevelData) {
                    ArenaCombatClock.advance(world);
                }
            }
            Set<ServerLevel> tickedWorlds = Collections.newSetFromMap(new IdentityHashMap<>());
            for (ServerLevel world : worlds) {
                if (!isCurrentRound(game, round)) {
                    break;
                }
                if (canTick(server, world)) {
                    world.tick(() -> true);
                    tickedWorlds.add(world);
                }
            }
            for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                if (!isCurrentRound(game, round)) {
                    break;
                }
                ServerLevel world = player.level();
                if (tickedWorlds.contains(world) && canTick(server, world) && !player.isRemoved()
                        && !player.hasDisconnected() && !world.tickRateManager().isEntityFrozen(player)) {
                    double x = player.getX();
                    double y = player.getY();
                    double z = player.getZ();
                    player.xo = x;
                    player.yo = y;
                    player.zo = z;
                    player.doTick();
                    if (player.level() == world && !player.isRemoved()) {
                        player.absSnapTo(x, y, z, player.getYRot(), player.getXRot());
                    }
                }
            }
        } finally {
            TICKING.remove(server);
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
