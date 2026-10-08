package kim.biryeong.semiontd.entity.goal;

public final class CombatCooldown {
    public static final int MAX_EVENTS = 5;
    private double remaining = 1.0;
    private double elapsed;

    public void advance(double ticks) {
        elapsed = Double.isFinite(ticks) ? Math.clamp(ticks, 0.0, MAX_EVENTS) : 1.0;
        remaining = (remaining <= 0.0 ? 1.0 : remaining) - elapsed;
    }

    public boolean ready() {
        return remaining <= 1.0e-9;
    }

    public void restart(double interval) {
        remaining += Math.max(1.0, interval);
    }

    public double eventOffset(double updateInterval) {
        return Math.max(0.0, elapsed - 1.0 + remaining) * updateInterval;
    }

    public void reset() {
        remaining = 1.0;
    }
}
