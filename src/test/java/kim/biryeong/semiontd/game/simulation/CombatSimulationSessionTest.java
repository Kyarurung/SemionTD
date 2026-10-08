package kim.biryeong.semiontd.game.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

final class CombatSimulationSessionTest {
    @Test
    void circuitAndActorsCompleteInCanonicalOrderBeforeTheNextLogicalStep() throws Exception {
        FakeBridge bridge = new FakeBridge();
        Dispatcher dispatcher = new Dispatcher();
        AtomicReference<Thread> worker = new AtomicReference<>();
        try (var coordinator = coordinator(bridge, dispatcher, input -> worker.set(Thread.currentThread()))) {
            coordinator.beginFrame(2);
            while (!coordinator.idle()) {
                dispatcher.next().run();
            }
            assertEquals(List.of("begin:1", "circuit:1", "prepare:a:1", "apply:a:1", "prepare:b:1", "apply:b:1",
                    "game:1", "tasks:1", "complete:1", "begin:2", "circuit:2", "prepare:a:2", "apply:a:2",
                    "prepare:b:2", "apply:b:2", "game:2", "tasks:2", "complete:2"), bridge.events);
            assertFalse(Thread.currentThread() == worker.get());
            assertEquals(2, coordinator.completedSteps());
            coordinator.endFrame();
            assertEquals(List.of(2L), bridge.presentedTicks);
        }
    }

