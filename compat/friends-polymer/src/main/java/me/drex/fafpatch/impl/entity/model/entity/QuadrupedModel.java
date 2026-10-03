package me.drex.fafpatch.impl.entity.model.entity;

import eu.pb4.factorytools.api.virtualentity.emuvanilla.EntityValueExtraction;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public class QuadrupedModel<T extends LivingEntity> extends EntityModel<T> {
    protected final ModelPart head;
    protected final ModelPart body;
    protected final ModelPart rightHindLeg;
    protected final ModelPart leftHindLeg;
    protected final ModelPart rightFrontLeg;
    protected final ModelPart leftFrontLeg;

    protected QuadrupedModel(ModelPart modelPart) {
        super(modelPart);
        this.head = modelPart.getChild("head");
        this.body = modelPart.getChild("body");
        this.rightHindLeg = modelPart.getChild("right_hind_leg");
        this.leftHindLeg = modelPart.getChild("left_hind_leg");
        this.rightFrontLeg = modelPart.getChild("right_front_leg");
        this.leftFrontLeg = modelPart.getChild("left_front_leg");
    }

    public static MeshDefinition createBodyMesh(int i, boolean bl, boolean bl2, CubeDeformation cubeDeformation) {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        partDefinition.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F, cubeDeformation), PartPose.offset(0.0F, 18 - i, -6.0F)
        );
        partDefinition.addOrReplaceChild(
            "body",
            CubeListBuilder.create().texOffs(28, 8).addBox(-5.0F, -10.0F, -7.0F, 10.0F, 16.0F, 8.0F, cubeDeformation),
            PartPose.offsetAndRotation(0.0F, 17 - i, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
        );
        createLegs(partDefinition, bl, bl2, i, cubeDeformation);
        return meshDefinition;
    }

    static void createLegs(PartDefinition partDefinition, boolean bl, boolean bl2, int i, CubeDeformation cubeDeformation) {
        CubeListBuilder cubeListBuilder = CubeListBuilder.create().mirror(bl2).texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, (float)i, 4.0F, cubeDeformation);
        CubeListBuilder cubeListBuilder2 = CubeListBuilder.create().mirror(bl).texOffs(0, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, (float)i, 4.0F, cubeDeformation);
        partDefinition.addOrReplaceChild("right_hind_leg", cubeListBuilder, PartPose.offset(-3.0F, 24 - i, 7.0F));
        partDefinition.addOrReplaceChild("left_hind_leg", cubeListBuilder2, PartPose.offset(3.0F, 24 - i, 7.0F));
        partDefinition.addOrReplaceChild("right_front_leg", cubeListBuilder, PartPose.offset(-3.0F, 24 - i, -5.0F));
        partDefinition.addOrReplaceChild("left_front_leg", cubeListBuilder2, PartPose.offset(3.0F, 24 - i, -5.0F));
    }

    public void setupAnim(T livingEntity) {
        super.setupAnim(livingEntity);
        this.head.yRot = EntityValueExtraction.getRelativeHeadYaw(livingEntity) * ((float)Math.PI / 180);
        this.head.xRot = livingEntity.getXRot() * ((float)Math.PI / 180);
        float pos = livingEntity.walkAnimation.position(1);
        float speed = livingEntity.walkAnimation.speed(1);
        this.rightHindLeg.xRot = Mth.cos(pos * 0.6662F) * 1.4F * speed;
        this.leftHindLeg.xRot = Mth.cos(pos * 0.6662F + (float) Math.PI) * 1.4F * speed;
        this.rightFrontLeg.xRot = Mth.cos(pos * 0.6662F + (float) Math.PI) * 1.4F * speed;
        this.leftFrontLeg.xRot = Mth.cos(pos * 0.6662F) * 1.4F * speed;
    }
}
