package kim.biryeong.semiontd.tower.blueprint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Locale;
import kim.biryeong.semiontd.entity.monster.DamageType;

/**
 * 계정에 저장하는 설계도 내용. 가격은 저장하지 않고 불러올 때 지금 밸런스로 다시 셉니다(밸런스가 바뀌어도 값이 맞게).
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
        String visualSourceId
) {
    public static final Codec<BlueprintDesign> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(BlueprintDesign::name),
            Codec.DOUBLE.fieldOf("maxHealth").forGetter(BlueprintDesign::maxHealth),
            Codec.DOUBLE.fieldOf("damage").forGetter(BlueprintDesign::damage),
            Codec.INT.fieldOf("attackIntervalTicks").forGetter(BlueprintDesign::attackIntervalTicks),
            Codec.DOUBLE.fieldOf("range").forGetter(BlueprintDesign::range),
            Codec.INT.optionalFieldOf("aggroPriority", 25).forGetter(BlueprintDesign::aggroPriority),
            Codec.STRING.optionalFieldOf("damageType", DamageType.PHYSICAL.name()).forGetter(BlueprintDesign::damageType),
            Codec.STRING.fieldOf("visualSourceId").forGetter(BlueprintDesign::visualSourceId)
    ).apply(instance, BlueprintDesign::new));

    public BlueprintDesign {
        name = name == null ? "" : name;
        damageType = damageType == null ? DamageType.PHYSICAL.name() : damageType.toUpperCase(Locale.ROOT);
        visualSourceId = visualSourceId == null ? "" : visualSourceId;
    }

    public static BlueprintDesign of(String name, BlueprintStats stats, String visualSourceId) {
        return new BlueprintDesign(name, stats.maxHealth(), stats.damage(), stats.attackIntervalTicks(), stats.range(),
                stats.aggroPriority(), stats.damageType().name(), visualSourceId);
    }

    public BlueprintStats stats() {
        return new BlueprintStats(maxHealth, damage, attackIntervalTicks, range, aggroPriority, parsedDamageType());
    }

    private DamageType parsedDamageType() {
        try {
            return DamageType.valueOf(damageType);
        } catch (IllegalArgumentException ignored) {
            return DamageType.PHYSICAL;
        }
    }
}
