package kim.biryeong.semiontd.mixin.accessor;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntitySimulationAccessor {
    @Accessor("swingState")
    LivingEntity.SwingState semiontd$swingState();

    @Invoker("shouldTravelInFluid")
    boolean semiontd$shouldTravelInFluid(FluidState fluidState);

    @Invoker("getFlyingSpeed")
    float semiontd$flyingSpeed();

}
