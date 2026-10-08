package kim.biryeong.semiontd.game.simulation;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;
import java.util.function.Predicate;
import kim.biryeong.semiontd.entity.visual.SemionAnimationState;
import kim.biryeong.semiontd.mixin.accessor.CombatSimulationServerLevelAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class CombatSimulationRuntime {
    private static final Map<ServerLevel, Owner> OWNERS = new IdentityHashMap<>();
    private static final Map<ServerLevel, CombatSimulationSpatialIndex> INDICES = new IdentityHashMap<>();
    private static final ThreadLocal<Owner> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> NATIVE_ACCESS = ThreadLocal.withInitial(() -> false);

    private CombatSimulationRuntime() {
    }

    static void register(ServerLevel world, Owner owner) {
        Owner previous = OWNERS.get(world);
        if (previous != null && previous != owner) {
            throw new IllegalStateException("An arena already has a combat simulation owner.");
        }
        INDICES.computeIfAbsent(world, CombatSimulationSpatialIndex::new);
        OWNERS.put(world, owner);
    }

    static void unregister(Owner owner) {
        INDICES.keySet().removeIf(world -> OWNERS.get(world) == owner);
        OWNERS.values().removeIf(value -> value == owner);
    }

    public static boolean controls(Level world) {
        return world instanceof ServerLevel serverWorld && OWNERS.containsKey(serverWorld);
    }

    public static boolean controls(Entity entity) {
        Owner owner = owner(entity.level());
        return owner != null && owner.controls(entity);
    }

    public static boolean stepping(Entity entity) {
        Owner owner = ACTIVE.get();
        return owner != null && owner == owner(entity.level()) && owner.controls(entity);
    }

    public static int entityTick(Entity entity) {
        Owner owner = owner(entity.level());
        return owner == null || !owner.controls(entity) ? entity.tickCount : owner.entityTick(entity);
    }

    public static Long gameTime(ServerLevel world) {
        if (NATIVE_ACCESS.get()) {
            return null;
        }
        Owner owner = OWNERS.get(world);
        return owner == null ? null : owner.gameTime(world);
    }

    public static EntityView view(Entity entity) {
        if (NATIVE_ACCESS.get()) {
            return null;
        }
        Owner owner = ACTIVE.get();
        EntityView view = owner != null && owner == owner(entity.level()) ? owner.view(entity) : null;
        if (view != null && entity.level() instanceof ServerLevel world) {
            view.indexedEntity = entity;
            view.spatialIndex = INDICES.get(world);
        }
        return view;
    }

    public static List<Entity> entities(ServerLevel world, Entity excluded, AABB box,
            Predicate<? super Entity> predicate) {
        List<Entity> result = INDICES.get(world).query(excluded, box, predicate);
        for (var part : world.dragonParts()) {
            if (part != excluded && part.parentMob != excluded && part.getBoundingBox().intersects(box)
                    && predicate.test(part)) {
                result.add(part);
            }
        }
        return result;
    }

    public static <T extends Entity> void entities(ServerLevel world, EntityTypeTest<Entity, T> type,
            AABB box, Predicate<? super T> predicate, List<? super T> result, int maximum) {
        INDICES.get(world).query(type, box, predicate, result, maximum);
    }

    public static void added(Entity entity) {
        if (entity.level() instanceof ServerLevel world) {
            CombatSimulationSpatialIndex index = INDICES.get(world);
            if (index != null) {
                index.add(entity, entity.position());
            }
        }
    }

    public static void removed(Entity entity) {
        if (entity.level() instanceof ServerLevel world) {
            CombatSimulationSpatialIndex index = INDICES.get(world);
            if (index != null) {
                index.remove(entity);
            }
        }
    }

    public static void positionChanged(Entity entity) {
        if (!NATIVE_ACCESS.get() && entity.level() instanceof ServerLevel world) {
            CombatSimulationSpatialIndex index = INDICES.get(world);
            if (index != null) {
                index.move(entity, entity.position());
            }
        }
    }

    public static void chunkVisibility(Object manager, ChunkPos chunk) {
        for (var entry : INDICES.entrySet()) {
            if (((CombatSimulationServerLevelAccessor) entry.getKey()).semiontd$entityManager() == manager) {
                entry.getValue().visibility(chunk);
            }
        }
    }

    public static boolean usesView(Level world) {
        return !NATIVE_ACCESS.get() && ACTIVE.get() != null && ACTIVE.get() == owner(world);
    }

    public static Owner activeOwner(Level world) {
        return usesView(world) ? ACTIVE.get() : null;
    }

    public static boolean active(Owner owner) {
        return ACTIVE.get() == owner;
    }

    public static void changed(Entity entity) {
        Owner owner = owner(entity.level());
        if (owner != null) {
            owner.changed(entity);
        }
    }

    public static void animate(Entity entity, SemionAnimationState animation, Runnable presentation) {
        Owner owner = ACTIVE.get();
        if (owner != null && owner == owner(entity.level())) {
            owner.animate(entity, animation, presentation);
        } else {
            presentation.run();
        }
    }

    public static boolean input(ServerLevel world, Runnable input) {
        Owner owner = OWNERS.get(world);
        if (owner == null || ACTIVE.get() == owner) {
            return false;
        }
        owner.input(input);
        return true;
    }

    static void run(Owner owner, Runnable action) {
        Owner previous = ACTIVE.get();
        ACTIVE.set(owner);
        try {
            action.run();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    public static void nativeAccess(Runnable action) {
        boolean previous = NATIVE_ACCESS.get();
        NATIVE_ACCESS.set(true);
        try {
            action.run();
        } finally {
            NATIVE_ACCESS.set(previous);
        }
    }

    public static long nativeGameTime(ServerLevel world) {
        long[] result = new long[1];
        nativeAccess(() -> result[0] = world.getGameTime());
        return result[0];
    }

    private static Owner owner(Level world) {
        return world instanceof ServerLevel serverWorld ? OWNERS.get(serverWorld) : null;
    }

    public interface Owner {
        boolean controls(Entity entity);
        int entityTick(Entity entity);
        long gameTime(ServerLevel world);
        EntityView view(Entity entity);
        void changed(Entity entity);
        void animate(Entity entity, SemionAnimationState animation, Runnable presentation);
        void input(Runnable input);
        Iterable<Entity> entities(ServerLevel world);
    }

    public static final class EntityView {
        private Vec3 position;
        private AABB box;
        private Vec3 velocity;
        private boolean onGround;
        private long revision;
        private int age;
        private Entity indexedEntity;
        private CombatSimulationSpatialIndex spatialIndex;

        public EntityView(Vec3 position, AABB box, Vec3 velocity, boolean onGround, int age) {
            this.position = Objects.requireNonNull(position);
            this.box = Objects.requireNonNull(box);
            this.velocity = Objects.requireNonNull(velocity);
            this.onGround = onGround;
            this.age = age;
        }

        public static EntityView capture(Entity entity) {
            EntityView[] result = new EntityView[1];
            nativeAccess(() -> result[0] = new EntityView(entity.position(), entity.getBoundingBox(),
                    entity.getDeltaMovement(), entity.onGround(), entity.tickCount));
            return result[0];
        }

        public Vec3 position() { return position; }
        public AABB box() { return box; }
        public Vec3 velocity() { return velocity; }
        public boolean onGround() { return onGround; }
        public long revision() { return revision; }
        public int age() { return age; }
        public void age(int age) { this.age = age; }
        public void changed() { revision++; }

        public void position(Vec3 position) {
            box = box.move(position.subtract(this.position));
            this.position = position;
            changed();
            if (spatialIndex != null) {
                spatialIndex.move(indexedEntity, position);
            }
        }

        public void box(AABB box) {
            this.box = Objects.requireNonNull(box);
            changed();
        }

        public void velocity(Vec3 velocity) {
            this.velocity = Objects.requireNonNull(velocity);
            changed();
        }

        public void onGround(boolean onGround) {
            this.onGround = onGround;
            changed();
        }

        public void publish(Entity entity) {
            nativeAccess(() -> {
                entity.setPos(position);
                entity.setBoundingBox(box);
                entity.setDeltaMovement(velocity);
                entity.setOnGround(onGround);
            });
        }
    }
}
