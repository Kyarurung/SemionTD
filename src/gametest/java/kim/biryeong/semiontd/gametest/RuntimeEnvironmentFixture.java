package kim.biryeong.semiontd.gametest;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.LoggerFactory;

public final class RuntimeEnvironmentFixture implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var players = server.getPlayerList();
            LoggerFactory.getLogger(RuntimeEnvironmentFixture.class).info(
                    "GameTest server distances before runtime fixture: view={}, simulation={}",
                    players.getViewDistance(), players.getSimulationDistance());
            players.setViewDistance(10);
            players.setSimulationDistance(10);
        });
    }
    static void configureWorld(net.minecraft.server.level.ServerLevel world) {
        if (!(world.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) {
            throw new IllegalStateException("Runtime fixture requires a GameTest server.");
        }
        var players = world.getServer().getPlayerList();
        world.getChunkSource().setViewDistance(players.getViewDistance());
        world.getChunkSource().setSimulationDistance(players.getSimulationDistance());
    }
}
