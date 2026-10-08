package kim.biryeong.semiontd.entity.simulation;

import java.util.Optional;
import kim.biryeong.semiontd.config.AttackKind;
import kim.biryeong.semiontd.entity.SemionEntityTypes;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.game.TeamId;
import kim.biryeong.semiontd.gametest.RuntimeArenaFixture;
import kim.biryeong.semiontd.mixin.accessor.EntitySimulationAccessor;
import kim.biryeong.semiontd.mixin.accessor.LivingEntitySimulationAccessor;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class EntitySimulationPhysicsTest implements RuntimeArenaFixture {
    @GameTest(maxTicks = 120, structure = "semion-td-gametest:combat_arena")
    public void copiedWorkerGeometryPreservesNativeTravelAndCollision(GameTestHelper context) {
        for (String scenario : new String[]{"ground", "fall", "slab", "wall", "ice", "water", "lava", "climb", "slime"}) {
            setup(context, scenario);
            PhysicsMonster baseline = actor(context, scenario);
            PhysicsMonster simulated = actor(context, scenario);
            try {
                for (int step = 1; step <= 24; step++) {
                    baseline.tickCount = step;
                    simulated.tickCount = step;
                    ((EntitySimulationAccessor) (Object) baseline).semiontd$updateFluidInteraction();
                    baseline.updateSwimming();
                    ((LivingEntitySimulationAccessor) (Object) baseline).semiontd$tickEffects();
                    baseline.aiStep();
                    WorkerPhysics.Input input = EntitySimulationBridge.prepare(simulated);
                    WorkerPhysics.Result result = WorkerPhysics.advance(input);
                    EntitySimulationBridge.apply(simulated, input, result);
                    EntitySimulationBridge.finish(simulated);
                    assertVector(context, baseline.position(), simulated.position(), scenario + " position step=" + step);
                    assertVector(context, baseline.getDeltaMovement(), simulated.getDeltaMovement(), scenario + " velocity step=" + step);
                    context.assertTrue(baseline.onGround() == simulated.onGround(), scenario + " ground state step=" + step);
                    context.assertTrue(Math.abs(baseline.getHealth() - simulated.getHealth()) < 1.0e-5,
                            scenario + " native collision damage step=" + step);
                    context.assertTrue(baseline.tickCount == step && simulated.tickCount == step,
                            "The bridge must restore native entity age and never replay Entity.tick.");
                }
            } finally {
                baseline.discard();
                simulated.discard();
            }
        }
        context.succeed();
    }

    private static void setup(GameTestHelper context, String scenario) {
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                context.setBlock(new BlockPos(x, 1, z), scenario.equals("ice") ? Blocks.ICE
                        : scenario.equals("slime") ? Blocks.SLIME_BLOCK : Blocks.STONE);
                for (int y = 2; y <= 7; y++) {
                    context.setBlock(new BlockPos(x, y, z), scenario.equals("water") && y <= 5 ? Blocks.WATER
                            : scenario.equals("lava") && y <= 5 ? Blocks.LAVA : Blocks.AIR);
                }
            }
        }
        if (scenario.equals("slab")) {
            context.setBlock(new BlockPos(3, 2, 4), Blocks.STONE_SLAB);
        } else if (scenario.equals("wall") || scenario.equals("climb")) {
            for (int y = 2; y <= 5; y++) {
                context.setBlock(new BlockPos(3, y, 4), Blocks.STONE);
                if (scenario.equals("climb")) {
                    context.setBlock(new BlockPos(3, y, 3), Blocks.LADDER);
                }
            }
        }
    }

    private static PhysicsMonster actor(GameTestHelper context, String scenario) {
        PhysicsMonster actor = new PhysicsMonster(context.getLevel());
        actor.configureFrom(new Monster("worker-physics", TeamId.RED, 777, Optional.empty(), Optional.empty(),
                10000, 0, 0, AttackKind.MELEE, "minecraft:zombie", 0), null);
        Vec3 start = Vec3.atBottomCenterOf(context.absolutePos(new BlockPos(3, scenario.equals("fall")
                || scenario.equals("slime") ? 6 : 2, 2)));
        if (scenario.equals("climb")) {
            start = start.add(0, 0, 1);
        }
        actor.setPos(start);
        actor.setOnGround(!scenario.equals("fall") && !scenario.equals("slime"));
        actor.setDeltaMovement(0, -0.08, 0);
        return actor;
    }

    private static void assertVector(GameTestHelper context, Vec3 expected, Vec3 actual, String label) {
        context.assertTrue(expected.distanceToSqr(actual) < 1.0e-12,
                label + ": native=" + expected + ", worker=" + actual);
    }

    private static final class PhysicsMonster extends SemionMonsterEntity {
        private PhysicsMonster(ServerLevel level) {
            super(SemionEntityTypes.MONSTER, level);
        }

        @Override
        protected void registerGoals() {
        }

        @Override
        protected void customServerAiStep(ServerLevel level) {
            setSpeed(0.2F);
            zza = 1.0F;
        }
    }
}
