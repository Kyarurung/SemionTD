package kim.biryeong.semiontd.game.replay.benchmark;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;

public final class BenchmarkRecorder {
    private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
    private final com.sun.management.ThreadMXBean allocations = threads instanceof com.sun.management.ThreadMXBean bean ? bean : null;
    private final com.sun.management.OperatingSystemMXBean process = ManagementFactory.getOperatingSystemMXBean()
            instanceof com.sun.management.OperatingSystemMXBean bean ? bean : null;
    private final List<Frame> frames = new ArrayList<>();
    private Snapshot start;
    private long frameStart;
    private Long frameCpu;
    private Long frameAllocated;
    private long nativeStart;
    private long nativeNanos;
    private long outerNanos;
    private long waitStart;
    private long waitNanos;
    private Long waitCpuStart;
    private Long waitCpu;
    private boolean enabled;
    private Long workerId;
    private Long lastWorkerCpu;
    private Long lastWorkerAllocated;
    private boolean workerFrozen;

    public void worker(Thread worker) {
        workerId = worker == null ? null : worker.threadId();
        workerFrozen = false;
        lastWorkerCpu = lastWorkerAllocated = null;
    }

    public void freezeWorker() {
        if (workerId != null) {
            lastWorkerCpu = cpu(workerId);
            lastWorkerAllocated = allocated(workerId);
        }
        workerFrozen = true;
    }

    public BenchmarkRecorder() {
        if (threads.isThreadCpuTimeSupported() && !threads.isThreadCpuTimeEnabled()) {
            try { threads.setThreadCpuTimeEnabled(true); } catch (RuntimeException ignored) {}
        }
        if (allocations != null && allocations.isThreadAllocatedMemorySupported() && !allocations.isThreadAllocatedMemoryEnabled()) {
            try { allocations.setThreadAllocatedMemoryEnabled(true); } catch (RuntimeException ignored) {}
        }
    }

    public void begin() {
        frames.clear();
        start = snapshot();
        enabled = true;
        frameStart = start.wall();
        frameCpu = start.mainCpu();
        frameAllocated = start.mainAllocated();
        nativeNanos = outerNanos = waitNanos = 0;
        waitCpu = 0L;
    }

    public void frameBoundary(int configuredRate) {
        if (!enabled) {
            return;
        }
        long now = System.nanoTime();
        Long cpu = cpu(Thread.currentThread().threadId());
        Long allocated = allocated(Thread.currentThread().threadId());
        if (frameStart != 0) {
            frames.add(new Frame(now - frameStart, difference(cpu, frameCpu), difference(allocated, frameAllocated),
                    nativeNanos, outerNanos, waitNanos, waitCpu, now - frameStart > 1000000000L / configuredRate));
        }
        frameStart = now;
        frameCpu = cpu;
        frameAllocated = allocated;
        nativeNanos = outerNanos = waitNanos = 0;
        waitCpu = 0L;
    }

    public void nativeBegin() { if (enabled) { nativeStart = System.nanoTime(); } }
    public void nativeEnd() { if (enabled) { nativeNanos += System.nanoTime() - nativeStart; } }
    public void outerEnd() { if (enabled) { outerNanos = System.nanoTime() - frameStart; } }
    public void waitBegin() {
        if (enabled) {
            waitStart = System.nanoTime();
            waitCpuStart = cpu(Thread.currentThread().threadId());
        }
    }
    public void waitEnd() {
        if (enabled) {
            waitNanos += System.nanoTime() - waitStart;
            Long delta = difference(cpu(Thread.currentThread().threadId()), waitCpuStart);
            waitCpu = waitCpu == null || delta == null ? null : waitCpu + delta;
        }
    }

