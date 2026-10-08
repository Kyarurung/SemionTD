package kim.biryeong.semiontd.mixin;

import kim.biryeong.semiontd.game.simulation.CombatSimulationRuntime;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PersistentEntitySectionManager.class)
abstract class CombatSimulationEntityManagerMixin {
    @Inject(method = "updateChunkStatus(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/entity/Visibility;)V",
            at = @At("RETURN"))
    private void semiontd$visibilityChanged(ChunkPos chunk, Visibility visibility, CallbackInfo callback) {
        CombatSimulationRuntime.chunkVisibility(this, chunk);
    }
}
