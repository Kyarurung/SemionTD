package kim.biryeong.semiontd.entity.model;

import de.tomalbrc.bil.core.holder.positioned.PositionedHolder;
import de.tomalbrc.bil.core.holder.wrapper.DisplayWrapper;
import de.tomalbrc.bil.core.model.Animation;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.core.model.Pose;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import kim.biryeong.semiontd.util.Scheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 죽은 몬스터 자리에 남기는 모델 껍데기. 사망 애니메이션을 한 번 틀고 끝나면 스스로 사라집니다.
 *
 * <p>몬스터 엔티티는 죽는 즉시 지워야 합니다(타워 조준·레인 집계 등 여러 곳이 엔티티를 직접 찾습니다). 그래서 엔티티는
 * 그대로 지우고, 같은 자리·방향·크기로 모델만 띄워 {@value #DEATH_ANIMATION} 애니메이션을 보여 줍니다. 판정은 없습니다.
 */
public final class BilDeathVisual {
    public static final String DEATH_ANIMATION = "death";
    /** 사망 애니메이션이 끝난 뒤 마지막 자세로 남아 있는 시간(틱). */
    public static final int LINGER_TICKS = 20;

    private BilDeathVisual() {
    }

    /** 모델에 사망 애니메이션이 있으면 띄우고 {@code true}입니다. */
    public static boolean spawn(ServerLevel level, Vec3 position, float yaw, Model model, float scale) {
        if (level == null || model == null || position == null) {
            return false;
        }
        Animation death = model.animations().get(DEATH_ANIMATION);
        if (death == null) {
            return false;
        }
        Corpse corpse = new Corpse(level, position, model, yaw);
        corpse.setScale(scale);
        ChunkAttachment.ofTicking(corpse, level, position);
        corpse.getAnimator().playAnimation(DEATH_ANIMATION, 10, true);
        // 홀더 틱 도중에 지우면 BIL이 떨어진 부착을 따라가다 멈추므로, 서버 예약 작업으로 틱 밖에서 지웁니다.
        Scheduler.INSTANCE.submit(server -> {
            corpse.destroy();
        }, death.duration() + LINGER_TICKS);
        return true;
    }

    /** 몸 방향을 고정한 위치 홀더. BIL 생명체 홀더처럼 각 뼈 디스플레이의 yaw로 방향을 줍니다. */
    private static final class Corpse extends PositionedHolder {
        private final float yaw;

        private Corpse(ServerLevel level, Vec3 position, Model model, float yaw) {
            super(level, position, model);
            this.yaw = yaw;
        }

        @Override
        public void updateElement(ServerPlayer player, DisplayWrapper<?> display, Pose pose) {
            display.element().setYaw(yaw);
            super.updateElement(player, display, pose);
        }
    }
}
