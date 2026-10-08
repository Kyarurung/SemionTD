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
    void logicalStepsDoNotRepeatPhysicalWorldWork() {
        List<String> events = new ArrayList<>(List.of("world"));
        CombatStepRunner.run(3, () -> true, () -> events.add("game"));
        assertEquals(List.of("world", "game", "game", "game"), events);
    }

    @Test
    void exitingWaveOnAnyLogicalStepStopsTheBatch() {
        for (int exitStep : new int[] {1, 2, 3}) {
            AtomicInteger games = new AtomicInteger();
            CombatStepRunner.run(5, () -> games.get() < exitStep, games::incrementAndGet);
            assertEquals(exitStep, games.get());
        }
    }

    @Test
    void preparationEnteringWaveDoesNotGainExtraTicks() {
        AtomicBoolean wave = new AtomicBoolean(false);
        boolean initiallyEligible = wave.get();
        AtomicInteger games = new AtomicInteger();
        CombatStepRunner.run(5, () -> initiallyEligible && wave.get(), () -> {
            games.incrementAndGet();
            wave.set(true);
        });
        assertEquals(1, games.get());
    }

    @Test
    void replacingTheActiveGameStopsExtraTicks() {
        Object initialGame = new Object();
        Object[] activeGame = {initialGame};
        AtomicInteger games = new AtomicInteger();
        CombatStepRunner.run(5, () -> activeGame[0] == initialGame, () -> {
            games.incrementAndGet();
            activeGame[0] = new Object();
        });
        assertEquals(1, games.get());
    }

    @Test
    void disabledExtraWorkStillRunsTheNormalGameTick() {
        AtomicInteger games = new AtomicInteger();
        CombatStepRunner.run(5, () -> false, games::incrementAndGet);
        assertEquals(1, games.get());
    }

    @Test
    void invalidStepCountDoesNotExecuteCallbacks() {
        for (int steps : new int[] {-1, 0, 6, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CombatStepRunner.run(steps, () -> true,
                    () -> {throw new AssertionError();}));
        }
    }
}
