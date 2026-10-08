package kim.biryeong.semiontd.tower.engineer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.game.CombatSpeedRuntime;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

public final class EngineerCircuitWorld implements AutoCloseable {
    private static final Map<ServerLevel, EngineerCircuitWorld> WORLDS = new IdentityHashMap<>();
    private static final int PUBLISH_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SKIP_ON_PLACE | Block.UPDATE_SKIP_SHAPE_UPDATE_ON_WIRE;
    private final ServerLevel world;
    private final List<PlayerLane> lanes;
    private final UUID bridgeId = UUID.randomUUID();
    private EngineerCircuitTopology.Capture capture;
    private EngineerCircuitSimulation.Snapshot snapshot;
    private EngineerCircuitSimulation.Snapshot publishedSnapshot;
    private Map<UUID, EngineerCircuitSimulation.NodeState> publishedStates = Map.of();
    private final Map<UUID, PendingRestore> admissions = new HashMap<>();
    private Map<UUID, EngineerCircuitSimulation.NodeState> states = Map.of();
    private long topologyRevision;
    private long stateRevision;
    private boolean closed;

    public EngineerCircuitWorld(ServerLevel world, List<PlayerLane> lanes) {
        this.world = Objects.requireNonNull(world);
        this.lanes = List.copyOf(lanes);
        requireServerThread();
        if (this.lanes.stream().anyMatch(lane -> lane.arenaWorld() != world)) {
            throw new IllegalArgumentException("Circuit lanes must belong to the bridge world");
        }
        if (WORLDS.containsKey(world)) {
            throw new IllegalStateException("World already has an engineer circuit bridge");
        }
        refreshTopology();
        publishedSnapshot = snapshot;
        publishedStates = states;
        admissions.clear();
        WORLDS.put(world, this);
    }

    public static EngineerCircuitWorld current(ServerLevel world) {
        if (!world.getServer().isSameThread()) {
            throw new IllegalStateException("Circuit world access requires the server thread");
        }
        return WORLDS.get(world);
    }

    public Request request(long targetTick, List<EngineerCircuitSimulation.Input> inputs) {
        requireOpen();
        refreshTopology();
        return new Request(bridgeId, topologyRevision, stateRevision, capture.graph(), snapshot, targetTick, inputs);
    }

    public static Result calculate(Request request) {
        EngineerCircuitSimulation simulation = EngineerCircuitSimulation.restore(request.graph(), request.snapshot());
        List<EngineerCircuitSimulation.Change> changes = simulation.advanceTo(request.targetTick(), request.inputs());
        return new Result(request, simulation.snapshot(), changes);
    }

    public boolean accept(Result result) {
        requireOpen();
        Request request = result.request();
        if (!bridgeId.equals(request.bridgeId()) || topologyRevision != request.topologyRevision()
                || stateRevision != request.stateRevision() || !snapshot.equals(request.snapshot())
                || !capture.graph().equals(request.graph()) || result.snapshot().tick() != request.targetTick()) {
            return false;
        }
        EngineerCircuitSimulation.restore(capture.graph(), result.snapshot());
        install(result.snapshot());
        stateRevision++;
        return true;
    }

