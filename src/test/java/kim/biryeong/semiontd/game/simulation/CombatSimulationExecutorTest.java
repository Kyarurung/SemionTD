package kim.biryeong.semiontd.game.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

final class CombatSimulationExecutorTest {
    @Test
    void offerAndPollNeverWaitForTheDedicatedWorker() throws Exception {
        Thread owner = Thread.currentThread();
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            executionThread.set(Thread.currentThread());
            entered.countDown();
            await(release);
            return input * 2;
        })) {
            CombatSimulationToken token = token(executor.generation());
            assertTimeout(Duration.ofMillis(200), () -> assertTrue(executor.offer(token, 7)));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(200), () -> {
                assertTrue(executor.poll().isEmpty());
                assertFalse(executor.offer(token, 9));
            });
            assertNotSame(owner, executionThread.get());
            assertTrue(executionThread.get().isDaemon());
            assertFalse(executionThread.get().isVirtual());
            release.countDown();
            var result = completion(executor);
            assertEquals(token, result.token());
            assertEquals(14, result.output());
            assertNull(result.error());
            assertTrue(executor.poll().isEmpty());
        } finally {
            release.countDown();
        }
    }

    @Test
    void unconsumedCompletionPreventsAnotherJobAndIsDeliveredOnce() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Thread> worker = new AtomicReference<>();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            calls.incrementAndGet();
            return input;
        }, action -> {
            Thread thread = new Thread(action);
            worker.set(thread);
            return thread;
        })) {
            CombatSimulationToken token = token(executor.generation());
            assertTrue(executor.offer(token, 1));
            waitUntil(() -> calls.get() == 1 && worker.get().getState() == Thread.State.WAITING);
            assertFalse(executor.offer(token, 2));
            assertEquals(1, completion(executor).output());
            assertTrue(executor.poll().isEmpty());
            assertTrue(executor.offer(token, 2));
            assertEquals(2, completion(executor).output());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void invalidationRetainsTheLeaseUntilInterruptIgnoringCodeActuallyFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                awaitIgnoringInterrupts(release, interrupted);
            }
            return input;
        })) {
            CombatSimulationToken oldToken = token(executor.generation());
            assertTrue(executor.offer(oldToken, 1));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            long generation = executor.invalidate();
            assertTrue(interrupted.await(3, TimeUnit.SECONDS));
            CombatSimulationToken next = token(generation);
            assertTimeout(Duration.ofMillis(200), () -> {
                assertFalse(executor.offer(next, 2));
                assertTrue(executor.poll().isEmpty());
            });
            release.countDown();
            waitUntil(() -> executor.offer(next, 2));
            var result = completion(executor);
            assertEquals(next, result.token());
            assertEquals(2, result.output());
            assertTrue(executor.poll().isEmpty());
            assertFalse(executor.offer(oldToken, 3));
        } finally {
            release.countDown();
        }
    }

    @Test
    void invalidatingBeforeExecutionSkipsTheOldFunctionAndReleasesTheLease() throws Exception {
        CountDownLatch startWorker = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            calls.incrementAndGet();
            return input;
        }, action -> new Thread(() -> {
            awaitIgnoringInterrupts(startWorker, new CountDownLatch(0));
            action.run();
        }))) {
            assertTrue(executor.offer(token(executor.generation()), 1));
            CombatSimulationToken next = token(executor.invalidate());
            assertFalse(executor.offer(next, 2));
            startWorker.countDown();
            waitUntil(() -> executor.offer(next, 2));
            assertEquals(2, completion(executor).output());
            assertEquals(1, calls.get());
        } finally {
            startWorker.countDown();
        }
    }

    @Test
    void invalidationDropsAnAlreadyPublishedCompletion() throws Exception {
        AtomicReference<Thread> worker = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            calls.incrementAndGet();
            return input;
        }, action -> {
            Thread thread = new Thread(action);
            worker.set(thread);
            return thread;
        })) {
            assertTrue(executor.offer(token(executor.generation()), 1));
            waitUntil(() -> calls.get() == 1 && worker.get().getState() == Thread.State.WAITING);
            CombatSimulationToken next = token(executor.invalidate());
            assertTrue(executor.poll().isEmpty());
            assertTrue(executor.offer(next, 2));
            assertEquals(next, completion(executor).token());
        }
    }

    @Test
    void failuresAreReturnedAndTheSameWorkerCanRunTheNextJob() throws Exception {
        RuntimeException failure = new IllegalArgumentException("simulation failed");
        AtomicReference<Thread> firstWorker = new AtomicReference<>();
        AtomicReference<Thread> secondWorker = new AtomicReference<>();
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            if (input == 1) {
                firstWorker.set(Thread.currentThread());
                throw failure;
            }
            secondWorker.set(Thread.currentThread());
            return input;
        })) {
            CombatSimulationToken token = token(executor.generation());
            assertTrue(executor.offer(token, 1));
            var result = completion(executor);
            assertSame(failure, result.error());
            assertNull(result.output());
            waitUntil(() -> executor.offer(token, 2));
            assertEquals(2, completion(executor).output());
            assertSame(firstWorker.get(), secondWorker.get());
        }
    }

    @Test
    void closeDoesNotWaitForIgnoredInterruptsOrPermitLateResultsAndRestart() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Thread> worker = new AtomicReference<>();
        var executor = new CombatSimulationExecutor<Integer, Integer>(input -> {
            worker.set(Thread.currentThread());
            entered.countDown();
            awaitIgnoringInterrupts(release, new CountDownLatch(0));
            return input;
        });
        try {
            assertTrue(executor.offer(token(executor.generation()), 1));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertTimeout(Duration.ofMillis(200), executor::close);
            long generation = executor.generation();
            executor.close();
            assertEquals(generation, executor.generation());
            assertFalse(executor.offer(token(generation), 2));
            assertTrue(executor.poll().isEmpty());
            release.countDown();
            waitUntil(() -> !worker.get().isAlive());
            assertTrue(executor.poll().isEmpty());
            assertFalse(executor.offer(token(executor.generation()), 3));
        } finally {
            release.countDown();
            executor.close();
        }
    }

    @Test
    void lifecycleOperationsRequireTheConstructingOwnerThread() throws Exception {
        try (var executor = new CombatSimulationExecutor<Integer, Integer>(input -> input)) {
            var token = token(executor.generation());
            CompletableFuture.runAsync(() -> {
                assertThrows(IllegalStateException.class, executor::generation);
                assertThrows(IllegalStateException.class, () -> executor.offer(token, 1));
                assertThrows(IllegalStateException.class, executor::poll);
                assertThrows(IllegalStateException.class, executor::invalidate);
                assertThrows(IllegalStateException.class, executor::close);
            }).get(3, TimeUnit.SECONDS);
            assertTrue(executor.offer(token, 1));
            assertEquals(1, completion(executor).output());
        }
    }

    @Test
    void everySessionTokenComponentParticipatesInIdentity() {
        CombatSimulationToken original = token(3);
        assertFalse(original.equals(new CombatSimulationToken(4, 7, 20, 2, 11)));
        assertFalse(original.equals(new CombatSimulationToken(3, 8, 20, 2, 11)));
        assertFalse(original.equals(new CombatSimulationToken(3, 7, 21, 2, 11)));
        assertFalse(original.equals(new CombatSimulationToken(3, 7, 20, 3, 11)));
        assertFalse(original.equals(new CombatSimulationToken(3, 7, 20, 2, 12)));
        assertEquals(original, new CombatSimulationToken(3, 7, 20, 2, 11));
    }

    private static CombatSimulationToken token(long generation) {
        return new CombatSimulationToken(generation, 7, 20, 2, 11);
    }

    private static <I, O> CombatSimulationExecutor.Completion<O> completion(CombatSimulationExecutor<I, O> executor)
            throws InterruptedException {
        AtomicReference<CombatSimulationExecutor.Completion<O>> result = new AtomicReference<>();
        waitUntil(() -> {
            Optional<CombatSimulationExecutor.Completion<O>> polled = executor.poll();
            polled.ifPresent(result::set);
            return result.get() != null;
        });
        return result.get();
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "Timed out waiting for the controlled test worker.");
            Thread.sleep(1);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out releasing the controlled test worker.");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch, CountDownLatch interrupted) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (latch.getCount() > 0) {
            try {
                if (!latch.await(Math.max(1, deadline - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                    throw new AssertionError("Timed out releasing the interrupt-ignoring test worker.");
                }
            } catch (InterruptedException ignored) {
                interrupted.countDown();
            }
        }
    }
}
