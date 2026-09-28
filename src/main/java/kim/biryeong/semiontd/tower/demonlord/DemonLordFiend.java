package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kim.biryeong.semiontd.entity.visual.EntityVisual;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * 마수 소환으로 불러낸 임시 마수(커다란 검은 늑대). 마왕 대신 어그로를 끄는 몸빵입니다.
 *
 * <p>레인의 타워 목록에 임시 복제본으로 들어갑니다. 그래서 타워 수를 차지하지 않고, 팔 수 없으며, 라운드가 끝나면
 * 레인이 알아서 치웁니다. 몬스터는 어그로를 끄는 방어 대상을 마왕보다 먼저 노리고, 이 마수는 어그로 우선도가
 * 어떤 타워보다 높아 주변 몬스터를 모두 끌어갑니다. 가까이 온 적은 물어뜯어 반격합니다.
 *
 * <p>라인 방어·최종 방어 판정에서는 빠집니다. 임시 몸빵이 살아 있다는 이유로 무너진 라인이 버티는 것처럼
 * 잡히면 안 되기 때문입니다. 지속 시간이 끝나거나 쓰러지면 {@link DemonLordSkills}가 거둬 갑니다.
 */
public class DemonLordFiend extends ProductionTower {
    public static final String TYPE_ID = "demon_lord_fiend";
    /** 어떤 타워보다도 높게 둡니다(타워 최대 80). */
    public static final int AGGRO_PRIORITY = 100;
    /** 늑대 기본 크기의 배율. 2.2배면 어깨높이가 마왕 가슴께까지 옵니다. */
    public static final double VISUAL_SCALE = 2.2;

    private final long expiryTick;

    public DemonLordFiend(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position, long expiryTick) {
        super(type, ownerPlayer, teamId, laneId, position);
        this.expiryTick = expiryTick;
        markTemporaryCopy(UUID.randomUUID());
    }

    /** 이번 시전의 수치로 짠 마수 한 마리의 능력치. 체력과 공격력은 마왕 레벨을 따라갑니다. */
    public static TowerType type(double maxHealth, double damage, double attackRange, int attackIntervalTicks) {
        return new TowerType(
                TYPE_ID,
                "소환된 마수",
                TowerCategory.DIRECT,
                0,
                Math.max(1.0, maxHealth),
                attackRange,
                damage,
                Math.max(1, attackIntervalTicks),
                AGGRO_PRIORITY,
                List.of("마왕이 불러낸 임시 마수입니다. 주변 몬스터의 어그로를 끌고 가까운 적을 물어뜯습니다."),
                // 검은 늑대를 크게 키운 마수. 몸집이 커야 어그로를 끄는 몸빵으로 읽힙니다.
                new EntityVisual("minecraft:wolf", null, VISUAL_SCALE, Map.of("wolf_variant", "minecraft:black")),
                List.of()
        );
    }

    public long expiryTick() {
        return expiryTick;
    }

    @Override
    public boolean countsForLaneDefense() {
        return false;
    }

    @Override
    public boolean participatesInFinalDefense() {
        return false;
    }

    @Override
    public boolean canBeSold() {
        return false;
    }

    @Override
    public boolean triggersNearbyDeathEffects() {
        return false;
    }

    @Override
    public List<String> runtimeDetailLines() {
        return List.of("마왕이 불러낸 임시 마수 · 어그로 우선도 " + AGGRO_PRIORITY);
    }
}
