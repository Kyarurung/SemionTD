package kim.biryeong.semiontd.config;

public record CombatSpeedConfig(
        Boolean enabled,
        Float combatTickRate,
        Double maxAverageTickTimeMillis,
        Mode mode
) {
    public enum Mode {
        SIMULATION,
        MULTIPLIER,
        SERVER
    }

    private static final float DEFAULT_COMBAT_TICK_RATE = 40.0F;
    private static final double DEFAULT_MAX_AVERAGE_TICK_TIME_MILLIS = 25.0;

    public CombatSpeedConfig {
        mode = mode == null ? Mode.SIMULATION : mode;
        enabled = enabled != null && enabled;
        combatTickRate = combatTickRate != null && Float.isFinite(combatTickRate) && combatTickRate > 0.0F
                ? Math.max(20.0F, Math.min(100.0F, combatTickRate))
                : DEFAULT_COMBAT_TICK_RATE;
        maxAverageTickTimeMillis = maxAverageTickTimeMillis != null
                && Double.isFinite(maxAverageTickTimeMillis)
                && maxAverageTickTimeMillis > 0.0
                ? Math.max(1.0, Math.min(50.0, maxAverageTickTimeMillis))
                : DEFAULT_MAX_AVERAGE_TICK_TIME_MILLIS;
    }

    public CombatSpeedConfig(Boolean enabled, Float combatTickRate, Double maxAverageTickTimeMillis) {
        this(enabled, combatTickRate, maxAverageTickTimeMillis, Mode.SIMULATION);
    }

    public float serverTickRate(float effectiveTickRate) {
        return mode == Mode.SERVER ? effectiveTickRate : 20.0F;
    }

    public static CombatSpeedConfig defaultConfig() {
        return new CombatSpeedConfig(false, DEFAULT_COMBAT_TICK_RATE, DEFAULT_MAX_AVERAGE_TICK_TIME_MILLIS);
    }
}
