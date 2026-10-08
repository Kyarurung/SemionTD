package kim.biryeong.semiontd.game.replay.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

public final class CombatBenchmarkPacingTest {
    @GameTest(maxTicks = 1400)
    public void nativeWaitPacesTwentyAndOneHundredSixtyTicksAndDrainsOwnerTasks(GameTestHelper context) {
        if (!CombatBenchmarkHooks.enabled()) {
            context.succeed();
            return;
        }
        MinecraftServer server = context.getLevel().getServer();
        Probe probe = new Probe(context, server);
        server.tickRateManager().setTickRate(20);
        CombatBenchmarkHooks.install(server, probe);
        probe.startNotificationThread();
    }

    private static final class Probe implements CombatBenchmarkHooks.Listener {
        private final GameTestHelper context;
        private final MinecraftServer server;
        private final float originalRate;
        private final CountDownLatch notificationRequested = new CountDownLatch(1);
        private final List<Long> intervals = new ArrayList<>();
        private final List<Long> warmIntervals = new ArrayList<>();
        private int rate = 20;
        private int warmFrames;
        private long previousStart;
        private long warmStart;
        private long warmElapsed;
        private long measuredStart;
        private long waitCallsAtStart;
        private long nativeTicks;
        private long nativeTicksAtStart;
        private long frame;
        private long notificationFrame;
        private long notificationDeadline;
        private long notificationCompleted;
        private boolean frameOpen;
        private boolean nativeOpen;
        private boolean nativeFinished;
        private boolean waiting;
        private boolean notificationDuringWait;
        private boolean notificationOnOwner;
        private boolean finished;
        private volatile String failure;

        private Probe(GameTestHelper context, MinecraftServer server) {
            this.context = context;
            this.server = server;
            originalRate = server.tickRateManager().tickrate();
        }

        private void startNotificationThread() {
            Thread.ofPlatform().daemon().name("semion-td-benchmark-pacing-probe").start(() -> {
                try {
                    if (!notificationRequested.await(30, TimeUnit.SECONDS)) {
                        failure = "The native wait notification was never requested.";
                        return;
                    }
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    server.execute(() -> {
                        notificationCompleted = System.nanoTime();
                        notificationDuringWait = waiting && frame == notificationFrame;
                        notificationOnOwner = server.isSameThread();
                    });
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failure = "The native wait notification probe was interrupted.";
                }
            });
        }

        @Override
        public void beforeFrame(MinecraftServer current) {
            if (finished) {
                return;
            }
            check(!frameOpen && !waiting && !nativeOpen, "Frame and wait hooks must not overlap.");
            if (failure != null) {
                finish();
                return;
            }
            long now = System.nanoTime();
            if (measuredStart != 0) {
                intervals.add(now - previousStart);
                if (intervals.size() == rate) {
                    verifyWindow(now);
                    if (failure != null || rate == 160) {
                        finish();
                        return;
                    }
                    rate = 160;
                    server.tickRateManager().setTickRate(rate);
                    measuredStart = 0;
                    previousStart = 0;
                    warmFrames = 0;
                    warmStart = now;
                    intervals.clear();
                    warmIntervals.clear();
                }
            }
            if (measuredStart == 0) {
                if (warmStart == 0) {
                    warmStart = now;
                }
                if (previousStart != 0) {
                    warmIntervals.add(now - previousStart);
                }
                warmFrames++;
                if (warmIntervals.size() >= rate && warmWindowMatchesRate()) {
                    measuredStart = now;
                    warmElapsed = now - warmStart;
                    waitCallsAtStart = CombatBenchmarkHooks.baseWaitCallCount(server);
                    nativeTicksAtStart = nativeTicks;
                }
                check(warmFrames < 600, "Native deadlines did not settle at configured rate " + rate);
            }
            previousStart = now;
            frame++;
            frameOpen = true;
            nativeFinished = false;
        }

