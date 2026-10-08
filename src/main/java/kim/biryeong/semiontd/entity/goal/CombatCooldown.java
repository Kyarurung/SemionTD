package kim.biryeong.semiontd.entity.goal;

public final class CombatCooldown {
    public static final int MAX_EVENTS = 5;
    private double remaining = 1.0;

    public void advance(double ticks) {
        double elapsed = Double.isFinite(ticks) ? Math.clamp(ticks, 0.0, MAX_EVENTS) : 1.0;
        remaining = (remaining <= 0.0 ? 1.0 : remaining) - elapsed;
    }

    public boolean ready() {
        return remaining <= 1.0e-9;
    }

    public void restart(double interval) {
        remaining += Math.max(1.0, interval);
    }

    public void reset() {
        remaining = 1.0;
    }
}
