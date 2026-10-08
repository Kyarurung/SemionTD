package kim.biryeong.semiontd.game.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

final class CombatSimulationExecutorContinuationTest {
    @Test
    void ownerContinuationCanConsumeAndImmediatelyOfferTheNextJob() throws Exception {
        Thread owner = Thread.currentThread();
        OwnerDispatcher dispatcher = new OwnerDispatcher();
        AtomicReference<CombatSimulationExecutor<Integer, Integer>> current = new AtomicReference<>();
        List<Integer> outputs = new ArrayList<>();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> input * 2, dispatcher, () -> {
            assertSame(owner, Thread.currentThread());
            var result = current.get().poll().orElseThrow();
            outputs.add(result.output());
            if (outputs.size() == 1) {
                assertTrue(current.get().offer(token(current.get().generation(), 2), 2));
            }
        })) {
            current.set(executor);
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            Runnable firstReady = dispatcher.next();
            assertTrue(outputs.isEmpty(), "The worker must only schedule, never execute, owner continuation.");
            assertFalse(executor.offer(token(executor.generation(), 2), 2), "An unconsumed result holds backpressure.");
            firstReady.run();
            dispatcher.next().run();
            assertEquals(List.of(2, 4), outputs);
            assertTrue(executor.poll().isEmpty());
        }
    }

    @Test
    void canceledInterruptIgnoringWorkNotifiesAfterReleasingItsLease() throws Exception {
        OwnerDispatcher dispatcher = new OwnerDispatcher();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<CombatSimulationExecutor<Integer, Integer>> current = new AtomicReference<>();
        List<Integer> outputs = new ArrayList<>();
        AtomicInteger readyCalls = new AtomicInteger();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            if (input == 1) {
                entered.countDown();
                awaitIgnoringInterrupts(release);
            }
            return input;
        }, dispatcher, () -> {
            readyCalls.incrementAndGet();
            var result = current.get().poll();
            if (result.isEmpty()) {
                assertTrue(current.get().offer(token(current.get().generation(), 2), 2));
            } else {
                outputs.add(result.orElseThrow().output());
            }
        })) {
            current.set(executor);
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            long generation = executor.invalidate();
            assertFalse(executor.offer(token(generation, 2), 2));
            assertTrue(dispatcher.isEmpty());
            release.countDown();
            dispatcher.next().run();
            dispatcher.next().run();
            assertEquals(2, readyCalls.get());
            assertEquals(List.of(2), outputs);
        } finally {
            release.countDown();
        }
    }

    @Test
    void invalidationBeforeWorkerStartStillDispatchesLeaseRelease() throws Exception {
        OwnerDispatcher dispatcher = new OwnerDispatcher();
        CountDownLatch startWorker = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<CombatSimulationExecutor<Integer, Integer>> current = new AtomicReference<>();
        List<Integer> outputs = new ArrayList<>();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            calls.incrementAndGet();
            return input;
        }, action -> new Thread(() -> {
            awaitIgnoringInterrupts(startWorker);
            action.run();
        }), dispatcher, () -> {
            var result = current.get().poll();
            if (result.isEmpty()) {
                assertTrue(current.get().offer(token(current.get().generation(), 2), 2));
            } else {
                outputs.add(result.orElseThrow().output());
            }
        })) {
            current.set(executor);
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            executor.invalidate();
            assertTrue(dispatcher.isEmpty());
            startWorker.countDown();
            dispatcher.next().run();
            dispatcher.next().run();
            assertEquals(List.of(2), outputs);
            assertEquals(1, calls.get(), "The invalidated queued computation must never execute.");
        } finally {
            startWorker.countDown();
        }
    }

    @Test
    void invalidatedCompletionStillWakesOwnerWithoutApplyingItsOldOutput() throws Exception {
        OwnerDispatcher dispatcher = new OwnerDispatcher();
        AtomicReference<CombatSimulationExecutor<Integer, Integer>> current = new AtomicReference<>();
        List<Integer> outputs = new ArrayList<>();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> input, dispatcher, () -> {
            var result = current.get().poll();
            if (result.isPresent()) {
                outputs.add(result.orElseThrow().output());
            } else {
                assertTrue(current.get().offer(token(current.get().generation(), 2), 2));
            }
        })) {
            current.set(executor);
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            Runnable staleReady = dispatcher.next();
            executor.invalidate();
            staleReady.run();
            dispatcher.next().run();
            assertEquals(List.of(2), outputs);
        }
    }

    @Test
    void closingSuppressesAlreadyQueuedOwnerActions() throws Exception {
        OwnerDispatcher dispatcher = new OwnerDispatcher();
        AtomicInteger readyCalls = new AtomicInteger();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> input, dispatcher,
                readyCalls::incrementAndGet)) {
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            Runnable pending = dispatcher.next();
            executor.close();
            pending.run();
            assertEquals(0, readyCalls.get());
            assertTrue(executor.poll().isEmpty());
            assertTrue(executor.notificationFailure().isEmpty());
        }
    }

    @Test
    void activeDispatcherRejectionIsVisibleAndDoesNotKillTheWorker() throws Exception {
        OwnerDispatcher dispatcher = new OwnerDispatcher();
        RejectedExecutionException rejected = new RejectedExecutionException("owner stopped accepting tasks");
        AtomicBoolean reject = new AtomicBoolean(true);
        AtomicReference<Thread> firstWorker = new AtomicReference<>();
        AtomicReference<Thread> secondWorker = new AtomicReference<>();
        AtomicReference<CombatSimulationExecutor<Integer, Integer>> current = new AtomicReference<>();
        List<Integer> outputs = new ArrayList<>();
        Executor sometimesRejects = action -> {
            if (reject.get()) {
                throw rejected;
            }
            dispatcher.execute(action);
        };
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            (input == 1 ? firstWorker : secondWorker).set(Thread.currentThread());
            return input;
        }, sometimesRejects, () -> outputs.add(current.get().poll().orElseThrow().output()))) {
            current.set(executor);
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            waitUntil(() -> executor.notificationFailure().isPresent());
            assertSame(rejected, executor.notificationFailure().orElseThrow());
            var result = executor.poll().orElseThrow();
            assertSame(rejected, result.error());
            assertEquals(null, result.output());
            reject.set(false);
            assertTrue(executor.offer(token(executor.generation(), 2), 2));
            dispatcher.next().run();
            waitUntil(() -> executor.notificationFailure().isEmpty());
            assertEquals(List.of(2), outputs);
            assertSame(firstWorker.get(), secondWorker.get());
        }
    }

    @Test
    void shutdownDispatcherRejectionCannotPublishALateFailure() throws Exception {
        CountDownLatch dispatchEntered = new CountDownLatch(1);
        CountDownLatch releaseDispatch = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicInteger readyCalls = new AtomicInteger();
        Executor rejectingAfterShutdown = action -> {
            dispatchEntered.countDown();
            awaitIgnoringInterrupts(releaseDispatch);
            throw new RejectedExecutionException("shutdown");
        };
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            worker.set(Thread.currentThread());
            return input;
        }, rejectingAfterShutdown, readyCalls::incrementAndGet)) {
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            assertTrue(dispatchEntered.await(3, TimeUnit.SECONDS));
            executor.close();
            releaseDispatch.countDown();
            waitUntil(() -> !worker.get().isAlive());
            assertEquals(0, readyCalls.get());
            assertTrue(executor.poll().isEmpty());
            assertTrue(executor.notificationFailure().isEmpty());
        } finally {
            releaseDispatch.countDown();
        }
    }

    @Test
    void inlineDispatcherCannotExecuteTheOwnerActionOnTheWorker() throws Exception {
        AtomicInteger readyCalls = new AtomicInteger();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> input, Runnable::run,
                readyCalls::incrementAndGet)) {
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            waitUntil(() -> executor.notificationFailure().isPresent());
            assertInstanceOf(IllegalStateException.class, executor.notificationFailure().orElseThrow());
            assertInstanceOf(IllegalStateException.class, executor.poll().orElseThrow().error());
            assertEquals(0, readyCalls.get());
        }
    }

    @Test
    void calculationFailureAlsoDispatchesAnOwnerContinuation() throws Exception {
        OwnerDispatcher dispatcher = new OwnerDispatcher();
        AtomicReference<CombatSimulationExecutor<Integer, Integer>> current = new AtomicReference<>();
        RuntimeException failure = new IllegalArgumentException("calculation failed");
        AtomicReference<Throwable> received = new AtomicReference<>();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            throw failure;
        }, dispatcher, () -> received.set(current.get().poll().orElseThrow().error()))) {
            current.set(executor);
            assertTrue(executor.offer(token(executor.generation(), 1), 1));
            dispatcher.next().run();
            assertSame(failure, received.get());
            assertTrue(executor.poll().isEmpty());
        }
    }

    private static CombatSimulationToken token(long generation, long sequence) {
        return new CombatSimulationToken(generation, 7, sequence, 2, sequence);
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for the controlled worker.");
            Thread.sleep(1);
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (latch.getCount() > 0) {
            try {
                assertTrue(latch.await(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS),
                        "Timed out releasing the controlled dispatcher.");
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static final class OwnerDispatcher implements Executor {
        private final LinkedBlockingQueue<Runnable> pending = new LinkedBlockingQueue<>();

        @Override
        public void execute(Runnable action) {
            pending.add(action);
        }

        private Runnable next() throws InterruptedException {
            Runnable action = pending.poll(3, TimeUnit.SECONDS);
            assertTrue(action != null, "The worker must dispatch an owner continuation.");
            return action;
        }

        private boolean isEmpty() {
            return pending.isEmpty();
        }
    }
}
