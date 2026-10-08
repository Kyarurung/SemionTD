package kim.biryeong.semiontd.game;

public final class CombatStepAccumulator {
    private double remainder;

    public int steps(float effectiveTickRate) {
        if (!Float.isFinite(effectiveTickRate) || effectiveTickRate <= 20.0F || effectiveTickRate > 100.0F) {
            reset();
            return 1;
        }
        remainder += effectiveTickRate - 20.0;
        int extraSteps = (int) (remainder / 20.0);
        remainder -= extraSteps * 20.0;
        return 1 + extraSteps;
    }

    public void reset() {
        remainder = 0.0;
    }
}
