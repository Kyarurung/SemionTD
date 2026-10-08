package kim.biryeong.semiontd.game;

import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public final class ArenaCombatClock {
    private static final Map<ServerLevel, Long> EXTRA_TICKS = new WeakHashMap<>();
    private static boolean lifecycleRegistered;

    private ArenaCombatClock() {
    }

    public static synchronized long gameTime(ServerLevel world, long vanillaTime) {
        return vanillaTime + EXTRA_TICKS.getOrDefault(world, 0L);
    }

    public static synchronized void advance(ServerLevel world) {
        if (!lifecycleRegistered) {
            ServerWorldEvents.UNLOAD.register((server, unloadedWorld) -> remove(unloadedWorld));
            ServerLifecycleEvents.SERVER_STOPPING.register(ArenaCombatClock::clear);
            lifecycleRegistered = true;
        }
        EXTRA_TICKS.merge(world, 1L, Long::sum);
    }

    public static synchronized void remove(ServerLevel world) {
        EXTRA_TICKS.remove(world);
    }

    public static synchronized void clear(MinecraftServer server) {
        EXTRA_TICKS.keySet().removeIf(world -> world.getServer() == server);
    }
}
