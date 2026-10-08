package kim.biryeong.semiontd.tower.engineer;

import static kim.biryeong.semiontd.tower.engineer.EngineerCircuitSimulation.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class EngineerCircuitSimulationTest {
    private static final UUID OWNER = new UUID(1, 1);

    @Test
    void plateReleasesAfterTenLogicalTicksAndRepressRefreshesTheDeadline() {
        var plate = plate(1, 0, 0, PlateKind.GOLD);
        var simulation = simulation(List.of(plate), List.of());
        simulation.advanceTo(0, List.of(new PlatePress(0, plate.id())));
        assertEquals(15, state(simulation, plate).strength());
        assertEquals(10, state(simulation, plate).releaseAt());
        simulation.advanceTo(9, List.of(new PlatePress(9, plate.id())));
        assertEquals(1, simulation.snapshot().pending().size());
        assertEquals(19, state(simulation, plate).releaseAt());
        assertEquals(9, state(simulation, plate).pressedAt());
        simulation.advanceTo(18, List.of());
        assertEquals(15, state(simulation, plate).strength());
        var changes = simulation.advanceTo(19, List.of());
        assertEquals(Reason.RELEASE, changes.getFirst().reason());
        assertEquals(0, state(simulation, plate).strength());
        assertEquals(9, state(simulation, plate).pressedAt());
        assertTrue(simulation.snapshot().pending().isEmpty());
    }

    @Test
    void dueReleaseOccursBeforeASameTickPress() {
        var plate = plate(1, 0, 0, PlateKind.WOOD);
        var simulation = simulation(List.of(plate), List.of());
        simulation.advanceTo(0, List.of(new PlatePress(0, plate.id())));
        var changes = simulation.advanceTo(10, List.of(new PlatePress(10, plate.id())));
        assertEquals(List.of(Reason.RELEASE, Reason.PRESS), changes.stream().map(Change::reason).toList());
        assertEquals(20, state(simulation, plate).releaseAt());
    }

    @Test
    void wireStrengthAttenuatesAndDoesNotPowerAnArbitrarilyLongRoute() {
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        var plate = plate(1, 0, 0, PlateKind.WOOD);
        nodes.add(plate);
        for (int i = 1; i <= 17; i++) {
            var wire = wire(i + 1, i, 0);
            edges.add(edge(nodes.getLast(), wire, Direction.EAST, Port.SIGNAL, i == 1 ? 0 : 1));
            nodes.add(wire);
        }
        var target = terminal(20, 18, 0);
        edges.add(edge(nodes.getLast(), target, Direction.EAST, Port.SIGNAL, 0));
        nodes.add(target);
        var simulation = simulation(nodes, edges);
        simulation.advanceTo(0, List.of(new PlatePress(0, plate.id())));
        for (int i = 1; i <= 17; i++) {
            assertEquals(Math.max(0, 16 - i), state(simulation, nodes.get(i)).strength());
        }
        assertEquals(0, state(simulation, target).strength());
        simulation.advanceTo(10, List.of());
        assertTrue(simulation.snapshot().states().stream().allMatch(value -> value.strength() == 0));
    }

    @Test
    void externalBoundaryPowersAConnectedWireLoopWithoutLeavingSelfSustainingPower() {
        var first = wire(1, 0, 0);
        var second = wire(2, 1, 0);
        var simulation = simulation(List.of(first, second), List.of(
                edge(first, second, Direction.EAST, Port.SIGNAL, 1),
                edge(second, first, Direction.WEST, Port.SIGNAL, 1)));
        simulation.advanceTo(0, List.of(boundary(0, first, Port.SIGNAL, 7)));
        assertEquals(7, state(simulation, first).strength());
        assertEquals(6, state(simulation, second).strength());
        simulation.advanceTo(1, List.of(new BoundaryUpdate(1, List.of())));
        assertEquals(0, state(simulation, first).strength());
        assertEquals(0, state(simulation, second).strength());
    }

    @Test
    void repeaterDelaysBothOnAndOffAndRestoresSignalStrength() {
        var plate = plate(1, 0, 0, PlateKind.STONE);
        var repeater = repeater(2, 1, 0, Direction.EAST, 2, false);
        var target = terminal(3, 2, 0);
        var simulation = simulation(List.of(plate, repeater, target), List.of(
                edge(plate, repeater, Direction.EAST, Port.REAR, 0),
                edge(repeater, target, Direction.EAST, Port.SIGNAL, 0)));
        simulation.advanceTo(1, List.of(new PlatePress(0, plate.id())));
        assertEquals(0, state(simulation, target).strength());
        simulation.advanceTo(2, List.of());
        assertEquals(15, state(simulation, target).strength());
        simulation.advanceTo(11, List.of());
        assertEquals(0, state(simulation, plate).strength());
        assertEquals(15, state(simulation, target).strength());
        simulation.advanceTo(12, List.of());
        assertEquals(0, state(simulation, target).strength());
    }

    @Test
    void shortInputPulseStillProducesAConfiguredDelayLengthOutputPulse() {
        var repeater = repeater(1, 0, 0, Direction.EAST, 4, false);
        var simulation = simulation(List.of(repeater), List.of());
        simulation.advanceTo(3, List.of(boundary(0, repeater, Port.REAR, 1), new BoundaryUpdate(1, List.of())));
        assertEquals(0, state(simulation, repeater).strength());
        simulation.advanceTo(4, List.of());
        assertEquals(15, state(simulation, repeater).strength());
        assertEquals(8, simulation.snapshot().pending().getFirst().dueTick());
        assertEquals(Priority.VERY_HIGH, simulation.snapshot().pending().getFirst().priority());
        simulation.advanceTo(7, List.of());
        assertEquals(15, state(simulation, repeater).strength());
        simulation.advanceTo(8, List.of());
        assertEquals(0, state(simulation, repeater).strength());
    }

    @Test
    void everyConfiguredRepeaterDelayUsesLogicalTicksAndReturningInputCancelsPendingOff() {
        for (int delay : List.of(2, 4, 6, 8)) {
            var repeater = repeater(1, 0, 0, Direction.SOUTH, delay, false);
            var simulation = simulation(List.of(repeater), List.of());
            simulation.advanceTo(delay - 1, List.of(boundary(0, repeater, Port.REAR, 1)));
            assertEquals(0, state(simulation, repeater).strength());
            simulation.advanceTo(delay, List.of());
            assertEquals(15, state(simulation, repeater).strength());
            simulation.advanceTo(2L * delay, List.of(new BoundaryUpdate(delay + 1, List.of()),
                    boundary(delay + 2, repeater, Port.REAR, 1)));
            assertEquals(15, state(simulation, repeater).strength());
            simulation.advanceTo(2L * delay + 1, List.of());
            assertEquals(15, state(simulation, repeater).strength());
            assertTrue(simulation.snapshot().pending().isEmpty());
        }
    }

    @Test
    void lockedDueTransitionIsConsumedAndUnlockSchedulesAFreshDelay() {
        var repeater = repeater(1, 0, 0, Direction.EAST, 2, false);
        var simulation = simulation(List.of(repeater), List.of());
        simulation.advanceTo(2, List.of(boundary(0, repeater, Port.REAR, 15),
                new BoundaryUpdate(1, List.of(new BoundarySignal(repeater.id(), Port.REAR, 15),
                        new BoundarySignal(repeater.id(), Port.SIDE, 15)))));
        assertTrue(state(simulation, repeater).locked());
        assertEquals(0, state(simulation, repeater).strength());
        assertTrue(simulation.snapshot().pending().isEmpty());
        simulation.advanceTo(4, List.of(boundary(3, repeater, Port.REAR, 15)));
        assertFalse(state(simulation, repeater).locked());
        assertEquals(0, state(simulation, repeater).strength());
        simulation.advanceTo(5, List.of());
        assertEquals(15, state(simulation, repeater).strength());
        simulation.advanceTo(8, List.of(new BoundaryUpdate(6, List.of()), boundary(7, repeater, Port.SIDE, 15)));
        assertTrue(state(simulation, repeater).locked());
        assertEquals(15, state(simulation, repeater).strength());
        simulation.advanceTo(11, List.of(new BoundaryUpdate(10, List.of())));
        assertEquals(15, state(simulation, repeater).strength());
        simulation.advanceTo(12, List.of());
        assertEquals(0, state(simulation, repeater).strength());
    }

    @Test
    void actualSideRepeaterOutputLocksTheNeighbor() {
        var target = repeater(1, 0, 0, Direction.EAST, 4, false);
        var side = repeater(2, 0, 1, Direction.NORTH, 2, true);
        var simulation = simulation(List.of(target, side), List.of(edge(side, target, Direction.NORTH, Port.SIDE, 0)));
        simulation.advanceTo(4, List.of(new BoundaryUpdate(0, List.of(
                new BoundarySignal(target.id(), Port.REAR, 15), new BoundarySignal(side.id(), Port.REAR, 15)))));
        assertEquals(15, state(simulation, side).strength());
        assertTrue(state(simulation, target).locked());
        assertEquals(0, state(simulation, target).strength());
    }

    @Test
    void scheduledPriorityPrecedesInsertionOrderAndEqualPriorityRetainsSequenceOrder() {
        var first = repeater(1, 0, 0, Direction.EAST, 2, false);
        var priority = repeater(2, 0, 2, Direction.EAST, 2, true);
        var last = repeater(3, 0, 4, Direction.EAST, 2, false);
        var simulation = simulation(List.of(first, priority, last), List.of());
        var changes = simulation.advanceTo(2, List.of(new BoundaryUpdate(0, List.of(
                new BoundarySignal(last.id(), Port.REAR, 15), new BoundarySignal(priority.id(), Port.REAR, 15),
                new BoundarySignal(first.id(), Port.REAR, 15)))));
        assertEquals(List.of(priority.id(), first.id(), last.id()), changes.stream()
                .filter(value -> value.reason() == Reason.REPEATER).map(value -> value.state().nodeId()).toList());
    }

    @Test
    void frameGroupingOneTwoAndFivePreservesEveryStateAndEventIncludingRestoration() {
        var plate = plate(1, 0, 0, PlateKind.IRON);
        var wire = wire(2, 1, 0);
        var repeater = repeater(3, 2, 0, Direction.EAST, 4, false);
        var target = terminal(4, 3, 0);
        var graph = new Graph(List.of(plate, wire, repeater, target), List.of(
                edge(plate, wire, Direction.EAST, Port.SIGNAL, 0),
                edge(wire, repeater, Direction.EAST, Port.REAR, 0),
                edge(repeater, target, Direction.EAST, Port.SIGNAL, 0)), List.of());
        List<Input> inputs = List.of(new PlatePress(0, plate.id()), new PlatePress(3, plate.id()),
                boundary(6, repeater, Port.SIDE, 15), new BoundaryUpdate(10, List.of()),
                new PlatePress(13, plate.id()), new PlatePress(15, plate.id()),
                boundary(28, wire, Port.SIGNAL, 3), new BoundaryUpdate(29, List.of()));
        Trace baseline = trace(graph, inputs, 1, false);
        assertEquals(baseline, trace(graph, inputs, 2, false));
        assertEquals(baseline, trace(graph, inputs, 5, false));
        assertEquals(baseline, trace(graph, inputs, 1, true));
        assertEquals(baseline, trace(graph, inputs, 2, true));
        assertEquals(baseline, trace(graph, inputs, 5, true));
    }

    @Test
    void selectionUsesNewestThenDistanceThenCoordinatesIndependentlyOfPhysicalPower() {
        var left = plate(1, -1, 0, PlateKind.WOOD);
        var right = plate(2, 1, 0, PlateKind.GOLD);
        var far = plate(3, 0, 2, PlateKind.IRON);
        var wire = wire(4, 0, 1);
        var target = terminal(5, 0, 0);
        var foreign = new Node(id(6), new UUID(9, 9), 0, 0, -1, Kind.PLATE, Direction.NONE,
                PlateKind.STONE, 0, false);
        var graph = new Graph(List.of(left, right, far, wire, target, foreign), List.of(), List.of(
                authorization(left, target, Direction.EAST), authorization(right, target, Direction.WEST),
                authorization(far, wire, Direction.NORTH), authorization(wire, target, Direction.NORTH),
                authorization(foreign, target, Direction.SOUTH)));
        var simulation = new EngineerCircuitSimulation(graph, 0);
        simulation.advanceTo(0, List.of(new PlatePress(0, left.id()), new PlatePress(0, right.id()),
                new PlatePress(0, far.id()), new PlatePress(0, foreign.id())));
        assertEquals(left.id(), simulation.selectPlate(target.id(), OWNER, 0, true).orElseThrow().plateId());
        assertEquals(0, state(simulation, target).strength());
        simulation.advanceTo(2, List.of(new PlatePress(1, far.id()), new PlatePress(2, foreign.id())));
        var selected = simulation.selectPlate(target.id(), OWNER, 0, true).orElseThrow();
        assertEquals(far.id(), selected.plateId());
        assertEquals(2, selected.distance());
        assertTrue(simulation.selectPlate(target.id(), OWNER, 2, true).isEmpty());
        assertEquals(far.id(), simulation.selectPlate(target.id(), OWNER, 2, false).orElseThrow().plateId());
    }

    @Test
    void authorizationRequiresEveryIntermediateOwnerAndRepeaterTravelDirection() {
        var plate = plate(1, 0, 0, PlateKind.WOOD);
        var repeater = repeater(2, 1, 0, Direction.WEST, 2, false);
        var target = terminal(3, 2, 0);
        var graph = new Graph(List.of(plate, repeater, target), List.of(), List.of(
                authorization(plate, repeater, Direction.EAST), authorization(repeater, target, Direction.EAST)));
        assertTrue(new EngineerCircuitSimulation(graph, 0).selectPlate(target.id(), OWNER, 0, false).isEmpty());
        var foreign = new Node(repeater.id(), new UUID(9, 9), 1, 0, 0, Kind.REPEATER, Direction.EAST,
                null, 2, false);
        var foreignGraph = new Graph(List.of(plate, foreign, target), List.of(), graph.authorizationEdges());
        assertTrue(new EngineerCircuitSimulation(foreignGraph, 0).selectPlate(target.id(), OWNER, 0, false).isEmpty());
    }

    @Test
    void occupiedTerminalRetainsLegacyPositiveCycleDistance() {
        var plate = plate(1, 0, 0, PlateKind.WOOD);
        var wire = wire(2, 1, 0);
        var graph = new Graph(List.of(plate, wire), List.of(), List.of(
                authorization(plate, wire, Direction.EAST), authorization(wire, plate, Direction.WEST)));
        var simulation = new EngineerCircuitSimulation(graph, 0);
        assertEquals(2, simulation.selectPlate(plate.id(), OWNER, 0, false).orElseThrow().distance());
    }

    @Test
    void invalidPortOrientationStrengthAndTemporalInputsAreRejectedBeforeMutation() {
        var wire = wire(1, 0, 0);
        var repeater = repeater(2, 1, 0, Direction.EAST, 2, false);
        assertThrows(IllegalArgumentException.class, () -> simulation(List.of(wire, repeater), List.of(
                edge(repeater, wire, Direction.WEST, Port.SIGNAL, 0))));
        assertThrows(IllegalArgumentException.class, () -> simulation(List.of(wire, repeater), List.of(
                edge(wire, repeater, Direction.EAST, Port.SIDE, 0))));
        assertThrows(IllegalArgumentException.class, () -> new BoundarySignal(wire.id(), Port.SIGNAL, 16));
        var simulation = simulation(List.of(wire, repeater), List.of());
        var before = simulation.snapshot();
        assertThrows(IllegalArgumentException.class, () -> simulation.advanceTo(5,
                List.of(boundary(0, wire, Port.SIGNAL, 3), boundary(6, wire, Port.SIGNAL, 5))));
        assertThrows(IllegalArgumentException.class, () -> simulation.advanceTo(5,
                List.of(boundary(2, wire, Port.SIGNAL, 3), boundary(1, wire, Port.SIGNAL, 5))));
        assertThrows(IllegalArgumentException.class, () -> simulation.advanceTo(0,
                List.of(new PlatePress(0, wire.id()))));
        assertEquals(before, simulation.snapshot());
    }

    @Test
    void snapshotsAndInputCollectionsAreImmutableAndMissingPlateReleaseIsRejected() {
        var plate = plate(1, 0, 0, PlateKind.WOOD);
        List<Node> nodes = new ArrayList<>(List.of(plate));
        var graph = new Graph(nodes, List.of(), List.of());
        nodes.clear();
        var simulation = new EngineerCircuitSimulation(graph, 0);
        simulation.advanceTo(0, List.of(new PlatePress(0, plate.id())));
        var snapshot = simulation.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.states().clear());
        assertThrows(IllegalArgumentException.class, () -> EngineerCircuitSimulation.restore(graph,
                new Snapshot(snapshot.tick(), snapshot.nextSequence(), snapshot.states(), List.of(), List.of())));
        simulation.advanceTo(10, List.of());
        assertEquals(15, snapshot.states().getFirst().strength());
    }

    private static Trace trace(Graph graph, List<Input> inputs, int grouping, boolean restore) {
        var simulation = new EngineerCircuitSimulation(graph, 0);
        List<Change> changes = new ArrayList<>();
        List<Snapshot> checkpoints = new ArrayList<>();
        int cursor = 0;
        for (int target = 0; target <= 40; target += grouping) {
            List<Input> frame = new ArrayList<>();
            while (cursor < inputs.size() && inputs.get(cursor).tick() <= target) {
                frame.add(inputs.get(cursor++));
            }
            changes.addAll(simulation.advanceTo(target, frame));
            if (target % 10 == 0) {
                checkpoints.add(simulation.snapshot());
            }
            if (restore) {
                simulation = EngineerCircuitSimulation.restore(graph, simulation.snapshot());
            }
        }
        return new Trace(simulation.snapshot(), List.copyOf(changes), List.copyOf(checkpoints));
    }

    private static EngineerCircuitSimulation simulation(List<Node> nodes, List<Edge> edges) {
        return new EngineerCircuitSimulation(new Graph(nodes, edges, List.of()), 0);
    }

    private static NodeState state(EngineerCircuitSimulation simulation, Node node) {
        return simulation.snapshot().states().stream().filter(value -> value.nodeId().equals(node.id()))
                .findFirst().orElseThrow();
    }

    private static UUID id(int value) {
        return new UUID(0, value);
    }

    private static Node plate(int id, int x, int z, PlateKind kind) {
        return new Node(id(id), OWNER, x, 0, z, Kind.PLATE, Direction.NONE, kind, 0, false);
    }

    private static Node wire(int id, int x, int z) {
        return new Node(id(id), OWNER, x, 0, z, Kind.WIRE, Direction.NONE, null, 0, false);
    }

    private static Node terminal(int id, int x, int z) {
        return new Node(id(id), null, x, 0, z, Kind.TERMINAL, Direction.NONE, null, 0, false);
    }

    private static Node repeater(int id, int x, int z, Direction direction, int delay, boolean prioritize) {
        return new Node(id(id), OWNER, x, 0, z, Kind.REPEATER, direction, null, delay, prioritize);
    }

    private static Edge edge(Node source, Node target, Direction direction, Port port, int attenuation) {
        return new Edge(source.id(), target.id(), direction, port, attenuation);
    }

    private static AuthorizationEdge authorization(Node source, Node target, Direction direction) {
        return new AuthorizationEdge(source.id(), target.id(), direction);
    }

    private static BoundaryUpdate boundary(long tick, Node node, Port port, int strength) {
        return new BoundaryUpdate(tick, List.of(new BoundarySignal(node.id(), port, strength)));
    }

    private record Trace(Snapshot snapshot, List<Change> changes, List<Snapshot> checkpoints) {
    }
}
