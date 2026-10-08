package kim.biryeong.semiontd.tower.legion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

final class IllusionSpawnScheduleTest {
    @Test
    void pairedArenaStepsPreserveLegacySpawnSequenceAndTiming() {
        IllusionSpawnSchedule<String> legacy = new IllusionSpawnSchedule<>();
        IllusionSpawnSchedule<String> scoped = new IllusionSpawnSchedule<>();
        for (int index = 0; index < 48; index++) {
            int delay = index / 3;
            legacy.enqueue("clone-" + index, delay);
            scoped.enqueue("clone-" + index, delay);
        }
        List<String> expected = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        for (int step = 0; step < 40; step++) {
            int logicalTick = step;
            legacy.tick(2, value -> expected.add(logicalTick + ":" + value));
            if (step % 2 == 0) {
                scoped.tick(2, value -> actual.add(logicalTick + ":" + value));
            } else {
                scoped.tick(value -> true, 2, value -> actual.add(logicalTick + ":" + value));
            }
        }
        assertEquals(48, actual.size());
        assertEquals(expected, actual);
    }

    @Test
    void extraArenaStepsNeitherSpawnNorAdvanceSandboxEntries() {
        IllusionSpawnSchedule<String> queue = new IllusionSpawnSchedule<>();
        List<String> spawned = new ArrayList<>();
        queue.enqueue("sandbox-ready", 0);
        queue.enqueue("arena-first", 0);
        queue.enqueue("arena-second", 0);
        queue.enqueue("sandbox-later", 2);
        queue.enqueue("arena-later", 2);
        Predicate<String> arena = value -> value.startsWith("arena-");

        queue.tick(arena, 1, spawned::add);
        assertEquals(List.of("arena-first"), spawned);
        queue.tick(arena, 1, spawned::add);
        assertEquals(List.of("arena-first", "arena-second"), spawned);
        queue.tick(arena, 1, spawned::add);
        assertEquals(List.of("arena-first", "arena-second", "arena-later"), spawned);
        queue.tick(10, spawned::add);
        assertEquals(List.of("arena-first", "arena-second", "arena-later", "sandbox-ready"), spawned);
        queue.tick(10, spawned::add);
        assertEquals(4, spawned.size());
        queue.tick(10, spawned::add);
        assertEquals("sandbox-later", spawned.getLast());
    }

    @Test
    void ordinaryTickKeepsOneSharedCapAndDueTimeThenFifoOrdering() {
        IllusionSpawnSchedule<String> queue = new IllusionSpawnSchedule<>();
        List<String> spawned = new ArrayList<>();
        queue.enqueue("later", 1);
        queue.enqueue("sandbox", 0);
        queue.enqueue("arena", 0);
        queue.tick(1, spawned::add);
        assertEquals(List.of("sandbox"), spawned);
        queue.tick(1, spawned::add);
        assertEquals(List.of("sandbox", "arena"), spawned);
        queue.tick(1, spawned::add);
        assertEquals(List.of("sandbox", "arena", "later"), spawned);
    }

    @Test
    void invalidAndCancelledEntriesDoNotConsumeSpawnBudget() {
        IllusionSpawnSchedule<String> queue = new IllusionSpawnSchedule<>();
        List<String> spawned = new ArrayList<>();
        queue.enqueue("invalid", 0);
        queue.enqueue("cancelled", 0);
        queue.enqueue("valid", 0);
        queue.removeIf("cancelled"::equals);
        queue.tick(value -> true, 1, value -> !value.equals("invalid") && spawned.add(value));
        assertEquals(List.of("valid"), spawned);
    }

    @Test
    void childrenEnqueuedDuringExtraDrainWaitForTheNextLogicalStep() {
        IllusionSpawnSchedule<String> queue = new IllusionSpawnSchedule<>();
        List<String> spawned = new ArrayList<>();
        queue.enqueue("parent", 0);
        queue.tick(value -> true, 10, value -> {
            queue.enqueue("child", 1);
            return spawned.add(value);
        });
        assertEquals(List.of("parent"), spawned);
        queue.tick(10, spawned::add);
        assertEquals(List.of("parent", "child"), spawned);
    }

    @Test
    void clearingRemovesPendingEntriesAndRestoresRelativeDelays() {
        IllusionSpawnSchedule<String> queue = new IllusionSpawnSchedule<>();
        List<String> spawned = new ArrayList<>();
        queue.enqueue("old", 10);
        queue.tick(value -> true, 1, spawned::add);
        queue.clear();
        queue.enqueue("new", 1);
        queue.tick(10, spawned::add);
        assertEquals(List.of(), spawned);
        queue.tick(10, spawned::add);
        assertEquals(List.of("new"), spawned);
    }
}
