package kim.biryeong.semiontd.tower.blueprint;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** 모듈 아이콘. */
final class BlueprintIcons {
    private BlueprintIcons() {
    }

    static Item module(BlueprintModule module) {
        return switch (module) {
            case MULTISHOT -> Items.SPECTRAL_ARROW;
            case SPLASH -> Items.FIRE_CHARGE;
            case CHAIN -> Items.LIGHTNING_ROD;
            case SLOW -> Items.COBWEB;
            case STUN -> Items.ANVIL;
            case POISON -> Items.SPIDER_EYE;
            case VULNERABILITY -> Items.FERMENTED_SPIDER_EYE;
            case CRIT -> Items.DIAMOND_SWORD;
            case EXECUTE -> Items.NETHERITE_AXE;
            case KILL_EXPLOSION -> Items.TNT;
            case LIFESTEAL -> Items.REDSTONE;
            case THORNS -> Items.CACTUS;
            case ARMOR -> Items.SHIELD;
            case REGEN -> Items.GLISTERING_MELON_SLICE;
            case HEAL_AURA -> Items.BEACON;
            case HASTE_AURA -> Items.SUGAR;
        };
    }
}
