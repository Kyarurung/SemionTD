package kim.biryeong.semiontd.game.simulation;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class CombatSimulationExecutor<I, O> implements AutoCloseable {
    private final Thread owner = Thread.currentThread();
    private final Function<I, O> simulation;
    private final Semaphore available = new Semaphore(0);
    private final AtomicReference<Job<I>> unfinished = new AtomicReference<>();
    private final AtomicReference<Completion<O>> completed = new AtomicReference<>();
    private final AtomicLong generation = new AtomicLong();
    private final Thread worker;
    private volatile boolean closed;

    public CombatSimulationExecutor(Function<I, O> simulation) {
        this(simulation, action -> new Thread(action, "semion-td-combat-simulation"));
    }

    CombatSimulationExecutor(Function<I, O> simulation, ThreadFactory factory) {
        this.simulation = Objects.requireNonNull(simulation, "simulation");
        worker = Objects.requireNonNull(factory.newThread(this::runWorker), "worker");
        if (worker.isVirtual()) {
            throw new IllegalArgumentException("Combat simulation requires a platform thread.");
        }
        worker.setDaemon(true);
        worker.start();
    }

    public long generation() {
        requireOwner();
        return generation.get();
    }

    public boolean offer(CombatSimulationToken token, I input) {
        requireOwner();
        Objects.requireNonNull(token, "token");
        if (closed || token.generation() != generation.get() || completed.get() != null) {
            return false;
        }
        Job<I> job = new Job<>(token, input);
        if (!unfinished.compareAndSet(null, job)) {
            return false;
        }
        available.release();
        return true;
    }

    public Optional<Completion<O>> poll() {
        requireOwner();
        Completion<O> result = completed.getAndSet(null);
        return result != null && !closed && result.token().generation() == generation.get()
                ? Optional.of(result) : Optional.empty();
    }

    public long invalidate() {
        requireOwner();
        if (closed) {
            return generation.get();
        }
        long next = generation.incrementAndGet();
        completed.set(null);
        worker.interrupt();
        return next;
    }

    @Override
    public void close() {
        requireOwner();
        if (closed) {
            return;
        }
        closed = true;
        generation.incrementAndGet();
        completed.set(null);
        worker.interrupt();
    }

    private void runWorker() {
        while (!closed) {
            try {
                available.acquire();
            } catch (InterruptedException interrupted) {
                continue;
            }
            Job<I> job = unfinished.get();
            if (job == null) {
                continue;
            }
            try {
                if (!closed && job.token().generation() == generation.get()) {
                    Completion<O> result;
                    try {
                        result = new Completion<>(job.token(), simulation.apply(job.input()), null);
                    } catch (Throwable failure) {
                        result = new Completion<>(job.token(), null, failure);
                    }
                    if (!closed && job.token().generation() == generation.get()) {
                        completed.set(result);
                        if (closed || job.token().generation() != generation.get()) {
                            completed.compareAndSet(result, null);
                        }
                    }
                }
            } finally {
                Thread.interrupted();
                unfinished.compareAndSet(job, null);
            }
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Combat simulation lifecycle must run on its owner thread.");
        }
    }

    public record Completion<O>(CombatSimulationToken token, O output, Throwable error) {
        public Completion {
            Objects.requireNonNull(token, "token");
        }
    }

    private record Job<I>(CombatSimulationToken token, I input) {
    }
}
