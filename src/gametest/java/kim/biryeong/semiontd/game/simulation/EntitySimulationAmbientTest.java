package kim.biryeong.semiontd.game.simulation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.config.EconomyConfig;
import kim.biryeong.semiontd.augment.AugmentEconomyService;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.boss.BossMonster;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.MonsterAttackStyle;
import kim.biryeong.semiontd.entity.monster.MonsterOrigin;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.simulation.EntitySimulationBridge;
import kim.biryeong.semiontd.entity.simulation.WorkerPhysics;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.game.SemionPlayer;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.mixin.accessor.EntitySimulationAccessor;
import kim.biryeong.semiontd.mixin.accessor.LivingEntitySimulationAccessor;
import kim.biryeong.semiontd.mixin.accessor.LivingEntitySwingStateAccessor;
import kim.biryeong.semiontd.vfx.DisplayEffect;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class EntitySimulationAmbientTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void registeredEngineerLootingRunsOnlyInLogicalMobTail(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        CopperGolem actor = EntityTypes.COPPER_GOLEM.create(world, EntitySpawnReason.TRIGGERED);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(6, 4, 6))));
        actor.setNoAi(true);
        actor.setNoGravity(true);
        actor.setCanPickUpLoot(true);
        context.assertTrue(world.addFreshEntity(actor), "Looting golem must enter the real world.");
        EntitySimulationBridge.registerEngineerGolem(actor);
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        context.assertTrue(actor.wantsToPickUp(world, stack), "Native Copper Golem must accept the test loot.");
        ItemEntity item = new ItemEntity(world, actor.getX(), actor.getY(), actor.getZ(), stack);
        item.setNoPickUpDelay();
        context.assertTrue(world.addFreshEntity(item), "Loot must enter the real world.");
        ContactOwner owner = new ContactOwner(List.of(actor));
        CombatSimulationRuntime.register(world, owner);
        try {
            world.tickNonPassenger(actor);
            context.assertTrue(!item.isRemoved() && actor.getMainHandItem().isEmpty(),
                    "A held physical Mob.aiStep must not run its looting tail.");
            owner.age = 1;
            owner.views.get(actor).age(1);
            CombatSimulationRuntime.run(owner, () -> {
                WorkerPhysics.Input input = EntitySimulationBridge.prepare(actor);
                EntitySimulationBridge.apply(actor, input, WorkerPhysics.advance(input));
                EntitySimulationBridge.finish(actor);
            });
            context.assertTrue(item.isRemoved() && actor.getMainHandItem().is(Items.IRON_SWORD),
                    "The logical Mob tail must perform the actual native pickup once.");
            world.tickNonPassenger(actor);
            context.assertTrue(actor.getMainHandItem().getCount() == 1 && actor.tickCount == 2,
                    "Physical ticks must retain the equipped native result without replaying the logical tail.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            item.discard();
            actor.discard();
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void logicalHeadingUsesCurrentSwingAnimationAcrossRestartAndCompletion(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        SwingHeadingMonster baseline = swingHeadingActor(context);
        SwingHeadingMonster simulated = swingHeadingActor(context);
        TestOwner owner = new TestOwner(simulated);
        owner.logicalView = CombatSimulationRuntime.EntityView.capture(simulated);
        CombatSimulationRuntime.register(world, owner);
        boolean restartedAtZero = false;
        boolean completedAtZero = false;
        boolean activeSwing = false;
        try {
            for (int frame = 0; frame < 10; frame++) {
                if (frame != 3) {
                    world.tickNonPassenger(simulated);
                }
                for (int substep = 0; substep < 2; substep++) {
                    owner.age = frame * 2 + substep + 1;
                    owner.logicalView.age(owner.age);
                    world.tickNonPassenger(baseline);
                    WorkerPhysics.Input[] prepared = new WorkerPhysics.Input[1];
                    CombatSimulationRuntime.run(owner, () -> prepared[0] = EntitySimulationBridge.prepare(simulated));
                    float current = swingAnimation(simulated);
                    context.assertTrue(current == swingAnimation(baseline)
                                    && simulated.getSwingAnimation(1.0F) == baseline.getSwingAnimation(1.0F)
                                    && simulated.isSwinging() == baseline.isSwinging(),
                            "The native swing clock must advance once per logical step " + owner.age);
                    if (current == 0.0F && simulated.getSwingAnimation(1.0F) > 0.0F) {
                        if (simulated.isSwinging()) {
                            restartedAtZero = true;
                        } else {
                            completedAtZero = true;
                        }
                        List<Float> held = heading(simulated);
                        if (owner.age == 6) {
                            world.tickNonPassenger(simulated);
                            context.assertTrue(swingAnimation(simulated) == current
                                            && simulated.getSwingAnimation(1.0F) == baseline.getSwingAnimation(1.0F)
                                            && heading(simulated).equals(held),
                                    "A held physical tick must preserve restarted swing state and heading.");
                        }
                    }
                    activeSwing |= current > 0.0F;
                    CombatSimulationRuntime.run(owner, () -> {
                        EntitySimulationBridge.apply(simulated, prepared[0], WorkerPhysics.advance(prepared[0]));
                        EntitySimulationBridge.finish(simulated);
                    });
                    context.assertTrue(baseline.headTurnArgument == simulated.headTurnArgument
                                    && heading(baseline).equals(heading(simulated)),
                            "Head-turn dispatch must use native current swing animation at step " + owner.age
                                    + ": native=" + baseline.headTurnArgument + ", simulated=" + simulated.headTurnArgument);
                }
                owner.logicalView.publish(simulated);
            }
            context.assertTrue(activeSwing && restartedAtZero && completedAtZero,
                    "The regression must cover positive animation and both restarted and completed zero-animation states.");
            context.assertTrue(baseline.tickCount == 20 && simulated.tickCount == 10,
                    "Logical swing phases must retain actual physical tick counts.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            baseline.discard();
            simulated.discard();
        }
        context.succeed();
    }

    private static float swingAnimation(Mob actor) {
        return ((LivingEntitySwingStateAccessor) ((LivingEntitySimulationAccessor) actor).semiontd$swingState())
                .semiontd$animation();
    }

    private static SwingHeadingMonster swingHeadingActor(GameTestHelper context) {
        SwingHeadingMonster actor = new SwingHeadingMonster(context.getLevel());
        actor.configureFrom(new Monster("swing-heading", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0), null);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(2, 4, 2))));
        actor.setNoGravity(true);
        actor.setYRot(45);
        actor.setYHeadRot(45);
        actor.setYBodyRot(45);
        actor.setOldPosAndRot();
        return actor;
    }

    private static final class SwingHeadingMonster extends SemionMonsterEntity {
        private float headTurnArgument;
        private SwingHeadingMonster(ServerLevel world) { super(SemionEntityTypes.MONSTER, world); }
        @Override protected void registerGoals() { }

        @Override
        protected void customServerAiStep(ServerLevel world) {
            setDeltaMovement(0.25, 0, 0);
            setYRot(45);
            setYHeadRot(45);
            if (tickCount == 1 || tickCount == 5) {
                swing(InteractionHand.MAIN_HAND, new SwingAnimation(SwingAnimation.DEFAULT.type(), 4), false);
            }
            xxa = 0;
            yya = 0;
            zza = 0;
        }

        @Override
        protected void tickHeadTurn(float rotation) {
            headTurnArgument = rotation;
            super.tickHeadTurn(rotation);
        }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void logicalHeadingTailMatchesNativeMoveStopRotationWrapping(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        HeadingMonster baseline = headingActor(context);
        HeadingMonster simulated = headingActor(context);
        TestOwner owner = new TestOwner(simulated);
        owner.logicalView = CombatSimulationRuntime.EntityView.capture(simulated);
        CombatSimulationRuntime.register(world, owner);
        try {
            for (int frame = 0; frame < 20; frame++) {
                if (frame != 1) {
                    world.tickNonPassenger(simulated);
                }
                for (int substep = 0; substep < 2; substep++) {
                    owner.age = frame * 2 + substep + 1;
                    owner.logicalView.age(owner.age);
                    world.tickNonPassenger(baseline);
                    WorkerPhysics.Input[] prepared = new WorkerPhysics.Input[1];
                    CombatSimulationRuntime.run(owner, () -> prepared[0] = EntitySimulationBridge.prepare(simulated));
                    if (owner.age == 2) {
                        List<Float> held = heading(simulated);
                        world.tickNonPassenger(simulated);
                        context.assertTrue(held.equals(heading(simulated)),
                                "Physical ticks must preserve the held heading and all old rotation fields.");
                    }
                    CombatSimulationRuntime.run(owner, () -> {
                        EntitySimulationBridge.apply(simulated, prepared[0], WorkerPhysics.advance(prepared[0]));
                        EntitySimulationBridge.finish(simulated);
                    });
                    context.assertTrue(heading(baseline).equals(heading(simulated)),
                            "Native body controller and rotation wrapping must match after logical step " + owner.age
                                    + ": native=" + heading(baseline) + ", simulated=" + heading(simulated));
                }
                owner.logicalView.publish(simulated);
            }
            context.assertTrue(baseline.tickCount == 40 && simulated.tickCount == 20,
                    "Heading phases must preserve physical tick counts.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            baseline.discard();
            simulated.discard();
        }
        context.succeed();
    }

    private static List<Float> heading(Mob actor) {
        return List.of(actor.getYRot(), actor.getXRot(), actor.yBodyRot, actor.yHeadRot,
                actor.yRotO, actor.xRotO, actor.yBodyRotO, actor.yHeadRotO);
    }

    private static HeadingMonster headingActor(GameTestHelper context) {
        HeadingMonster actor = new HeadingMonster(context.getLevel());
        actor.configureFrom(new Monster("heading-tail", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0), null);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(2, 4, 2))));
        actor.setNoGravity(true);
        actor.setYRot(0);
        actor.setXRot(0);
        actor.setYHeadRot(0);
        actor.setYBodyRot(0);
        actor.setOldPosAndRot();
        return actor;
    }

    private static final class HeadingMonster extends SemionMonsterEntity {
        private HeadingMonster(ServerLevel world) { super(SemionEntityTypes.MONSTER, world); }
        @Override protected void registerGoals() { }

        @Override
        protected void customServerAiStep(ServerLevel world) {
            setDeltaMovement(tickCount == 1 ? new Vec3(0.25, 0, 0) : Vec3.ZERO);
            if (tickCount <= 2) {
                setYRot(tickCount == 1 ? 350 : 10);
                setXRot(tickCount == 1 ? 200 : -200);
                setYHeadRot(tickCount == 1 ? 350 : 10);
            }
            xxa = 0;
            yya = 0;
            zza = 0;
        }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void registeredEngineerWeatheringUsesLogicalTimeAndStatueChance(GameTestHelper context) throws Exception {
        ServerLevel world = context.getLevel();
        CopperGolem actor = EntityTypes.COPPER_GOLEM.create(world, EntitySpawnReason.TRIGGERED);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(6, 4, 6))));
        actor.setNoAi(true);
        actor.setNoGravity(true);
        context.assertTrue(world.addFreshEntity(actor), "Weathering golem must enter the real world.");
        EntitySimulationBridge.registerEngineerGolem(actor);
        var deadline = CopperGolem.class.getDeclaredField("nextWeatheringTick");
        deadline.setAccessible(true);
        deadline.setLong(actor, 2);
        ContactOwner owner = new ContactOwner(List.of(actor));
        CombatSimulationRuntime.register(world, owner);
        try {
            for (int physical = 0; physical < 3; physical++) {
                world.tickNonPassenger(actor);
            }
            context.assertTrue(actor.getWeatherState() == WeatheringCopper.WeatherState.UNAFFECTED
                            && deadline.getLong(actor) == 2,
                    "Physical golem ticks must neither consume nor reschedule logical weathering.");
            for (int age = 1; age <= 2; age++) {
                owner.age = age;
                owner.views.get(actor).age(age);
                CombatSimulationRuntime.run(owner, () -> {
                    WorkerPhysics.Input input = EntitySimulationBridge.prepare(actor);
                    EntitySimulationBridge.apply(actor, input, WorkerPhysics.advance(input));
                    EntitySimulationBridge.finish(actor);
                });
                context.assertTrue(actor.getWeatherState() == (age == 1
                                ? WeatheringCopper.WeatherState.UNAFFECTED : WeatheringCopper.WeatherState.EXPOSED),
                        "Weathering must fire at its exact logical deadline.");
            }
            context.assertTrue(deadline.getLong(actor) >= 504002 && deadline.getLong(actor) <= 552002,
                    "The native weathering callback must retain its native rescheduling range.");
            actor.setWeatherState(WeatheringCopper.WeatherState.OXIDIZED);
            deadline.setLong(actor, 0);
            long seed = 0;
            while (RandomSource.create(seed).nextFloat() > 0.0058F) {
                seed++;
            }
            world.getRandom().setSeed(seed);
            world.tickNonPassenger(actor);
            context.assertTrue(!actor.isRemoved(), "A held physical tick must not consume a statue conversion chance.");
            world.getRandom().setSeed(seed);
            owner.age = 3;
            owner.views.get(actor).age(3);
            CombatSimulationRuntime.run(owner, () -> {
                WorkerPhysics.Input input = EntitySimulationBridge.prepare(actor);
                EntitySimulationBridge.apply(actor, input, WorkerPhysics.advance(input));
                EntitySimulationBridge.finish(actor);
            });
            context.assertTrue(actor.isRemoved() && !world.getBlockState(actor.blockPosition()).isAir(),
                    "The native logical weathering phase must preserve actual statue placement and removal.");
            context.assertTrue(actor.tickCount == 4, "Logical weathering must not replay physical golem ticks.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            actor.discard();
            world.setBlock(actor.blockPosition(), Blocks.AIR.defaultBlockState(), 3);
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void registeredEngineerGolemPreservesOrderedNativeContactPushes(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        List<Mob> baseline = contactActors(context);
        List<List<Vec3>> expectedVelocities = new ArrayList<>();
        try {
            context.assertTrue(!EntitySimulationBridge.supports(baseline.getFirst()),
                    "Ordinary no-AI golems must remain outside combat ownership.");
            for (int step = 0; step < 40; step++) {
                baseline.forEach(world::tickNonPassenger);
                expectedVelocities.add(baseline.stream().map(Entity::getDeltaMovement).toList());
            }
            context.assertTrue(expectedVelocities.getFirst().get(1).x < -0.05,
                    "The native reference must include both ordered contact impulses.");
            context.assertTrue(baseline.stream().allMatch(actor -> actor.tickCount == 40),
                    "The native reference must execute forty real entity ticks.");
        } finally {
            baseline.forEach(Entity::discard);
        }
        List<Mob> simulated = contactActors(context);
        EntitySimulationBridge.registerEngineerGolem(simulated.getFirst());
        context.assertTrue(EntitySimulationBridge.supports(simulated.getFirst()),
                "Explicitly registered engineer golems must join the ordered logical actors.");
        ContactOwner owner = new ContactOwner(simulated);
        CombatSimulationRuntime.register(world, owner);
        try {
            for (int frame = 0; frame < 20; frame++) {
                if (frame != 1) {
                    for (Mob actor : simulated) {
                        Vec3[] physicalVelocity = new Vec3[1];
                        CombatSimulationRuntime.nativeAccess(() -> physicalVelocity[0] = actor.getDeltaMovement());
                        world.tickNonPassenger(actor);
                        CombatSimulationRuntime.nativeAccess(() -> context.assertTrue(
                                actor.getDeltaMovement().equals(physicalVelocity[0]),
                                "Owned physical bodies must not emit duplicate contact impulses or damp velocity."));
                    }
                }
                for (int substep = 0; substep < 2; substep++) {
                    owner.age = frame * 2 + substep + 1;
                    owner.views.values().forEach(view -> view.age(owner.age));
                    for (Mob actor : simulated) {
                        WorkerPhysics.Input[] prepared = new WorkerPhysics.Input[1];
                        CombatSimulationRuntime.run(owner, () -> prepared[0] = EntitySimulationBridge.prepare(actor));
                        if (owner.age == 2 && actor == simulated.getFirst()) {
                            List<Vec3> heldVelocities = simulated.stream().map(owner.views::get)
                                    .map(CombatSimulationRuntime.EntityView::velocity).toList();
                            simulated.forEach(world::tickNonPassenger);
                            for (int index = 0; index < simulated.size(); index++) {
                                context.assertTrue(owner.views.get(simulated.get(index)).velocity()
                                                .equals(heldVelocities.get(index)),
                                        "Held physical ticks must not damp or push either logical receiver.");
                            }
                        }
                        CombatSimulationRuntime.run(owner, () -> {
                            EntitySimulationBridge.apply(actor, prepared[0], WorkerPhysics.advance(prepared[0]));
                            EntitySimulationBridge.finish(actor);
                        });
                    }
                    for (int index = 0; index < simulated.size(); index++) {
                        Vec3 expected = expectedVelocities.get(owner.age - 1).get(index);
                        Vec3 actual = owner.views.get(simulated.get(index)).velocity();
                        context.assertTrue(expected.distanceToSqr(actual) < 1.0e-12,
                                "Both ordered native push receivers must match at step " + owner.age
                                        + ", actor " + index + ": native=" + expected + ", simulated=" + actual);
                    }
                }
                simulated.forEach(actor -> owner.views.get(actor).publish(actor));
            }
            context.assertTrue(simulated.stream().allMatch(actor -> actor.tickCount == 20),
                    "Forty logical contact phases must retain twenty physical entity ticks.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            simulated.forEach(Entity::discard);
        }
        context.succeed();
    }

    private static List<Mob> contactActors(GameTestHelper context) {
        Mob golem = EntityTypes.COPPER_GOLEM.create(context.getLevel(), EntitySpawnReason.TRIGGERED);
        TraceMonster monster = monster(context, "contact");
        Vec3 start = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(4, 4, 4)));
        golem.setPos(start.add(0.491175028542816, 0, 0));
        monster.setPos(start);
        List<Mob> actors = List.of(golem, monster);
        for (Mob actor : actors) {
            actor.setNoAi(true);
            actor.setNoGravity(true);
            actor.setDeltaMovement(Vec3.ZERO);
            context.assertTrue(context.getLevel().addFreshEntity(actor), "Contact actors must enter the real world.");
        }
        return actors;
    }

    private static final class ContactOwner implements CombatSimulationRuntime.Owner {
        private final List<Mob> actors;
        private final Map<Entity, CombatSimulationRuntime.EntityView> views = new IdentityHashMap<>();
        private int age;

        private ContactOwner(List<Mob> actors) {
            this.actors = actors;
            actors.forEach(actor -> views.put(actor, CombatSimulationRuntime.EntityView.capture(actor)));
        }

        @Override public boolean controls(Entity entity) { return views.containsKey(entity); }
        @Override public int entityTick(Entity entity) { return age; }
        @Override public long gameTime(ServerLevel world) { return age; }
        @Override public CombatSimulationRuntime.EntityView view(Entity entity) { return views.get(entity); }
        @Override public void changed(Entity entity) { }
        @Override public void animate(Entity entity, SemionAnimationState animation, Runnable presentation) { }
        @Override public void input(Runnable input) { input.run(); }
        @Override public Iterable<Entity> entities(ServerLevel world) { return new ArrayList<>(actors); }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void logicalMovementHistoryMatchesNativeTicksDuringMoveStopAndWorkerWait(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        MotionHistoryMonster baseline = motionActor(context);
        MotionHistoryMonster simulated = motionActor(context);
        TestOwner owner = new TestOwner(simulated);
        owner.logicalView = CombatSimulationRuntime.EntityView.capture(simulated);
        CombatSimulationRuntime.register(world, owner);
        try {
            for (int frame = 0; frame < 20; frame++) {
                if (frame != 1) {
                    world.tickNonPassenger(simulated);
                }
                for (int substep = 0; substep < 2; substep++) {
                    owner.age = frame * 2 + substep + 1;
                    owner.logicalView.age(owner.age);
                    world.tickNonPassenger(baseline);
                    WorkerPhysics.Input[] prepared = new WorkerPhysics.Input[1];
                    CombatSimulationRuntime.run(owner, () -> prepared[0] = EntitySimulationBridge.prepare(simulated));
                    assertHistory(context, baseline, simulated, owner.age);
                    if (owner.age == 2) {
                        Vec3[] published = new Vec3[1];
                        CombatSimulationRuntime.nativeAccess(() -> published[0] = simulated.position());
                        context.assertTrue(simulated.oldPosition().distanceToSqr(published[0]) > 0.01,
                                "The held prefix must have logical history ahead of the published body.");
                        world.tickNonPassenger(simulated);
                        assertHistory(context, baseline, simulated, owner.age);
                    }
                    CombatSimulationRuntime.run(owner, () -> {
                        EntitySimulationBridge.apply(simulated, prepared[0], WorkerPhysics.advance(prepared[0]));
                        EntitySimulationBridge.finish(simulated);
                        assertHistory(context, baseline, simulated, owner.age);
                        context.assertTrue(baseline.position().distanceToSqr(simulated.position()) < 1.0e-12
                                        && baseline.getKnownMovement().distanceToSqr(simulated.getKnownMovement()) < 1.0e-12,
                                "Logical movement must match native movement at step " + owner.age);
                        if (owner.age == 4) {
                            context.assertTrue(simulated.getKnownMovement().equals(Vec3.ZERO)
                                            && Math.abs(simulated.getKnownSpeed().x - 0.25) < 1.0e-6,
                                    "Stopping must retain the preceding logical displacement as known speed.");
                        }
                    });
                }
                owner.logicalView.publish(simulated);
            }
            context.assertTrue(baseline.tickCount == 40 && simulated.tickCount == 20,
                    "Movement history must not replay or replace physical entity ticks.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            baseline.discard();
            simulated.discard();
        }
        context.succeed();
    }

    private static MotionHistoryMonster motionActor(GameTestHelper context) {
        MotionHistoryMonster actor = new MotionHistoryMonster(context.getLevel());
        actor.configureFrom(new Monster("motion-history", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0), null);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(2, 4, 2))));
        actor.setNoGravity(true);
        actor.setYRot(0);
        actor.setXRot(0);
        actor.setYHeadRot(0);
        actor.setYBodyRot(0);
        actor.yHeadRotO = 0;
        actor.yBodyRotO = 0;
        actor.setOldPosAndRot();
        return actor;
    }

    private static void assertHistory(GameTestHelper context, SemionMonsterEntity expected,
                                      SemionMonsterEntity actual, int logicalAge) {
        context.assertTrue(expected.oldPosition().distanceToSqr(actual.oldPosition()) < 1.0e-12
                        && Math.abs(expected.xo - actual.xo) < 1.0e-6
                        && Math.abs(expected.yo - actual.yo) < 1.0e-6
                        && Math.abs(expected.zo - actual.zo) < 1.0e-6,
                "Old position must describe the previous logical pose at step " + logicalAge);
        context.assertTrue(expected.yRotO == actual.yRotO && expected.xRotO == actual.xRotO
                        && expected.yHeadRotO == actual.yHeadRotO && expected.yBodyRotO == actual.yBodyRotO,
                "Old rotations must match native logical history at step " + logicalAge);
        context.assertTrue(expected.getKnownSpeed().distanceToSqr(actual.getKnownSpeed()) < 1.0e-12,
                "Known speed must use consecutive logical positions at step " + logicalAge
                        + ": native=" + expected.getKnownSpeed() + ", simulated=" + actual.getKnownSpeed());
    }

    private static final class MotionHistoryMonster extends SemionMonsterEntity {
        private MotionHistoryMonster(ServerLevel world) { super(SemionEntityTypes.MONSTER, world); }
        @Override protected void registerGoals() { }

        @Override
        protected void customServerAiStep(ServerLevel world) {
            boolean moving = (tickCount & 1) == 1;
            setDeltaMovement(moving ? new Vec3(0.25, 0, 0) : Vec3.ZERO);
            setYRot(moving ? 30 : 0);
            setXRot(moving ? 10 : 0);
            setYHeadRot(moving ? 30 : 0);
            setYBodyRot(moving ? 30 : 0);
            xxa = 0;
            yya = 0;
            zza = 0;
        }
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void capturedSupportNameDoesNotReadUnfinishedProgress(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        SemionPlayer buyer = new SemionPlayer(UUID.randomUUID(), "지원 구매자", TeamId.BLUE, 1,
                new PlayerEconomy(EconomyConfig.defaultConfig()));
        AugmentEconomyService.beginPrepare(buyer, 15);
        AugmentEconomyService.onSelected(buyer, "semiontd:support_performance", 15, Map.of());
        Monster logical = new Monster("ghast", TeamId.RED, 777, Optional.of(buyer.uuid()), Optional.of(TeamId.BLUE),
                100, 0, 10, AttackKind.MELEE, "minecraft:zombie", 0);
        logical.setOrigin(MonsterOrigin.NORMAL_PAID);
        logical.setSenderName(buyer.name());
        var plan = AugmentEconomyService.quotePurchase(buyer, UUID.randomUUID(), 15, true, true, true, false, false, 100, 10);
        context.assertTrue(AugmentEconomyService.commitPurchase(buyer, plan, logical), "A real paid support purchase must establish nameplate progress.");
        SemionMonsterEntity actor = new SemionMonsterEntity(SemionEntityTypes.MONSTER, world);
        actor.configureFrom(logical, null);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, 2, 3))));
        TestOwner owner = new TestOwner(actor);
        owner.age = 20;
        CombatSimulationRuntime.register(world, owner);
        try {
            Runnable[] completed = new Runnable[1];
            CombatSimulationRuntime.run(owner, () -> completed[0] = EntitySimulationBridge.capturePresentation(actor));
            var senderColor = actor.getCustomName().getStyle().getColor();
            Monster target = new Monster("support-target", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                    100, 0, 1, AttackKind.MELEE, "minecraft:zombie", 0);
            target.setOrigin(MonsterOrigin.NATURAL_WAVE);
            AugmentEconomyService.recordSupport(logical, target, 1.0);
            context.assertTrue(AugmentEconomyService.supportProgress(logical).orElseThrow().equals("지원 1/3"), "The unfinished state must contain actual support progress.");
            completed[0].run();
            context.assertTrue(actor.getCustomName().getString().contains("지원 구매자 · 지원 0/3"), "The completed nameplate must not read unfinished support progress.");
            owner.age = 40;
            CombatSimulationRuntime.run(owner, () -> completed[0] = EntitySimulationBridge.capturePresentation(actor));
            context.assertTrue(actor.getCustomName().getString().contains("지원 0/3"), "Capture must not write the new nameplate early.");
            completed[0].run();
            context.assertTrue(actor.getCustomName().getString().contains("지원 구매자 · 지원 1/3")
                    && senderColor.equals(actor.getCustomName().getStyle().getColor()), "The next completed snapshot must publish support progress with the original buyer color.");
            context.assertTrue(AugmentEconomyService.supportProgress(logical).orElseThrow().equals("지원 1/3"), "Publication must not restore gameplay support state.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            actor.discard();
            AugmentEconomyService.close(buyer);
        }
        context.succeed();
    }

    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void capturedStealthPresentationIgnoresUnfinishedRevealPrefix(GameTestHelper context) throws ReflectiveOperationException {
        ServerLevel world = context.getLevel();
        SemionMonsterEntity actor = new SemionMonsterEntity(SemionEntityTypes.MONSTER, world);
        actor.configureFrom(new Monster("elf_assassin", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                1000, 0, 1, AttackKind.MELEE, "minecraft:zombie", "semion-td:invasion/elf_assassin", 0), null);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, 2, 3))));
        actor.setStealthCapable(true);
        actor.setAttackStyle(new MonsterAttackStyle() {
            @Override public int hitDelayTicks() { return 3; }
            @Override public void hit(SemionMonsterEntity source, net.minecraft.world.entity.LivingEntity target) {
                MonsterAttackStyle.strike(source, target, source.attackDamageAmount());
            }
        });
        TraceMonster target = monster(context, "attacker");
        TestOwner owner = new TestOwner(actor);
        owner.age = 40;
        CombatSimulationRuntime.register(world, owner);
        try {
            context.assertTrue(actor.hasBilModelHolder(), "The regression must use the actual packaged elf BIL model.");
            LivingEntityHolder<?> holder = (LivingEntityHolder<?>) actor.getHolder();
            var counterField = DisplayEffect.class.getDeclaredField("UNIT_ACTIVE");
            counterField.setAccessible(true);
            AtomicInteger unitEffects = (AtomicInteger) counterField.get(null);
            float visibleScale = holder.getScale();
            Runnable[] completed = new Runnable[1];
            CombatSimulationRuntime.run(owner, () -> completed[0] = EntitySimulationBridge.capturePresentation(actor));
            completed[0].run();
            context.assertTrue(holder.getScale() == 0.001F && !actor.isCustomNameVisible(), "Completed hidden state must hide the actual holder and nameplate.");
            int completedEffects = unitEffects.get();
            owner.age = 41;
            CombatSimulationRuntime.run(owner, () -> actor.startAttack(target));
            context.assertTrue(actor.hasPendingHit() && !actor.isStealthed(), "The unfinished native attack prefix must change live reveal state.");
            owner.age = 40;
            for (int frame = 0; frame < 3; frame++) {
                completed[0].run();
                context.assertTrue(holder.getScale() == 0.001F && !actor.isCustomNameVisible(),
                        "Publishing the completed frame must not reveal from the unfinished prefix.");
                context.assertTrue(!actor.isStealthed() && actor.hasPendingHit(), "Presentation must not restore live gameplay deadlines or pending hits.");
                context.assertTrue(unitEffects.get() == completedEffects, "An unfinished reveal must not emit a new stealth puff.");
            }
            owner.age = 41;
            CombatSimulationRuntime.run(owner, () -> completed[0] = EntitySimulationBridge.capturePresentation(actor));
            int effectsBeforeReveal = unitEffects.get();
            completed[0].run();
            context.assertTrue(holder.getScale() == visibleScale && actor.isCustomNameVisible(), "Completed reveal must publish the captured visible state.");
            int expectedEffects = effectsBeforeReveal + (effectsBeforeReveal < DisplayEffect.MAX_UNIT_ACTIVE ? 1 : 0);
            context.assertTrue(unitEffects.get() == expectedEffects, "A completed reveal must emit exactly one puff when the native budget permits it.");
            completed[0].run();
            context.assertTrue(holder.getScale() == visibleScale, "Repeated completed publication must retain the same visible scale.");
            context.assertTrue(unitEffects.get() == expectedEffects, "Repeated completed publication must not emit another puff.");
            owner.age = 65;
            CombatSimulationRuntime.run(owner, () -> completed[0] = EntitySimulationBridge.capturePresentation(actor));
            context.assertTrue(holder.getScale() == visibleScale, "Capturing a final hidden state must not write presentation early.");
            completed[0].run();
            context.assertTrue(holder.getScale() == 0.001F && !actor.isCustomNameVisible(), "The next completed hidden state must publish once.");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            actor.discard();
            target.discard();
        }
        context.succeed();
    }

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
        private CombatSimulationRuntime.EntityView logicalView;

        private TestOwner(Entity actor) { this.actor = actor; }
        @Override public boolean controls(Entity entity) { return entity == actor; }
        @Override public int entityTick(Entity entity) { return age; }
        @Override public long gameTime(ServerLevel world) { return age; }
        @Override public CombatSimulationRuntime.EntityView view(Entity entity) { return entity == actor ? logicalView : null; }
        @Override public void changed(Entity entity) { }
        @Override public void animate(Entity entity, SemionAnimationState animation, Runnable presentation) { }
        @Override public void input(Runnable input) { input.run(); }
        @Override public Iterable<Entity> entities(ServerLevel world) { return List.of(actor); }
    }
}
