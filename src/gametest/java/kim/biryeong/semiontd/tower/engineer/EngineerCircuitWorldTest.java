package kim.biryeong.semiontd.tower.engineer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.fantasy.Fantasy;
import xyz.nucleoid.fantasy.RuntimeLevelConfig;
import xyz.nucleoid.fantasy.RuntimeLevelHandle;
import xyz.nucleoid.fantasy.util.VoidChunkGenerator;
import xyz.nucleoid.map_templates.BlockBounds;

public final class EngineerCircuitWorldTest {
    @GameTest(maxTicks = 160)
    public void nativeCircuitTracesMatchBridgeAndOneTwoStepWorkerFrames(GameTestHelper context) {
        var server = context.getLevel().getServer();
        RuntimeLevelHandle handle = Fantasy.get(server).openTemporaryLevel(
                Identifier.fromNamespaceAndPath("semion-td-gametest", "engineer_circuit_" + UUID.randomUUID()),
                new RuntimeLevelConfig().setGenerator(new VoidChunkGenerator(server))
                        .setGameRule(GameRules.SPAWN_MOBS, false));
        handle.setTickWhenEmpty(true);
        ServerLevel world = handle.asLevel();
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                world.setChunkForced(x, z, true);
                world.getChunk(x, z);
            }
        }
        context.startSequence().thenWaitUntil(() -> {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    context.assertTrue(world.areEntitiesActuallyLoadedAndTicking(new ChunkPos(x, z)),
                            "Circuit reference chunks must be ticking");
                }
            }
        }).thenExecute(() -> {
            Fixture fixture;
            try {
                fixture = new Fixture(world, handle);
            } catch (Throwable failure) {
                handle.unload();
                context.fail(Component.literal("Circuit fixture failed: " + failure));
                return;
            }
            runTrace(context, fixture, 0);
        });
    }

    private static void runTrace(GameTestHelper context, Fixture fixture, int elapsed) {
        try {
            if (elapsed == 0) {
                fixture.start();
            } else {
                require(fixture.world.getGameTime() == fixture.startTick + elapsed,
                        "The isolated native world must advance exactly one physical tick per reference step");
                var request = fixture.bridge.request(fixture.startTick + elapsed, List.of());
                var result = CompletableFuture.supplyAsync(() -> EngineerCircuitWorld.calculate(request)).join();
                require(fixture.bridge.accept(result), "Current worker result must be accepted");
                fixture.one.advanceTo(fixture.startTick + elapsed, List.of());
                if (elapsed % 2 == 0) {
                    var inputs = elapsed == 26 ? List.<EngineerCircuitSimulation.Input>of(
                            new EngineerCircuitSimulation.PlatePress(fixture.startTick + 25,
                                    fixture.lockedBridge.plate.logicalId())) : List.<EngineerCircuitSimulation.Input>of();
                    fixture.two.advanceTo(fixture.startTick + elapsed, inputs);
                }
            }
            if (elapsed == 4) {
                fixture.lockedNative.sidePlate.pressPlate(fixture.nativeLane);
                require(fixture.lockedBridge.sidePlate.pressPlate(fixture.bridgeLane), "Side plate press succeeds");
                var press = new EngineerCircuitSimulation.PlatePress(fixture.startTick + 4,
                        fixture.lockedBridge.sidePlate.logicalId());
                fixture.one.advanceTo(fixture.startTick + 4, List.of(press));
                fixture.two.advanceTo(fixture.startTick + 4, List.of(press));
            } else if (elapsed == 25) {
                fixture.lockedNative.plate.pressPlate(fixture.nativeLane);
                require(fixture.lockedBridge.plate.pressPlate(fixture.bridgeLane), "Second main plate press succeeds");
                fixture.one.advanceTo(fixture.startTick + 25, List.of(new EngineerCircuitSimulation.PlatePress(
                        fixture.startTick + 25, fixture.lockedBridge.plate.logicalId())));
            }
            fixture.assertNativeTrace();
            require(fixture.bridge.snapshot().equals(fixture.one.snapshot()), "One-step worker snapshot matches bridge");
            if (elapsed % 2 == 0) {
                require(fixture.one.snapshot().equals(fixture.two.snapshot()), "Two-step worker snapshot matches one-step");
                fixture.bridge.publish(fixture.bridge.snapshot());
                fixture.assertPublished();
            }
            if (elapsed == 40) {
                fixture.assertMutableBoundaryAndTopology();
                fixture.close();
                context.succeed();
            } else {
                context.runAfterDelay(1, () -> runTrace(context, fixture, elapsed + 1));
            }
        } catch (Throwable failure) {
            fixture.close();
            context.fail(Component.literal("Engineer native circuit trace failed at " + elapsed + ": " + failure));
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final ServerLevel world;
        private final RuntimeLevelHandle handle;
        private final PlayerLane bridgeLane;
        private final PlayerLane nativeLane;
        private final List<Pair> pairs = new ArrayList<>();
        private final List<TerminalPair> terminalPairs = new ArrayList<>();
        private final List<EngineerCircuitTower> initialBridgePresses = new ArrayList<>();
        private final List<EngineerCircuitTower> initialNativePresses = new ArrayList<>();
        private final LockedCircuit lockedBridge;
        private final LockedCircuit lockedNative;
        private final EngineerCircuitWorld bridge;
        private EngineerCircuitSimulation one;
        private EngineerCircuitSimulation two;
        private long startTick;
        private boolean closed;

        private Fixture(ServerLevel world, RuntimeLevelHandle handle) {
            this.world = world;
            this.handle = handle;
            bridgeLane = lane(world, UUID.randomUUID());
            nativeLane = lane(world, UUID.randomUUID());
            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    world.setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            addAttenuation(bridgeLane, 3, nativeLane, 7);
            lockedBridge = locked(bridgeLane, 12);
            lockedNative = locked(nativeLane, 17);
            pairs.add(new Pair(lockedBridge.plate, lockedNative.plate));
            pairs.add(new Pair(lockedBridge.repeater, lockedNative.repeater));
            pairs.add(new Pair(lockedBridge.sidePlate, lockedNative.sidePlate));
            pairs.add(new Pair(lockedBridge.sideRepeater, lockedNative.sideRepeater));
            initialBridgePresses.add(lockedBridge.plate);
            initialNativePresses.add(lockedNative.plate);
            addBent(bridgeLane, 22, nativeLane, 27);
            addStairs(bridgeLane, 3, nativeLane, 7);
            terminalPairs.add(new TerminalPair(trap(bridgeLane, 19, 3).signalPosition(),
                    trap(nativeLane, 19, 7).signalPosition()));
            bridge = new EngineerCircuitWorld(world, List.of(bridgeLane));
        }

        private void start() {
            startTick = world.getGameTime();
            var seed = bridge.snapshot();
            var request = bridge.request(startTick, List.of());
            one = EngineerCircuitSimulation.restore(request.graph(), seed);
            two = EngineerCircuitSimulation.restore(request.graph(), seed);
            List<EngineerCircuitSimulation.Input> inputs = new ArrayList<>();
            for (int i = 0; i < initialBridgePresses.size(); i++) {
                require(initialNativePresses.get(i).pressPlate(nativeLane), "Native reference plate press succeeds");
                EngineerCircuitTower plate = initialBridgePresses.get(i);
                require(plate.pressPlate(bridgeLane), "Bridge plate press succeeds");
                inputs.add(new EngineerCircuitSimulation.PlatePress(startTick, plate.logicalId()));
            }
            one.advanceTo(startTick, inputs);
            two.advanceTo(startTick, inputs);
            long revision = bridge.snapshot().nextSequence();
            bridge.publish(seed);
            require(bridge.snapshot().nextSequence() == revision, "Completed publication cannot change logical state");
            require(!world.getBlockState(initialBridgePresses.getFirst().circuitPosition())
                    .getValue(BlockStateProperties.POWERED), "Completed off snapshot cannot expose a partial press");
        }

        private void addAttenuation(PlayerLane first, int firstZ, PlayerLane second, int secondZ) {
            EngineerCircuitTower firstPlate = plate(first, 1, firstZ);
            EngineerCircuitTower secondPlate = plate(second, 1, secondZ);
            pairs.add(new Pair(firstPlate, secondPlate));
            initialBridgePresses.add(firstPlate);
            initialNativePresses.add(secondPlate);
            for (int x = 2; x <= 18; x++) {
                pairs.add(new Pair(circuit(first, EngineerTowers.REDSTONE_DUST, x, 65, firstZ),
                        circuit(second, EngineerTowers.REDSTONE_DUST, x, 65, secondZ)));
            }
        }

        private LockedCircuit locked(PlayerLane lane, int z) {
            EngineerCircuitTower plate = plate(lane, 3, z);
            EngineerCircuitTower repeater = circuit(lane, EngineerTowers.repeater(Direction.EAST), 4, 65, z);
            EngineerCircuitTower sideRepeater = circuit(lane, EngineerTowers.repeater(Direction.NORTH), 4, 65, z + 1);
            EngineerCircuitTower sidePlate = plate(lane, 4, z + 2);
            return new LockedCircuit(plate, repeater, sidePlate, sideRepeater);
        }

        private void addBent(PlayerLane first, int firstZ, PlayerLane second, int secondZ) {
            for (PlayerLane lane : List.of(first, second)) {
                int z = lane == first ? firstZ : secondZ;
                EngineerCircuitTower plate = plate(lane, 10, z);
                circuit(lane, EngineerTowers.repeater(Direction.EAST), 11, 65, z);
                world.setBlock(new BlockPos(12, 65, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                circuit(lane, EngineerTowers.repeater(Direction.NORTH), 12, 65, z - 1);
                if (lane == first) {
                    initialBridgePresses.add(plate);
                } else {
                    initialNativePresses.add(plate);
                }
            }
            for (int i = 0; i < 3; i++) {
                var firstCircuit = first.towers().get(first.towers().size() - 3 + i);
                var secondCircuit = second.towers().get(second.towers().size() - 3 + i);
                pairs.add(new Pair((EngineerCircuitTower) firstCircuit, (EngineerCircuitTower) secondCircuit));
            }
        }

        private void assertNativeTrace() {
            for (Pair pair : pairs) {
                BlockState physical = world.getBlockState(pair.bridge.circuitPosition());
                BlockState logical = bridge.getBlockState(pair.bridge.circuitPosition(), physical);
                BlockState nativeState = world.getBlockState(pair.nativeCircuit.circuitPosition());
                require(power(logical) == power(nativeState), "Native power differs for " + pair.bridge.type().id()
                        + " at " + pair.bridge.circuitPosition() + ": " + power(logical) + " vs " + power(nativeState));
                if (logical.hasProperty(RepeaterBlock.LOCKED)) {
                    require(logical.getValue(RepeaterBlock.LOCKED).equals(nativeState.getValue(RepeaterBlock.LOCKED)),
                            "Native repeater lock differs");
                }
                require(bridge.neighborSignal(pair.bridge.circuitPosition())
                                == world.hasNeighborSignal(pair.nativeCircuit.circuitPosition()),
                        "Native neighbor power differs for " + pair.bridge.circuitPosition());
            }
            for (TerminalPair pair : terminalPairs) {
                require(bridge.neighborSignal(pair.bridge) == world.hasNeighborSignal(pair.nativePosition),
                        "Registered trap terminal sees the same native neighbor power");
            }
        }

        private void addStairs(PlayerLane first, int firstZ, PlayerLane second, int secondZ) {
            List<EngineerCircuitTower> firstNodes = stairs(first, firstZ);
            List<EngineerCircuitTower> secondNodes = stairs(second, secondZ);
            initialBridgePresses.add(firstNodes.getFirst());
            initialNativePresses.add(secondNodes.getFirst());
            for (int i = 0; i < firstNodes.size(); i++) {
                pairs.add(new Pair(firstNodes.get(i), secondNodes.get(i)));
            }
        }

        private List<EngineerCircuitTower> stairs(PlayerLane lane, int z) {
            var plate = plate(lane, 22, z);
            var lower = circuit(lane, EngineerTowers.REDSTONE_DUST, 23, 65, z);
            var upper = circuit(lane, EngineerTowers.REDSTONE_DUST, 24, 66, z);
            var far = circuit(lane, EngineerTowers.REDSTONE_DUST, 25, 66, z);
            return List.of(plate, lower, upper, far);
        }

        private void assertPublished() {
            for (Pair pair : pairs) {
                require(power(world.getBlockState(pair.bridge.circuitPosition()))
                                == power(world.getBlockState(pair.nativeCircuit.circuitPosition())),
                        "Physical frame publication matches native power");
                var chunk = (net.minecraft.world.ticks.LevelChunkTicks<Block>)
                        world.getChunkAt(pair.bridge.circuitPosition()).getBlockTicks();
                require(chunk.getAll().noneMatch(tick -> tick.pos().equals(pair.bridge.circuitPosition())),
                        "Owned circuit has no native scheduled transition after publication");
            }
        }

        private void assertMutableBoundaryAndTopology() {
            var completed = bridge.snapshot();
            Pair lastWire = pairs.get(17);
            for (EngineerCircuitTower wire : List.of(lastWire.bridge, lastWire.nativeCircuit)) {
                world.setBlock(wire.circuitPosition().above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            }
            var request = bridge.request(bridge.snapshot().tick(), List.of());
            require(bridge.accept(EngineerCircuitWorld.calculate(request)), "Changed boundary is sampled by request");
            assertNativeTrace();
            for (EngineerCircuitTower wire : List.of(lastWire.bridge, lastWire.nativeCircuit)) {
                world.setBlock(wire.circuitPosition().above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            require(bridge.accept(EngineerCircuitWorld.calculate(bridge.request(bridge.snapshot().tick(), List.of()))),
                    "Removed boundary is sampled without waiting for a new round");
            assertNativeTrace();
            require(lockedBridge.plate.pressPlate(bridgeLane), "Topology fixture starts a pending repeater transition");
            var before = bridge.snapshot();
            var stale = bridge.request(before.tick() + 1, List.of());
            BlockPos position = lockedBridge.repeater.circuitPosition();
            world.setBlock(position, world.getBlockState(position).setValue(RepeaterBlock.DELAY, 4),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            bridge.refreshTopology();
            require(bridge.snapshot().pending().containsAll(before.pending()),
                    "Identity-stable delay change retains already scheduled transitions");
            require(!bridge.accept(EngineerCircuitWorld.calculate(stale)), "Pre-refresh worker result must be rejected");
            var current = bridge.request(bridge.snapshot().tick(), List.of());
            require(current.graph().nodes().stream().filter(node -> node.id().equals(lockedBridge.repeater.logicalId()))
                    .findFirst().orElseThrow().delayTicks() == 8, "Actual repeater delay must be captured");
            require(CompletableFuture.supplyAsync(() -> {
                try {
                    bridge.snapshot();
                    return false;
                } catch (IllegalStateException expected) {
                    return true;
                }
            }).join(), "World bridge access must reject the worker thread");
            GridPosition oldPosition = lockedBridge.plate.originalPosition();
            require(bridgeLane.removeTower(lockedBridge.plate), "Old plate can leave the topology during a partial step");
            EngineerCircuitTower replacement = circuit(bridgeLane, EngineerTowers.plate(EngineerTowers.PlateKind.WOOD),
                    oldPosition.x(), oldPosition.y() + 1, oldPosition.z());
            bridge.refreshTopology();
            require(!world.getBlockState(replacement.circuitPosition()).getValue(BlockStateProperties.POWERED),
                    "Replacing a source must not publish the old identity's partial press");
            assertNativePendingClockAndCompletedClose(completed);
        }

        private void assertNativePendingClockAndCompletedClose(EngineerCircuitSimulation.Snapshot completed) {
            require(bridge.accept(EngineerCircuitWorld.calculate(bridge.request(bridge.snapshot().tick() + 80, List.of()))),
                    "Clock fixture can advance logical circuit time independently of the native world");
            long logicalTick = bridge.snapshot().tick();
            long nativeTick = world.getGameTime();
            EngineerCircuitTower queuedPlate = plate(bridgeLane, 29, 4);
            BlockPos position = queuedPlate.circuitPosition();
            world.setBlock(position, world.getBlockState(position).setValue(BlockStateProperties.POWERED, true),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            world.scheduleTick(position, Blocks.OAK_PRESSURE_PLATE, 10);
            bridge.refreshTopology();
            var state = bridge.snapshot().states().stream()
                    .filter(value -> value.nodeId().equals(queuedPlate.logicalId())).findFirst().orElseThrow();
            require(state.releaseAt() == logicalTick + 10,
                    "New native pending release translates its remaining delay onto the logical clock");
            require(bridge.accept(EngineerCircuitWorld.calculate(bridge.request(logicalTick + 9, List.of()))),
                    "Worker reaches the tick before the captured release");
            require(bridge.getBlockState(position, world.getBlockState(position)).getValue(BlockStateProperties.POWERED),
                    "Captured plate stays pressed for all nine logical ticks");
            require(bridge.accept(EngineerCircuitWorld.calculate(bridge.request(logicalTick + 10, List.of()))),
                    "Worker reaches the captured release tick");
            require(!bridge.getBlockState(position, world.getBlockState(position)).getValue(BlockStateProperties.POWERED),
                    "Captured plate releases on the tenth logical tick");
            bridge.close(completed);
            require(world.getBlockState(position).getValue(BlockStateProperties.POWERED),
                    "Closing from an older completed snapshot does not publish a newly admitted partial release");
            var nativeTicks = (net.minecraft.world.ticks.LevelChunkTicks<Block>) world.getChunkAt(position).getBlockTicks();
            require(nativeTicks.getAll().anyMatch(tick -> tick.pos().equals(position)
                            && tick.triggerTick() == nativeTick + 10 && tick.priority() == net.minecraft.world.ticks.TickPriority.NORMAL),
                    "Unpublished admission restores its original native release delay and priority");
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            bridge.close();
            bridgeLane.clearTowers();
            nativeLane.clearTowers();
            handle.unload();
        }
    }

    private static PlayerLane lane(ServerLevel world, UUID owner) {
        var bounds = BlockBounds.of(new BlockPos(0, 64, 0), new BlockPos(31, 70, 31));
        var spawn = new Vec3(1, 65, 1);
        return new PlayerLane(TeamId.RED, 1, owner, world,
                new LaneRegionLayout(1, spawn, List.of(new Vec3(16, 65, 16)), new Vec3(30, 65, 30), bounds,
                        List.of(new GridPosition(30, 64, 30))));
    }

    private static EngineerCircuitTower plate(PlayerLane lane, int x, int z) {
        return circuit(lane, EngineerTowers.plate(EngineerTowers.PlateKind.WOOD), x, 65, z);
    }

    private static EngineerTrapTower trap(PlayerLane lane, int x, int z) {
        var floor = new GridPosition(x, 64, z);
        var trap = new EngineerTrapTower(EngineerTowers.trap(EngineerTowers.TrapKind.SLIME, 1),
                lane.ownerPlayer(), lane.teamId(), lane.laneId(), floor, floor);
        lane.addTower(trap);
        return trap;
    }

    private static EngineerCircuitTower circuit(PlayerLane lane, kim.biryeong.semiontd.tower.TowerType type,
                                                int x, int y, int z) {
        GridPosition floor = new GridPosition(x, y - 1, z);
        lane.arenaWorld().setBlock(new BlockPos(x, y - 1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        var tower = new EngineerCircuitTower(type, lane.ownerPlayer(), lane.teamId(), lane.laneId(), floor, floor);
        lane.addTower(tower);
        return tower;
    }

    private static int power(BlockState state) {
        if (state.hasProperty(BlockStateProperties.POWER)) {
            return state.getValue(BlockStateProperties.POWER);
        }
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record Pair(EngineerCircuitTower bridge, EngineerCircuitTower nativeCircuit) {
    }

    private record TerminalPair(BlockPos bridge, BlockPos nativePosition) {
    }

    private record LockedCircuit(EngineerCircuitTower plate, EngineerCircuitTower repeater,
                                 EngineerCircuitTower sidePlate, EngineerCircuitTower sideRepeater) {
    }
}
