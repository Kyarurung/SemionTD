package me.drex.fafpatch.mixin.mod;

import com.faboslav.friendsandfoes.common.network.packet.TotemEffectPacket;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TotemEffectPacket.class)
public abstract class TotemEffectPacketMixin {
    @WrapMethod(method = "sendToClient")
    private static void vanillaTotemAnimation(Player player, Item totem, Operation<Void> original) {
        player.level().broadcastEntityEvent(player, EntityEvent.PROTECTED_FROM_DEATH);
    }
}
