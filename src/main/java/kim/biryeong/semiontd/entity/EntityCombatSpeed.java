package kim.biryeong.semiontd.entity;

import kim.biryeong.semiontd.game.CombatSpeedRuntime;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class EntityCombatSpeed {
    private static final Identifier MOVEMENT = Identifier.fromNamespaceAndPath("semion-td", "combat_movement_speed");

    private EntityCombatSpeed() {
    }

    public static void updateMovement(LivingEntity entity) {
        var speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double amount = CombatSpeedRuntime.multiplier(entity.level()) - 1.0;
        var existing = speed.getModifier(MOVEMENT);
        if (Math.abs(amount) < 1.0e-9) {
            if (existing != null) {
                speed.removeModifier(MOVEMENT);
            }
        } else if (existing == null || Double.compare(existing.amount(), amount) != 0) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    MOVEMENT, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
