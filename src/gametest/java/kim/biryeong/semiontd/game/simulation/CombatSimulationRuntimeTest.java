package kim.biryeong.semiontd.game.simulation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.game.ArenaCombatClock;
import kim.biryeong.semiontd.game.CombatSpeedRuntime;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CombatSimulationRuntimeTest implements RuntimeArenaFixture {
    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void logicalPoseAndNativeSpatialOrderSurviveFramePublication(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        SemionMonsterEntity first = new SemionMonsterEntity(SemionEntityTypes.MONSTER, world);
        SemionMonsterEntity second = new SemionMonsterEntity(SemionEntityTypes.MONSTER, world);
        Vec3 origin = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(5, 3, 5)));
        world.setBlock(BlockPos.containing(origin), Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(BlockPos.containing(origin.add(17, 0, 0)), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        first.setPos(origin);
        second.setPos(origin.add(1, 0, 0));
        world.addFreshEntity(first);
        world.addFreshEntity(second);
        context.assertTrue(first.getInBlockState().isAir(), "The published block cache must begin at the native pose");
        TestOwner owner = new TestOwner(world.getGameTime());
        owner.views.put(first, CombatSimulationRuntime.EntityView.capture(first));
        owner.views.put(second, CombatSimulationRuntime.EntityView.capture(second));
        AABB query = new AABB(origin.add(-32, -3, -32), origin.add(32, 3, 32));
        List<Entity> initial = world.getEntities((Entity) null, query, entity -> entity == first || entity == second);
        try {
            CombatSimulationRuntime.register(world, owner);
            CombatSimulationRuntime.run(owner, () -> {
                context.assertTrue(world.getEntities((Entity) null, query, entity -> entity == first || entity == second)
                        .equals(initial), "Logical spatial queries must preserve native section and insertion order");
                first.setPos(origin.add(17, 0, 0));
                first.setDeltaMovement(0.25, 0.1, -0.3);
                first.setOnGround(false);
                context.assertTrue(first.position().equals(origin.add(17, 0, 0)), "Callbacks must read the logical pose");
                context.assertTrue(first.blockPosition().equals(BlockPos.containing(first.position())),
                        "Block positions must follow the logical pose");
                context.assertTrue(first.getInBlockState().is(Blocks.GOLD_BLOCK),
                        "Logical movement must not reuse the published block cache");
            });
            context.assertTrue(first.position().equals(origin), "Worker steps must leave the published pose unchanged");
            context.assertTrue(first.getInBlockState().isAir(), "Unfinished logical movement must not overwrite the native block cache");
            List<List<Entity>> logical = new ArrayList<>();
            CombatSimulationRuntime.run(owner, () -> logical.add(world.getEntities((Entity) null, query,
                    entity -> entity == first || entity == second)));
            owner.views.get(first).publish(first);
            List<Entity> nativeOrder = world.getEntities((Entity) null, query, entity -> entity == first || entity == second);
            context.assertTrue(logical.getFirst().equals(nativeOrder), "Section crossings must match native query ordering");
            context.assertTrue(first.position().equals(origin.add(17, 0, 0)), "Frame publication must apply the completed pose");
            context.assertTrue(first.getDeltaMovement().equals(new Vec3(0.25, 0.1, -0.3)) && !first.onGround(),
                    "Frame publication must preserve velocity and ground state");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            first.discard();
            second.discard();
        }
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void ownerClockAndInputDoNotDoubleApplySpeed(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        long initial = world.getGameTime();
        TestOwner owner = new TestOwner(initial + 2);
        int[] calls = new int[1];
        long[] handoff = new long[1];
        try {
            CombatSimulationRuntime.register(world, owner);
            context.assertTrue(world.getGameTime() == initial + 2 && CombatSpeedRuntime.gameTime(world) == initial + 2,
                    "Game time must follow the completed logical clock");
            context.assertTrue(CombatSpeedRuntime.multiplier(world) == 1 && CombatSpeedRuntime.logicalSteps(world) == 1,
                    "A logical step must not also apply the old speed multiplier");
            context.assertTrue(CombatSimulationRuntime.nativeGameTime(world) == initial,
                    "Native clock access must bypass simulation ownership");
            context.assertTrue(CombatSimulationRuntime.input(world, () -> calls[0]++), "External input must be queued");
            context.assertTrue(calls[0] == 0, "Queued input must not mutate an in-flight step");
            CombatSimulationRuntime.run(owner, () -> {
                owner.inputs.getFirst().run();
                context.assertTrue(!CombatSimulationRuntime.input(world, () -> calls[0]++),
                        "A boundary-applied input must not be queued again");
            });
            context.assertTrue(calls[0] == 1, "The accepted input must execute exactly once");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            ArenaCombatClock.adopt(world, initial + 2);
            handoff[0] = world.getGameTime();
            ArenaCombatClock.adopt(world, initial);
        }
        context.assertTrue(handoff[0] == initial + 2, "Closing a session must preserve its logical clock");
        context.succeed();
    }

    @GameTest(structure = "semion-td-gametest:combat_arena")
    public void externalImpulseSurvivesCompletedFramePublication(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        SemionMonsterEntity actor = new SemionMonsterEntity(SemionEntityTypes.MONSTER, world);
        actor.setPos(Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, 3, 3))));
        world.addFreshEntity(actor);
        TestOwner owner = new TestOwner(world.getGameTime());
        owner.views.put(actor, CombatSimulationRuntime.EntityView.capture(actor));
        Vec3 impulse = new Vec3(0.2, 0, -0.1);
        try {
            CombatSimulationRuntime.register(world, owner);
            actor.push(impulse);
            context.assertTrue(owner.inputs.size() == 1 && actor.getDeltaMovement().equals(Vec3.ZERO),
                    "An external impulse must wait without changing the published or in-flight velocity");
            owner.views.get(actor).publish(actor);
            context.assertTrue(actor.getDeltaMovement().equals(Vec3.ZERO),
                    "Publishing the prior completed frame must not consume the queued impulse");
            CombatSimulationRuntime.run(owner, () -> owner.inputs.removeFirst().run());
            context.assertTrue(owner.inputs.isEmpty() && owner.views.get(actor).velocity().equals(impulse),
                    "The accepted impulse must modify logical velocity exactly once without requeueing");
            owner.views.get(actor).publish(actor);
            context.assertTrue(actor.getDeltaMovement().equals(impulse),
                    "The next completed frame must publish the accepted impulse");
            owner.rejectInputs = true;
            actor.push(impulse);
            context.assertTrue(owner.inputs.isEmpty() && actor.getDeltaMovement().equals(impulse)
                            && owner.views.get(actor).velocity().equals(impulse),
                    "A saturated boundary must reject an external impulse without physical or logical fallback");
        } finally {
            CombatSimulationRuntime.unregister(owner);
            actor.discard();
        }
        context.succeed();
    }

    private static final class TestOwner implements CombatSimulationRuntime.Owner {
        private final Map<Entity, CombatSimulationRuntime.EntityView> views = new IdentityHashMap<>();
        private final List<Runnable> inputs = new ArrayList<>();
        private final long clock;
        private boolean rejectInputs;

        private TestOwner(long clock) { this.clock = clock; }
        public boolean controls(Entity entity) { return views.containsKey(entity); }
        public int entityTick(Entity entity) { return views.get(entity).age(); }
        public long gameTime(ServerLevel world) { return clock; }
        public CombatSimulationRuntime.EntityView view(Entity entity) { return views.get(entity); }
        public void changed(Entity entity) { views.get(entity).changed(); }
        public void animate(Entity entity, SemionAnimationState animation, Runnable presentation) { inputs.add(presentation); }
        public void input(Runnable input) {
            if (rejectInputs) {
                throw new java.util.concurrent.RejectedExecutionException("Injected full input boundary");
            }
            inputs.add(input);
        }
        public Iterable<Entity> entities(ServerLevel world) { return world.getAllEntities(); }
    }
}
