package me.drex.fafpatch.impl.entity.holder;

import com.faboslav.friendsandfoes.common.entity.CrabEntity;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import me.drex.fafpatch.impl.entity.model.entity.CrabEntityModel;

public class CrabElementHolder extends SimpleElementHolder<CrabEntity, CrabEntityModel> {
    public CrabElementHolder(CrabEntity entity) {
        super(entity);
    }

    @Override
    public float getEntityScale() {
        return entity.isBaby() ? CrabEntity.BABY_SCALE : 1;
    }
}
