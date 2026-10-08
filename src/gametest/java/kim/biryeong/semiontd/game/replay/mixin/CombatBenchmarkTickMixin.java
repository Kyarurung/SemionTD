package kim.biryeong.semiontd.game.replay.mixin;

import java.util.function.BooleanSupplier;
import kim.biryeong.semiontd.game.replay.benchmark.CombatBenchmarkHooks;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class CombatBenchmarkTickMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void semiontd$benchmarkNativeTickStart(BooleanSupplier haveTime, CallbackInfo callback) {
        CombatBenchmarkHooks.beforeNativeTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void semiontd$benchmarkNativeTickEnd(BooleanSupplier haveTime, CallbackInfo callback) {
        CombatBenchmarkHooks.afterNativeTick((MinecraftServer) (Object) this);
    }
}
