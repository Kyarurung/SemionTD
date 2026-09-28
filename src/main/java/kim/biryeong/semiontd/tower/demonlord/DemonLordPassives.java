package kim.biryeong.semiontd.tower.demonlord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.Tower;
import kim.biryeong.semiontd.tower.TowerPlacementPositions;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.body.BodyTowers;
import kim.biryeong.semiontd.tower.end.EndTower;
import kim.biryeong.semiontd.tower.engineer.EngineerTowers;
import kim.biryeong.semiontd.tower.frost.FrostTowers;
import kim.biryeong.semiontd.tower.futureagency.FutureAgencyTowers;
import kim.biryeong.semiontd.tower.hero.HeroPartyTower;
import kim.biryeong.semiontd.tower.mage.MageTowers;
import kim.biryeong.semiontd.tower.succubus.SuccubusTowers;
import kim.biryeong.semiontd.tower.warlock.WarlockTowers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

/** 8·9번 슬롯 패시브의 실제 효과. */
public final class DemonLordPassives {
    private DemonLordPassives() {
    }

    // ------------------------------------------------------------ 흡혈 참격

    /**
     * 마검 평타가 맞은 적 주변까지 베고, 이번 한 번에 준 피해의 일부만큼 회복합니다. 회복은 한 번에 최대 체력의
     * {@code lifeStealCap}까지로 묶어, 떼로 몰린 적을 한 번 베어 체력이 다 차지 않게 합니다.
     */
    static void bloodCleave(ServerPlayer player, PlayerLane lane, DemonLordState state, DemonLordSkillTower altar,
            SemionMonsterEntity target, double swingDamage, double dealtToTarget) {
        DemonLordPassive passive = DemonLordPassive.BLOOD_CLEAVE;
        double radius = passive.ability("cleaveRadius", 2.5) * state.skillRangeMultiplier();
        double cleave = swingDamage * passive.ability("cleaveRatio", 0.6);
        double dealt = Math.max(0.0, dealtToTarget);
        Vec3 centre = target.position();
        AABB box = new AABB(centre, centre).inflate(radius, 2.0, radius);
        for (SemionMonsterEntity other : lane.arenaWorld().getEntitiesOfClass(SemionMonsterEntity.class, box, entity ->
                entity != target && entity.isAlive() && entity.runtimeMonster() != null
                        && state.canFight(entity.runtimeMonster())
                        && entity.position().distanceToSqr(centre) <= radius * radius)) {
            dealt += DemonLordService.dealDamage(player, lane, altar, other, cleave, DamageType.PHYSICAL).dealtDamage();
        }
        double heal = Math.min(dealt * passive.ability("lifeStealRatio", 0.15),
                state.maxHealth() * passive.ability("lifeStealCap", 0.04));
        if (heal > 0.0) {
            state.heal(heal);
        }
        DemonLordVfx.play(lane, DemonLordDisplayVfx.bloodCleave(radius, DemonLordVfx.seed(lane)), centre);
    }

    // ------------------------------------------------------------ 군단의 잔영

