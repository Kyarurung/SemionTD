package me.drex.fafpatch.impl.entity.holder;

import com.faboslav.friendsandfoes.common.entity.GlareEntity;
import me.drex.fafpatch.impl.FriendsAndFoesPatch;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import me.drex.fafpatch.impl.entity.model.EntityModels;
import me.drex.fafpatch.impl.entity.model.entity.GlareEntityModel;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;

public class GlareElementHolder extends SimpleElementHolder<GlareEntity, GlareEntityModel> {

    public static final Identifier FLOWERING_LAYER = FriendsAndFoesPatch.id("flowering_layer");

    public GlareElementHolder(GlareEntity entity) {
        super(entity);
        addConditionalLayer(GlareElementHolder::shouldRenderFlowers, FLOWERING_LAYER, flowers -> flowers ? EntityModels.FLOWERING_GLARE : null);
    }

    @Override
    public float getEntityScale() {
        return entity.isBaby() ? GlareEntity.BABY_SCALE : GlareEntity.ADULT_SCALE;
    }

    private static boolean shouldRenderFlowers(GlareEntity entity) {
        String string = ChatFormatting.stripFormatting(entity.getName().getString());

        return "Anna".equals(string)
            || entity.isTame();
    }
}