    public void refreshTopology() {
        requireOpen();
        CombatSimulationRuntime.nativeAccess(() -> {
            EngineerCircuitTopology.Capture next = EngineerCircuitTopology.capture(world, bridgeId, lanes);
            if (capture != null && capture.graph().equals(next.graph())
                    && snapshot.boundarySignals().equals(next.boundaries())) {
                capture = next;
                clearNativeTicks();
                return;
            }
            long tick = snapshot == null ? CombatSpeedRuntime.gameTime(world) : snapshot.tick();
            Map<UUID, EngineerCircuitSimulation.Node> previousNodes = new HashMap<>();
            if (capture != null) {
                capture.graph().nodes().forEach(node -> previousNodes.put(node.id(), node));
                resumeRemovedSources(next);
            }
            List<EngineerCircuitSimulation.NodeState> nextStates = new ArrayList<>();
            List<EngineerCircuitSimulation.Transition> nextPending = new ArrayList<>();
            long sequence = snapshot == null ? 0 : snapshot.nextSequence();
            List<ScheduledSource> nativeTicks = new ArrayList<>();
            List<UUID> admittedSources = new ArrayList<>();
            for (EngineerCircuitTopology.Binding binding : next.bindings()) {
                EngineerCircuitSimulation.Node node = binding.node();
                EngineerCircuitSimulation.Node previous = previousNodes.get(node.id());
                EngineerCircuitSimulation.NodeState state = states.get(node.id());
                boolean retain = state != null && previous != null && previous.kind() == node.kind();
                if (retain) {
                    nextStates.add(state);
                    snapshot.pending().stream().filter(transition -> transition.nodeId().equals(node.id()))
                            .forEach(nextPending::add);
                    continue;
                }
                if (node.kind() != EngineerCircuitSimulation.Kind.TERMINAL) {
                    admittedSources.add(node.id());
                }
                int strength = power(binding.physicalState());
                long pressedAt = binding.circuit() == null ? Long.MIN_VALUE : binding.circuit().lastPressedGameTime();
                long releaseAt = Long.MIN_VALUE;
                if (node.kind() == EngineerCircuitSimulation.Kind.TERMINAL) {
                    strength = 0;
                } else if (node.kind() == EngineerCircuitSimulation.Kind.PLATE && strength > 0) {
                    ScheduledTick<Block> scheduled = nextScheduled(binding);
                    releaseAt = scheduled == null ? tick + 10 : logicalDue(scheduled.triggerTick(), tick);
                    nativeTicks.add(new ScheduledSource(node.id(), releaseAt,
                            scheduled == null ? TickPriority.NORMAL : scheduled.priority(),
                            scheduled == null ? Long.MAX_VALUE : scheduled.subTickOrder(),
                            EngineerCircuitSimulation.TransitionKind.PLATE_RELEASE));
                } else if (node.kind() == EngineerCircuitSimulation.Kind.REPEATER) {
                    ScheduledTick<Block> scheduled = nextScheduled(binding);
                    if (scheduled != null) {
                        nativeTicks.add(new ScheduledSource(node.id(), logicalDue(scheduled.triggerTick(), tick),
                                scheduled.priority(), scheduled.subTickOrder(),
                                EngineerCircuitSimulation.TransitionKind.REPEATER_TICK));
                    }
                }
                boolean locked = node.kind() == EngineerCircuitSimulation.Kind.REPEATER
                        && binding.physicalState().getValue(RepeaterBlock.LOCKED);
                nextStates.add(new EngineerCircuitSimulation.NodeState(node.id(), strength, locked,
                        node.kind() == EngineerCircuitSimulation.Kind.PLATE ? pressedAt : Long.MIN_VALUE, releaseAt));
            }
            nativeTicks.sort(Comparator.comparingLong(ScheduledSource::dueTick)
                    .thenComparing(ScheduledSource::priority).thenComparingLong(ScheduledSource::subTickOrder));
            for (ScheduledSource transition : nativeTicks) {
                nextPending.add(new EngineerCircuitSimulation.Transition(transition.nodeId(), transition.dueTick(),
                        EngineerCircuitSimulation.Priority.valueOf(transition.priority().name()), sequence++, transition.kind()));
            }
            capture = next;
            var seed = new EngineerCircuitSimulation.Snapshot(tick, sequence, nextStates, next.boundaries(), nextPending);
            for (UUID id : admittedSources) {
                admissions.put(id, new PendingRestore(tick, nextPending.stream()
                        .filter(transition -> transition.nodeId().equals(id)).toList()));
            }
            EngineerCircuitSimulation simulation = EngineerCircuitSimulation.restore(next.graph(), seed);
            simulation.advanceTo(tick, List.of(new EngineerCircuitSimulation.BoundaryUpdate(tick, next.boundaries())));
            install(simulation.snapshot());
            topologyRevision++;
            stateRevision++;
            clearNativeTicks();
        });
    }

    public BlockState getBlockState(BlockPos position, BlockState physicalState) {
        requireOpen();
        EngineerCircuitTopology.Binding binding = capture.sources().get(position);
        if (binding == null || !physicalState.is(binding.physicalState().getBlock())) {
            return physicalState;
        }
        EngineerCircuitSimulation.NodeState state = states.get(binding.node().id());
        return withPower(physicalState, state.strength(), state.locked());
    }