        @Override
        public void beforeNativeTick(MinecraftServer current) {
            if (frame == 0) {
                return;
            }
            check(frameOpen && !nativeOpen && !nativeFinished, "The native body must start inside its outer frame.");
            nativeOpen = true;
            nativeTicks++;
        }

        @Override
        public void afterNativeTick(MinecraftServer current) {
            if (frame == 0) {
                return;
            }
            check(nativeOpen, "The native body must end after its start.");
            nativeOpen = false;
            nativeFinished = true;
        }

        @Override
        public void afterFrame(MinecraftServer current) {
            if (frame == 0) {
                return;
            }
            check(frameOpen && nativeFinished && !nativeOpen, "The outer frame must include its native body.");
            frameOpen = false;
        }

        @Override
        public void beforeWait(MinecraftServer current) {
            if (frame == 0) {
                return;
            }
            check(!frameOpen && !waiting, "Native waiting must follow the completed outer frame.");
            waiting = true;
            if (rate == 20 && measuredStart != 0 && notificationFrame == 0) {
                notificationFrame = frame;
                notificationDeadline = server.getNextTickTime();
                notificationRequested.countDown();
            }
        }

        @Override
        public void afterWait(MinecraftServer current) {
            if (frame == 0) {
                return;
            }
            check(waiting, "Native waiting must end after its start.");
            waiting = false;
        }

        private void verifyWindow(long now) {
            double elapsedSeconds = (now - measuredStart) / 1.0e9;
            double actualRate = intervals.size() / elapsedSeconds;
            long waitCalls = CombatBenchmarkHooks.baseWaitCallCount(server) - waitCallsAtStart;
            check(actualRate >= rate * 0.8 && actualRate <= rate * 1.2,
                    "Measured native frame rate must match configured " + rate + ": actual=" + actualRate);
            check(waitCalls == intervals.size() && nativeTicks - nativeTicksAtStart == intervals.size(),
                    "Each measured physical frame must include one native body and one successful native wait.");
            if (rate == 20) {
                check(notificationOnOwner && notificationDuringWait && notificationCompleted < notificationDeadline,
                        "A task posted from another thread must complete on the owner during the same wait before its deadline.");
            }
            List<Long> ordered = new ArrayList<>(intervals);
            Collections.sort(ordered);
            System.out.println("BENCHMARK_PACING_CALIBRATION configured_tps=" + rate + " frames=" + intervals.size()
                    + " warm_frames=" + warmFrames + " elapsed_seconds=" + elapsedSeconds + " actual_tps=" + actualRate
                    + " warm_elapsed_seconds=" + warmElapsed / 1.0e9
                    + " min_interval_ms=" + ordered.getFirst() / 1.0e6
                    + " median_interval_ms=" + ordered.get(ordered.size() / 2) / 1.0e6
                    + " max_interval_ms=" + ordered.getLast() / 1.0e6 + " base_wait_calls=" + waitCalls
                    + " owner_notification_during_wait=" + notificationDuringWait
                    + " owner_notification_before_deadline=" + (notificationCompleted < notificationDeadline));
        }

        private boolean warmWindowMatchesRate() {
            long elapsed = 0;
            for (int index = warmIntervals.size() - rate; index < warmIntervals.size(); index++) {
                elapsed += warmIntervals.get(index);
            }
            double actualRate = rate / (elapsed / 1.0e9);
            return actualRate >= rate * 0.8 && actualRate <= rate * 1.2;
        }

        private void check(boolean condition, String message) {
            if (!condition && failure == null) {
                failure = message;
            }
        }

        private void finish() {
            finished = true;
            CombatBenchmarkHooks.remove(server);
            server.tickRateManager().setTickRate(originalRate);
            context.runAfterDelay(1, () -> {
                context.assertTrue(failure == null, failure == null ? "Native pacing calibration passed." : failure);
                context.succeed();
            });
        }
    }
}
