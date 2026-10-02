package kim.biryeong.semiontd.tower.blueprint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import kim.biryeong.semiontd.entity.monster.DamageType;

/**
 * 설계도의 능력치와 모듈. 한도 안인지는 {@link BlueprintPricing#validate}가 판단합니다.
 *
 * @param maxHealth           최대 체력
 * @param damage              한 번 공격의 피해
 * @param attackIntervalTicks 공격 간격(서버 틱, 20 = 1초)
 * @param range               사거리(블록)
 * @param aggroPriority       몹이 이 타워를 노리는 우선도
 * @param damageType          기본 공격 피해 유형
 * @param modules             붙인 모듈과 단계(붙인 순서 유지)
 * @param targetPriority      공격 대상 우선도
 */
public record BlueprintStats(
        double maxHealth,
        double damage,
        int attackIntervalTicks,
        double range,
        int aggroPriority,
        DamageType damageType,
        Map<BlueprintModule, Integer> modules,
        BlueprintTargetPriority targetPriority
) {
    public BlueprintStats {
        Objects.requireNonNull(damageType, "damageType");
        modules = modules == null || modules.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(modules));
        targetPriority = targetPriority == null ? BlueprintTargetPriority.FIRST : targetPriority;
    }

    /** 모듈 없는 기본 설계. */
    public BlueprintStats(double maxHealth, double damage, int attackIntervalTicks, double range, int aggroPriority,
            DamageType damageType) {
        this(maxHealth, damage, attackIntervalTicks, range, aggroPriority, damageType, Map.of(), BlueprintTargetPriority.FIRST);
    }

    /** 초당 피해(공격 간격 기준). */
    public double damagePerSecond() {
        return attackIntervalTicks <= 0 ? 0.0 : damage * 20.0 / attackIntervalTicks;
    }

    /** 이 모듈의 단계. 안 붙였으면 0. */
    public int level(BlueprintModule module) {
        return modules.getOrDefault(module, 0);
    }

    public BlueprintStats withModules(Map<BlueprintModule, Integer> nextModules, BlueprintTargetPriority nextPriority) {
        return new BlueprintStats(maxHealth, damage, attackIntervalTicks, range, aggroPriority, damageType, nextModules, nextPriority);
    }
}
