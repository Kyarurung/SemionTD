package kim.biryeong.semiontd.game;

import com.google.gson.Gson;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class GameLaneTickPerformanceTest {
    private static final int[] SIZES = {6, 32, 128, 512};
    private static final int REPETITIONS = 100;
    private static final UUID OWNER = UUID.nameUUIDFromBytes("lane-tick-profile".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    private static final TowerType TYPE = new TowerType("lane_profile", "Lane Profile", TowerCategory.DIRECT, 0, 100, 0, 0, 20, 0);

    @GameTest(maxTicks = 20000)
    public void measuresLaneDispatch(GameTestHelper context) {
        if (!Boolean.getBoolean("semiontd.laneProfile")) {
            SemionTd.LOGGER.info("LANE_PROFILE_NOT_RUN: opt-in dispatch benchmark; no performance evidence.");
            context.succeed();
            return;
        }
        if (!"semion-td-gametest:game_lane_tick_performance_test_measures_lane_dispatch"
                .equals(System.getProperty("fabric-api.gametest.filter"))
                || !(context.getLevel().getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) {
            context.fail(Component.literal("Dispatch profile requires its exact isolated filter and GameTestServer."));
            return;
        }
        new Run(context).start();
    }

    private static final class Run {
        final GameTestHelper context;
        final com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        final List<Double> times = new ArrayList<>();
        final List<Double> serverTimes = new ArrayList<>();
        PlayerLane lane;
        int stage;
        int tick;
        long allocated;
        long gcCount;
        long gcMillis;
        long start;

        Run(GameTestHelper context) { this.context = context; }

        void start() {
            command("spark profiler start --force-java-sampler --interval 1");
            startStage();
        }

        void command(String command) {
            var server = context.getLevel().getServer();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }

        void startStage() {
            var layout = new LaneRegionLayout(1, new Vec3(.5,64,.5), List.of(new Vec3(.5,64,2.5)),
                    new Vec3(.5,64,10.5), BlockBounds.of(new BlockPos(0,63,0),new BlockPos(1024,66,10)),
                    List.of(new GridPosition(0,63,10)));
            lane = new PlayerLane(TeamId.BLUE,1,OWNER,null,layout);
            for (int i=0;i<SIZES[stage];i++) lane.addTower(new ProfileTower(i));
            tick=0;
            times.clear();
            serverTimes.clear();
            context.runAfterDelay(1,this::sample);
        }

        void sample() {
            try {
                if (tick==40) {
                    allocated=threads.getThreadAllocatedBytes(Thread.currentThread().threadId());
                    gcCount=gc(false);
                    gcMillis=gc(true);
                    start=System.nanoTime();
                }
                long before=System.nanoTime();
                for(int i=0;i<REPETITIONS;i++) lane.tickTowers();
                double elapsed=(System.nanoTime()-before)/1_000_000.0;
                if(tick>=40) {
                    times.add(elapsed/REPETITIONS);
                    var server=context.getLevel().getServer();
                    long[] samples=server.getTickTimesNanos();
                    serverTimes.add(samples[Math.floorMod(server.getTickCount()-1,samples.length)]/1_000_000.0);
                }
                if(++tick<190) {
                    context.runAfterDelay(1,this::sample);
                    return;
                }
                long allocatedBytes=threads.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated;
                long callbacks=lane.towers().stream().mapToLong(t->((ProfileTower)t).ticks).sum();
                if(callbacks != (long)SIZES[stage]*tick*REPETITIONS) throw new AssertionError("Callback count changed: "+callbacks);
                SemionTd.LOGGER.info("LANE_DISPATCH_PROFILE {}",new Gson().toJson(Map.of(
                        "variant",System.getProperty("semiontd.profileVariant","unspecified"),
                        "towers",SIZES[stage],"repetitionsPerTick",REPETITIONS,
                        "dispatchMs",stats(times),"serverMspt",stats(serverTimes),
                        "serverThreadAllocatedBytes",allocatedBytes,"gcCount",gc(false)-gcCount,
                        "gcMillis",gc(true)-gcMillis,"wallSeconds",(System.nanoTime()-start)/1e9,
                        "limits","SYNTHETIC_NOOP_TOWERS; DISPATCH_ONLY; NO_CLIENTS; NOT_FULL_MATCH")));
                lane.clearTowers();
                if(++stage<SIZES.length) {startStage();return;}
                command("spark profiler stop --save-to-file");
                context.runAfterDelay(1,this::finish);
            } catch (RuntimeException|AssertionError e) {
                command("spark profiler stop --save-to-file");
                context.fail(Component.literal(e.toString()));
            }
        }

        void finish() {
            try (var files = java.nio.file.Files.list(java.nio.file.Path.of("config/spark"))) {
                if (files.anyMatch(p -> p.toString().endsWith(".sparkprofile"))) {
                    context.succeed();
                    return;
                }
            } catch (java.io.IOException e) {
                context.fail(Component.literal(e.toString()));
                return;
            }
            context.runAfterDelay(1,this::finish);
        }

        static long gc(boolean time) {return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b->time?b.getCollectionTime():b.getCollectionCount()).filter(v->v>=0).sum();}
        static Map<String,Double> stats(List<Double> input) {
            var sorted=input.stream().sorted().toList();
            return Map.of("mean",input.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                    "median",sorted.get(sorted.size()/2),"p95",sorted.get((int)Math.ceil(sorted.size()*.95)-1),
                    "max",sorted.getLast());
        }
    }

    private static final class ProfileTower extends Tower {
        long ticks;
        ProfileTower(int index) {super(TYPE,OWNER,TeamId.BLUE,1,new GridPosition(index,64,0));}
        @Override public void tick(PlayerLane lane) {ticks++;}
        @Override protected boolean execute(PlayerLane lane) {return false;}
    }
}
