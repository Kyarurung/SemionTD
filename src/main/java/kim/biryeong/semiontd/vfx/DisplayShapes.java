package kim.biryeong.semiontd.vfx;

import java.util.Random;
import kim.biryeong.semiontd.vfx.DisplayEffect.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * {@link DisplayEffect}에 자주 쓰는 움직임(바닥 문양이 퍼졌다 사라짐, 빛줄기, 튀는 파편, 솟는 가시)을 짜 주는 도우미.
 *
 * <p>모든 좌표는 연출 기준점 기준이고, {@code yaw}는 시전자가 바라보는 방향입니다. 로컬 좌표계는 +Z가 앞,
 * +X가 옆, +Y가 위입니다. {@link #local}로 로컬 좌표를 월드 방향으로 돌립니다.
 */
public final class DisplayShapes {
    /** 땅에 까는 첫 장의 높이(블록). 땅과 z-fighting이 나지 않을 만큼만 띄웁니다. */
    public static final float GROUND_LIFT = 0.005F;
    /** 땅에 겹쳐 까는 장끼리의 높이 차(블록). 위에 까는 장일수록 한 칸씩 올립니다. */
    public static final float GROUND_STEP = 0.005F;

    private final DisplayEffect effect;
    private final float yaw;
    private final Random random;

    public DisplayShapes(DisplayEffect effect, float yaw, long seed) {
        this.effect = effect;
        this.yaw = yaw;
        this.random = new Random(seed);
    }

    public DisplayEffect effect() {
        return effect;
    }

    public float yaw() {
        return yaw;
    }

    /** 바라보는 방향 벡터(수평)로 yaw를 구합니다. +Z가 0입니다. */
    public static float yawOf(double dirX, double dirZ) {
        return (float) Math.atan2(dirX, dirZ);
    }

    public Vector3f local(double x, double y, double z) {
        return new Quaternionf().rotateY(yaw).transform(new Vector3f((float) x, (float) y, (float) z));
    }

    /** 시전 방향 기준 로컬 회전(도 단위). */
    public Quaternionf localRotation(double yawDeg, double pitchDeg, double rollDeg) {
        return new Quaternionf().rotateY(yaw + rad(yawDeg)).rotateX(rad(pitchDeg)).rotateZ(rad(rollDeg));
    }

    public static Quaternionf rotation(double yawRad, double pitchDeg, double rollDeg) {
        return new Quaternionf().rotateY((float) yawRad).rotateX(rad(pitchDeg)).rotateZ(rad(rollDeg));
    }

    /** 땅에 까는 {@code layer}번째 장의 높이. 0이 땅 바로 위이고, 번호가 클수록 위에 겹칩니다. */
    public static float ground(int layer) {
        return GROUND_LIFT + layer * GROUND_STEP;
    }

    public static Vector3f vec(double x, double y, double z) {
        return new Vector3f((float) x, (float) y, (float) z);
    }

    public double random(double min, double max) {
        return min + random.nextDouble() * (max - min);
    }

    /**
     * 바닥 문양(마법진·충격파·균열). 지름 {@code size0}에서 {@code size1}로 커지며 나타나 {@code spinDeg}만큼 돕니다.
     *
     * <p>디스플레이는 투명도를 보간하지 못하므로 크기로 사라지게 합니다. {@code fadeGrowth}가 1 이상이면
     * {@code fadeAt}부터 그 배율까지 더 번진 뒤 한순간에 꺼지고(충격파), 1 미만이면 안쪽으로 오므라들며 사라집니다(마법진).
     */
    public DisplayEffect.Part decal(DisplaySprite sprite, Vector3f centre, double size0, double size1,
            int start, int grow, int fadeAt, int fade, double spinDeg, double fadeGrowth) {
        float turn = rad(random(0, 360));
        Quaternionf from = new Quaternionf().rotateY(turn);
        Quaternionf to = new Quaternionf().rotateY(turn + rad(spinDeg));
        Quaternionf end = new Quaternionf().rotateY(turn + rad(spinDeg * 1.3));
        DisplayEffect.Part part = effect.part(sprite, Pose.of(centre, from, vec(size0, 1, size0)).hidden())
                .to(start, grow, Pose.of(centre, to, vec(size1, 1, size1)));
        if (fadeGrowth >= 1.0) {
            double last = size1 * fadeGrowth;
            part.to(fadeAt, fade, Pose.of(centre, end, vec(last, 1, last)))
                    .to(fadeAt + fade, 0, Pose.of(centre, end, vec(last, 1, last)).hidden());
        } else {
            part.to(fadeAt, fade, Pose.of(centre, end, vec(0, 1, 0)).hidden());
        }
        return part;
    }

    /**
     * 두 지점을 잇는 빛줄기(교차 모델). 모델의 세로(+Y)를 줄기 방향으로 눕힙니다. {@code start}에 시작점에서 뻗어
     * {@code grow}틱 만에 끝까지 닿고, {@code fadeAt}부터 가늘어지며 사라집니다.
     */
    public void beam(DisplaySprite sprite, Vector3f from, Vector3f to, double width, int start, int grow,
            int fadeAt, int fade) {
        Vector3f delta = new Vector3f(to).sub(from);
        float length = delta.length();
        if (length < 1.0e-3F) {
            return;
        }
        Quaternionf rotation = alongAxis(delta);
        Vector3f middle = new Vector3f(from).add(to).mul(0.5F);
        Pose full = Pose.of(middle, rotation, vec(width, length, width));
        effect.part(sprite, Pose.of(from, rotation, vec(width, 0, width)))
                .to(start, grow, full)
                .to(fadeAt, fade, full.scaled(vec(0, length, 0)));
    }

    /** 모델의 +Y를 {@code direction}으로 돌리는 회전. */
    public static Quaternionf alongAxis(Vector3f direction) {
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        double elevation = Math.atan2(direction.y, horizontal);
        return new Quaternionf()
                .rotateY((float) Math.atan2(direction.x, direction.z))
                .rotateX((float) (Math.PI / 2.0 - elevation));
    }

    /**
     * 중심에서 사방으로 튀는 빌보드 조각(불티·파편·깃). {@code upward}가 클수록 위로 솟고, {@code gravity}만큼 떨어집니다.
     */
    public void burst(DisplaySprite sprite, int count, Vector3f centre, double distance, double size,
            double upward, double gravity, int start, int travel, int fadeAt, int fade) {
        for (int index = 0; index < count; index++) {
            double angle = Math.PI * 2.0 * index / count + random(-0.3, 0.3);
            double lift = random(0.3, 1.0) * upward;
            double reach = distance * random(0.6, 1.05);
            Vector3f target = new Vector3f(centre).add(vec(Math.sin(angle) * reach, lift, Math.cos(angle) * reach));
            Quaternionf roll = new Quaternionf().rotateZ(rad(random(0, 360)));
            Quaternionf rollEnd = new Quaternionf(roll).rotateZ(rad(random(90, 240)));
            float edge = (float) (size * random(0.75, 1.25));
            effect.part(sprite, Pose.of(centre, roll, vec(0, 0, 1)))
                    .to(start, travel, Pose.of(target, rollEnd, vec(edge, edge, 1)))
                    .to(fadeAt, fade, Pose.of(new Vector3f(target).add(0, (float) -gravity, 0), rollEnd, vec(0, 0, 1)));
        }
    }

    /**
     * 바닥 원형 연출에 겹치는 반투명 원기둥 벽({@link DisplaySprite.Shape#CYLINDER}). 밑면 중심이 {@code centre}입니다.
     * {@code start}에 지름 {@code size0}·높이 0에서 솟아 {@code grow}틱 만에 지름 {@code size1}·높이 {@code height}가 되고,
     * {@code fadeAt}부터 {@code fade}틱 동안 지름이 {@code fadeGrowth}배로 더 번지며 높이가 0으로 내려앉아 사라집니다.
     * 둘레 텍스처가 {@code spinDeg}만큼 돌아갑니다.
     */
    public DisplayEffect.Part cylinder(DisplaySprite sprite, Vector3f centre, double size0, double size1, double height,
            int start, int grow, int fadeAt, int fade, double spinDeg, double fadeGrowth) {
        float turn = rad(random(0, 360));
        Quaternionf from = new Quaternionf().rotateY(turn);
        Quaternionf to = new Quaternionf().rotateY(turn + rad(spinDeg));
        Quaternionf end = new Quaternionf().rotateY(turn + rad(spinDeg * 1.4));
        double last = size1 * Math.max(0.0, fadeGrowth);
        return effect.part(sprite, Pose.of(new Vector3f(centre), from, vec(size0, 0, size0)))
                .to(start, grow, Pose.of(new Vector3f(centre).add(0, (float) height / 2.0F, 0), to, vec(size1, height, size1)))
                .to(fadeAt, fade, Pose.of(new Vector3f(centre), end, vec(last, 0, last)));
    }

    /** 제자리에서 커졌다 사라지는 빌보드 한 장(섬광·룬). */
    public void pop(DisplaySprite sprite, Vector3f at, double size, int start, int grow, int fadeAt, int fade) {
        Quaternionf roll = new Quaternionf().rotateZ(rad(random(0, 90)));
        effect.part(sprite, Pose.of(at, roll, vec(0, 0, 1)))
                .to(start, grow, Pose.of(at, new Quaternionf(roll).rotateZ(0.4F), vec(size, size, 1)))
                .to(fadeAt, fade, Pose.of(at, new Quaternionf(roll).rotateZ(0.8F), vec(0, 0, 1)));
    }

    /**
     * 땅에서 솟았다가 도로 가라앉는 가시(교차 모델). {@code tilt}는 바깥쪽으로 기울어지는 각(도)입니다.
     */
    public void spike(DisplaySprite sprite, Vector3f base, double outwardYawRad, double tilt, double width,
            double height, int start, int rise, int sinkAt, int sink) {
        Quaternionf rotation = rotation(outwardYawRad, tilt, 0.0);
        Vector3f up = rotation.transform(new Vector3f(0.0F, (float) height / 2.0F, 0.0F));
        Pose hidden = Pose.of(new Vector3f(base).add(0, -0.2F, 0), rotation, vec(width * 0.6, 0.0, width * 0.6));
        Pose risen = Pose.of(new Vector3f(base).add(up), rotation, vec(width, height, width));
        effect.part(sprite, hidden)
                .to(start, rise, risen)
                .to(sinkAt, sink, hidden);
    }

    /** 정육면체의 대각선(모서리→모서리)을 세로축에 맞추는 회전. 이대로 세로로 늘리면 양 끝이 뾰족한 방추가 됩니다. */
    private static final Quaternionf CORNER_UP = new Quaternionf()
            .rotationTo(new Vector3f(1, 1, 1).normalize(), new Vector3f(0, 1, 0));
    /** 단위 정육면체 대각선의 절반 길이. 세로 크기 1당 꼭짓점이 중심에서 이만큼 올라갑니다. */
    private static final double HALF_DIAGONAL = Math.sqrt(3.0) / 2.0;

    /**
     * 땅을 뚫고 솟는 입체 가시.
     *
     * <p>정육면체를 모서리가 위로 서게 돌린 뒤(오른쪽 회전) 가늘고 길게 늘려 끝이 뾰족한 방추를 만들고, 아래 절반은
     * 땅속에 묻어 둡니다. 솟을 때는 크기를 바꾸지 않고 통째로 땅 밖으로 밀어 올리므로 끝이 먼저 뚫고 나옵니다.
     * {@code core}가 있으면 더 가늘고 조금 더 긴 빛나는 심을 함께 세워 끝만 붉게 빛나게 합니다.
     *
     * @param height 땅 위로 드러나는 높이(블록)
     * @param tilt 바깥쪽으로 기울어지는 각(도)
     */
    public void solidSpike(DisplaySprite body, DisplaySprite core, Vector3f base, double outwardYawRad, double tilt,
            double width, double height, int start, int rise, int sinkAt, int sink) {
        risingSpindle(body, base, outwardYawRad, tilt, width, height, start, rise, sinkAt, sink);
        if (core != null) {
            risingSpindle(core, base, outwardYawRad, tilt, width * 0.4, height * 1.12, start, rise, sinkAt, sink);
        }
    }

    private void risingSpindle(DisplaySprite sprite, Vector3f base, double outwardYawRad, double tilt, double width,
            double height, int start, int rise, int sinkAt, int sink) {
        Quaternionf lean = rotation(outwardYawRad, tilt, 0.0);
        Vector3f axis = lean.transform(new Vector3f(0, 1, 0));
        Vector3f scale = vec(width, height / HALF_DIAGONAL, width);
        Pose risen = new Pose(base, lean, scale, CORNER_UP);
        Pose buried = risen.moved(new Vector3f(base).sub(new Vector3f(axis).mul((float) (height * 1.05))));
        effect.part(sprite, buried)
                .to(start, rise, risen)
                .to(sinkAt, sink, buried)
                .to(sinkAt + sink, 0, buried.hidden());
    }

    private static float rad(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
