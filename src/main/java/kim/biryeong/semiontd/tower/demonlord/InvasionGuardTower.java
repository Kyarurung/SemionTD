package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * 침공군 호위 패시브로 아군 라인을 지키러 간 침공군 유닛.
 *
 * <p>마수처럼 레인의 타워 목록에 임시 복제본으로 들어갑니다. 타워 수를 차지하지 않고, 팔 수 없으며, 라운드가 끝나면
 * 레인이 치웁니다. 라인 방어·최종 방어 판정에서는 빠집니다. 능력치는 그 웨이브에 적 레인으로 보냈을 유닛의
 * 체력·공격력·사거리·공격 간격을 그대로 따릅니다.
 */
public class InvasionGuardTower extends ProductionTower {
    public static final String TYPE_PREFIX = "invasion_guard_";
    /** 보통 타워보다 조금 낮게 둬, 원래 라인을 지키던 탱커의 어그로를 빼앗지 않습니다. */
    public static final int AGGRO_PRIORITY = 10;

    public InvasionGuardTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position) {
        super(type, ownerPlayer, teamId, laneId, position);
        markTemporaryCopy(UUID.randomUUID());
    }

    /** 보냈을 유닛({@code unit})의 능력치와 인컴 타워({@code incomeType})의 모습으로 짠 호위 한 기. */
    public static TowerType type(TowerType incomeType, Monster unit, double healthRatio, double damageRatio) {
        return new TowerType(
                TYPE_PREFIX + incomeType.id(),
                incomeType.displayName() + " (호위)",
                TowerCategory.DIRECT,
                0,
                Math.max(1.0, unit.maxHealth() * healthRatio),
                Math.max(1.0, unit.attackRange()),
                Math.max(0.0, unit.attackDamage() * damageRatio),
                Math.max(1, unit.attackIntervalTicks()),
                AGGRO_PRIORITY,
                List.of("마왕의 침공군이 이 웨이브 동안 아군 라인을 지킵니다."),
                incomeType.visual(),
                List.of()
        );
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
        return List.of("마왕의 침공군 호위 · 이 웨이브 동안만 머뭅니다");
    }
}
