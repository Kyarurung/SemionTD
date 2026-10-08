package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntitySimulationAccessor {
    @Accessor("stuckSpeedMultiplier")
    Vec3 semiontd$stuckSpeedMultiplier();

    @Invoker("updateFluidInteraction")
    boolean semiontd$updateFluidInteraction();
}
