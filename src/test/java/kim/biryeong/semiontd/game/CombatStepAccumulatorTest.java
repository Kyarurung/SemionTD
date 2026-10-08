package kim.biryeong.semiontd.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CombatStepAccumulatorTest {
    @Test
    void everySupportedQuarterRateKeepsCumulativeSimulationWithinOneStep() {
        for (int quarters = 80; quarters <= 400; quarters++) {
            CombatStepAccumulator accumulator = new CombatStepAccumulator();
            long actual = 0;
            for (int wallTick = 1; wallTick <= 400; wallTick++) {
                int steps = accumulator.steps(quarters / 4.0F);
                assertTrue(steps >= 1 && steps <= 5);
                actual += steps;
                assertEquals((long) wallTick * quarters / 80, actual,
                        "Rate " + quarters / 4.0F + " at wall tick " + wallTick);
            }
        }
    }

    @Test
    void invalidOrNormalRateDropsPendingFractionWithoutCatchUp() {
        for (float rate : new float[] {Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1, 0, 19, 20, 101}) {
            CombatStepAccumulator accumulator = new CombatStepAccumulator();
            assertEquals(1, accumulator.steps(30));
            assertEquals(1, accumulator.steps(rate));
            assertEquals(1, accumulator.steps(30));
            assertEquals(2, accumulator.steps(30));
        }
    }

    @Test
    void resetDiscardsFractionAtWaveReloadAndFallbackBoundaries() {
        CombatStepAccumulator accumulator = new CombatStepAccumulator();
        assertEquals(1, accumulator.steps(39));
        accumulator.reset();
        assertEquals(1, accumulator.steps(21));
        assertEquals(2, accumulator.steps(39));
        accumulator.reset();
        assertEquals(2, accumulator.steps(40));
        assertEquals(5, accumulator.steps(100));
    }
}
