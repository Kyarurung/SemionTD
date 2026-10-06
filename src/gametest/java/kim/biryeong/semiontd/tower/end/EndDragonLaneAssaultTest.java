package kim.biryeong.semiontd.tower.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.augment.AugmentChoice;
import kim.biryeong.semiontd.augment.AugmentConfig;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.augment.AugmentSnapshot;
import kim.biryeong.semiontd.augment.PlayerAugmentState;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.GridPosition;
import kim.biryeong.semiontd.game.PlayerLane;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.map.LaneRegionLayout;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.area.AreaEffectLaneIndex;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import xyz.nucleoid.map_templates.BlockBounds;

public final class EndDragonLaneAssaultTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:dragon_lane", maxTicks = 200)
    public void actualFiftyBySevenLaneAndSpawnEdgesReceiveOnePhysicalHitInEveryDirection(GameTestHelper context) {
        for (int direction = 0; direction < 4; direction++) {
            try (Arena arena = new Arena(context, direction, 50)) {
                List<SemionMonsterEntity> targets = new ArrayList<>();
                for (int cell = 0; cell < 50; cell++) {
                    for (double edge : new double[]{-3.49, 3.49}) {
                        targets.add(arena.target(arena.at(cell + .5, edge), 1, TeamId.RED, "paid"));
                    }
                }
                for (int depth = 0; depth < 7; depth++) {
                    for (int width = -3; width <= 3; width++) {
                        targets.add(arena.target(arena.at(43 + depth + .5, width), 1, TeamId.RED, "paid"));
                    }
                }
                var otherLane = arena.target(arena.at(20, 0), 2, TeamId.RED, "paid");
                var otherTeam = arena.target(arena.at(20, 0), 1, TeamId.BLUE, "paid");
                var outside = arena.target(arena.at(20, 3.51), 1, TeamId.RED, "paid");
                arena.tick(1);
                arena.tick(60);
                require(arena.core.assaultPhase() == EndDragonAssault.Phase.RUSHING, "Charge must end at sixty ticks.");
                double damage = arena.damage(targets.getFirst(), 1);
                arena.tick(59);
                require(arena.core.assaultPhase() == EndDragonAssault.Phase.RUSHING, "The rush must last all sixty ticks.");
                arena.tick(1);
                require(arena.core.assaultPhase() == EndDragonAssault.Phase.EXITING, "The sixtieth rush tick reaches the front edge.");
                close(0, arena.source.position().distanceTo(arena.geometry.front()), "Rush endpoint must be the true outer front edge.");
                double expectedYaw = Math.toDegrees(Math.atan2(arena.geometry.direction().z, arena.geometry.direction().x)) + 270;
                close(0, Mth.wrapDegrees(arena.source.getYRot() - expectedYaw), "Model yaw rotates 180 degrees without reversing the sweep.");
                for (var target : targets) {
                    close(damage, 10000 - target.runtimeMonster().health(), "Every edge and spawn cell receives one physical hit, including after knockback.");
                    close(200, target.activeTimedEffectTicks(TimedEffectType.MONSTER_STUN), "Income stun stays ten seconds.");
                }
                close(10000, otherLane.runtimeMonster().health(), "Another lane is excluded.");
                close(10000, otherTeam.runtimeMonster().health(), "An allied target is excluded.");
                close(10000, outside.runtimeMonster().health(), "Outside the true seven-block width is excluded.");
                close(damage * targets.size(), arena.core.roundPhysicalDamageDealt(), "Damage attribution has no duplicates or hidden hits.");
            }
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane", maxTicks = 200)
    public void chargeExitTeleportAndFixedDiagonalBreathFollowFloorAndFullLane(GameTestHelper context) {
        try (Arena arena = new Arena(context, 1, 37)) {
            Vec3 hatch = arena.source.position();
            arena.tick(1);
            arena.tick(59);
            close(0, arena.source.position().distanceTo(hatch), "Charge remains at hatch location through tick 59.");
            arena.tick(1);
            close(0, arena.source.position().distanceTo(arena.geometry.rear()), "Charge completion teleports directly to rear.");
            arena.tick(60);
            arena.advance(EndDragonAssault.Phase.VANISHED);
            close(0, arena.source.position().distanceTo(arena.geometry.point(42)), "Vanish exactly five blocks beyond a 37-block lane.");
            require(arena.source.isInvisible(), "The outgoing dragon disappears before reappearing.");
            arena.tick(1);
            Vec3 emitter = arena.geometry.airborneRear(arena.floorY, 10);
            close(0, arena.source.position().distanceTo(emitter), "Reappear five behind rear at actual floor plus ten.");
            require(!arena.source.isInvisible(), "Reappearance restores visibility.");
            List<SemionMonsterEntity> targets = new ArrayList<>();
            for (int cell = 0; cell < 37; cell++) {
                targets.add(arena.target(arena.at(cell + .5, -3.49), 1, TeamId.RED, "paid"));
                targets.add(arena.target(arena.at(cell + .5, 3.49), 1, TeamId.RED, "paid"));
            }
            var otherLane = arena.target(arena.at(20, 0), 2, TeamId.RED, "paid");
            var otherTeam = arena.target(arena.at(20, 0), 1, TeamId.BLUE, "paid");
            var outside = arena.target(arena.at(20, -3.51), 1, TeamId.RED, "paid");
            arena.tick(59);
            require(arena.core.assaultPhase() == EndDragonAssault.Phase.BREATHING, "One breath sweep is still active at tick 59.");
            close(0, arena.source.position().distanceTo(emitter), "Breath emitter remains fixed behind the lane.");
            require(arena.source.getXRot() > 0, "Breath faces diagonally downward.");
            arena.tick(1);
            require(arena.core.assaultPhase() == EndDragonAssault.Phase.RETURNING, "One full breath pass completes.");
            for (var target : targets) {require(target.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) > 0, "Both lane edges burn through the front spawn area.");}
            arena.tick(201);
            for (var target : targets) {close(arena.damage(target, .25) * 10, 10000 - target.runtimeMonster().health(), "Each victim burns exactly ten times.");}
            close(10000, otherLane.runtimeMonster().health(), "Breath excludes other lanes.");
            close(10000, otherTeam.runtimeMonster().health(), "Breath excludes allies.");
            close(10000, outside.runtimeMonster().health(), "Breath stays within the real lane width.");
            require(!arena.source.isNoAi() && !arena.source.isInvisible(), "Normal AI and visibility are restored.");
            require(arena.core.canAttackTarget(arena.source, targets.getFirst()), "Normal combat is available after the sequence.");
            double physicalBefore = arena.core.roundPhysicalDamageDealt();
            arena.source.forceAttackReady();
            new kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal(arena.source).tick();
            require(arena.core.roundPhysicalDamageDealt() > physicalBefore,
                    "The production attack goal must actually deal a normal hit after the flight ends.");
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane", maxTicks = 200)
    public void roundResetAndDeathRestoreFlightInEveryPhaseWithoutSameRoundRestart(GameTestHelper context) {
        for (var phase : List.of(EndDragonAssault.Phase.CHARGING, EndDragonAssault.Phase.RUSHING,
                EndDragonAssault.Phase.EXITING, EndDragonAssault.Phase.VANISHED, EndDragonAssault.Phase.BREATHING)) {
            try (Arena arena = new Arena(context, 0, 37)) {
                arena.advance(phase);
                arena.core.onWaveStarted(arena.lane, 5);
                require(arena.core.assaultPhase() == phase, "Repeated wave notifications cannot restart a flight.");
                arena.lane.killTower(arena.core);
                require(arena.core.assaultPhase() == EndDragonAssault.Phase.SPENT, "Death consumes an interrupted use.");
                require(!arena.source.isInvisible() && !arena.source.isNoAi(), "Death restores visibility and AI flags.");
                arena.core.onWaveStarted(arena.lane, 5);
                arena.tick(2);
                require(arena.core.assaultPhase() == EndDragonAssault.Phase.SPENT, "Same-round notifications cannot grant another assault after death.");
                arena.core.resetForRound(arena.lane);
                arena.tick(100);
                require(arena.core.assaultPhase() == EndDragonAssault.Phase.READY, "Preparation cannot trigger the next assault.");
                arena.core.onWaveStarted(arena.lane, 6);
                arena.tick(1);
                require(arena.core.assaultPhase() == EndDragonAssault.Phase.CHARGING, "New round hatching grants one fresh use.");
                arena.core.resetForRound(arena.lane);
                require(!arena.core.controlsAssaultFlight(), "Round reset cancels active control.");
            }
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane", maxTicks = 200)
    public void knockbackPreservesBossAndFinalDefenseImmunityAndLaneProgress(GameTestHelper context) {
        try (Arena arena = new Arena(context, 0, 50)) {
            var target = arena.target(arena.at(10, 0), 1, TeamId.RED, "paid");
            var boss = arena.target(arena.at(10, 1), 1, TeamId.RED, "income_boss");
            var finalDefense = arena.target(arena.at(10, -1), 1, TeamId.RED, "paid");
            finalDefense.runtimeMonster().enterFinalDefenseCombat();
            Vec3 origin = target.position();
            double progress = arena.lane.laneLayout().progressAt(origin);
            EndDragonAssault.knockBack(arena.lane, target, 20);
            close(20, target.position().distanceTo(origin), "An unobstructed long lane permits twenty blocks of reverse-path movement.");
            close(progress - 20 / arena.lane.laneLayout().pathLength(), target.runtimeMonster().laneProgress(), "Logical progress rewinds with physical movement.");
            Vec3 bossOrigin = boss.position();
            Vec3 finalOrigin = finalDefense.position();
            EndDragonAssault.knockBack(arena.lane, boss, 20);
            EndDragonAssault.knockBack(arena.lane, finalDefense, 20);
            close(0, boss.position().distanceTo(bossOrigin), "Boss-id knockback immunity is preserved.");
            close(0, finalDefense.position().distanceTo(finalOrigin), "Final-defense knockback immunity is preserved.");
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:dragon_lane", maxTicks = 200)
    public void repeatedDragonEvolutionCannotRestartAndDominatedVictimsStopBurning(GameTestHelper context) {
        try (Arena arena = new Arena(context, 0, 37)) {
            arena.advance(EndDragonAssault.Phase.BREATHING);
            var victim = arena.target(arena.at(.1, 0), 1, TeamId.RED, "paid");
            arena.tick(1);
            require(victim.activeTimedEffectTicks(TimedEffectType.MONSTER_IGNITED) > 0, "The victim first receives a burn.");
            victim.setDominatedFor(1);
            arena.tick(200);
            close(10000, victim.runtimeMonster().health(), "A converted ally cannot keep receiving pending burn damage.");
            var dragonType = arena.core.type();
            arena.core.refreshType(EndTowers.BASE_END_TOWER, arena.lane);
            arena.tick(1);
            require(arena.core.state() == EndTowerState.PHANTOM, "Lower health returns the core to phantom form.");
            arena.core.refreshType(dragonType, arena.lane);
            arena.tick(1);
            require(arena.core.state() == EndTowerState.DRAGON, "The same-round core evolves again.");
            require(arena.core.assaultPhase() == EndDragonAssault.Phase.SPENT, "Re-evolution cannot create a second assault in this round.");
        }
        context.succeed();
    }

    private static final class Arena implements AutoCloseable {
        final PlayerLane lane;
        final EndTower core;
        final SemionTowerEntity source;
        final EndDragonAssaultGeometry geometry;
        final double floorY;
        final List<SemionMonsterEntity> targets = new ArrayList<>();

        Arena(GameTestHelper context, int direction, int length) {
            boolean x = direction < 2;
            boolean positive = direction % 2 == 1;
            int minX = x ? 10 : 30;
            int minZ = x ? 30 : 10;
            int maxX = minX + (x ? length : 7) - 1;
            int maxZ = minZ + (x ? 7 : length) - 1;
            for (int xx = minX; xx <= maxX; xx++) {
                for (int zz = minZ; zz <= maxZ; zz++) {context.setBlock(new BlockPos(xx, 2, zz), Blocks.STONE);}
            }
            BlockPos min = context.absolutePos(new BlockPos(minX, 3, minZ));
            BlockPos max = context.absolutePos(new BlockPos(maxX, 3, maxZ));
            BlockBounds spawn = x
                    ? BlockBounds.of(new BlockPos(positive ? max.getX() - 6 : min.getX(), min.getY(), min.getZ()),
                            new BlockPos(positive ? max.getX() : min.getX() + 6, max.getY(), max.getZ()))
                    : BlockBounds.of(new BlockPos(min.getX(), min.getY(), positive ? max.getZ() - 6 : min.getZ()),
                            new BlockPos(max.getX(), max.getY(), positive ? max.getZ() : min.getZ() + 6));
            BlockBounds path = x
                    ? BlockBounds.of(new BlockPos(positive ? min.getX() : min.getX() + 7, min.getY(), min.getZ()),
                            new BlockPos(positive ? max.getX() - 7 : max.getX(), max.getY(), max.getZ()))
                    : BlockBounds.of(new BlockPos(min.getX(), min.getY(), positive ? min.getZ() : min.getZ() + 7),
                            new BlockPos(max.getX(), max.getY(), positive ? max.getZ() - 7 : max.getZ()));
            floorY = min.getY();
            Vec3 spawnPoint = new Vec3((spawn.min().getX() + spawn.max().getX() + 1.0) / 2, floorY + 1,
                    (spawn.min().getZ() + spawn.max().getZ() + 1.0) / 2);
            Vec3 back = x ? new Vec3(positive ? min.getX() : max.getX() + 1, floorY + 1, spawnPoint.z)
                    : new Vec3(spawnPoint.x, floorY + 1, positive ? min.getZ() : max.getZ() + 1);
            var layout = new LaneRegionLayout(1, spawnPoint, spawn, List.of(back), back, path, List.of());
            UUID owner = UUID.randomUUID();
            lane = new PlayerLane(TeamId.RED, 1, owner, context.getLevel(), layout);
            lane.assignAugmentSnapshot(new AugmentSnapshot(AugmentConfig.defaults(), List.of(new PlayerAugmentState.Selection(
                    5, AugmentRarity.PRISMATIC, EndAugments.ASSAULT, PlayerAugmentState.Outcome.SELECTED, null, AugmentChoice.none()))));
            AreaEffectLaneIndex.register(lane);
            TowerType base = EndTowers.BASE_END_TOWER;
            TowerType giant = new TowerType(base.id(), base.displayName(), base.category(), base.mineralCost(),
                    1_000_000, base.range(), base.damage(), base.attackIntervalTicks(), base.aggroPriority(),
                    base.description(), base.visual(), base.upgradeOptions());
            core = new EndTower(giant, owner, TeamId.RED, 1, GridPosition.from(BlockPos.containing(back.lerp(spawnPoint, .5))));
            lane.addTower(core);
            source = core.runtimeEntity(lane).orElseThrow();
            source.setNoAi(false);
            core.onWaveStarted(lane, 5);
            geometry = EndDragonAssaultGeometry.from(layout, floorY);
        }

        Vec3 at(double distance, double side) {
            Vec3 direction = geometry.direction();
            return geometry.point(distance).add(-direction.z * side, -1, direction.x * side);
        }

        SemionMonsterEntity target(Vec3 position, int laneId, TeamId team, String id) {
            Monster runtime = new Monster(id, team, laneId, Optional.empty(), Optional.of(TeamId.BLUE),
                    10000, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0);
            runtime.setOrigin(MonsterOrigin.NORMAL_PAID);
            var entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, lane.arenaWorld());
            entity.configureFrom(runtime, lane.laneLayout());
            entity.setNoAi(true);
            entity.setNoGravity(true);
            entity.setPos(position);
            require(lane.arenaWorld().addFreshEntity(entity), "Target must spawn.");
            runtime.markMinecraftEntitySpawned(entity.getId(), position.x, position.y, position.z);
            lane.activeMonsters().add(runtime);
            targets.add(entity);
            return entity;
        }

        double damage(SemionMonsterEntity target, double ratio) {
            return core.resolveBasicAttackOutgoingDamage(source, target, source.attackDamageAmount(target) * ratio);
        }

        void tick(int ticks) {for (int tick = 0; tick < ticks; tick++) {core.tick(lane);}}

        void advance(EndDragonAssault.Phase phase) {
            for (int tick = 0; tick < 500 && core.assaultPhase() != phase; tick++) {tick(1);}
            require(core.assaultPhase() == phase, "Expected phase " + phase + ", got " + core.assaultPhase());
        }

        @Override public void close() {
            targets.forEach(SemionMonsterEntity::discard);
            lane.clearTowers();
            AreaEffectLaneIndex.unregister(lane);
        }
    }

    private static void require(boolean condition, String message) {if (!condition) {throw new AssertionError(message);}}
    private static void close(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < .001, message + " Expected " + expected + ", got " + actual);
    }
}