    @Test
    void nativePrefixIsNotRepeatedAndPresentationNeverPublishesAPartialActorStep() throws Exception {
        FakeBridge bridge = new FakeBridge();
        Dispatcher dispatcher = new Dispatcher();
        CountDownLatch physicsEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var coordinator = coordinator(bridge, dispatcher, input -> {
            if (input.actor().equals("a")) {
                physicsEntered.countDown();
                await(release);
            }
        })) {
            coordinator.beginFrame(1);
            dispatcher.next().run();
            assertTrue(physicsEntered.await(3, TimeUnit.SECONDS));
            assertEquals(1, bridge.prefixes.get("a"));
            coordinator.endFrame();
            assertEquals(List.of(0L), bridge.presentedTicks);
            assertFalse(bridge.events.contains("apply:a:1"));
            release.countDown();
            while (!coordinator.idle()) {
                dispatcher.next().run();
            }
            coordinator.endFrame();
            assertEquals(List.of(0L, 1L), bridge.presentedTicks);
            assertEquals(1, bridge.prefixes.get("a"));
        } finally {
            release.countDown();
        }
    }

    @Test
    void earlierActorRemovalAndNewSpawnsAffectOnlyTheCorrectSnapshot() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.afterApply = actor -> {
            if (actor.equals("a") && bridge.tick == 1) {
                bridge.removed.add("b");
                bridge.members.add("c");
            }
        };
        Dispatcher dispatcher = new Dispatcher();
        try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
            coordinator.beginFrame(2);
            while (!coordinator.idle()) {
                dispatcher.next().run();
            }
            assertFalse(bridge.prefixes.containsKey("b"));
            assertEquals(1, bridge.prefixes.get("c"));
            assertTrue(bridge.events.indexOf("complete:1") < bridge.events.indexOf("prepare:c:2"));
        }
    }

    @Test
    void pendingActorRemovalAbortsItsPreparedInputWithoutApplyingOrReplayingIt() throws Exception {
        FakeBridge bridge = new FakeBridge();
        Dispatcher dispatcher = new Dispatcher();
        try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
            coordinator.beginFrame(1);
            dispatcher.next().run();
            Runnable actorReady = dispatcher.next();
            bridge.removed.add("a");
            actorReady.run();
            while (!coordinator.idle()) {
                dispatcher.next().run();
            }
            assertTrue(bridge.events.contains("abort:a"));
            assertFalse(bridge.events.contains("apply:a:1"));
            assertEquals(1, bridge.prefixes.get("a"));
        }
    }

    @Test
    void orderedInputsWaitForTheCompletedPrefixAndRunBeforeTheNextStep() throws Exception {
        FakeBridge bridge = new FakeBridge();
        Dispatcher dispatcher = new Dispatcher();
        try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
            coordinator.beginFrame(2);
            coordinator.input(() -> bridge.events.add("input:first"));
            coordinator.input(() -> bridge.events.add("input:second"));
            assertFalse(bridge.events.contains("input:first"));
            while (!coordinator.idle()) {
                dispatcher.next().run();
            }
            assertTrue(bridge.events.indexOf("complete:1") < bridge.events.indexOf("input:first"));
            assertTrue(bridge.events.indexOf("input:first") < bridge.events.indexOf("input:second"));
            assertTrue(bridge.events.indexOf("input:second") < bridge.events.indexOf("begin:2"));
        }
    }

    @Test
    void busyFramesCannotAccumulateUnboundedCatchUp() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.members.clear();
        Dispatcher dispatcher = new Dispatcher();
        try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
            coordinator.beginFrame(5);
            for (int frame = 0; frame < 100; frame++) {
                coordinator.beginFrame(2);
            }
            assertEquals(5, coordinator.pendingSteps());
            while (!coordinator.idle()) {
                dispatcher.next().run();
            }
            assertEquals(5, coordinator.completedSteps());
            assertEquals(5, bridge.tick);
        }
    }

    @Test
    void changedSessionOrActorRevisionCannotApplyAStaleResultOrRepeatThePrefix() throws Exception {
        for (boolean actorChanged : List.of(false, true)) {
            FakeBridge bridge = new FakeBridge();
            Dispatcher dispatcher = new Dispatcher();
            try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
                coordinator.beginFrame(1);
                dispatcher.next().run();
                Runnable ready = dispatcher.next();
                if (actorChanged) {
                    bridge.actorRevisions.merge("a", 1L, Long::sum);
                } else {
                    bridge.revision++;
                }
                assertThrows(IllegalStateException.class, ready::run);
                assertFalse(bridge.events.contains("apply:a:1"));
                assertEquals(1, bridge.prefixes.get("a"));
                assertEquals(1, bridge.closes);
                assertEquals(0, coordinator.completedSteps());
            }
        }
    }

    @Test
    void phaseExitAndCloseDiscardQueuedCompletionsAndInputs() throws Exception {
        FakeBridge bridge = new FakeBridge();
        Dispatcher dispatcher = new Dispatcher();
        try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
            coordinator.beginFrame(2);
            coordinator.input(() -> bridge.events.add("late-input"));
            Runnable ready = dispatcher.next();
            bridge.valid = false;
            ready.run();
            coordinator.close();
            coordinator.endFrame();
            assertEquals(1, bridge.closes);
            assertFalse(bridge.events.contains("late-input"));
            assertFalse(bridge.events.contains("circuit:1"));
            assertEquals(0, coordinator.completedSteps());
            assertThrows(IllegalStateException.class, () -> coordinator.input(() -> {}));
        }
    }

    @Test
    void closeFinishesTheAcceptedStepWithoutReplayingAnEarlierKillOrReward() throws Exception {
        FakeBridge bridge = new FakeBridge();
        Dispatcher dispatcher = new Dispatcher();
        bridge.afterApply = actor -> {
            if (actor.equals("a")) {
                bridge.events.add("kill");
            }
        };
        try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
            coordinator.beginFrame(2);
            dispatcher.next().run();
            dispatcher.next().run();
            Runnable secondActorReady = dispatcher.next();
            assertTrue(bridge.events.contains("kill"));
            coordinator.close();
            assertFalse(coordinator.isClosed());
            coordinator.beginFrame(2);
            assertEquals(1, coordinator.pendingSteps());
            secondActorReady.run();
            assertTrue(coordinator.isClosed());
            assertEquals(1, coordinator.completedSteps());
            assertEquals(1, bridge.prefixes.get("a"));
            assertEquals(1, bridge.events.stream().filter("kill"::equals).count());
            assertEquals(1, bridge.events.stream().filter("game:1"::equals).count());
            assertEquals(1, bridge.committedTick);
            assertEquals(1, bridge.closes);
        }
    }

    @Test
    void calculationFailureClosesTheSessionWithoutNativeApplyOrFallback() throws Exception {
        FakeBridge bridge = new FakeBridge();
        Dispatcher dispatcher = new Dispatcher();
        try (var coordinator = coordinator(bridge, dispatcher, input -> {
            throw new IllegalArgumentException("calculation failed");
        })) {
            coordinator.beginFrame(1);
            assertThrows(IllegalStateException.class, () -> dispatcher.next().run());
            assertEquals(1, bridge.closes);
            assertEquals(0, coordinator.completedSteps());
            assertFalse(bridge.events.contains("circuit:1"));
        }
    }

    @Test
    void synchronousDeadActorAmbientWorkCompletesWithoutAWorkerApply() throws Exception {
        FakeBridge bridge = new FakeBridge();
        bridge.ambient.add("a");
        Dispatcher dispatcher = new Dispatcher();
        try (var coordinator = coordinator(bridge, dispatcher, ignored -> {})) {
            coordinator.beginFrame(2);
            while (!coordinator.idle()) {
                dispatcher.next().run();
            }
            assertEquals(2, coordinator.completedSteps());
            assertEquals(2, bridge.prefixes.get("a"));
            assertFalse(bridge.events.stream().anyMatch(event -> event.startsWith("apply:a:")));
            assertEquals(2, bridge.prefixes.get("b"));
            assertEquals(2, bridge.committedTick);
        }
    }

    @Test
    void rejectedOwnerDispatchClosesOnTheNextFrameInsteadOfStallingTheAcceptedStep() throws Exception {
        FakeBridge bridge = new FakeBridge();
        try (var coordinator = new CombatSimulationSession.Coordinator<>(bridge,
                input -> new Result(input.actor(), input.tick()), action -> {
                    throw new RejectedExecutionException("owner dispatcher stopped");
                })) {
            coordinator.beginFrame(1);
            IllegalStateException reported = null;
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (reported == null && System.nanoTime() < deadline) {
                try {
                    coordinator.endFrame();
                } catch (IllegalStateException failure) {
                    reported = failure;
                }
                if (reported == null) {
                    Thread.sleep(1);
                }
            }
            assertTrue(reported != null, "A failed dispatch must be reported to the owner frame.");
            assertTrue(reported.getCause() instanceof RejectedExecutionException);
            assertTrue(coordinator.isClosed());
            assertEquals(1, bridge.closes);
            assertEquals(0, coordinator.completedSteps());
            assertFalse(bridge.events.contains("circuit:1"));
        }
    }

    private static CombatSimulationSession.Coordinator<String, Request, Result> coordinator(
            FakeBridge bridge, Dispatcher dispatcher, Consumer<Request> work) {
        return new CombatSimulationSession.Coordinator<>(bridge, input -> {
            work.accept(input);
            return new Result(input.actor(), input.tick());
        }, dispatcher);
    }

    private record Request(String actor, long tick) {
    }

    private record Result(String actor, long tick) {
    }

    private static final class Dispatcher implements Executor {
        private final LinkedBlockingQueue<Runnable> actions = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable action) { actions.add(action); }

        Runnable next() throws InterruptedException {
            Runnable next = actions.poll(3, TimeUnit.SECONDS);
            assertTrue(next != null, "A worker completion must schedule the next cooperative boundary.");
            return next;
        }
    }

    private static final class FakeBridge implements CombatSimulationSession.Bridge<String, Request, Result> {
        private final List<String> events = new ArrayList<>();
        private final List<String> members = new ArrayList<>(List.of("a", "b"));
        private final Set<String> removed = new HashSet<>();
        private final Set<String> ambient = new HashSet<>();
        private final Map<String, Integer> prefixes = new HashMap<>();
        private final Map<String, Long> actorRevisions = new HashMap<>();
        private final List<Long> presentedTicks = new ArrayList<>();
        private Consumer<String> afterApply = ignored -> {};
        private boolean valid = true;
        private long tick;
        private long revision;
        private long committedTick;
        private int closes;

        public boolean valid() { return valid; }
        public long revision() { return revision; }
        public long logicalTick() { return tick; }
        public int round() { return 1; }
        public List<Request> beginStep() {
            tick++;
            events.add("begin:" + tick);
            return List.of(new Request("circuit", tick));
        }
        public void acceptCircuit(int index, Result result) { events.add("circuit:" + tick); }
        public List<String> actors() { return List.copyOf(members); }
        public boolean available(String actor) { return !removed.contains(actor); }
        public long actorRevision(String actor) { return actorRevisions.getOrDefault(actor, 0L); }
        public Request prepare(String actor) {
            events.add("prepare:" + actor + ":" + tick);
            prefixes.merge(actor, 1, Integer::sum);
            return ambient.contains(actor) ? null : new Request(actor, tick);
        }
        public void apply(String actor, Request input, Result result) {
            assertEquals(actor, input.actor());
            assertEquals(actor, result.actor());
            assertEquals(tick, result.tick());
            events.add("apply:" + actor + ":" + tick);
            afterApply.accept(actor);
        }
        public void abort(String actor) { events.add("abort:" + actor); }
        public void runInput(Runnable input) { revision++; input.run(); }
        public void completeStep() {
            events.add("game:" + tick);
            events.add("tasks:" + tick);
            events.add("complete:" + tick);
            committedTick = tick;
        }
        public void present() { presentedTicks.add(committedTick); }
        public void close() { closes++; }
    }

    private static void await(CountDownLatch release) {
        try {
            assertTrue(release.await(3, TimeUnit.SECONDS), "Timed out releasing controlled physics work.");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
