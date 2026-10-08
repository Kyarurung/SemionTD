package kim.biryeong.semiontd.tower.engineer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.Tower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;

final class EngineerCircuitTopology {
    private final ServerLevel world;
    private final UUID bridgeId;
    private final Map<BlockPos, Binding> sources = new LinkedHashMap<>();
    private final Map<BlockPos, Binding> terminals = new LinkedHashMap<>();
    private final ArrayDeque<Binding> pending = new ArrayDeque<>();
    private final List<Binding> bindings = new ArrayList<>();
    private final LinkedHashSet<EngineerCircuitSimulation.Edge> edges = new LinkedHashSet<>();
    private final Map<BoundaryPort, Integer> boundaries = new LinkedHashMap<>();

    private EngineerCircuitTopology(ServerLevel world, UUID bridgeId, List<PlayerLane> lanes) {
        this.world = world;
        this.bridgeId = bridgeId;
        for (PlayerLane lane : lanes) {
            for (Tower tower : lane.towers()) {
                if (tower instanceof EngineerCircuitTower circuit) {
                    Binding source = addSource(circuit.circuitPosition(), circuit.logicalId(), circuit, lane);
                    if (source != null) {
                        addTerminal(source.position(), stableId("terminal/" + source.node().id()), circuit.ownerPlayer());
                    }
                } else if (tower instanceof EngineerTrapTower trap) {
                    addTerminal(trap.signalPosition(), stableId("terminal/" + trap.logicalId()), trap.ownerPlayer());
                }
            }
        }
    }

