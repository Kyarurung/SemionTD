package kim.biryeong.semiontd.game;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class CombatStepRunner {
    private CombatStepRunner() {
    }

    public static void run(int steps, BooleanSupplier canContinue, Runnable tickGame) {
        if (steps < 1 || steps > 5) {
            throw new IllegalArgumentException("Combat steps must be between 1 and 5.");
        }
        Objects.requireNonNull(canContinue, "canContinue");
        Objects.requireNonNull(tickGame, "tickGame");
        tickGame.run();
        for (int step = 1; step < steps && canContinue.getAsBoolean(); step++) {
            tickGame.run();
        }
    }
}
