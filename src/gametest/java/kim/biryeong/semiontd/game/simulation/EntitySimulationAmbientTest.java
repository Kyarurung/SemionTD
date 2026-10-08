package kim.biryeong.semiontd.game.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.simulation.EntitySimulationBridge;
import kim.biryeong.semiontd.entity.simulation.WorkerPhysics;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.mixin.accessor.EntitySimulationAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class EntitySimulationAmbientTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void ambientDamageAndDeathMatchFortyNativeTicksWithinTwentyFrames(GameTestHelper context) {
        for (String scenario : new String[]{"fire", "drowning", "poison", "death"}) {
            compareAmbient(context, scenario);
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void bossAcceptedDamageAndImmunityMatchEveryLogicalTick(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        TraceBoss baseline = boss(context);
        TraceBoss simulated = boss(context);
        TraceMonster attacker = monster(context, "attacker");
        ((EntitySimulationAccessor) (Object) baseline).semiontd$invulnerableTime(6);
        ((EntitySimulationAccessor) (Object) simulated).semiontd$invulnerableTime(6);
        TestOwner owner = new TestOwner(simulated);
        CombatSimulationRuntime.register(world, owner);
        try {
            for (int frame = 0; frame < 20; frame++) {
                world.tickNonPassenger(simulated);
                for (int substep = 0; substep < 2; substep++) {
                    owner.age = frame * 2 + substep + 1;
                    world.tickNonPassenger(baseline);
                    float damage = owner.age % 11 == 0 ? 20.0F : 10.0F;
                    baseline.hurtServer(world, world.damageSources().mobAttack(attacker), damage);
                    CombatSimulationRuntime.run(owner, () -> {
                        step(simulated);
                        simulated.hurtServer(world, world.damageSources().mobAttack(attacker), damage);
                    });
                    context.assertTrue(baseline.trace.equals(simulated.trace), "Accepted boss hit trace must match at logical tick " + owner.age);
                    context.assertTrue(baseline.damageCooldownTime == simulated.damageCooldownTime,
                            "Native boss immunity must match at logical tick " + owner.age);
                    context.assertTrue(((EntitySimulationAccessor) (Object) baseline).semiontd$invulnerableTime()
                                    == ((EntitySimulationAccessor) (Object) simulated).semiontd$invulnerableTime(),
                            "Temporary native invulnerability must advance once per logical tick.");
                    context.assertTrue(baseline.getLastHurtByMobTimestamp() == simulated.getLastHurtByMobTimestamp(),
                            "Native incoming-damage attribution must use the target's logical entity age.");
                    context.assertTrue(baseline.runtimeBossHealth() == simulated.runtimeBossHealth(),
                            "Accepted native damage must reach the same boss runtime health.");
                }
            }
            context.assertTrue(baseline.tickCount == 40 && simulated.tickCount == 20,
                    "Logical combat must preserve the physical entity tick counts.");
            context.assertTrue(simulated.getHealth() < 10000.0F && simulated.getHealth() > 9600.0F,
                    "The test must exercise both accepted and rejected native damage.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            baseline.discard();
            simulated.discard();
            attacker.discard();
        }
        context.succeed();
    }

    private static void compareAmbient(GameTestHelper context, String scenario) {
        ServerLevel world = context.getLevel();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                for (int y = 2; y <= 5; y++) {
                    context.setBlock(new BlockPos(x, y, z), scenario.equals("drowning") ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        TraceMonster baseline = monster(context, scenario);
        TraceMonster simulated = monster(context, scenario);
        TestOwner owner = new TestOwner(simulated);
        CombatSimulationRuntime.register(world, owner);
        try {
            for (int frame = 0; frame < 20; frame++) {
                if (!simulated.isRemoved()) {
                    int fireBefore = simulated.getRemainingFireTicks();
                    int airBefore = simulated.getAirSupply();
                    int deathBefore = simulated.deathTime;
                    world.tickNonPassenger(simulated);
                    context.assertTrue(fireBefore == simulated.getRemainingFireTicks() && airBefore == simulated.getAirSupply()
                            && deathBefore == simulated.deathTime, "Physical ticks must not advance owned ambient clocks.");
                }
                for (int substep = 0; substep < 2; substep++) {
                    owner.age = frame * 2 + substep + 1;
                    if (!baseline.isRemoved()) {
                        world.tickNonPassenger(baseline);
                    }
                    if (!simulated.isRemoved()) {
                        CombatSimulationRuntime.run(owner, () -> {
                            if (simulated.isAlive()) {
                                step(simulated);
                            } else {
                                EntitySimulationBridge.ambient(simulated);
                            }
                        });
                    }
                    context.assertTrue(baseline.trace.equals(simulated.trace), scenario + " damage trace tick=" + owner.age
                            + ": native=" + baseline.trace + ", simulated=" + simulated.trace);
                    context.assertTrue(baseline.getRemainingFireTicks() == simulated.getRemainingFireTicks()
                            && baseline.getAirSupply() == simulated.getAirSupply() && baseline.deathTime == simulated.deathTime
                            && baseline.isRemoved() == simulated.isRemoved(), scenario + " ambient state tick=" + owner.age);
                }
            }
            if (scenario.equals("death")) {
                context.assertTrue(baseline.isRemoved() && simulated.isRemoved() && simulated.deathTime == 20,
                        "Native death removal must occur after twenty logical ticks.");
                context.assertTrue(baseline.tickCount == 20 && simulated.tickCount == 10,
                        "Death cleanup must take ten physical frames at two logical ticks per frame.");
            } else {
                context.assertTrue(!simulated.trace.isEmpty(), scenario + " must exercise real native damage.");
                context.assertTrue(baseline.tickCount == 40 && simulated.tickCount == 20,
                        "Ambient clock parity must not replay physical entity ticks.");
            }
        } finally {
            CombatSimulationRuntime.unregister(owner);
            baseline.discard();
            simulated.discard();
        }
    }

    private static void step(Mob actor) {
        WorkerPhysics.Input input = EntitySimulationBridge.prepare(actor);
        if (actor.isRemoved() || !actor.isAlive()) {
            EntitySimulationBridge.abort(actor);
            return;
        }
        EntitySimulationBridge.apply(actor, input, WorkerPhysics.advance(input));
        EntitySimulationBridge.finish(actor);
    }

    private static TraceMonster monster(GameTestHelper context, String scenario) {
        TraceMonster actor = new TraceMonster(context.getLevel());
        actor.configureFrom(new Monster("ambient-clock", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0), null);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, 2, 3))));
        actor.setNoAi(true);
        actor.setNoGravity(true);
        switch (scenario) {
            case "fire" -> actor.setRemainingFireTicks(40);
            case "drowning" -> actor.setAirSupply(0);
            case "poison" -> actor.addEffect(new MobEffectInstance(MobEffects.POISON, 41));
            case "death" -> {
                actor.setHealth(0.0F);
                actor.die(context.getLevel().damageSources().generic());
            }
        }
        return actor;
    }

    private static TraceBoss boss(GameTestHelper context) {
        TraceBoss actor = new TraceBoss(context.getLevel());
        actor.runtime = new BossMonster(TeamId.RED, 10000, 0, 13);
        actor.configure(TeamId.RED, actor.runtime);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, 2, 3))));
        actor.setAnchorPosition(actor.position());
        actor.setNoAi(true);
        return actor;
    }

    private static final class TraceMonster extends SemionMonsterEntity {
        private final List<String> trace = new ArrayList<>();

        private TraceMonster(ServerLevel world) { super(SemionEntityTypes.MONSTER, world); }
        @Override protected void registerGoals() { }

        @Override
        public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
            float before = getHealth();
            boolean accepted = super.hurtServer(world, source, amount);
            trace.add(CombatSimulationRuntime.entityTick(this) + ":" + source.getMsgId() + ":" + accepted + ":" + before + ":" + getHealth());
            return accepted;
        }
    }

    private static final class TraceBoss extends SemionBossEntity {
        private final List<String> trace = new ArrayList<>();
        private BossMonster runtime;

        private TraceBoss(ServerLevel world) { super(SemionEntityTypes.BOSS, world); }
        private double runtimeBossHealth() { return runtime.health(); }

        @Override
        public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
            float before = getHealth();
            boolean accepted = super.hurtServer(world, source, amount);
            trace.add(CombatSimulationRuntime.entityTick(this) + ":" + accepted + ":" + before + ":" + getHealth());
            return accepted;
        }
    }

    private static final class TestOwner implements CombatSimulationRuntime.Owner {
        private final Entity actor;
        private int age;

        private TestOwner(Entity actor) { this.actor = actor; }
        @Override public boolean controls(Entity entity) { return entity == actor; }
        @Override public int entityTick(Entity entity) { return age; }
        @Override public long gameTime(ServerLevel world) { return age; }
        @Override public CombatSimulationRuntime.EntityView view(Entity entity) { return null; }
        @Override public void changed(Entity entity) { }
        @Override public void animate(Entity entity, SemionAnimationState animation, Runnable presentation) { }
        @Override public void input(Runnable input) { input.run(); }
        @Override public Iterable<Entity> entities(ServerLevel world) { return List.of(actor); }
    }
}
