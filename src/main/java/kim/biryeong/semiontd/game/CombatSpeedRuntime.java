package kim.biryeong.semiontd.game;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class CombatSpeedRuntime {
    private static final Map<ServerLevel, Float> ARENA_TICK_RATES = new IdentityHashMap<>();

    private CombatSpeedRuntime() {
    }

    static void configure(MinecraftServer server, SemionGame game, float tickRate) {
        clear();
        if (game == null || tickRate <= 20.0F) {
            return;
        }
        for (ServerLevel world : server.getAllLevels()) {
            if (game.arena().containsWorld(world)) {
                ARENA_TICK_RATES.put(world, tickRate);
            }
        }
    }

    public static float effectiveTickRate(MinecraftServer server, ServerLevel world) {
        return ARENA_TICK_RATES.getOrDefault(world, server.tickRateManager().tickrate());
    }

    public static float effectiveTickRate(ServerLevel world) {
        return effectiveTickRate(world.getServer(), world);
    }

    static void clear() {
        ARENA_TICK_RATES.clear();
    }
}
