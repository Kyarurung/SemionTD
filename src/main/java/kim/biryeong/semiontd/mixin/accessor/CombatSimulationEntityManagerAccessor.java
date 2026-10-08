package kim.biryeong.semiontd.mixin.accessor;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PersistentEntitySectionManager.class)
public interface CombatSimulationEntityManagerAccessor {
    @Accessor("sectionStorage")
    EntitySectionStorage<Entity> semiontd$sectionStorage();

    @Accessor("chunkVisibility")
    Long2ObjectMap<Visibility> semiontd$chunkVisibility();
}
