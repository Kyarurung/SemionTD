package kim.biryeong.semiontd.entity.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import kim.biryeong.semiontd.entity.EntityCombatSpeed;
import kim.biryeong.semiontd.entity.boss.SemionBossEntity;
import kim.biryeong.semiontd.entity.monster.SemionMonsterEntity;
import kim.biryeong.semiontd.entity.tower.SemionTowerEntity;
import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import kim.biryeong.semiontd.mixin.accessor.EntitySimulationAccessor;
import kim.biryeong.semiontd.mixin.accessor.LivingEntitySimulationAccessor;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class EntitySimulationBridge {
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
    private static final ThreadLocal<CollisionCommit> COLLISION = new ThreadLocal<>();
    private static final Map<Mob, WorkerPhysics.Input> PREPARED = new WeakHashMap<>();

    private EntitySimulationBridge() {
    }

    public static boolean supports(Entity entity) {
        return entity instanceof SemionTowerEntity || entity instanceof SemionMonsterEntity
                || entity instanceof SemionBossEntity;
    }

    public static WorkerPhysics.Input prepare(Mob actor) {
        checkServerThread(actor);
        if (!supports(actor)) {
            throw new IllegalArgumentException("Unsupported combat actor: " + actor.getType());
        }
        if (CAPTURE.get() != null) {
            throw new IllegalStateException("Combat actor preparation cannot be nested.");
        }
        if (actor instanceof SemionTowerEntity || actor instanceof SemionMonsterEntity) {
            EntityCombatSpeed.updateMovement(actor);
        }
        Capture capture = new Capture(actor);
        CAPTURE.set(capture);
        int physicalAge = actor.tickCount;
        actor.tickCount = CombatSimulationRuntime.entityTick(actor);
        try {
            tickAmbient(actor);
            ((LivingEntitySimulationAccess) actor).semiontd$prepareLivingTick();
            if (actor.isAlive() && actor.getY() < actor.level().getMinY()) {
                if (actor instanceof SemionTowerEntity tower && tower.runtimeTower() != null) {
                    tower.runtimeTower().syncHealth(0.0);
                }
                actor.setHealth(0.0F);
                actor.die(actor.damageSources().fellOutOfWorld());
            }
            if (actor.isRemoved()) {
                capture.input = inactiveInput(actor);
            } else {
                ((MobSimulationAccess) actor).semiontd$prepareLivingAi();
            }
        } finally {
            actor.tickCount = physicalAge;
            CAPTURE.remove();
        }
        if (capture.input == null) {
            throw new IllegalStateException("Native AI did not reach the combat travel boundary.");
        }
        PREPARED.put(actor, capture.input);
        return capture.input;
    }

    public static void ambient(Mob actor) {
        checkServerThread(actor);
        int physicalAge = actor.tickCount;
        actor.tickCount = CombatSimulationRuntime.entityTick(actor);
        try {
            tickAmbient(actor);
        } finally {
            actor.tickCount = physicalAge;
        }
        CombatSimulationRuntime.changed(actor);
    }

    private static void tickAmbient(Mob actor) {
        EntitySimulationAccessor access = (EntitySimulationAccessor) actor;
        if (access.semiontd$invulnerableTime() > 0) {
            access.semiontd$invulnerableTime(access.semiontd$invulnerableTime() - 1);
        }
        actor.setOldPosAndRot();
        actor.baseTick();
    }

    public static boolean capture(LivingEntity entity) {
        Capture capture = CAPTURE.get();
        if (capture == null || capture.actor != entity) {
            return false;
        }
        capture.input = snapshot(capture.actor);
        Profiler.get().pop();
        return true;
    }

    public static void apply(Mob actor, WorkerPhysics.Input input, WorkerPhysics.Result result) {
        checkServerThread(actor);
        if (PREPARED.get(actor) != input || !box(actor.getBoundingBox()).equals(input.box())
                || !vector(actor.getDeltaMovement()).equals(input.travel().velocity())) {
            throw new IllegalStateException("Combat movement input became stale before commit.");
        }
        if (COLLISION.get() != null) {
            throw new IllegalStateException("Combat movement commits cannot be nested.");
        }
        int physicalAge = actor.tickCount;
        actor.tickCount = CombatSimulationRuntime.entityTick(actor);
        COLLISION.set(new CollisionCommit(actor, result));
        try {
            if (actor.hasEffect(MobEffects.SLOW_FALLING) || actor.hasEffect(MobEffects.LEVITATION)) {
                actor.resetFallDistance();
            }
            if (input.travel().mode() != WorkerPhysics.Mode.NONE) {
                actor.travel(vec3(input.travel().input()));
            }
        } finally {
            COLLISION.remove();
            actor.tickCount = physicalAge;
        }
        CombatSimulationRuntime.changed(actor);
    }

    public static Vec3 collision(Entity entity, Vec3 requested) {
        CollisionCommit commit = COLLISION.get();
        if (commit == null || commit.actor != entity) {
            return null;
        }
        if (requested.distanceToSqr(vec3(commit.result.collisionInput())) > 1.0e-18) {
            throw new IllegalStateException("Native combat travel diverged from its copied worker input.");
        }
        return vec3(commit.result.movement());
    }

    public static void finish(Mob actor) {
        checkServerThread(actor);
        WorkerPhysics.Input input = PREPARED.remove(actor);
        if (input == null) {
            throw new IllegalStateException("Combat actor has no prepared logical step.");
        }
        int physicalAge = actor.tickCount;
        actor.tickCount = CombatSimulationRuntime.entityTick(actor);
        try {
            ((LivingEntitySimulationAccess) actor).semiontd$finishLivingAi(aabb(input.box()));
            ((MobSimulationAccess) actor).semiontd$finishMobAi();
            if (actor instanceof SemionTowerEntity tower) {
                tower.finishSimulationStep();
            } else if (actor instanceof SemionMonsterEntity monster) {
                monster.finishSimulationStep();
            } else if (actor instanceof SemionBossEntity boss) {
                boss.finishSimulationStep();
            }
            ((LivingEntitySimulationAccess) actor).semiontd$finishLivingTick();
            ((MobSimulationAccess) actor).semiontd$finishMobTick();
        } finally {
            actor.tickCount = physicalAge;
        }
        CombatSimulationRuntime.changed(actor);
    }

    public static void abort(Mob actor) {
        checkServerThread(actor);
        PREPARED.remove(actor);
    }

    public static void present(Entity entity) {
        capturePresentation(entity).run();
    }

    public static Runnable capturePresentation(Entity entity) {
        checkServerThread(entity);
        Thread serverThread = Thread.currentThread();
        Runnable presentation;
        if (entity instanceof SemionTowerEntity tower) {
            presentation = tower::presentSimulationFrame;
        } else if (entity instanceof SemionMonsterEntity monster) {
            presentation = monster.captureSimulationFrame();
        } else {
            presentation = () -> { };
        }
        return () -> {
            if (Thread.currentThread() != serverThread) {
                throw new IllegalStateException("Combat presentation requires the capturing server thread.");
            }
            presentation.run();
        };
    }

    private static WorkerPhysics.Input snapshot(Mob actor) {
        LivingEntitySimulationAccessor access = (LivingEntitySimulationAccessor) actor;
        WorkerPhysics.Mode mode;
        float acceleration;
        boolean climbable = actor.onClimbable();
        if (!actor.canSimulateMovement() || !actor.isEffectiveAi()) {
            mode = WorkerPhysics.Mode.NONE;
            acceleration = 0.0F;
        } else if (access.semiontd$shouldTravelInFluid(actor.level().getFluidState(actor.blockPosition()))) {
            mode = actor.isInWater() ? WorkerPhysics.Mode.WATER : WorkerPhysics.Mode.LAVA;
            acceleration = 0.02F;
            if (mode == WorkerPhysics.Mode.WATER) {
                float efficiency = (float) actor.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
                if (!actor.onGround()) {
                    efficiency *= 0.5F;
                }
                if (efficiency > 0.0F) {
                    acceleration += (actor.getSpeed() - acceleration) * efficiency;
                }
            }
        } else if (actor.isFallFlying() && !climbable) {
            mode = WorkerPhysics.Mode.GLIDING;
            acceleration = 0.0F;
        } else {
            mode = WorkerPhysics.Mode.AIR;
            float blockFriction = actor.onGround() ? Math.clamp(1.0F
                    - (1.0F - actor.level().getBlockState(actor.getBlockPosBelowThatAffectsMyMovement())
                    .getBlock().getFriction()) * (float) actor.getAttributeValue(Attributes.FRICTION_MODIFIER), 0.0F, 1.0F) : 1.0F;
            acceleration = actor.onGround() ? blockFriction > 0.6
                    ? actor.getSpeed() * (0.21600002F / (blockFriction * blockFriction * blockFriction))
                    : actor.getSpeed() : access.semiontd$flyingSpeed();
        }
        float yaw = actor.getYRot() * (float) (Math.PI / 180.0);
        float lean = actor.getXRot() * (float) (Math.PI / 180.0);
        double gravity = actor.getGravity();
        if (actor.getDeltaMovement().y <= 0.0 && actor.hasEffect(MobEffects.SLOW_FALLING)) {
            gravity = Math.min(gravity, 0.01);
        }
        WorkerPhysics.Travel travel = new WorkerPhysics.Travel(mode, vector(actor.getDeltaMovement()),
                new WorkerPhysics.Vector(actor.xxa, actor.yya, actor.zza), acceleration, Mth.sin(yaw), Mth.cos(yaw),
                climbable, gravity, vector(actor.getLookAngle()), lean, Mth.sin(lean));
        Vec3 stuck = ((EntitySimulationAccessor) actor).semiontd$stuckSpeedMultiplier();
        WorkerPhysics.Vector movement = WorkerPhysics.travelMovement(travel);
        if (!actor.noPhysics && stuck.lengthSqr() > 1.0e-7) {
            movement = movement.multiply(stuck.x, stuck.y, stuck.z);
        }
        AABB currentBox = actor.getBoundingBox();
        float stepHeight = actor.maxUpStep();
        AABB query = currentBox.expandTowards(vec3(movement)).expandTowards(0.0, stepHeight, 0.0)
                .expandTowards(0.0, -1.0e-5F, 0.0);
        List<WorkerPhysics.Collider> colliders = new ArrayList<>();
        if (!actor.noPhysics && mode != WorkerPhysics.Mode.NONE) {
            for (VoxelShape shape : actor.level().getEntityCollisions(actor, query)) {
                colliders.add(collider(WorkerPhysics.ColliderKind.ENTITY, shape));
            }
            for (VoxelShape shape : actor.level().getBlockCollisions(actor, query)) {
                colliders.add(collider(WorkerPhysics.ColliderKind.BLOCK, shape));
            }
            if (actor.level().getWorldBorder().isInsideCloseToBorder(actor, query)) {
                colliders.add(collider(WorkerPhysics.ColliderKind.BORDER,
                        actor.level().getWorldBorder().getCollisionShape()));
            }
        }
        return new WorkerPhysics.Input(travel, box(currentBox), actor.onGround(), actor.noPhysics, stepHeight,
                vector(stuck), colliders);
    }

    private static WorkerPhysics.Input inactiveInput(Mob actor) {
        WorkerPhysics.Travel travel = new WorkerPhysics.Travel(WorkerPhysics.Mode.NONE, vector(actor.getDeltaMovement()),
                WorkerPhysics.Vector.ZERO, 0, 0, 1, false, 0, WorkerPhysics.Vector.ZERO, 0, 0);
        return new WorkerPhysics.Input(travel, box(actor.getBoundingBox()), actor.onGround(), actor.noPhysics, 0,
                WorkerPhysics.Vector.ZERO, List.of());
    }

    private static WorkerPhysics.Collider collider(WorkerPhysics.ColliderKind kind, VoxelShape shape) {
        List<WorkerPhysics.Box> boxes = shape.toAabbs().stream().map(EntitySimulationBridge::box).toList();
        List<Double> heights = new ArrayList<>();
        for (double height : shape.getCoords(Direction.Axis.Y)) {
            heights.add(height);
        }
        return new WorkerPhysics.Collider(kind, boxes, heights);
    }

    private static void checkServerThread(Entity actor) {
        if (!(actor.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            throw new IllegalStateException("Combat entity adaptation requires the server thread.");
        }
    }

    private static WorkerPhysics.Vector vector(Vec3 vector) {
        return new WorkerPhysics.Vector(vector.x, vector.y, vector.z);
    }

    private static Vec3 vec3(WorkerPhysics.Vector vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private static WorkerPhysics.Box box(AABB box) {
        return new WorkerPhysics.Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static AABB aabb(WorkerPhysics.Box box) {
        return new AABB(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private static final class Capture {
        private final Mob actor;
        private WorkerPhysics.Input input;

        private Capture(Mob actor) {
            this.actor = actor;
        }
    }

    private record CollisionCommit(Mob actor, WorkerPhysics.Result result) {
    }
}
