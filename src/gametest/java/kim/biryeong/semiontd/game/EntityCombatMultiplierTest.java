package kim.biryeong.semiontd.game;

import java.util.Optional;
import java.util.UUID;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.effect.TimedEffectType;
import kim.biryeong.semiontd.entity.EntityCombatSpeed;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterAttackStyle;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.entity.tower.goal.TowerAttackMonsterGoal;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import kim.biryeong.semiontd.test.tower.TestTower;
import kim.biryeong.semiontd.tower.TowerCategory;
import kim.biryeong.semiontd.tower.TowerType;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class EntityCombatMultiplierTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void basicAttackDamageMatchesFortyFrameReferenceWithTwentyEntityFrames(GameTestHelper context) {
        try {
            double reference = towerDamage(context, 40, 20.0F, 1, 3);
            double scaled = towerDamage(context, 20, 40.0F, 2, 3);
            close(reference, scaled, "Odd attack intervals must preserve damage over equal combat time.");
            double fastReference = towerDamage(context, 100, 20.0F, 1, 1);
            double fastScaled = towerDamage(context, 20, 100.0F, 5, 1);
            close(fastReference, fastScaled, "Fast basic attacks must retain bounded multiple events per goal call.");
        } finally {
            CombatSpeedRuntime.clear();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void movementScalesOnceAndAttributeRestoresAfterSpeedEnds(GameTestHelper context) {
        SemionTowerEntity tower = tower(context, 0, 3);
        SemionMonsterEntity monster = monster(context);
        try {
            Vec3 origin = tower.position();
            Vec3 destination = origin.add(5.0, 0.0, 0.0);
            configure(context, 20.0F, 1);
            for (int frame = 0; frame < 20; frame++) {
                tower.moveTowardTarget(destination, 0.1);
            }
            double reference = tower.position().distanceTo(origin);
            tower.setPos(origin);
            configure(context, 40.0F, 2);
            for (int frame = 0; frame < 10; frame++) {
                tower.moveTowardTarget(destination, 0.1);
            }
            close(reference, tower.position().distanceTo(origin), "Direct movement must use one speed multiplier.");
            double base = monster.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
            EntityCombatSpeed.updateMovement(monster);
            close(base * 2.0, monster.getAttributeValue(Attributes.MOVEMENT_SPEED), "Navigation must receive doubled movement speed.");
            EntityCombatSpeed.updateMovement(monster);
            close(base * 2.0, monster.getAttributeValue(Attributes.MOVEMENT_SPEED), "Refreshing speed must not stack modifiers.");
            CombatSpeedRuntime.clear();
            EntityCombatSpeed.updateMovement(monster);
            close(base, monster.getAttributeValue(Attributes.MOVEMENT_SPEED), "Ending speed must restore the movement attribute.");
        } finally {
            tower.discard();
            monster.discard();
            CombatSpeedRuntime.clear();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void effectsAndPendingHitsAdvanceWithoutAdditionalEntityTicks(GameTestHelper context) {
        SemionMonsterEntity monster = monster(context);
        try {
            configure(context, 40.0F, 2);
            monster.applyTimedEffect(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS, 0.25, 40);
            int[] hits = {0};
            monster.setAttackStyle(new MonsterAttackStyle() {
                @Override
                public int hitDelayTicks() {
                    return 3;
                }

                @Override
                public void hit(SemionMonsterEntity attacker, LivingEntity target) {
                    hits[0]++;
                }
            });
            monster.startAttack(monster);
            int before = monster.tickCount;
            context.getLevel().tickNonPassenger(monster);
            require(hits[0] == 0 && monster.hasPendingHit(), "A delayed hit must retain its logical delay.");
            context.getLevel().tickNonPassenger(monster);
            require(hits[0] == 1 && !monster.hasPendingHit(), "The third logical tick must resolve within the second physical frame.");
            for (int frame = 2; frame < 19; frame++) {
                context.getLevel().tickNonPassenger(monster);
            }
            require(monster.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS) == 2,
                    "Forty-tick effects must still exist after nineteen doubled frames.");
            context.getLevel().tickNonPassenger(monster);
            require(monster.activeTimedEffectTicks(TimedEffectType.MONSTER_ATTACK_DAMAGE_BONUS) == 0,
                    "Forty-tick effects must expire after twenty doubled frames.");
            require(monster.tickCount - before == 20, "Timer acceleration must not add physical entity ticks.");
        } finally {
            monster.discard();
            CombatSpeedRuntime.clear();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void monsterGoalPreservesAttackRateAndBoundsFastEvents(GameTestHelper context) {
        SemionTowerEntity target = tower(context, 0, 3);
        SemionMonsterEntity monster = monster(context);
        try {
            monster.setTarget(target);
            monster.setPos(target.position().add(1, 0, 0));
            monster.runtimeMonster().applyCombatProfile(1.0, 3.0, 1);
            int[] hits = {0};
            monster.setAttackStyle(new MonsterAttackStyle() {
                @Override public int hitDelayTicks() { return 0; }
                @Override public void hit(SemionMonsterEntity attacker, LivingEntity victim) { hits[0]++; }
            });
            configure(context, 100.0F, 5);
            MonsterAttackTargetGoal goal = new MonsterAttackTargetGoal(monster, 1.0);
            for (int frame = 0; frame < 20; frame++) {
                if (frame % 2 == 0) goal.tick();
            }
            require(hits[0] == 50, "Ten alternating goal calls must deliver fifty bounded fast attack events.");
        } finally {
            monster.discard();
            target.discard();
            CombatSpeedRuntime.clear();
        }
        context.succeed();
    }

    private static double towerDamage(GameTestHelper context, int frames, float rate, int steps, int interval) {
        SemionTowerEntity tower = tower(context, 10.0, interval);
        SemionMonsterEntity target = monster(context);
        try {
            target.setPos(tower.position().add(1, 0, 0));
            context.getLevel().addFreshEntity(target);
            configure(context, rate, steps);
            TowerAttackMonsterGoal goal = new TowerAttackMonsterGoal(tower);
            int before = tower.tickCount;
            for (int frame = 0; frame < frames; frame++) {
                context.getLevel().tickNonPassenger(tower);
                if (frame % 2 == 0) goal.tick();
            }
            require(tower.tickCount - before == frames, "Attack acceleration must not add entity frames.");
            double damage = target.runtimeMonster().maxHealth() - target.runtimeMonster().health();
            require(damage > 0.0, "The shared tower damage pipeline must be exercised.");
            return damage;
        } finally {
            tower.discard();
            target.discard();
            CombatSpeedRuntime.clear();
        }
    }

    private static SemionTowerEntity tower(GameTestHelper context, double damage, int interval) {
        BlockPos at = context.absolutePos(new BlockPos(2, 2, 2));
        SemionTowerEntity tower = new SemionTowerEntity(SemionEntityTypes.TOWER, context.getLevel());
        tower.configure(new TestTower(new TowerType("combat-multiplier", "Combat multiplier", TowerCategory.DIRECT,
                0, 1000.0, 3.0, damage, interval, 0), UUID.randomUUID(), TeamId.RED, 777, GridPosition.from(at)), null);
        tower.setPos(Vec3.atBottomCenterOf(at));
        tower.setNoAi(true);
        tower.setNoGravity(true);
        return tower;
    }

    private static SemionMonsterEntity monster(GameTestHelper context) {
        SemionMonsterEntity entity = new SemionMonsterEntity(SemionEntityTypes.MONSTER, context.getLevel());
        entity.configureFrom(new Monster("combat-multiplier-target", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                10000.0, 0.0, 10.0, AttackKind.MELEE, "minecraft:zombie", 0), null);
        entity.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, 2, 2))));
        entity.setNoAi(true);
        entity.setNoGravity(true);
        return entity;
    }

    private static void configure(GameTestHelper context, float rate, int steps) {
        SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                SyntheticArenaFactory.create(context.getLevel(), context.absolutePos(BlockPos.ZERO)));
        CombatSpeedRuntime.configure(context.getLevel().getServer(), game, rate, steps);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void close(double expected, double actual, String message) {
        require(Math.abs(expected - actual) < 1.0e-6, message + " Expected " + expected + ", got " + actual);
    }
}
