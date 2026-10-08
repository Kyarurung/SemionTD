package kim.biryeong.semiontd.util;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ArenaTaskQueueTest {
    @Test
    void delayedDamageMatchesLegacySchedulerElapsedSteps() {
        ArenaTaskQueue queue = new ArenaTaskQueue();
        List<Long> hits = new ArrayList<>();
        long[] worldTime = {100};
        queue.submit(100, () -> hits.add(worldTime[0]), 5);

        for (; worldTime[0] < 104; worldTime[0]++) {
            queue.runTasks(worldTime[0]);
        }
        assertTrue(hits.isEmpty());
        queue.runTasks(worldTime[0]);
        queue.runTasks(worldTime[0]);
        assertEquals(List.of(104L), hits);
        assertTrue(queue.isEmpty());
    }

    @Test
    void accelerationAndFallbackKeepTheSameDeadlineAndOrdering() {
        ArenaTaskQueue queue = new ArenaTaskQueue();
        List<String> hits = new ArrayList<>();
        queue.submit(40, () -> hits.add("first"), 4);
        queue.submit(40, () -> hits.add("second"), 4);
        queue.runTasks(40);
        queue.runTasks(41);
        queue.runTasks(42);
        assertTrue(hits.isEmpty());
        queue.runTasks(43);
        assertEquals(List.of("first", "second"), hits);
    }

    @Test
    void immediateTasksRunAtTheCurrentStepAndSeparateArenasDoNotDrainEachOther() {
        ArenaTaskQueue active = new ArenaTaskQueue();
        ArenaTaskQueue sandbox = new ArenaTaskQueue();
        List<String> hits = new ArrayList<>();
        active.submit(10, () -> hits.add("active"), 0);
        sandbox.submit(10, () -> hits.add("sandbox"), 0);
        active.runTasks(10);
        assertEquals(List.of("active"), hits);
        assertFalse(sandbox.isEmpty());
        sandbox.runTasks(10);
        assertEquals(List.of("active", "sandbox"), hits);
    }
}
