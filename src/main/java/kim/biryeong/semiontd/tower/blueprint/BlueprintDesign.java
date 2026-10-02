package kim.biryeong.semiontd.tower.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import kim.biryeong.semiontd.entity.monster.DamageType;

/**
 * 계정에 저장하는 설계도 내용. 가격은 저장하지 않고 불러올 때 지금 밸런스로 다시 셉니다(밸런스가 바뀌어도 값이 맞게).
 * 모듈은 id → 단계, 대상 우선도는 id로 저장해 순서가 바뀌거나 항목이 늘어도 읽힙니다. 모르는 모듈 id는 불러올 때 버립니다.
 *
 * @param visualSourceId 겉모습을 빌려 오는 타워 id
 */
public record BlueprintDesign(
        String name,
        double maxHealth,
        double damage,
        int attackIntervalTicks,
        double range,
        int aggroPriority,
        String damageType,
        String visualSourceId,
        Map<String, Integer> modules,
        String targetPriority
) {
    public static final Codec<BlueprintDesign> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(BlueprintDesign::name),
            Codec.DOUBLE.fieldOf("maxHealth").forGetter(BlueprintDesign::maxHealth),
            Codec.DOUBLE.fieldOf("damage").forGetter(BlueprintDesign::damage),
            Codec.INT.fieldOf("attackIntervalTicks").forGetter(BlueprintDesign::attackIntervalTicks),
            Codec.DOUBLE.fieldOf("range").forGetter(BlueprintDesign::range),
            Codec.INT.optionalFieldOf("aggroPriority", 25).forGetter(BlueprintDesign::aggroPriority),
            Codec.STRING.optionalFieldOf("damageType", DamageType.PHYSICAL.name()).forGetter(BlueprintDesign::damageType),
            Codec.STRING.fieldOf("visualSourceId").forGetter(BlueprintDesign::visualSourceId),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("modules", Map.of()).forGetter(BlueprintDesign::modules),
            Codec.STRING.optionalFieldOf("targetPriority", BlueprintTargetPriority.FIRST.id()).forGetter(BlueprintDesign::targetPriority)
    ).apply(instance, BlueprintDesign::new));

    public BlueprintDesign {
        name = name == null ? "" : name;
        damageType = damageType == null ? DamageType.PHYSICAL.name() : damageType.toUpperCase(Locale.ROOT);
        visualSourceId = visualSourceId == null ? "" : visualSourceId;
        modules = modules == null || modules.isEmpty() ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(modules));
        targetPriority = targetPriority == null ? BlueprintTargetPriority.FIRST.id() : targetPriority;
    }

    public static BlueprintDesign of(String name, BlueprintStats stats, String visualSourceId) {
        Map<String, Integer> modules = new LinkedHashMap<>();
        stats.modules().forEach((module, level) -> modules.put(module.id(), level));
        return new BlueprintDesign(name, stats.maxHealth(), stats.damage(), stats.attackIntervalTicks(), stats.range(),
                stats.aggroPriority(), stats.damageType().name(), visualSourceId, modules, stats.targetPriority().id());
    }

    public BlueprintDesign withName(String nextName) {
        return new BlueprintDesign(nextName, maxHealth, damage, attackIntervalTicks, range, aggroPriority, damageType,
                visualSourceId, modules, targetPriority);
    }

    public BlueprintStats stats() {
        Map<BlueprintModule, Integer> parsed = new LinkedHashMap<>();
        modules.forEach((id, level) -> BlueprintModule.byId(id).ifPresent(module -> parsed.put(module, level)));
        return new BlueprintStats(maxHealth, damage, attackIntervalTicks, range, aggroPriority, parsedDamageType(), parsed,
                BlueprintTargetPriority.byId(targetPriority).orElse(BlueprintTargetPriority.FIRST));
    }

    private DamageType parsedDamageType() {
        try {
            return DamageType.valueOf(damageType);
        } catch (IllegalArgumentException ignored) {
            return DamageType.PHYSICAL;
        }
    }
}
