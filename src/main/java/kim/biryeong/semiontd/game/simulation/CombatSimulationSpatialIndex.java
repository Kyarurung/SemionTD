package kim.biryeong.semiontd.game.simulation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kim.biryeong.semiontd.mixin.accessor.CombatSimulationEntityManagerAccessor;
import kim.biryeong.semiontd.mixin.accessor.CombatSimulationServerLevelAccessor;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Continuation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CombatSimulationSpatialIndex {
    private final CombatSimulationEntityManagerAccessor manager;
    private final EntitySectionStorage<Entity> sections;
    private final Map<Entity, Long> membership = new IdentityHashMap<>();

    CombatSimulationSpatialIndex(ServerLevel world) {
        manager = (CombatSimulationEntityManagerAccessor) ((CombatSimulationServerLevelAccessor) world)
                .semiontd$entityManager();
        sections = new EntitySectionStorage<>(Entity.class, manager.semiontd$chunkVisibility()::get);
        EntitySectionStorage<Entity> nativeSections = manager.semiontd$sectionStorage();
        for (long chunk : nativeSections.getAllChunksWithExistingSections()) {
            nativeSections.getExistingSectionPositionsInChunk(chunk).forEach(key -> {
                EntitySection<Entity> source = nativeSections.getSection(key);
                EntitySection<Entity> target = sections.getOrCreateSection(key);
                target.updateChunkStatus(source.getStatus());
                source.getEntities().forEach(entity -> {
                    target.add(entity);
                    membership.put(entity, key);
                });
            });
        }
    }

    void add(Entity entity, Vec3 position) {
        if (!membership.containsKey(entity)) {
            long section = key(position);
            sections.getOrCreateSection(section).add(entity);
            membership.put(entity, section);
        }
    }

    void move(Entity entity, Vec3 position) {
        Long previous = membership.get(entity);
        if (previous == null) {
            return;
        }
        long section = key(position);
        if (previous != section) {
            remove(entity);
            add(entity, position);
        }
    }

    void remove(Entity entity) {
        Long previous = membership.remove(entity);
        if (previous != null) {
            EntitySection<Entity> section = sections.getSection(previous);
            section.remove(entity);
            if (section.isEmpty()) {
                sections.remove(previous);
            }
        }
    }

    void visibility(ChunkPos chunk) {
        sections.getExistingSectionsInChunk(chunk.pack()).forEach(section ->
                section.updateChunkStatus(manager.semiontd$chunkVisibility().get(chunk.pack())));
    }

    List<Entity> query(Entity excluded, AABB box, Predicate<? super Entity> predicate) {
        List<Entity> result = new ArrayList<>();
        sections.getEntities(box, entity -> {
            if (entity != excluded && !entity.isRemoved() && predicate.test(entity)) {
                result.add(entity);
            }
            return Continuation.CONTINUE;
        });
        return result;
    }

    <T extends Entity> void query(EntityTypeTest<Entity, T> type, AABB box, Predicate<? super T> predicate,
            List<? super T> result, int maximum) {
        if (result.size() >= maximum) {
            return;
        }
        sections.getEntities(type, box, entity -> {
            if (!entity.isRemoved() && predicate.test(entity)) {
                result.add(entity);
            }
            return Continuation.abortIf(result.size() >= maximum);
        });
    }

    private static long key(Vec3 position) {
        return SectionPos.asLong(SectionPos.posToSectionCoord(position.x),
                SectionPos.posToSectionCoord(position.y), SectionPos.posToSectionCoord(position.z));
    }
}
