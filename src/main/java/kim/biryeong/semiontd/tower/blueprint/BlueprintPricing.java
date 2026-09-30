package kim.biryeong.semiontd.tower.blueprint;

import java.util.Optional;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.DamageType;

/**
 * 설계도 가격과 한도.
 *
 * <p>가격은 "위력" P를 초선형으로 올린 값입니다.
 * <pre>
 *   P     = (초당 피해 × 피해 유형 배율 / dpsUnit) × (사거리 / rangePivot)^rangeExponent + 체력 / healthUnit
 *   가격  = priceScale × P^priceExponent   (priceStep 단위로 반올림, 최소 minimumPrice)
 * </pre>
 * 기본 계수는 기존 빌더 타워의 가격대별 중앙값(가격 50·130·260 → 초당 피해 8·16.7·26.7, 체력 88·140·200)에
 * 맞췄습니다. 같은 가격이면 기존 타워 중앙값쯤의 위력이 나오고, 지수가 1보다 커서 세게 만들수록 값이 가파르게 오릅니다.
 * 계수는 모두 {@link BlueprintTowers#CONFIG_ID} 아래 능력 값이라 설정에서 바꿀 수 있습니다.
 */
public final class BlueprintPricing {
    private BlueprintPricing() {
    }

    /** 이 능력치로 만든 타워의 설치 가격(다이아). */
    public static long price(BlueprintStats stats) {
        double power = power(stats);
        double raw = value("priceScale") * Math.pow(Math.max(0.0, power), value("priceExponent"));
        double step = Math.max(1.0, value("priceStep"));
        long rounded = Math.round(raw / step) * (long) step;
        return Math.max((long) value("minimumPrice"), rounded);
    }

    /** 가격 공식의 위력 P. 공격 쪽과 생존 쪽을 더합니다. */
    public static double power(BlueprintStats stats) {
        double offense = stats.damagePerSecond() * damageTypeMultiplier(stats.damageType()) / value("dpsUnit");
        double rangeFactor = Math.pow(Math.max(0.1, stats.range()) / value("rangePivot"), value("rangeExponent"));
        double defense = stats.maxHealth() / value("healthUnit");
        return offense * rangeFactor + defense;
    }

    /** 가격이 높을수록 타워 수(인구)를 더 차지합니다. */
    public static int slotCost(long price) {
        if (price >= (long) value("threeSlotPrice")) {
            return 3;
        }
        return price >= (long) value("twoSlotPrice") ? 2 : 1;
    }

    /** 한 사람이 한 경기에 만들 수 있는 설계도 수. */
    public static int maxBlueprints() {
        return Math.max(1, (int) value("maxBlueprintsPerPlayer"));
    }

    /** 한도를 벗어난 첫 항목을 설명합니다. 문제가 없으면 비어 있습니다. */
    public static Optional<String> validate(BlueprintStats stats) {
        if (outside(stats.maxHealth(), "minHealth", "maxHealth")) {
            return Optional.of(range("체력", "minHealth", "maxHealth"));
        }
        if (outside(stats.damage(), "minDamage", "maxDamage")) {
            return Optional.of(range("공격력", "minDamage", "maxDamage"));
        }
        if (outside(stats.attackIntervalTicks(), "minAttackIntervalTicks", "maxAttackIntervalTicks")) {
            return Optional.of(range("공격 간격(틱)", "minAttackIntervalTicks", "maxAttackIntervalTicks"));
        }
        if (outside(stats.range(), "minRange", "maxRange")) {
            return Optional.of(range("사거리", "minRange", "maxRange"));
        }
        if (outside(stats.aggroPriority(), "minAggroPriority", "maxAggroPriority")) {
            return Optional.of(range("어그로", "minAggroPriority", "maxAggroPriority"));
        }
        return Optional.empty();
    }

    public static double damageTypeMultiplier(DamageType type) {
        return switch (type) {
            case PHYSICAL -> 1.0;
            case MAGIC -> value("magicDamageMultiplier");
            case TRUE -> value("trueDamageMultiplier");
        };
    }

    public static double value(String key) {
        return TowerBalanceRuntime.ability(BlueprintTowers.CONFIG_ID, key);
    }

    private static boolean outside(double actual, String minKey, String maxKey) {
        return actual < value(minKey) - 1.0e-9 || actual > value(maxKey) + 1.0e-9;
    }

    private static String range(String label, String minKey, String maxKey) {
        return label + "은(는) " + trim(value(minKey)) + " ~ " + trim(value(maxKey)) + " 사이여야 합니다.";
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }
}
