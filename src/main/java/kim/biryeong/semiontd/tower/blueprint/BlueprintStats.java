package kim.biryeong.semiontd.tower.blueprint;

import java.util.Objects;
import kim.biryeong.semiontd.entity.monster.DamageType;

/**
 * 설계도의 기본 능력치. 한도 안인지는 {@link BlueprintPricing#validate}가 판단합니다.
 *
 * @param maxHealth           최대 체력
 * @param damage              한 번 공격의 피해
 * @param attackIntervalTicks 공격 간격(서버 틱, 20 = 1초)
 * @param range               사거리(블록)
 * @param aggroPriority       몹이 이 타워를 노리는 우선도
 * @param damageType          기본 공격 피해 유형
 */
public record BlueprintStats(
        double maxHealth,
        double damage,
        int attackIntervalTicks,
        double range,
        int aggroPriority,
        DamageType damageType
) {
    public BlueprintStats {
        Objects.requireNonNull(damageType, "damageType");
    }

    /** 초당 피해(공격 간격 기준). */
    public double damagePerSecond() {
        return attackIntervalTicks <= 0 ? 0.0 : damage * 20.0 / attackIntervalTicks;
    }
}
