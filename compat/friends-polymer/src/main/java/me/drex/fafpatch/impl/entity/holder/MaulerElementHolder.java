package me.drex.fafpatch.impl.entity.holder;

import com.faboslav.friendsandfoes.common.entity.MaulerEntity;
import me.drex.fafpatch.impl.entity.SimpleElementHolder;
import me.drex.fafpatch.impl.entity.model.EntityModels;
import me.drex.fafpatch.impl.entity.model.entity.MaulerEntityModel;

public class MaulerElementHolder extends SimpleElementHolder<MaulerEntity, MaulerEntityModel> {
    public MaulerElementHolder(MaulerEntity entity) {
        super(entity);
        addConditionalLayer(MaulerEntity::getMaulerType, MAIN_LAYER, EntityModels.MAULER::get);
    }
}
