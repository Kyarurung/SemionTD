package me.drex.fafpatch.impl.entity.model.animation;

import com.faboslav.friendsandfoes.common.entity.animation.AnimationDefinition;
import com.faboslav.friendsandfoes.common.entity.animation.AnimationState;
import com.faboslav.friendsandfoes.common.entity.animation.animator.context.AnimationContextTracker;
import com.faboslav.friendsandfoes.common.entity.animation.animator.context.KeyframeAnimationContext;
import com.faboslav.friendsandfoes.common.entity.animation.animator.loader.json.AnimationHolder;
import me.drex.fafpatch.impl.entity.animation.animator.KeyframeAnimator;
import eu.pb4.factorytools.api.virtualentity.emuvanilla.model.EntityModel;
import org.joml.Vector3f;

import java.util.ArrayList;

public final class KeyframeModelAnimator
{
	private static final Vector3f TEMP = new Vector3f();

	public static void updateKeyframeAnimations(
		EntityModel<?> model,
		AnimationContextTracker animationContextTracker,
		ArrayList<AnimationHolder> animations,
		int currentTick,
		float animationProgress,
		float speedModifier
	) {
		animations.forEach((keyframeAnimation -> {
			updateKeyframeAnimation(model, animationContextTracker, keyframeAnimation, currentTick, animationProgress, speedModifier);
		}));
	}

	public static void updateMovementKeyframeAnimations(
		EntityModel<?> model,
		AnimationHolder movementAnimation,
		float limbAngle,
		float limbDistance,
		float limbAngleScale,
		float limbDistanceScale,
		float speedModifier
	) {
		long runningTime = (long) (limbAngle * 50.0F * limbAngleScale);
		float scale = Math.min(limbDistance * limbDistanceScale, 1.0F);
		updateMovementKeyframeAnimation(model, movementAnimation, runningTime, scale, speedModifier);
	}

	public static void updateStaticKeyframeAnimation(
		EntityModel<?> model,
		AnimationContextTracker animationContextTracker,
		AnimationHolder animationHolder,
		int currentTick,
		float animationProgress,
		float speedModifier
	) {
		var animation = animationHolder.get();

		KeyframeAnimationContext keyframeAnimationContext = animationContextTracker.get(animationHolder);
		keyframeAnimationContext.setCurrentTick(currentTick);
		AnimationState animationState = keyframeAnimationContext.getAnimationState();

		if(!animationState.isStarted()) {
			animationState.start(currentTick);
		}

		animationState.updateTime(animationProgress, 1.0F);
		KeyframeAnimator.animateKeyframe(model, animation, animationState.getAccumulatedTime(), 1.0F, TEMP, speedModifier);
	}

	public static void updateKeyframeAnimation(
		EntityModel<?> model,
		AnimationContextTracker animationContextTracker,
		AnimationHolder animationHolder,
		int currentTick,
		float animationProgress,
		float speedModifier
	) {
		var animation = animationHolder.get();

		KeyframeAnimationContext keyframeAnimationContext = animationContextTracker.get(animationHolder);
		keyframeAnimationContext.setCurrentTick(currentTick);
		AnimationState animationState = keyframeAnimationContext.getAnimationState();

		animationState.ifStarted((state) -> {
			state.updateTime(animationProgress, 1.0F);
			KeyframeAnimator.animateKeyframe(model, animation, state.getAccumulatedTime(), 1.0F, TEMP, speedModifier);
		});
	}

	public static void updateMovementKeyframeAnimation(
		EntityModel<?> model,
		AnimationHolder movementAnimation,
		long runningTime,
		float scale,
		float speedModifier
	) {
		AnimationDefinition animation = movementAnimation.get();
		KeyframeAnimator.animateKeyframe(model, animation, runningTime, scale, TEMP, speedModifier);
	}
}