package kim.biryeong.semiontd.game.replay.mixin;

import java.util.UUID;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.game.replay.ReplayLogicalMonsterAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Monster.class)
public abstract class ReplayMonsterIdentityMixin implements ReplayLogicalMonsterAccess {
    @Shadow @Final @Mutable
    private UUID logicalId;

    @Override
    public void replay$logicalId(UUID id) {
        logicalId = id;
    }
}