    public Boolean neighborSignal(BlockPos position) {
        requireOpen();
        EngineerCircuitTopology.Binding terminal = capture.terminals().get(position);
        return terminal == null ? null : states.get(terminal.node().id()).strength() > 0;
    }

    public Boolean pressPlate(PlayerLane lane, EngineerCircuitTower circuit) {
        requireOpen();
        if (!lanes.contains(lane) || !lane.towers().contains(circuit)) {
            return null;
        }
        refreshTopology();
        EngineerCircuitTopology.Binding binding = capture.sources().get(circuit.circuitPosition());
        if (binding == null || binding.circuit() != circuit || circuit.plateKind() == null) {
            return false;
        }
        Request request = new Request(bridgeId, topologyRevision, stateRevision, capture.graph(), snapshot,
                snapshot.tick(), List.of(new EngineerCircuitSimulation.PlatePress(snapshot.tick(), binding.node().id())));
        return accept(calculate(request)) && states.get(binding.node().id()).strength() > 0;
    }

    public EngineerCircuitSimulation.Snapshot snapshot() {
        requireOpen();
        return snapshot;
    }

    public void publish() {
        publish(snapshot());
    }

    public void publish(EngineerCircuitSimulation.Snapshot completedSnapshot) {
        requireOpen();
        Map<UUID, EngineerCircuitSimulation.NodeState> completedStates = new HashMap<>();
        completedSnapshot.states().forEach(state -> completedStates.put(state.nodeId(), state));
        CombatSimulationRuntime.nativeAccess(() -> {
            for (EngineerCircuitTopology.Binding binding : capture.sources().values()) {
                publish(binding, completedStates);
            }
            clearNativeTicks();
        });
        publishedSnapshot = completedSnapshot;
        publishedStates = Map.copyOf(completedStates);
        admissions.keySet().removeAll(completedStates.keySet());
    }

    @Override
    public void close() {
        close(snapshot);
    }

    public void close(EngineerCircuitSimulation.Snapshot completedSnapshot) {
        requireServerThread();
        if (closed) {
            return;
        }
        publish(completedSnapshot);
        WORLDS.remove(world, this);
        CombatSimulationRuntime.nativeAccess(() -> {
            for (EngineerCircuitTopology.Binding binding : capture.sources().values()) {
                UUID id = binding.node().id();
                if (publishedStates.containsKey(id)) {
                    resume(binding, completedSnapshot);
                } else if (admissions.containsKey(id)) {
                    resume(binding, admissions.get(id));
                }
            }
        });
        admissions.clear();
        closed = true;
    }

    static BlockState withPower(BlockState state, int strength, boolean locked) {
        if (state.hasProperty(BlockStateProperties.POWER)) {
            state = state.setValue(BlockStateProperties.POWER, strength);
        } else if (state.hasProperty(BlockStateProperties.POWERED)) {
            state = state.setValue(BlockStateProperties.POWERED, strength > 0);
        }
        if (state.hasProperty(RepeaterBlock.LOCKED)) {
            state = state.setValue(RepeaterBlock.LOCKED, locked);
        }
        return state;
    }

    private static int power(BlockState state) {
        if (state.hasProperty(BlockStateProperties.POWER)) {
            return state.getValue(BlockStateProperties.POWER);
        }
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }

    private void install(EngineerCircuitSimulation.Snapshot next) {
        snapshot = next;
        Map<UUID, EngineerCircuitSimulation.NodeState> indexed = new HashMap<>();
        for (EngineerCircuitSimulation.NodeState state : next.states()) {
            indexed.put(state.nodeId(), state);
        }
        states = Map.copyOf(indexed);
        for (EngineerCircuitTopology.Binding binding : capture.bindings()) {
            if (binding.circuit() != null && binding.circuit().plateKind() != null) {
                binding.circuit().recordPressTime(states.get(binding.node().id()).pressedAt());
            }
        }
    }

    private long logicalDue(long nativeTick, long logicalTick) {
        return Math.addExact(logicalTick, Math.max(1, nativeTick - world.getGameTime()));
    }

    private List<ScheduledTick<Block>> scheduledTicks(EngineerCircuitTopology.Binding binding) {
        return chunkTicks(binding.position()).getAll().filter(transition -> transition.pos().equals(binding.position())
                && transition.type() == binding.physicalState().getBlock()).toList();
    }