    public JsonObject end(long logicalTicks, long pacingCalls) {
        Snapshot finish = snapshot();
        enabled = false;
        JsonObject result = new JsonObject();
        result.addProperty("elapsed_ns", finish.wall() - start.wall());
        result.addProperty("logical_ticks", logicalTicks);
        result.addProperty("configured_logical_tps", 160);
        result.addProperty("observed_logical_tps", logicalTicks * 1000000000.0 / (finish.wall() - start.wall()));
        result.addProperty("base_wait_calls", pacingCalls);
        result.addProperty("physical_frames", frames.size());
        result.addProperty("worker_cutoff", "LAST_LOGICAL_OBSERVER_BEFORE_SESSION_AUTOCLOSE");
        nullable(result, "main_cpu_ns", difference(finish.mainCpu(), start.mainCpu()));
        nullable(result, "main_allocated_bytes", difference(finish.mainAllocated(), start.mainAllocated()));
        nullable(result, "worker_cpu_ns", difference(finish.workerCpu(), start.workerCpu()));
        nullable(result, "worker_allocated_bytes", difference(finish.workerAllocated(), start.workerAllocated()));
        nullable(result, "process_cpu_ns", difference(finish.processCpu(), start.processCpu()));
        nullable(result, "gc_count", difference(finish.gcCount(), start.gcCount()));
        nullable(result, "gc_time_ms", difference(finish.gcMillis(), start.gcMillis()));
        JsonArray frameRows = new JsonArray();
        for (Frame frame : frames) {
            JsonObject row = new JsonObject();
            row.addProperty("start_to_next_start_ns", frame.interval());
            nullable(row, "whole_main_cpu_ns", frame.cpu());
            nullable(row, "main_allocated_bytes", frame.allocated());
            row.addProperty("native_tick_wall_ns", frame.nativeTick());
            row.addProperty("outer_tick_wall_ns", frame.outerTick());
            row.addProperty("wait_wall_ns", frame.waitTime());
            nullable(row, "wait_main_cpu_ns", frame.waitCpu());
            row.addProperty("deadline_overrun", frame.overrun());
            frameRows.add(row);
        }
        result.add("frames", frameRows);
        return result;
    }

    private Snapshot snapshot() {
        Long workerCpu = workerId == null ? 0L : workerFrozen ? lastWorkerCpu : cpu(workerId);
        Long workerAllocated = workerId == null ? 0L : workerFrozen ? lastWorkerAllocated : allocated(workerId);
        Long gcCount = 0L;
        Long gcMillis = 0L;
        for (var collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            long count = collector.getCollectionCount();
            long millis = collector.getCollectionTime();
            gcCount = gcCount == null || count < 0 ? null : gcCount + count;
            gcMillis = gcMillis == null || millis < 0 ? null : gcMillis + millis;
        }
        return new Snapshot(System.nanoTime(), cpu(Thread.currentThread().threadId()), allocated(Thread.currentThread().threadId()),
                workerCpu, workerAllocated, process == null ? null : available(process.getProcessCpuTime()), gcCount, gcMillis);
    }

    private Long cpu(long id) {
        return threads.isThreadCpuTimeSupported() && threads.isThreadCpuTimeEnabled() ? available(threads.getThreadCpuTime(id)) : null;
    }
    private Long allocated(long id) {
        return allocations != null && allocations.isThreadAllocatedMemoryEnabled() ? available(allocations.getThreadAllocatedBytes(id)) : null;
    }
    private static Long available(long value) { return value < 0 ? null : value; }
    private static Long difference(Long after, Long before) { return after == null || before == null || after < before ? null : after - before; }
    private static void nullable(JsonObject object, String key, Long value) { object.addProperty(key, value); }

    private record Snapshot(long wall, Long mainCpu, Long mainAllocated, Long workerCpu, Long workerAllocated,
                            Long processCpu, Long gcCount, Long gcMillis) {}
    private record Frame(long interval, Long cpu, Long allocated, long nativeTick, long outerTick,
                         long waitTime, Long waitCpu, boolean overrun) {}
}
