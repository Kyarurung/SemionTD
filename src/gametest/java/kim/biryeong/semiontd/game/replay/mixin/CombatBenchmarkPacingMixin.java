package kim.biryeong.semiontd.game.replay.mixin;

import java.util.function.BooleanSupplier;
import kim.biryeong.semiontd.game.replay.benchmark.CombatBenchmarkHooks;
import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestServer.class)
abstract class CombatBenchmarkPacingMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private void semiontd$benchmarkFrameStart(BooleanSupplier haveTime, CallbackInfo callback) {
        CombatBenchmarkHooks.beforeFrame((GameTestServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void semiontd$benchmarkFrameEnd(BooleanSupplier haveTime, CallbackInfo callback) {
        CombatBenchmarkHooks.afterFrame((GameTestServer) (Object) this);
    }

    @Inject(method = "waitUntilNextTick", at = @At("HEAD"), cancellable = true)
    private void semiontd$benchmarkNativeWait(CallbackInfo callback) {
        if (CombatBenchmarkHooks.enabled()) {
            CombatBenchmarkHooks.waitUntilNextTick((GameTestServer) (Object) this);
            callback.cancel();
        }
    }
}