    private ScheduledTick<Block> nextScheduled(EngineerCircuitTopology.Binding binding) {
        return scheduledTicks(binding).stream().min(Comparator.comparingLong(ScheduledTick<Block>::triggerTick)
                .thenComparing(ScheduledTick::priority).thenComparingLong(ScheduledTick::subTickOrder)).orElse(null);
    }

    private LevelChunkTicks<Block> chunkTicks(BlockPos position) {
        return (LevelChunkTicks<Block>) world.getChunkAt(position).getBlockTicks();
    }

    private void clearNativeTicks() {
        for (EngineerCircuitTopology.Binding binding : capture.sources().values()) {
            chunkTicks(binding.position()).removeIf(transition -> transition.pos().equals(binding.position())
                    && transition.type() == binding.physicalState().getBlock());
        }
    }

    private void publish(EngineerCircuitTopology.Binding binding,
                         Map<UUID, EngineerCircuitSimulation.NodeState> presentationStates) {
        if (!world.hasChunkAt(binding.position())) {
            return;
        }
        BlockState physical = world.getBlockState(binding.position());
        var state = presentationStates.get(binding.node().id());
        if (state != null && physical.is(binding.physicalState().getBlock())) {
            world.setBlock(binding.position(), withPower(physical, state.strength(), state.locked()), PUBLISH_FLAGS);
        }
    }

    private void resumeRemovedSources(EngineerCircuitTopology.Capture next) {
        for (EngineerCircuitTopology.Binding previous : capture.sources().values()) {
            var replacement = next.sources().get(previous.position());
            UUID id = previous.node().id();
            if (replacement == null) {
                if (publishedStates.containsKey(id)) {
                    publish(previous, publishedStates);
                    resume(previous, publishedSnapshot);
                } else if (admissions.containsKey(id)) {
                    resume(previous, admissions.get(id));
                }
                admissions.remove(id);
            } else if (!replacement.node().id().equals(id)) {
                admissions.remove(id);
            }
        }
    }

    private void resume(EngineerCircuitTopology.Binding binding, EngineerCircuitSimulation.Snapshot completedSnapshot) {
        resume(binding, new PendingRestore(completedSnapshot.tick(), completedSnapshot.pending()));
    }

    private void resume(EngineerCircuitTopology.Binding binding, PendingRestore completed) {
        if (!world.hasChunkAt(binding.position())
                || !world.getBlockState(binding.position()).is(binding.physicalState().getBlock())) {
            return;
        }
        for (EngineerCircuitSimulation.Transition transition : completed.pending()) {
            if (transition.nodeId().equals(binding.node().id())) {
                long remaining = Math.max(1, transition.dueTick() - completed.tick());
                world.scheduleTick(binding.position(), binding.physicalState().getBlock(),
                        Math.toIntExact(Math.min(Integer.MAX_VALUE, remaining)),
                        TickPriority.valueOf(transition.priority().name()));
            }
        }
    }

    private void requireServerThread() {
        if (!world.getServer().isSameThread()) {
            throw new IllegalStateException("Circuit bridge requires the server thread");
        }
    }

    private void requireOpen() {
        requireServerThread();
        if (closed) {
            throw new IllegalStateException("Circuit bridge is closed");
        }
    }

    public record Request(UUID bridgeId, long topologyRevision, long stateRevision,
                          EngineerCircuitSimulation.Graph graph, EngineerCircuitSimulation.Snapshot snapshot,
                          long targetTick, List<EngineerCircuitSimulation.Input> inputs) {
        public Request {
            Objects.requireNonNull(bridgeId);
            Objects.requireNonNull(graph);
            Objects.requireNonNull(snapshot);
            inputs = List.copyOf(inputs);
        }
    }

    public record Result(Request request, EngineerCircuitSimulation.Snapshot snapshot,
                         List<EngineerCircuitSimulation.Change> changes) {
        public Result {
            Objects.requireNonNull(request);
            Objects.requireNonNull(snapshot);
            changes = List.copyOf(changes);
        }
    }

    private record ScheduledSource(UUID nodeId, long dueTick, TickPriority priority, long subTickOrder,
                                   EngineerCircuitSimulation.TransitionKind kind) {
    }

    private record PendingRestore(long tick, List<EngineerCircuitSimulation.Transition> pending) {
    }
}
