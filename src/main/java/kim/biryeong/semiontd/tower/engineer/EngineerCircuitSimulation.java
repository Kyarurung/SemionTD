package kim.biryeong.semiontd.tower.engineer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public final class EngineerCircuitSimulation {
    private static final Comparator<Transition> TRANSITION_ORDER = Comparator.comparingLong(Transition::dueTick)
            .thenComparing(Transition::priority).thenComparingLong(Transition::sequence);
    private final Graph graph;
    private final Map<UUID, Node> nodes = new LinkedHashMap<>();
    private final Map<UUID, List<Edge>> incoming = new HashMap<>();
    private final Map<UUID, List<AuthorizationEdge>> authorizedIncoming = new HashMap<>();
    private final Map<UUID, NodeState> states = new LinkedHashMap<>();
    private final PriorityQueue<Transition> pending = new PriorityQueue<>(TRANSITION_ORDER);
    private List<BoundarySignal> boundarySignals = List.of();
    private long tick;
    private long sequence;

    public EngineerCircuitSimulation(Graph graph, long initialTick) {
        this.graph = Objects.requireNonNull(graph);
        if (initialTick < 0) {
            throw new IllegalArgumentException("Negative logical tick");
        }
        tick = initialTick;
        for (Node node : graph.nodes()) {
            if (nodes.putIfAbsent(node.id(), node) != null) {
                throw new IllegalArgumentException("Duplicate node ID");
            }
            states.put(node.id(), new NodeState(node.id(), 0, false, Long.MIN_VALUE, Long.MIN_VALUE));
        }
        for (Edge edge : graph.edges()) {
            Node source = node(edge.source());
            Node target = node(edge.target());
            validatePort(target, edge.port());
            if (source.kind() == Kind.TERMINAL
                    || (source.kind() == Kind.REPEATER && source.orientation() != edge.direction())
                    || (edge.port() == Port.REAR && target.orientation() != edge.direction())
                    || (edge.port() == Port.SIDE && (source.kind() != Kind.REPEATER
                    || target.orientation().axis() == edge.direction().axis()))
                    || (source.kind() == Kind.WIRE && target.kind() == Kind.WIRE && edge.attenuation() == 0)) {
                throw new IllegalArgumentException("Invalid physical signal edge");
            }
            incoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
        }
        for (AuthorizationEdge edge : graph.authorizationEdges()) {
            Node source = node(edge.source());
            Node target = node(edge.target());
            if (source.y() != target.y() || Math.abs((long) source.x() - target.x())
                    + Math.abs((long) source.z() - target.z()) != 1
                    || !edge.direction().horizontal()
                    || (long) target.x() - source.x() != edge.direction().x
                    || (long) target.z() - source.z() != edge.direction().z) {
                throw new IllegalArgumentException("Authorization requires horizontal adjacent nodes");
            }
            authorizedIncoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
        }
    }

    public List<Change> advanceTo(long targetTick, List<Input> inputs) {
        if (targetTick < tick) {
            throw new IllegalArgumentException("Cannot rewind logical time");
        }
        inputs = List.copyOf(inputs);
        long previousTick = tick;
        for (Input input : inputs) {
            if (input.tick() < previousTick || input.tick() > targetTick) {
                throw new IllegalArgumentException("Inputs must be ordered within the frame");
            }
            previousTick = input.tick();
            if (input instanceof PlatePress press) {
                if (node(press.nodeId()).kind() != Kind.PLATE) {
                    throw new IllegalArgumentException("Only plates can be pressed");
                }
            } else if (input instanceof BoundaryUpdate update) {
                validateBoundary(update.signals());
            }
        }
        List<Change> changes = new ArrayList<>();
        int cursor = 0;
        while (true) {
            while (cursor < inputs.size() && inputs.get(cursor).tick() == tick) {
                apply(inputs.get(cursor++), changes);
            }
            if (tick == targetTick) {
                return List.copyOf(changes);
            }
            tick++;
            while (!pending.isEmpty() && pending.peek().dueTick() <= tick) {
                process(pending.remove(), changes);
            }
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(tick, sequence, List.copyOf(states.values()), boundarySignals,
                pending.stream().sorted(TRANSITION_ORDER).toList());
    }

    public static EngineerCircuitSimulation restore(Graph graph, Snapshot snapshot) {
        EngineerCircuitSimulation simulation = new EngineerCircuitSimulation(graph, snapshot.tick());
        if (snapshot.nextSequence() < 0 || snapshot.states().size() != simulation.nodes.size()) {
            throw new IllegalArgumentException("Invalid snapshot state");
        }
        Set<UUID> restored = new HashSet<>();
        for (NodeState state : snapshot.states()) {
            Node node = simulation.node(state.nodeId());
            if (!restored.add(state.nodeId()) || (state.locked() && node.kind() != Kind.REPEATER)
                    || (node.kind() != Kind.WIRE && node.kind() != Kind.TERMINAL
                    && state.strength() != 0 && state.strength() != 15)
                    || (node.kind() != Kind.PLATE && (state.pressedAt() != Long.MIN_VALUE
                    || state.releaseAt() != Long.MIN_VALUE))
                    || (node.kind() == Kind.PLATE && (state.pressedAt() > snapshot.tick()
                    || (state.strength() == 0) != (state.releaseAt() == Long.MIN_VALUE)))) {
                throw new IllegalArgumentException("Invalid node state");
            }
            simulation.states.put(state.nodeId(), state);
        }
        simulation.validateBoundary(snapshot.boundarySignals());
        simulation.boundarySignals = snapshot.boundarySignals();
        Set<UUID> scheduled = new HashSet<>();
        Set<Long> sequences = new HashSet<>();
        for (Transition transition : snapshot.pending()) {
            Node node = simulation.node(transition.nodeId());
            if (transition.dueTick() <= snapshot.tick() || transition.sequence() < 0
                    || transition.sequence() >= snapshot.nextSequence() || !scheduled.add(node.id())
                    || !sequences.add(transition.sequence())
                    || (transition.kind() == TransitionKind.PLATE_RELEASE
                    ? node.kind() != Kind.PLATE || simulation.states.get(node.id()).releaseAt() != transition.dueTick()
                    : node.kind() != Kind.REPEATER)) {
                throw new IllegalArgumentException("Invalid pending transition");
            }
            simulation.pending.add(transition);
        }
        for (NodeState state : simulation.states.values()) {
            if (state.releaseAt() != Long.MIN_VALUE && !scheduled.contains(state.nodeId())) {
                throw new IllegalArgumentException("Powered plate missing release");
            }
        }
        simulation.sequence = snapshot.nextSequence();
        return simulation;
    }

    public Optional<PlateSelection> selectPlate(UUID targetId, UUID ownerId, long oldestAccepted,
                                               boolean requireRecentPress) {
        node(targetId);
        Objects.requireNonNull(ownerId);
        Map<UUID, Integer> distances = new HashMap<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(targetId);
        while (!queue.isEmpty()) {
            UUID currentId = queue.removeFirst();
            Node current = node(currentId);
            boolean terminal = currentId.equals(targetId);
            int distance = terminal ? 0 : distances.get(currentId);
            for (AuthorizationEdge edge : authorizedIncoming.getOrDefault(currentId, List.of())) {
                Node previous = node(edge.source());
                if (!ownerId.equals(previous.ownerId()) || (!terminal && !ownerId.equals(current.ownerId()))
                        || !permits(previous, edge.direction()) || (!terminal && !permits(current, edge.direction()))
                        || distances.containsKey(previous.id())) {
                    continue;
                }
                distances.put(previous.id(), distance + 1);
                if (!previous.id().equals(targetId)) {
                    queue.addLast(previous.id());
                }
            }
        }
        Comparator<PlateSelection> order = Comparator.comparingLong(PlateSelection::pressedAt).reversed()
                .thenComparingInt(PlateSelection::distance).thenComparingInt(value -> node(value.plateId()).x())
                .thenComparingInt(value -> node(value.plateId()).y())
                .thenComparingInt(value -> node(value.plateId()).z()).thenComparing(PlateSelection::plateId);
        return graph.nodes().stream().filter(value -> value.kind() == Kind.PLATE && ownerId.equals(value.ownerId()))
                .filter(value -> distances.getOrDefault(value.id(), 0) > 0)
                .filter(value -> !requireRecentPress || (states.get(value.id()).pressedAt() >= oldestAccepted
                        && states.get(value.id()).pressedAt() <= tick))
                .map(value -> new PlateSelection(value.id(), value.plateKind(), states.get(value.id()).pressedAt(),
                        distances.get(value.id()))).min(order);
    }

    private void apply(Input input, List<Change> changes) {
        if (input instanceof PlatePress press) {
            NodeState before = states.get(press.nodeId());
            long releaseAt = Math.addExact(tick, 10);
            pending.removeIf(value -> value.nodeId().equals(press.nodeId()));
            update(new NodeState(before.nodeId(), 15, false, tick, releaseAt), Reason.PRESS, changes);
            schedule(node(before.nodeId()), releaseAt, Priority.NORMAL, TransitionKind.PLATE_RELEASE);
        } else if (input instanceof BoundaryUpdate update) {
            boundarySignals = update.signals();
        }
        reconcile(changes);
    }

    private void process(Transition transition, List<Change> changes) {
        Node node = node(transition.nodeId());
        NodeState state = states.get(node.id());
        if (transition.kind() == TransitionKind.PLATE_RELEASE) {
            update(new NodeState(node.id(), 0, false, state.pressedAt(), Long.MIN_VALUE), Reason.RELEASE, changes);
        } else {
            if (state.locked()) {
                return;
            }
            boolean inputOn = signal(node.id(), Port.REAR, states) > 0;
            if (state.strength() > 0) {
                if (inputOn) {
                    return;
                }
                update(new NodeState(node.id(), 0, false, Long.MIN_VALUE, Long.MIN_VALUE), Reason.REPEATER, changes);
            } else {
                update(new NodeState(node.id(), 15, false, Long.MIN_VALUE, Long.MIN_VALUE), Reason.REPEATER, changes);
                if (!inputOn) {
                    schedule(node, Math.addExact(tick, node.delayTicks()), Priority.VERY_HIGH,
                            TransitionKind.REPEATER_TICK);
                }
            }
        }
        reconcile(changes);
    }

    private void reconcile(List<Change> changes) {
        Map<UUID, NodeState> settled = new HashMap<>(states);
        for (Node node : graph.nodes()) {
            if (node.kind() == Kind.WIRE) {
                settled.put(node.id(), new NodeState(node.id(), 0, false, Long.MIN_VALUE, Long.MIN_VALUE));
            }
        }
        boolean changed;
        do {
            changed = false;
            Map<UUID, NodeState> next = new HashMap<>(settled);
            for (Node node : graph.nodes()) {
                if (node.kind() == Kind.WIRE) {
                    int strength = signal(node.id(), Port.SIGNAL, settled);
                    if (strength != settled.get(node.id()).strength()) {
                        next.put(node.id(), new NodeState(node.id(), strength, false, Long.MIN_VALUE, Long.MIN_VALUE));
                        changed = true;
                    }
                }
            }
            settled = next;
        } while (changed);
        for (Node node : graph.nodes()) {
            if (node.kind() == Kind.WIRE) {
                update(settled.get(node.id()), Reason.PROPAGATION, changes);
            } else if (node.kind() == Kind.TERMINAL) {
                update(new NodeState(node.id(), signal(node.id(), Port.SIGNAL, settled), false,
                        Long.MIN_VALUE, Long.MIN_VALUE), Reason.PROPAGATION, changes);
            }
        }
        for (Node node : graph.nodes()) {
            if (node.kind() != Kind.REPEATER) {
                continue;
            }
            NodeState state = states.get(node.id());
            boolean locked = signal(node.id(), Port.SIDE, states) > 0;
            update(new NodeState(node.id(), state.strength(), locked, Long.MIN_VALUE, Long.MIN_VALUE),
                    Reason.LOCK, changes);
            boolean inputOn = signal(node.id(), Port.REAR, states) > 0;
            if (!locked && inputOn != (state.strength() > 0)
                    && pending.stream().noneMatch(value -> value.nodeId().equals(node.id()))) {
                Priority priority = node.prioritize() ? Priority.EXTREMELY_HIGH
                        : state.strength() > 0 ? Priority.VERY_HIGH : Priority.HIGH;
                schedule(node, Math.addExact(tick, node.delayTicks()), priority, TransitionKind.REPEATER_TICK);
            }
        }
    }

    private int signal(UUID target, Port port, Map<UUID, NodeState> currentStates) {
        int strength = 0;
        for (Edge edge : incoming.getOrDefault(target, List.of())) {
            if (edge.port() == port) {
                strength = Math.max(strength, currentStates.get(edge.source()).strength() - edge.attenuation());
            }
        }
        for (BoundarySignal boundary : boundarySignals) {
            if (boundary.nodeId().equals(target) && boundary.port() == port) {
                strength = Math.max(strength, boundary.strength());
            }
        }
        return strength;
    }

    private void update(NodeState after, Reason reason, List<Change> changes) {
        NodeState before = states.put(after.nodeId(), after);
        if (!after.equals(before)) {
            changes.add(new Change(tick, sequence++, after, reason));
        }
    }

    private void schedule(Node node, long dueTick, Priority priority, TransitionKind kind) {
        pending.add(new Transition(node.id(), dueTick, priority, sequence++, kind));
    }

    private Node node(UUID id) {
        Node node = nodes.get(Objects.requireNonNull(id));
        if (node == null) {
            throw new IllegalArgumentException("Unknown circuit node: " + id);
        }
        return node;
    }

    private void validateBoundary(List<BoundarySignal> signals) {
        Set<String> ports = new HashSet<>();
        for (BoundarySignal signal : signals) {
            Node node = node(signal.nodeId());
            validatePort(node, signal.port());
            if (node.kind() == Kind.PLATE || !ports.add(signal.nodeId() + "/" + signal.port())) {
                throw new IllegalArgumentException("Invalid or duplicate boundary signal");
            }
        }
    }

    private static void validatePort(Node target, Port port) {
        if ((target.kind() == Kind.REPEATER) == (port == Port.SIGNAL) || target.kind() == Kind.PLATE) {
            throw new IllegalArgumentException("Port incompatible with node kind");
        }
    }

    private static boolean permits(Node node, Direction direction) {
        return node.kind() != Kind.REPEATER || node.orientation() == direction;
    }

    private static void validateStrength(int strength) {
        if (strength < 0 || strength > 15) {
            throw new IllegalArgumentException("Signal strength outside 0..15");
        }
    }

    public enum Kind { PLATE, WIRE, REPEATER, TERMINAL }

    public enum PlateKind { WOOD, STONE, IRON, GOLD }

    public enum Port { SIGNAL, REAR, SIDE }

    public enum Direction {
        NONE(0, 0), NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0), UP(0, 0), DOWN(0, 0);

        private final int x;
        private final int z;

        Direction(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private boolean horizontal() {
            return x != 0 || z != 0;
        }

        private int axis() {
            return x != 0 ? 0 : z != 0 ? 1 : 2;
        }
    }

    public enum Priority { EXTREMELY_HIGH, VERY_HIGH, HIGH, NORMAL }

    public enum TransitionKind { PLATE_RELEASE, REPEATER_TICK }

    public enum Reason { PRESS, RELEASE, PROPAGATION, REPEATER, LOCK }

    public record Node(UUID id, UUID ownerId, int x, int y, int z, Kind kind, Direction orientation,
                       PlateKind plateKind, int delayTicks, boolean prioritize) {
        public Node {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            Objects.requireNonNull(orientation);
            if ((kind == Kind.PLATE) != (plateKind != null)
                    || (kind == Kind.REPEATER ? !orientation.horizontal() || delayTicks < 2 || delayTicks > 8
                    || delayTicks % 2 != 0 : orientation != Direction.NONE || delayTicks != 0 || prioritize)) {
                throw new IllegalArgumentException("Invalid circuit node configuration");
            }
        }
    }

    public record Edge(UUID source, UUID target, Direction direction, Port port, int attenuation) {
        public Edge {
            Objects.requireNonNull(source);
            Objects.requireNonNull(target);
            Objects.requireNonNull(direction);
            Objects.requireNonNull(port);
            validateStrength(attenuation);
            if (direction == Direction.NONE) {
                throw new IllegalArgumentException("Signal edge requires direction");
            }
        }
    }

    public record AuthorizationEdge(UUID source, UUID target, Direction direction) {
        public AuthorizationEdge {
            Objects.requireNonNull(source);
            Objects.requireNonNull(target);
            Objects.requireNonNull(direction);
        }
    }

    public record Graph(List<Node> nodes, List<Edge> edges, List<AuthorizationEdge> authorizationEdges) {
        public Graph {
            nodes = List.copyOf(nodes);
            edges = List.copyOf(edges);
            authorizationEdges = List.copyOf(authorizationEdges);
        }
    }

    public record NodeState(UUID nodeId, int strength, boolean locked, long pressedAt, long releaseAt) {
        public NodeState {
            Objects.requireNonNull(nodeId);
            validateStrength(strength);
        }
    }

    public record BoundarySignal(UUID nodeId, Port port, int strength) {
        public BoundarySignal {
            Objects.requireNonNull(nodeId);
            Objects.requireNonNull(port);
            validateStrength(strength);
        }
    }

    public sealed interface Input permits PlatePress, BoundaryUpdate {
        long tick();
    }

    public record PlatePress(long tick, UUID nodeId) implements Input {
        public PlatePress {
            Objects.requireNonNull(nodeId);
        }
    }

    public record BoundaryUpdate(long tick, List<BoundarySignal> signals) implements Input {
        public BoundaryUpdate {
            signals = List.copyOf(signals);
        }
    }

    public record Transition(UUID nodeId, long dueTick, Priority priority, long sequence, TransitionKind kind) {
        public Transition {
            Objects.requireNonNull(nodeId);
            Objects.requireNonNull(priority);
            Objects.requireNonNull(kind);
        }
    }

    public record Snapshot(long tick, long nextSequence, List<NodeState> states,
                           List<BoundarySignal> boundarySignals, List<Transition> pending) {
        public Snapshot {
            states = List.copyOf(states);
            boundarySignals = List.copyOf(boundarySignals);
            pending = List.copyOf(pending);
        }
    }

    public record Change(long tick, long sequence, NodeState state, Reason reason) {
    }

    public record PlateSelection(UUID plateId, PlateKind kind, long pressedAt, int distance) {
    }
}
