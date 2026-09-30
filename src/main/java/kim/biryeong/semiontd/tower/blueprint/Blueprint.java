package kim.biryeong.semiontd.tower.blueprint;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * 한 번 만들면 바뀌지 않는 설계도. 더 센 타워가 필요하면 새 설계도를 만들어 바꿔 세웁니다.
 *
 * @param towerId        이 설계도로 세우는 타워의 id
 * @param owner          설계한 플레이어(이 사람만 세울 수 있음)
 * @param name           플레이어가 붙인 이름
 * @param stats          기본 능력치
 * @param visualSourceId 겉모습을 빌려 온 기존 타워 id
 * @param visual         그 타워의 겉모습
 * @param price          설치 가격(다이아)
 */
public record Blueprint(
        String towerId,
        UUID owner,
        String name,
        BlueprintStats stats,
        String visualSourceId,
        EntityVisual visual,
        long price
) {
    public Blueprint {
        Objects.requireNonNull(towerId, "towerId");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(stats, "stats");
        Objects.requireNonNull(visual, "visual");
    }

    public int slotCost() {
        return BlueprintPricing.slotCost(price);
    }

    public TowerType towerType() {
        return TowerType.builder(towerId, name)
                .category(TowerCategory.DIRECT)
                .mineralCost(price)
                .maxHealth(stats.maxHealth())
                .range(stats.range())
                .damage(stats.damage())
                .attackIntervalTicks(stats.attackIntervalTicks())
                .aggroPriority(stats.aggroPriority())
                .primaryDamageType(stats.damageType())
                .visual(visual)
                .description(description())
                .build();
    }

    private List<String> description() {
        return List.of(
                "<gray>빌더 빌더가 직접 설계한 타워입니다.</gray>",
                "<white>초당 피해 " + one(stats.damagePerSecond()) + " · 체력 " + one(stats.maxHealth())
                        + " · 사거리 " + one(stats.range()) + "</white>",
                "<gray>타워 수 " + slotCost() + "칸</gray>"
        );
    }

    private static String one(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
