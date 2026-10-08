package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CombatStepRunnerTest {
    @Test
    void normalWorldGroupIsNotRepeatedBeforeFirstGameStep() {
        List<String> events = new ArrayList<>(List.of("red", "blue"));
        CombatStepRunner.run(3, () -> true, () -> {
            events.add("red");
            events.add("blue");
            return true;
        }, () -> events.add("game"));
        assertEquals(List.of("red", "blue", "game", "red", "blue", "game", "red", "blue", "game"), events);
    }

    @Test
    void exitingWaveOnEitherNormalOrExtraStepStopsFurtherWorldAndGameWork() {
        for (int exitStep : new int[] {1, 2, 3}) {
            AtomicInteger games = new AtomicInteger();
            AtomicInteger worlds = new AtomicInteger();
            CombatStepRunner.run(5, () -> games.get() < exitStep, () -> {
                worlds.incrementAndGet();
                return true;
            }, games::incrementAndGet);
            assertEquals(exitStep, games.get());
            assertEquals(exitStep - 1, worlds.get());
        }
    }

    @Test
    void rejectedWorldStepDoesNotAdvanceGameOrRetryWithinTheBatch() {
        AtomicInteger games = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();
        CombatStepRunner.run(5, () -> true, () -> {
            attempts.incrementAndGet();
            return false;
        }, games::incrementAndGet);
        assertEquals(1, games.get());
        assertEquals(1, attempts.get());
    }

    @Test
    void worldStepChangingEligibilityCannotAdvanceTheStaleGame() {
        AtomicBoolean wave = new AtomicBoolean(true);
        AtomicInteger games = new AtomicInteger();
        AtomicInteger worlds = new AtomicInteger();
        CombatStepRunner.run(5, wave::get, () -> {
            worlds.incrementAndGet();
            wave.set(false);
            return true;
        }, games::incrementAndGet);
        assertEquals(1, games.get());
        assertEquals(1, worlds.get());
    }

    @Test
    void disabledExtraWorkStillRunsTheNormalGameTick() {
        AtomicInteger games = new AtomicInteger();
        CombatStepRunner.run(5, () -> false, () -> {
            throw new AssertionError("An excluded game must not tick extra worlds");
        }, games::incrementAndGet);
        assertEquals(1, games.get());
    }

    @Test
    void invalidStepCountDoesNotExecuteCallbacks() {
        for (int steps : new int[] {-1, 0, 6, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CombatStepRunner.run(steps, () -> true,
                    () -> {throw new AssertionError();}, () -> {throw new AssertionError();}));
        }
    }
}