    /**
     * 웨이브가 시작될 때 아군 라인의 타워를 무작위로 몇 개 복제해 마왕 라인의 빈자리에 세웁니다.
     *
     * <p>복제본은 임시 복제본이라 타워 수를 차지하지 않고 라운드가 끝나면 레인이 치웁니다. 원본 주인의 타워로
     * 만들어, 원본 빌더의 상태(마나·군대 등)를 읽는 타워도 제 주인의 값을 씁니다.
     *
     * @return 세운 복제본 수
     */
    static int summonLegion(PlayerLane lane, int round) {
        int copies = (int) Math.max(0.0, DemonLordPassive.LEGION_ECHO.ability("copies", 5.0));
        if (copies <= 0 || lane.arenaWorld() == null) {
            return 0;
        }
        List<Tower> pool = new ArrayList<>();
        for (PlayerLane ally : lane.teamLanes()) {
            if (ally == lane) {
                continue;
            }
            for (Tower tower : ally.towers()) {
                if (copyable(tower)) {
                    pool.add(tower);
                }
            }
        }
        if (pool.isEmpty()) {
            return 0;
        }
        Random random = new Random(lane.arenaWorld().getGameTime() ^ (lane.ownerPlayer() == null ? 0 : lane.ownerPlayer().hashCode()));
        Collections.shuffle(pool, random);
        List<GridPosition> free = freePositions(lane);
        Collections.shuffle(free, random);
        int placed = 0;
        for (Tower original : pool) {
            if (placed >= copies || placed >= free.size()) {
                break;
            }
            Optional<ProductionTowerCatalog.CatalogEntry> entry = ProductionTowerCatalog.find(original.type().id());
            if (entry.isEmpty()) {
                continue;
            }
            GridPosition at = free.get(placed);
            Tower copy = entry.get().create(original.ownerPlayer(), original.teamId(), lane.laneId(), at);
            copy.markTemporaryCopy(original.logicalId());
            lane.addTower(copy);
            // 웨이브 시작 처리가 이미 지나간 뒤에 들어오므로 시작 표시를 직접 해 줍니다.
            copy.markWaveStarted(round);
            copy.onWaveStarted(lane, round);
            DemonLordVfx.play(lane, DemonLordDisplayVfx.echo(DemonLordVfx.seed(lane) + placed),
                    new Vec3(at.x() + 0.5, at.y() + 1.0, at.z() + 0.5));
            placed++;
        }
        return placed;
    }

    /**
     * 복제할 수 있는 타워인지.
     *
     * <p>싸우는 타워만 복제합니다. 플레이어마다 하나만 둘 수 있는 핵심 타워(흑마법사 본체, 엔더 드래곤, 심장,
     * 골렘, 지부장, 마법핵, 서큐버스, 냉각 장치)와 파티 단위로 움직이는 용사 파티, 증강 타워, 임시 복제본은 뺍니다.
     */
    public static boolean copyable(Tower tower) {
        if (tower == null || tower.isTemporaryCopy() || tower.isAugmentTower() || tower.health() <= 0.0) {
            return false;
        }
        TowerType type = tower.type();
        if (type.damage() <= 0.0 || !tower.countsForLaneDefense() || !tower.targetableByMonsters()) {
            return false;
        }
        if (tower instanceof HeroPartyTower || tower instanceof EndTower || tower instanceof DemonLordFiend) {
            return false;
        }
        if (WarlockTowers.isWarlockCore(type) || BodyTowers.isHeart(type) || EngineerTowers.isGolem(type)
                || FutureAgencyTowers.isLeader(type) || MageTowers.isCore(type) || SuccubusTowers.isSuccubus(type)
                || FrostTowers.isEmissionCoolingDevice(type) || FrostTowers.isEruptionCoolingDevice(type)) {
            return false;
        }
        return ProductionTowerCatalog.find(type.id()).isPresent();
    }

    private static List<GridPosition> freePositions(PlayerLane lane) {
        BlockBounds bounds = lane.laneLayout().laneArea();
        List<GridPosition> free = new ArrayList<>();
        for (int x = bounds.min().getX(); x <= bounds.max().getX(); x++) {
            for (int z = bounds.min().getZ(); z <= bounds.max().getZ(); z++) {
                BlockPos candidate = new BlockPos(x, bounds.max().getY(), z);
                Optional<BlockPos> floor = TowerPlacementPositions.resolve(lane, candidate);
                if (floor.isEmpty()) {
                    continue;
                }
                GridPosition grid = GridPosition.from(floor.get());
                if (lane.towers().stream().noneMatch(tower ->
                        tower.position().x() == grid.x() && tower.position().z() == grid.z())) {
                    free.add(grid);
                }
            }
        }
        return free;
    }
}
