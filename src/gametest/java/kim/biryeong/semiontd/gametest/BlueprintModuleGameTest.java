package kim.biryeong.semiontd.gametest;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.monster.DamageType;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.TeamLaneGroup;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.blueprint.Blueprint;
import kim.biryeong.semiontd.tower.blueprint.BlueprintModule;
import kim.biryeong.semiontd.tower.blueprint.BlueprintPricing;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStates;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStats;
import kim.biryeong.semiontd.tower.blueprint.BlueprintTargetPriority;
import kim.biryeong.semiontd.tower.blueprint.BlueprintTower;
import kim.biryeong.semiontd.tower.blueprint.BlueprintVisuals;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class BlueprintModuleGameTest {
    /**
     * 설계도 모듈: 대상 우선도(낮은 체력), 다중 사격(가까운 적 둘에게 60%), 맞은 적 둔화·취약, 방호(받는 피해 감소),
     * 처형(체력 낮은 적 피해 증가)이 실제 전투 훅에서 돌고, 모듈을 붙이면 값이 오릅니다.
     */
    @GameTest
    public void modulesApplyThroughTheCombatHooks(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-modules".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats plain = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL);
            BlueprintStats armed = plain.withModules(Map.of(
                    BlueprintModule.MULTISHOT, 2,
                    BlueprintModule.SLOW, 1,
                    BlueprintModule.VULNERABILITY, 1,
                    BlueprintModule.ARMOR, 3
            ), BlueprintTargetPriority.WEAKEST);
            require(BlueprintPricing.price(armed) > BlueprintPricing.price(plain), "Modules must raise the price.");
            require(BlueprintPricing.validate(armed.withModules(Map.of(BlueprintModule.CRIT, 4), BlueprintTargetPriority.FIRST)).isPresent(),
                    "A module level above the cap must be rejected.");

            String visual = BlueprintVisuals.options().getFirst().sourceTowerId();
            var creation = BlueprintStates.create(owner, "시험", armed, visual);
            require(creation.success(), "Blueprint creation must succeed: " + creation.message());
            Blueprint blueprint = creation.blueprint();
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(blueprint.towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 3, 1, 3));
            lane.addTower(tower);
            SemionTowerEntity towerEntity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());

            Monster primary = spawnMonster(context, lane, "bp-primary", position(context, 4, 1, 3));
            Monster wounded = spawnMonster(context, lane, "bp-wounded", position(context, 4, 1, 4));
            Monster third = spawnMonster(context, lane, "bp-third", position(context, 3, 1, 5));
            wounded.damage(400.0, DamageType.TRUE);
            SemionMonsterEntity primaryEntity = entity(context, primary);
            SemionMonsterEntity woundedEntity = entity(context, wounded);
            SemionMonsterEntity thirdEntity = entity(context, third);

            require(tower.selectAttackTarget(towerEntity, List.of(primaryEntity, woundedEntity, thirdEntity))
                            .orElse(null) == woundedEntity,
                    "The weakest-first priority must pick the most wounded enemy.");

            double woundedBefore = wounded.health();
            double thirdBefore = third.health();
            tower.onAttackResolved(towerEntity, primaryEntity, 50.0, 50.0, 50.0, false);
            require(wounded.health() < woundedBefore && third.health() < thirdBefore,
                    "Multishot 2 must also hit the two other enemies in range.");
            require(primaryEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_MOVE_SPEED_REDUCTION) > 0.0,
                    "Slow must land on the struck enemy.");
            require(primaryEntity.activeTimedEffectMagnitude(TimedEffectType.MONSTER_TOWER_DAMAGE_TAKEN_BONUS) > 0.0,
                    "Vulnerability must land on the struck enemy.");

            double expectedArmor = 1.0 - BlueprintModule.ARMOR.value("reduction", 3);
            require(Math.abs(tower.modifyIncomingDamage(towerEntity, null, 100.0) - 100.0 * expectedArmor) < 1.0e-6,
                    "Armor 3 must cut incoming damage by its reduction.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    /** 처형: 체력이 문턱 아래인 적에게만 피해가 늘어납니다. */
    @GameTest
    public void executeOnlyBoostsDamageAgainstLowHealthTargets(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-execute".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats stats = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL)
                    .withModules(Map.of(BlueprintModule.EXECUTE, 1), BlueprintTargetPriority.FIRST);
            var creation = BlueprintStates.create(owner, "처형", stats, BlueprintVisuals.options().getFirst().sourceTowerId());
            require(creation.success(), creation.message());
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(creation.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 3, 1, 3));
            lane.addTower(tower);
            SemionTowerEntity towerEntity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());
            Monster healthy = spawnMonster(context, lane, "bp-healthy", position(context, 4, 1, 3));
            Monster dying = spawnMonster(context, lane, "bp-dying", position(context, 4, 1, 4));
            dying.damage(850.0, DamageType.TRUE);

            double bonus = 1.0 + BlueprintModule.EXECUTE.value("damageBonus", 1);
            require(Math.abs(tower.modifyOutgoingDamage(towerEntity, entity(context, healthy), 100.0) - 100.0) < 1.0e-6,
                    "A healthy enemy must take normal damage.");
            require(Math.abs(tower.modifyOutgoingDamage(towerEntity, entity(context, dying), 100.0) - 100.0 * bonus) < 1.0e-6,
                    "An enemy below the threshold must take execute damage.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    /**
     * 모듈 겹치기: 다중 사격 화살도 광역이 터지고, 처치 폭발은 죽은 적 자리에서 주변 적을 칩니다.
     * C는 첫 대상 A의 광역 반경 밖이고 다중 사격 대상도 아니지만, 다중 사격으로 맞은 B의 광역에 맞아야 합니다.
     */
    @GameTest
    public void multishotArrowsSplashAndKillsExplode(GameTestHelper context) {
        UUID owner = UUID.nameUUIDFromBytes("blueprint-combo".getBytes(StandardCharsets.UTF_8));
        PlayerLane lane = testLane(context, owner);
        TeamLaneGroup group = new TeamLaneGroup(TeamId.RED, BossMonster.defaultBoss(TeamId.RED));
        group.addLane(lane);
        try {
            fillFloor(context);
            BlueprintStats stats = new BlueprintStats(300.0, 50.0, 20, 6.0, 25, DamageType.PHYSICAL).withModules(Map.of(
                    BlueprintModule.MULTISHOT, 1,
                    BlueprintModule.SPLASH, 1,
                    BlueprintModule.KILL_EXPLOSION, 1
            ), BlueprintTargetPriority.FIRST);
            var creation = BlueprintStates.create(owner, "연계", stats, BlueprintVisuals.options().getFirst().sourceTowerId());
            require(creation.success(), creation.message());
            BlueprintTower tower = (BlueprintTower) ProductionTowerCatalog.find(creation.blueprint().towerId()).orElseThrow()
                    .create(owner, TeamId.RED, 1, position(context, 1, 1, 1));
            lane.addTower(tower);
            SemionTowerEntity towerEntity = (SemionTowerEntity) context.getLevel().getEntity(tower.entityId().getAsInt());

            Monster a = spawnMonster(context, lane, "combo-a", position(context, 4, 1, 3));
            Monster b = spawnMonster(context, lane, "combo-b", position(context, 4, 1, 5));
            Monster c = spawnMonster(context, lane, "combo-c", position(context, 5, 1, 6));
            double cBefore = c.health();
            tower.onAttackResolved(towerEntity, entity(context, a), 50.0, 50.0, 50.0, false);
            require(b.health() < 1_000.0, "Multishot must hit the nearest other enemy.");
            require(c.health() < cBefore, "The multishot arrow must splash onto the enemy next to its target.");

            Monster corpse = spawnMonster(context, lane, "combo-corpse", position(context, 2, 1, 6));
            Monster bystander = spawnMonster(context, lane, "combo-bystander", position(context, 2, 1, 7));
            double bystanderBefore = bystander.health();
            tower.onKill(towerEntity, entity(context, corpse), 100.0);
            require(bystander.health() < bystanderBefore, "A killed enemy must explode onto the enemy beside it.");
            context.succeed();
        } finally {
            BlueprintStates.clear(owner);
            group.closeRuntime();
        }
    }

    private static PlayerLane testLane(GameTestHelper context, UUID owner) {
        BlockPos min = context.absolutePos(new BlockPos(0, 1, 0));
        BlockPos max = context.absolutePos(new BlockPos(7, 4, 7));
        LaneRegionLayout layout = new LaneRegionLayout(
                1,
                Vec3.atCenterOf(context.absolutePos(new BlockPos(1, 2, 1))),
                List.of(Vec3.atCenterOf(context.absolutePos(new BlockPos(5, 2, 5)))),
                Vec3.atCenterOf(context.absolutePos(new BlockPos(7, 2, 7))),
                BlockBounds.of(min, max),
                List.of(position(context, 6, 1, 6))
        );
        return new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
    }

    private static void fillFloor(GameTestHelper context) {
        for (int x = 0; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                context.setBlock(x, 1, z, Blocks.STONE);
                context.setBlock(x, 2, z, Blocks.AIR);
            }
        }
    }

    private static Monster spawnMonster(GameTestHelper context, PlayerLane lane, String id, GridPosition position) {
        Monster monster = new Monster(id, lane.teamId(), lane.laneId(), Optional.empty(), Optional.empty(),
                1_000.0, 0.0, 1.0, AttackKind.MELEE, "minecraft:zombie", 0L);
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(monster, lane.laneLayout());
        entity.setNoAi(true);
        entity.setPos(position.x() + 0.5, position.y() + 1.0, position.z() + 0.5);
        context.getLevel().addFreshEntity(entity);
        monster.markMinecraftEntitySpawned(entity.getId(), entity.getX(), entity.getY(), entity.getZ());
        lane.activeMonsters().add(monster);
        return monster;
    }

    private static SemionMonsterEntity entity(GameTestHelper context, Monster monster) {
        return (SemionMonsterEntity) context.getLevel().getEntity(monster.minecraftEntityId());
    }

    private static GridPosition position(GameTestHelper context, int x, int y, int z) {
        return GridPosition.from(context.absolutePos(new BlockPos(x, y, z)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
