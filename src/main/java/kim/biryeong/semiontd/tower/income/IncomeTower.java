package kim.biryeong.semiontd.tower.income;

import java.util.List;
import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.tower.ProductionTower;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * 침공군 유닛 하나를 세워 둔 인컴 타워.
 *
 * <p>싸우지 않는 설비입니다. 공격하지 않고, 몬스터가 노리지도 않으며, 라인 방어·최종 방어 판정에서도
 * 빠집니다. 타워 수는 차지합니다. 준비 시간이 끝날 때마다 {@link #summonId()} 유닛을 레벨에 맞춰 한 마리
 * 적 레인으로 보내고(소모되지 않음), 가진 동안 레벨만큼 라운드 인컴을 올립니다.
 *
 * <p>에메랄드로 설치·레벨업하므로 다이아 판매가는 0입니다. 판매는 {@link IncomeTowerService#sell}이
 * 에메랄드 환불과 인컴 회수를 함께 처리하고, 일반 타워 판매 경로는 막습니다.
 */
public class IncomeTower extends ProductionTower {
    private final String summonId;
    private int level = 1;
    private long paidEmerald;

    public IncomeTower(TowerType type, UUID ownerPlayer, TeamId teamId, int laneId, GridPosition position, String summonId) {
        super(type, ownerPlayer, teamId, laneId, position);
        this.summonId = summonId;
    }

    public String summonId() {
        return summonId;
    }

    @Override
    public int level() {
        return level;
    }

    public boolean maxLevel() {
        return level >= IncomeTowerBalance.MAX_LEVEL;
    }

    void levelUp(long cost) {
        level = Math.min(IncomeTowerBalance.MAX_LEVEL, level + 1);
        paidEmerald += Math.max(0, cost);
    }

    public long paidEmerald() {
        return paidEmerald;
    }

    void recordPaidEmerald(long cost) {
        paidEmerald += Math.max(0, cost);
    }

    /**
     * 라운드가 진행되는 동안(몹이 오는 동안)은 레인의 본체를 숨깁니다. 싸우지 않는 설비가 전장을 가리지 않게
     * 하려는 것입니다. 다음 준비 단계에서 {@code resetForRound}가 엔티티가 없는 것을 보고 다시 세웁니다.
     * 적 레인으로 보낸 유닛은 별개의 몬스터라 영향을 받지 않습니다.
     */
    @Override
    public void onWaveStarted(PlayerLane lane, int currentRound) {
        super.onWaveStarted(lane, currentRound);
        onRemoved(lane);
    }

    @Override
    public boolean canChaseTargets() {
        return false;
    }

    @Override
    public boolean canAttackTarget(SemionTowerEntity towerEntity, SemionMonsterEntity target) {
        return false;
    }

    @Override
    public boolean invulnerable() {
        return true;
    }

    @Override
    public boolean drawsAggro() {
        return false;
    }

    @Override
    public boolean targetableByMonsters() {
        return false;
    }

    @Override
    public boolean countsForLaneDefense() {
        return false;
    }

    @Override
    public boolean participatesInFinalDefense() {
        return false;
    }

    /** 일반 판매 경로(다이아 환불)를 막습니다. 인컴 타워 창의 판매 버튼만 씁니다. */
    @Override
    public boolean canBeSold() {
        return false;
    }

    @Override
    public long sellRefundAmount() {
        return 0;
    }

    @Override
    public List<String> runtimeDetailLines() {
        return List.of("레벨 " + level + "/" + IncomeTowerBalance.MAX_LEVEL);
    }
}
