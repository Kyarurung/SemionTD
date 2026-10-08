package kim.biryeong.semiontd.game;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;

public final class ArenaCombatClock {
    private static final Map<ServerLevel, Long> EXTRA_TICKS = new WeakHashMap<>();
    private static boolean lifecycleRegistered;

    private ArenaCombatClock() {
    }

    public static synchronized long gameTime(ServerLevel world, long vanillaTime) {
        Long simulationTime = CombatSimulationRuntime.gameTime(world);
        if (simulationTime != null) {
            return simulationTime;
        }
        return vanillaTime + EXTRA_TICKS.getOrDefault(world, 0L);
    }

    public static synchronized void adopt(ServerLevel world, long logicalTime) {
        long currentTime = CombatSimulationRuntime.nativeGameTime(world);
        long offset = EXTRA_TICKS.getOrDefault(world, 0L);
        EXTRA_TICKS.put(world, Math.addExact(offset, Math.subtractExact(logicalTime, currentTime)));
        registerLifecycle();
    }

    public static synchronized void advance(ServerLevel world) {
        registerLifecycle();
        EXTRA_TICKS.merge(world, 1L, Long::sum);
    }

    private static void registerLifecycle() {
        if (!lifecycleRegistered) {
            ServerLevelEvents.UNLOAD.register((server, unloadedWorld) -> remove(unloadedWorld));
            ServerLifecycleEvents.SERVER_STOPPING.register(ArenaCombatClock::clear);
            lifecycleRegistered = true;
        }
    }

    public static synchronized void remove(ServerLevel world) {
        EXTRA_TICKS.remove(world);
    }

    public static synchronized void clear(MinecraftServer server) {
        EXTRA_TICKS.keySet().removeIf(world -> world.getServer() == server);
    }
}
