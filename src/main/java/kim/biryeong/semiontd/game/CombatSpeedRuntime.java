package kim.biryeong.semiontd.game;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class CombatSpeedRuntime {
    private static final Map<ServerLevel, Float> ARENA_TICK_RATES = new IdentityHashMap<>();
    private static final Map<ServerLevel, Integer> LOGICAL_STEPS = new IdentityHashMap<>();
    private static int logicalStep = -1;

    private CombatSpeedRuntime() {
    }

    static void configure(MinecraftServer server, SemionGame game, float tickRate) {
        configure(server, game, tickRate, 1);
    }

    static void configure(MinecraftServer server, SemionGame game, float tickRate, int steps) {
        clear();
        if (game == null || tickRate <= 20.0F) {
            return;
        }
        for (ServerLevel world : server.getAllLevels()) {
            if (game.arena().containsWorld(world)) {
                ARENA_TICK_RATES.put(world, tickRate);
                LOGICAL_STEPS.put(world, Math.clamp(steps, 1, 5));
            }
        }
    }

    public static float effectiveTickRate(MinecraftServer server, ServerLevel world) {
        return ARENA_TICK_RATES.getOrDefault(world, server.tickRateManager().tickrate());
    }

    public static float effectiveTickRate(ServerLevel world) {
        return effectiveTickRate(world.getServer(), world);
    }

    public static double multiplier(Level world) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return 1.0;
        }
        Float rate = ARENA_TICK_RATES.get(serverWorld);
        return rate == null ? 1.0 : rate / 20.0;
    }

    public static int logicalSteps(Level world) {
        return world instanceof ServerLevel serverWorld ? LOGICAL_STEPS.getOrDefault(serverWorld, 1) : 1;
    }

    public static long gameTime(Level world) {
        long time = world.getGameTime();
        return logicalStep < 0 ? time : time - Math.max(0, logicalSteps(world) - 1 - logicalStep);
    }

    static void runGameStep(int step, Runnable action) {
        int previous = logicalStep;
        logicalStep = step;
        try {
            action.run();
        } finally {
            logicalStep = previous;
        }
    }

    static void clear() {
        ARENA_TICK_RATES.clear();
        LOGICAL_STEPS.clear();
    }
}