    static Capture capture(ServerLevel world, UUID bridgeId, List<PlayerLane> lanes) {
        EngineerCircuitTopology scanner = new EngineerCircuitTopology(world, bridgeId, lanes);
        while (!scanner.pending.isEmpty()) {
            scanner.captureInputs(scanner.pending.removeFirst());
        }
        List<EngineerCircuitSimulation.AuthorizationEdge> authorization = new ArrayList<>();
        for (Binding target : scanner.bindings) {
            if (target.node().kind() != EngineerCircuitSimulation.Kind.TERMINAL && target.circuit() == null) {
                continue;
            }
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                Binding previous = scanner.sources.get(target.position().relative(direction.getOpposite()));
                if (previous != null && previous.circuit() != null) {
                    authorization.add(new EngineerCircuitSimulation.AuthorizationEdge(previous.node().id(),
                            target.node().id(), direction(direction)));
                }
            }
        }
        List<EngineerCircuitSimulation.BoundarySignal> boundarySignals = scanner.boundaries.entrySet().stream()
                .map(entry -> new EngineerCircuitSimulation.BoundarySignal(entry.getKey().nodeId(),
                        entry.getKey().port(), entry.getValue())).toList();
        return new Capture(new EngineerCircuitSimulation.Graph(scanner.bindings.stream().map(Binding::node).toList(),
                List.copyOf(scanner.edges), authorization), List.copyOf(scanner.bindings),
                Collections.unmodifiableMap(new LinkedHashMap<>(scanner.sources)),
                Collections.unmodifiableMap(new LinkedHashMap<>(scanner.terminals)), boundarySignals);
    }

    private Binding addSource(BlockPos position, UUID id, EngineerCircuitTower circuit, PlayerLane lane) {
        Binding previous = sources.get(position);
        if (previous != null) {
            return previous;
        }
        if (!world.hasChunkAt(position)) {
            return null;
        }
        BlockState state = world.getBlockState(position);
        EngineerCircuitSimulation.Kind kind;
        EngineerCircuitSimulation.Direction orientation = EngineerCircuitSimulation.Direction.NONE;
        EngineerCircuitSimulation.PlateKind plateKind = null;
        int delay = 0;
        boolean prioritize = false;
        if (state.is(Blocks.REDSTONE_WIRE)) {
            kind = EngineerCircuitSimulation.Kind.WIRE;
        } else if (state.is(Blocks.REPEATER)) {
            kind = EngineerCircuitSimulation.Kind.REPEATER;
            orientation = direction(state.getValue(RepeaterBlock.FACING).getOpposite());
            delay = state.getValue(RepeaterBlock.DELAY) * 2;
            prioritize = ((RepeaterBlock) state.getBlock()).shouldPrioritize(world, position, state);
        } else if (circuit != null && circuit.plateKind() != null && circuit.matchesPlacedBlock(state)) {
            kind = EngineerCircuitSimulation.Kind.PLATE;
            plateKind = EngineerCircuitSimulation.PlateKind.valueOf(circuit.plateKind().name());
        } else {
            return null;
        }
        if (!state.canSurvive(world, position)) {
            return null;
        }
        if (circuit != null && !circuit.matchesPlacedBlock(state)) {
            return null;
        }
        UUID nodeId = id == null ? stableId("source/" + position.asLong() + "/" + kind) : id;
        var node = new EngineerCircuitSimulation.Node(nodeId, circuit == null ? null : circuit.ownerPlayer(),
                position.getX(), position.getY(), position.getZ(), kind, orientation, plateKind, delay, prioritize);
        Binding binding = new Binding(node, position.immutable(), state, circuit, lane);
        sources.put(binding.position(), binding);
        bindings.add(binding);
        pending.addLast(binding);
        return binding;
    }

    private void addTerminal(BlockPos position, UUID id, UUID owner) {
        if (terminals.containsKey(position)) {
            return;
        }
        var node = new EngineerCircuitSimulation.Node(id, owner, position.getX(), position.getY(), position.getZ(),
                EngineerCircuitSimulation.Kind.TERMINAL, EngineerCircuitSimulation.Direction.NONE, null, 0, false);
        Binding binding = new Binding(node, position.immutable(), world.getBlockState(position), null, null);
        terminals.put(binding.position(), binding);
        bindings.add(binding);
        pending.addLast(binding);
    }

    private void captureInputs(Binding target) {
        switch (target.node().kind()) {
            case PLATE -> {
            }
            case WIRE -> {
                for (Direction direction : Direction.values()) {
                    signal(target.position().relative(direction), direction, target,
                            EngineerCircuitSimulation.Port.SIGNAL, true, false);
                }
                BlockPos above = target.position().above();
                boolean headroom = !world.getBlockState(above).isRedstoneConductor(world, above);
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos neighbor = target.position().relative(direction);
                    wireInput(neighbor, direction.getOpposite(), target);
                    boolean conductor = world.getBlockState(neighbor).isRedstoneConductor(world, neighbor);
                    if (conductor && headroom) {
                        wireInput(neighbor.above(), direction.getOpposite(), target);
                    } else if (!conductor) {
                        wireInput(neighbor.below(), direction.getOpposite(), target);
                    }
                }
            }
            case REPEATER -> {
                Direction facing = target.physicalState().getValue(RepeaterBlock.FACING);
                signal(target.position().relative(facing), facing, target,
                        EngineerCircuitSimulation.Port.REAR, false, true);
                for (Direction side : List.of(facing.getClockWise(), facing.getCounterClockWise())) {
                    BlockPos position = target.position().relative(side);
                    if (DiodeBlock.isDiode(world.getBlockState(position))) {
                        directSignal(position, side, target, EngineerCircuitSimulation.Port.SIDE, false);
                    }
                }
            }
            case TERMINAL -> {
                for (Direction direction : Direction.values()) {
                    signal(target.position().relative(direction), direction, target,
                            EngineerCircuitSimulation.Port.SIGNAL, false, false);
                }
            }
        }
    }

    private void wireInput(BlockPos position, Direction travel, Binding target) {
        if (!world.getBlockState(position).is(Blocks.REDSTONE_WIRE)) {
            return;
        }
        Binding source = addSource(position, null, null, null);
        if (source != null) {
            addEdge(source, target, travel, EngineerCircuitSimulation.Port.SIGNAL, 1);
        }
    }

    private void signal(BlockPos position, Direction queryDirection, Binding target,
                        EngineerCircuitSimulation.Port port, boolean ignoreDust, boolean rawDust) {
        BlockState state = world.getBlockState(position);
        if (!ignoreDust || !state.is(Blocks.REDSTONE_WIRE)) {
            captureEmitter(position, queryDirection, target, port, false, rawDust);
        }
        if (state.isRedstoneConductor(world, position)) {
            for (Direction direction : Direction.values()) {
                directSignal(position.relative(direction), direction, target, port, ignoreDust);
            }
        }
    }

    private void directSignal(BlockPos position, Direction queryDirection, Binding target,
                              EngineerCircuitSimulation.Port port, boolean ignoreDust) {
        if (!ignoreDust || !world.getBlockState(position).is(Blocks.REDSTONE_WIRE)) {
            captureEmitter(position, queryDirection, target, port, true, false);
        }
    }

    private void captureEmitter(BlockPos position, Direction queryDirection, Binding target,
                                EngineerCircuitSimulation.Port port, boolean direct, boolean rawDust) {
        BlockState physical = world.getBlockState(position);
        Binding source = sources.get(position);
        if (source == null && (physical.is(Blocks.REDSTONE_WIRE) || physical.is(Blocks.REPEATER))) {
            source = addSource(position, null, null, null);
            if (source == null) {
                return;
            }
        }
        if (source != null) {
            BlockState powered = EngineerCircuitWorld.withPower(source.physicalState(), 15, false);
            int output = rawDust && powered.is(Blocks.REDSTONE_WIRE) ? 15
                    : direct ? powered.getDirectSignal(world, position, queryDirection)
                    : powered.getSignal(world, position, queryDirection);
            if (output > 0) {
                addEdge(source, target, queryDirection.getOpposite(), port, 0);
            }
        } else {
            int strength = direct ? physical.getDirectSignal(world, position, queryDirection)
                    : physical.getSignal(world, position, queryDirection);
            if (strength > 0) {
                boundaries.merge(new BoundaryPort(target.node().id(), port), strength, Math::max);
            }
        }
    }

    private void addEdge(Binding source, Binding target, Direction travel,
                         EngineerCircuitSimulation.Port port, int attenuation) {
        EngineerCircuitSimulation.Direction input = port == EngineerCircuitSimulation.Port.REAR
                ? target.node().orientation() : direction(travel);
        edges.add(new EngineerCircuitSimulation.Edge(source.node().id(), target.node().id(), direction(travel),
                input, port, attenuation));
    }

    private UUID stableId(String value) {
        return UUID.nameUUIDFromBytes((bridgeId + "/" + value).getBytes(StandardCharsets.UTF_8));
    }

    private static EngineerCircuitSimulation.Direction direction(Direction direction) {
        return EngineerCircuitSimulation.Direction.valueOf(direction.name());
    }

    record Binding(EngineerCircuitSimulation.Node node, BlockPos position, BlockState physicalState,
                   EngineerCircuitTower circuit, PlayerLane lane) {
    }

    record Capture(EngineerCircuitSimulation.Graph graph, List<Binding> bindings, Map<BlockPos, Binding> sources,
                   Map<BlockPos, Binding> terminals, List<EngineerCircuitSimulation.BoundarySignal> boundaries) {
    }

    private record BoundaryPort(UUID nodeId, EngineerCircuitSimulation.Port port) {
    }
}
