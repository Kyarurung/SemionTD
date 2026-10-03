package me.drex.fafpatch.impl.entity.model.entity;

import com.faboslav.friendsandfoes.common.entity.IceologerIceChunkEntity;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.*;

public final class IceologerIceChunkModel extends EntityModel<IceologerIceChunkEntity> {
    private static final String MODEL_PART_FIRST_FULL_BLOCK = "firstFullBlock";
    private static final String MODEL_PART_SECOND_FULL_BLOCK = "secondFullBlock";
    private static final String MODEL_PART_THIRD_FULL_BLOCK = "thirdFullBlock";
    private static final String MODEL_PART_FIRST_VERTICAL_SLAB = "firstVerticalSlab";
    private static final String MODEL_PART_SECOND_VERTICAL_SLAB = "secondVerticalSlab";

    private final ModelPart root;
    private final ModelPart firstFullBlock;
    private final ModelPart secondFullBlock;
    private final ModelPart thirdFullBlock;
    private final ModelPart firstVerticalSlab;
    private final ModelPart secondVerticalSlab;

    public IceologerIceChunkModel(ModelPart root) {
        super(root);

        this.root = root;
        this.firstFullBlock = this.root.getChild(MODEL_PART_FIRST_FULL_BLOCK);
        this.secondFullBlock = this.root.getChild(MODEL_PART_SECOND_FULL_BLOCK);
        this.thirdFullBlock = this.root.getChild(MODEL_PART_THIRD_FULL_BLOCK);
        this.firstVerticalSlab = this.root.getChild(MODEL_PART_FIRST_VERTICAL_SLAB);
        this.secondVerticalSlab = this.root.getChild(MODEL_PART_SECOND_VERTICAL_SLAB);
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();

        root.addOrReplaceChild(MODEL_PART_FIRST_FULL_BLOCK, CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, 0.0F, -4.0F, 16.0F, 16.0F, 16.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild(MODEL_PART_SECOND_FULL_BLOCK, CubeListBuilder.create().texOffs(0, 0).addBox(0.0F, 0.0F, -4.0F, 16.0F, 16.0F, 16.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild(MODEL_PART_THIRD_FULL_BLOCK, CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, 0.0F, -20.0F, 16.0F, 16.0F, 16.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild(MODEL_PART_FIRST_VERTICAL_SLAB, CubeListBuilder.create().texOffs(0, 32).addBox(-16.0F, 0.0F, 12.0F, 16.0F, 16.0F, 8.0F), PartPose.offset(0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild(MODEL_PART_SECOND_VERTICAL_SLAB, CubeListBuilder.create().texOffs(0, 32).addBox(-20.0F, 0.0F, -8.0F, 16.0F, 16.0F, 8.0F), PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(modelData, 64, 64);
    }

    @Override
    public void setupAnim(IceologerIceChunkEntity entity) {
        super.setupAnim(entity);
        this.secondVerticalSlab.setRotation(0.0F, -1.5708F, 0.0F);
    }
}

