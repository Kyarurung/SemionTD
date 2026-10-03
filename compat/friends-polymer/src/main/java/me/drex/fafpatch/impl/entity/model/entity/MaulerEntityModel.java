package me.drex.fafpatch.impl.entity.model.entity;

import com.faboslav.friendsandfoes.common.entity.MaulerEntity;
import com.faboslav.friendsandfoes.common.entity.animation.MaulerAnimations;
import me.drex.fafpatch.impl.entity.model.animation.KeyframeModelAnimator;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.*;

public final class MaulerEntityModel extends EntityModel<MaulerEntity> {
    private static final String MODEL_PART_HEAD = "head";
    private static final String MODEL_PART_UPPER_JAW = "upperJaw";
    private static final String MODEL_PART_LOWER_JAW = "lowerJaw";
    private static final String MODEL_PART_BODY = "body";
    private static final String MODEL_PART_FRONT_LEFT_LEG = "frontLeftLeg";
    private static final String MODEL_PART_FRONT_RIGHT_LEG = "frontRightLeg";
    private static final String MODEL_PART_BACK_LEFT_LEG = "backLeftLeg";
    private static final String MODEL_PART_BACK_RIGHT_LEG = "backRightLeg";

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart upperJaw;
    private final ModelPart lowerJaw;
    private final ModelPart body;
    private final ModelPart frontLeftLeg;
    private final ModelPart frontRightLeg;
    private final ModelPart backLeftLeg;
    private final ModelPart backRightLeg;

    public MaulerEntityModel(ModelPart root) {
        super(root);

        this.root = root;
        this.head = this.root.getChild(MODEL_PART_HEAD);
        this.upperJaw = this.head.getChild(MODEL_PART_UPPER_JAW);
        this.lowerJaw = this.head.getChild(MODEL_PART_LOWER_JAW);
        this.body = this.root.getChild(MODEL_PART_BODY);
        this.frontLeftLeg = this.root.getChild(MODEL_PART_FRONT_LEFT_LEG);
        this.frontRightLeg = this.root.getChild(MODEL_PART_FRONT_RIGHT_LEG);
        this.backLeftLeg = this.root.getChild(MODEL_PART_BACK_LEFT_LEG);
        this.backRightLeg = this.root.getChild(MODEL_PART_BACK_RIGHT_LEG);
    }

    public static LayerDefinition getTexturedModelData() {
        MeshDefinition modelData = new MeshDefinition();
        PartDefinition root = modelData.getRoot();

        root.addOrReplaceChild(MODEL_PART_HEAD, CubeListBuilder.create(), PartPose.offset(0.0F, 20.0F, 4.0F));

        PartDefinition head = root.getChild(MODEL_PART_HEAD);
        head.addOrReplaceChild(MODEL_PART_UPPER_JAW, CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -3.0F, -10.0F, 9.0F, 3.0F, 10.0F), PartPose.offset(0.0F, -2.0F, 1.0F));
        head.addOrReplaceChild(MODEL_PART_LOWER_JAW, CubeListBuilder.create().texOffs(0, 13).addBox(-4.5F, 0.0F, -10.0F, 9.0F, 3.0F, 10.0F), PartPose.offset(0.0F, -2.0F, 1.0F));

        root.addOrReplaceChild(MODEL_PART_BODY, CubeListBuilder.create().texOffs(0, 26).addBox(-3.5F, 0.0F, -3.0F, 7.0F, 2.0F, 6.0F, new CubeDeformation(0.01F)), PartPose.offset(0.0F, 20.0F, 1.0F));
        root.addOrReplaceChild(MODEL_PART_FRONT_LEFT_LEG, CubeListBuilder.create().texOffs(0, 5).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(2.5F, 21.0F, -1.0F));
        root.addOrReplaceChild(MODEL_PART_FRONT_RIGHT_LEG, CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(-2.5F, 21.0F, -1.0F));
        root.addOrReplaceChild(MODEL_PART_BACK_LEFT_LEG, CubeListBuilder.create().texOffs(0, 18).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(2.5F, 21.0F, 3.0F));
        root.addOrReplaceChild(MODEL_PART_BACK_RIGHT_LEG, CubeListBuilder.create().texOffs(0, 13).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(-2.5F, 21.0F, 3.0F));

        return LayerDefinition.create(modelData, 64, 64);
    }

    @Override
    public void setupAnim(MaulerEntity mauler) {
        float limbAngle = mauler.walkAnimation.position(1);
        float limbDistance = mauler.walkAnimation.speed(1);
        var animationProgress = mauler.tickCount;

        this.root().getAllParts().forEach(ModelPart::resetPose);
        this.updateKeyframeAnimations(mauler, limbAngle, limbDistance, animationProgress);
    }

    public void updateKeyframeAnimations(
        MaulerEntity mauler,
        float limbAngle,
        float limbDistance,
        float animationProgress
    ) {
        var movementAnimation = mauler.getMovementAnimation();
        var animations = mauler.getTrackedAnimations();
        var animationContextTracker = mauler.getAnimationContextTracker();
        var currentTick = mauler.tickCount;
        var animationSpeedModifier = mauler.getAnimationSpeedModifier();

        KeyframeModelAnimator.updateMovementKeyframeAnimations(this, movementAnimation, limbAngle, limbDistance, 2.5F, 3.5F, animationSpeedModifier);

        if(mauler.isAngry()) {
            KeyframeModelAnimator.updateStaticKeyframeAnimation(this, animationContextTracker, MaulerAnimations.SNAP, currentTick, animationProgress, animationSpeedModifier);
        }

        KeyframeModelAnimator.updateKeyframeAnimations(this, animationContextTracker, animations, currentTick, animationProgress, animationSpeedModifier);
    }
}
