package kim.biryeong.semiontd.tower.blueprint;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.DamageType;

/**
 * 편집 창에서 고치는 중인 설계. 플레이어마다 하나라 모듈·겉모습·이름 창을 오가도 값이 남습니다.
 * 저장하면 {@link BlueprintDesign}이 되고 초안은 새로 시작합니다.
 */
public final class BlueprintDraft {
    private static final Map<UUID, BlueprintDraft> DRAFTS = new HashMap<>();

    public double maxHealth = 150.0;
    public double damage = 10.0;
    public int attackIntervalTicks = 20;
    public double range = 6.0;
    public int aggroPriority = 25;
    public DamageType damageType = DamageType.PHYSICAL;
    public final LinkedHashMap<BlueprintModule, Integer> modules = new LinkedHashMap<>();
    public BlueprintTargetPriority targetPriority = BlueprintTargetPriority.FIRST;
    public String visualSourceId = "";
    public String name = "";

    public static synchronized BlueprintDraft of(UUID owner) {
        return DRAFTS.computeIfAbsent(owner, ignored -> fresh());
    }

    public static synchronized void reset(UUID owner) {
        DRAFTS.put(owner, fresh());
    }

    public static synchronized void forget(UUID owner) {
        DRAFTS.remove(owner);
    }

    private static BlueprintDraft fresh() {
        BlueprintDraft draft = new BlueprintDraft();
        draft.visualSourceId = BlueprintVisuals.options().stream().findFirst().map(BlueprintVisuals.Option::sourceTowerId).orElse("");
        return draft;
    }

    public BlueprintStats stats() {
        return new BlueprintStats(maxHealth, damage, attackIntervalTicks, range, aggroPriority, damageType, modules, targetPriority);
    }

    public BlueprintDesign design() {
        return BlueprintDesign.of(name, stats(), visualSourceId);
    }

    /** 한도 안으로 값을 맞춥니다(± 버튼이 한도를 넘지 않게). */
    public void clamp() {
        maxHealth = clamp(maxHealth, "minHealth", "maxHealth");
        damage = clamp(damage, "minDamage", "maxDamage");
        attackIntervalTicks = (int) Math.round(clamp(attackIntervalTicks, "minAttackIntervalTicks", "maxAttackIntervalTicks"));
        range = clamp(range, "minRange", "maxRange");
        aggroPriority = (int) Math.round(clamp(aggroPriority, "minAggroPriority", "maxAggroPriority"));
    }

    private static double clamp(double value, String minKey, String maxKey) {
        return Math.max(BlueprintPricing.value(minKey), Math.min(BlueprintPricing.value(maxKey), value));
    }
}
