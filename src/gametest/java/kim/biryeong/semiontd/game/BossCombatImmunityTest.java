package kim.biryeong.semiontd.game;

import java.util.Optional;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.config.WaveConfig;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterAttackStyle;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.monster.goal.MonsterAttackTargetGoal;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.gametest.SyntheticArenaFactory;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class BossCombatImmunityTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void acceptedBossDamageMatchesNormalReferenceAtTwoAndFiveTimesSpeed(GameTestHelper context) {
        for (int interval : new int[]{1, 3, 5, 13}) {
            for (int delay : new int[]{0, 4}) {
                double normal40 = damage(context, 40, 20.0F, 1, interval, delay);
                double doubled20 = damage(context, 20, 40.0F, 2, interval, delay);
                context.assertTrue(Math.abs(normal40 - doubled20) < 1.0e-6,
                        "Boss immunity must preserve accepted damage at 2x, interval=" + interval + ", delay=" + delay
                                + ": reference=" + normal40 + ", scaled=" + doubled20);
                double normal100 = damage(context, 100, 20.0F, 1, interval, delay);
                double fiveTimes20 = damage(context, 20, 100.0F, 5, interval, delay);
                context.assertTrue(Math.abs(normal100 - fiveTimes20) < 1.0e-6,
                        "Batched damage must preserve boss immunity at 5x, interval=" + interval + ", delay=" + delay
                                + ": reference=" + normal100 + ", scaled=" + fiveTimes20);
            }
        }
        context.succeed();
    }

    private static double damage(GameTestHelper context, int frames, float rate, int steps, int interval, int delay) {
        ServerLevel world = context.getLevel();
        SemionBossEntity boss = new SemionBossEntity(SemionEntityTypes.BOSS, world);
        BossMonster runtimeBoss = new BossMonster(TeamId.RED, 10000.0, 0.0, 13);
        boss.configure(TeamId.RED, runtimeBoss);
        boss.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(2, 2, 2))));
        boss.setAnchorPosition(boss.position());
        boss.setNoAi(true);
        AttackingMonster attacker = new AttackingMonster(world);
        attacker.configureFrom(new Monster("boss-immunity-attacker", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                10000.0, 0.0, 10.0, AttackKind.MELEE, "minecraft:zombie", 0), null);
        attacker.runtimeMonster().applyCombatProfile(1.0, 3.0, interval);
        attacker.setPos(boss.position().add(1, 0, 0));
        attacker.setNoGravity(true);
        attacker.setTarget(boss);
        if (delay > 0) {
            attacker.setAttackStyle(new MonsterAttackStyle() {
                @Override public int hitDelayTicks() { return delay; }
                @Override public void hit(SemionMonsterEntity source, LivingEntity target) {
                    MonsterAttackStyle.strike(source, target, source.attackDamageAmount());
                }
            });
        }
        try {
            SemionGame game = new SemionGame(EconomyConfig.defaultConfig(), WaveConfig.defaultConfig(),
                    SyntheticArenaFactory.create(world, context.absolutePos(BlockPos.ZERO)));
            CombatSpeedRuntime.configure(world.getServer(), game, rate, steps);
            int beforeTicks = attacker.tickCount;
            for (int frame = 0; frame < frames; frame++) {
                for (int step = 0; step < steps; step++) {
                    ArenaCombatClock.advance(world);
                }
                world.tickNonPassenger(boss);
                world.tickNonPassenger(attacker);
            }
            context.assertTrue(attacker.tickCount - beforeTicks == frames,
                    "Damage events must not replay physical entity ticks.");
            double accepted = runtimeBoss.maxHealth() - runtimeBoss.health();
            context.assertTrue(accepted > 0.0 && accepted < frames * 10.0,
                    "The actual boss damage and immunity paths must both be exercised.");
            context.assertTrue(Math.abs(accepted - (10000.0 - boss.getHealth())) < 1.0e-6,
                    "Boss runtime health must agree with accepted entity damage.");
            return accepted;
        } finally {
            boss.discard();
            attacker.discard();
            CombatSpeedRuntime.clear();
            ArenaCombatClock.remove(world);
        }
    }

    private static final class AttackingMonster extends SemionMonsterEntity {
        private final MonsterAttackTargetGoal attackGoal;

        private AttackingMonster(ServerLevel world) {
            super(SemionEntityTypes.MONSTER, world);
            attackGoal = new MonsterAttackTargetGoal(this, 1.0);
        }

        @Override
        protected void registerGoals() {
        }

        @Override
        protected void customServerAiStep(ServerLevel world) {
            if ((tickCount & 1) == 1) {
                attackGoal.tick();
            }
        }
    }
}
