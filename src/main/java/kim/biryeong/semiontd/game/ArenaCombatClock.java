package kim.biryeong.semiontd.game;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;

public final class ArenaCombatClock {
    private static final Map<ServerLevel, Long> EXTRA_TICKS = new WeakHashMap<>();

    private ArenaCombatClock() {
    }

    public static synchronized long gameTime(ServerLevel world, long vanillaTime) {
        return vanillaTime + EXTRA_TICKS.getOrDefault(world, 0L);
    }

    public static synchronized void advance(ServerLevel world) {
        EXTRA_TICKS.merge(world, 1L, Long::sum);
    }
}
