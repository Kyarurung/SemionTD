package kim.biryeong.semiontd.tower.demonlord;

import java.util.List;
import kim.biryeong.semiontd.vfx.DisplayEffect;
import kim.biryeong.semiontd.vfx.DisplayEffect.Pose;
import kim.biryeong.semiontd.vfx.DisplayShapes;
import kim.biryeong.semiontd.vfx.DisplaySprite;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import static kim.biryeong.semiontd.vfx.DisplayShapes.GROUND_STEP;
import static kim.biryeong.semiontd.vfx.DisplayShapes.ground;
import static kim.biryeong.semiontd.vfx.DisplayShapes.vec;

/**
 * 마왕 스킬 연출. 파티클 대신 직접 그린 텍스처 판({@link DisplaySprite})을 디스플레이 엔티티로 띄워 키프레임으로 움직입니다.
 *
 * <p>상용 보스 스킬처럼 형태가 있는 연출을 목표로 합니다. 부채꼴로 쓸고 나가는 초승달 칼날, 펼쳤다 내려치는 박쥐 날개,
 * 돌진 경로의 빛줄기와 솟는 흑요석 가시, 하늘에서 떨어지는 마탄과 마법진, 몸을 도는 육각 방벽, 바닥에 새겨지는
 * 지옥불 인장, 영혼 사슬, 겹겹이 퍼지는 충격파, 대상을 움켜쥐는 발톱, 내려꽂히는 단두대 칼날.
 *
 * <p>텍스처는 {@code tools/vfx-textures/make_demon_lord_vfx.py}가 그리고, 색은 마왕 팔레트(검정·진홍·자홍, 영혼 흡수만
 * 청록)로 통일합니다. 각 연출은 순수 데이터라 시험과 미리보기 페이지 내보내기에서 그대로 만들 수 있습니다.
 * 좌표는 모두 시전 기준점 기준입니다.
 */
public final class DemonLordDisplayVfx {
    private static final String DIR = "semiontd/vfx/demon_lord";

    static final DisplaySprite SLASH = DisplaySprite.flat("slash", DIR);
    static final DisplaySprite SHOCKWAVE_CRIMSON = DisplaySprite.flat("shockwave_crimson", DIR);
    static final DisplaySprite SHOCKWAVE_VIOLET = DisplaySprite.flat("shockwave_violet", DIR);
    static final DisplaySprite CRACK = DisplaySprite.flat("crack", DIR);
    static final DisplaySprite SIGIL = DisplaySprite.flat("sigil", DIR);
    static final DisplaySprite CIRCLE_ARCANE = DisplaySprite.flat("circle_arcane", DIR);
    static final DisplaySprite RIFT = DisplaySprite.flat("rift", DIR);
    /** 균열참 검 궤적 플립북: 0이 가장 선명하고 3으로 갈수록 꼬리부터 흐려집니다. */
    static final List<DisplaySprite> SWING_ARC_FRAMES = List.of(
            DisplaySprite.flat("swing_arc_0", DIR), DisplaySprite.flat("swing_arc_1", DIR),
            DisplaySprite.flat("swing_arc_2", DIR), DisplaySprite.flat("swing_arc_3", DIR));
    static final DisplaySprite VORTEX = DisplaySprite.flat("vortex", DIR);
    static final DisplaySprite VOID_CORE = DisplaySprite.billboard("void_core", DIR);
    static final DisplaySprite WING = DisplaySprite.upright("wing", DIR);
    static final DisplaySprite BLADE = DisplaySprite.upright("blade", DIR);
    static final DisplaySprite BARRIER = DisplaySprite.upright("barrier", DIR);
    static final DisplaySprite BEAM_CRIMSON = DisplaySprite.cross("beam_crimson", DIR);
    static final DisplaySprite BEAM_ARCANE = DisplaySprite.cross("beam_arcane", DIR);
    static final DisplaySprite BEAM_SOUL = DisplaySprite.cross("beam_soul", DIR);
    static final DisplaySprite SPIKE_BLOCK = DisplaySprite.cube("spike_block", DIR);
    static final DisplaySprite SPIKE_CORE = DisplaySprite.cube("spike_core", DIR);
    static final DisplaySprite CLAW = DisplaySprite.cross("claw", DIR);
    static final DisplaySprite CHAIN = DisplaySprite.cross("chain", DIR);
    static final DisplaySprite RUNE = DisplaySprite.billboard("rune", DIR);
    static final DisplaySprite SHARD = DisplaySprite.billboard("shard", DIR);
    static final DisplaySprite BOLT = DisplaySprite.billboard("bolt", DIR);
    static final DisplaySprite FEATHER = DisplaySprite.billboard("feather", DIR);
    static final DisplaySprite FLAME = DisplaySprite.billboard("flame", DIR);
    static final DisplaySprite SOUL = DisplaySprite.billboard("soul", DIR);
    static final DisplaySprite FLASH_CRIMSON = DisplaySprite.billboard("flash_crimson", DIR);
    static final DisplaySprite FLASH_ARCANE = DisplaySprite.billboard("flash_arcane", DIR);

    /** 리소스팩에 넣을 스프라이트 전부. */
    public static final List<DisplaySprite> SPRITES = List.of(
            SLASH, RIFT, SWING_ARC_FRAMES.get(0), SWING_ARC_FRAMES.get(1), SWING_ARC_FRAMES.get(2), SWING_ARC_FRAMES.get(3), VORTEX, VOID_CORE, SHOCKWAVE_CRIMSON, SHOCKWAVE_VIOLET, CRACK, SIGIL, CIRCLE_ARCANE, WING, BLADE, BARRIER,
            BEAM_CRIMSON, BEAM_ARCANE, BEAM_SOUL, SPIKE_BLOCK, SPIKE_CORE, CLAW, CHAIN, RUNE, SHARD, BOLT, FEATHER, FLAME, SOUL,
            FLASH_CRIMSON, FLASH_ARCANE
    );

    private DemonLordDisplayVfx() {
    }

    // ------------------------------------------------------------ 악의 파동

    /** 악의 파동: 초승달 칼날 세 장이 부채꼴로 겹쳐 쓸고 나가고, 손끝의 섬광과 핏빛 파편이 앞으로 튑니다. */
    public static DisplayEffect waveOfMalice(float yaw, double range, double coneDegrees, long seed) {
        DisplayEffect effect = new DisplayEffect("wave_of_malice", 18);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        double spread = 2.0 * range * Math.tan(Math.toRadians(coneDegrees / 2.0));
        slashLayer(shapes, 1.05, 0.0, 2, spread, range, 1.0);
        slashLayer(shapes, 0.75, 9.0, 3, spread * 0.85, range * 0.9, 0.8);
        slashLayer(shapes, 1.4, -9.0, 4, spread * 0.7, range * 0.8, 0.65);
        shapes.pop(FLASH_CRIMSON, shapes.local(0, 1.2, 0.8), 1.8, 2, 2, 4, 3);
        for (int index = 0; index < 7; index++) {
            double angle = Math.toRadians(coneDegrees * (index / 6.0 - 0.5));
            Vector3f from = shapes.local(0, 1.1, 0.8);
            double reach = range * shapes.random(0.6, 1.0);
            Vector3f to = shapes.local(Math.sin(angle) * reach, 1.1 + shapes.random(-0.3, 0.6), Math.cos(angle) * reach);
            Quaternionf roll = new Quaternionf().rotateZ((float) shapes.random(0, 6.28));
            effect.part(SHARD, Pose.of(from, roll, vec(0, 0, 1)))
                    .to(3, 6, Pose.of(to, new Quaternionf(roll).rotateZ(2.0F), vec(0.45, 0.45, 1)))
                    .to(10, 4, Pose.of(new Vector3f(to).add(0, -0.6F, 0), roll, vec(0, 0, 1)));
        }
        return effect;
    }

    /** 바닥과 나란히 누운 초승달 칼날 한 장. 텍스처 아래쪽(앞)이 시선 방향을 향하게 눕힙니다. */
    private static void slashLayer(DisplayShapes shapes, double height, double rollDeg, int start,
            double width, double range, double depthRatio) {
        Quaternionf facing = new Quaternionf(shapes.localRotation(0.0, 0.0, rollDeg));
        double depth = range * 0.42 * depthRatio;
        shapes.effect().part(SLASH, Pose.of(shapes.local(0, height, 0.9), facing, vec(1.2, 1, 0.6)).hidden())
                .to(start, 5, Pose.of(shapes.local(0, height, range * 0.55), facing, vec(width, 1, depth)))
                .to(start + 5, 5, Pose.of(shapes.local(0, height + 0.1, range * 0.85), facing, vec(width * 1.15, 1, depth * 0.55)))
                .to(start + 10, 2, Pose.of(shapes.local(0, height + 0.1, range * 0.95), facing, vec(width * 1.2, 1, 0)));
    }

    // ------------------------------------------------------------ 악마의 날개

    /** 악마의 날개: 등 뒤로 박쥐 날개 한 쌍을 펼쳐 두 번 크게 내려칩니다. 시전자를 따라갑니다. */
    public static DisplayEffect demonWings(float yaw, long seed) {
        DisplayEffect effect = new DisplayEffect("demon_wings", 20);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        double span = 2.8;
        for (int side : new int[] {-1, 1}) {
            effect.part(WING, wingPose(shapes, side, span * 0.3, 70.0))
                    .to(2, 3, wingPose(shapes, side, span, 30.0))
                    .to(5, 3, wingPose(shapes, side, span, -25.0))
                    .to(8, 3, wingPose(shapes, side, span, 35.0))
                    .to(11, 3, wingPose(shapes, side, span, -20.0))
                    .to(15, 5, wingPose(shapes, side, span * 0.2, 60.0).hidden());
        }
        // 날갯짓에 떨어지는 검은 깃
        for (int index = 0; index < 6; index++) {
            int side = index % 2 == 0 ? 1 : -1;
            Vector3f from = shapes.local(side * shapes.random(0.8, 2.4), shapes.random(1.4, 2.4), -0.5);
            Vector3f to = new Vector3f(from).add(shapes.local(side * shapes.random(0.3, 1.0), -shapes.random(1.0, 1.6), -shapes.random(0.2, 0.8)));
            Quaternionf roll = new Quaternionf().rotateZ((float) shapes.random(0, 6.28));
            effect.part(FEATHER, Pose.of(from, roll, vec(0, 0, 1)))
                    .to(5 + index, 2, Pose.of(from, roll, vec(0.4, 0.4, 1)))
                    .to(7 + index, 8, Pose.of(to, new Quaternionf(roll).rotateZ(2.5F), vec(0.35, 0.35, 1)))
                    .to(15 + Math.min(index, 3), 2, Pose.of(to, roll, vec(0, 0, 1)));
        }
        return effect;
    }

    /**
     * 날개 한 장. 어깨(등 위 옆쪽)를 축으로 {@code raise}도 들어 올립니다. 텍스처 왼쪽이 어깨이므로 왼쪽 날개는
     * 판을 뒤로 돌려 거울처럼 보이게 합니다.
     */
    private static Pose wingPose(DisplayShapes shapes, int side, double span, double raise) {
        double rad = Math.toRadians(raise);
        Vector3f shoulder = shapes.local(side * 0.25, 1.55, -0.35);
        Vector3f out = shapes.local(side * Math.cos(rad) * span / 2.0, Math.sin(rad) * span / 2.0 - span * 0.12, 0);
        Quaternionf rotation = shapes.localRotation(side > 0 ? 0.0 : 180.0, 0.0, raise);
        return Pose.of(new Vector3f(shoulder).add(out), rotation, vec(span, span, 1));
    }

    /** 악마의 날개가 도약한 자리에 남기는 충격파와 균열. 날개와 달리 제자리에 남습니다. */
    public static DisplayEffect demonWingsShockwave(double radius, long seed) {
        DisplayEffect effect = new DisplayEffect("demon_wings_shockwave", 16);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(SHOCKWAVE_CRIMSON, vec(0, ground(3), 0), 1.0, radius * 2.0, 2, 5, 7, 4, 40, 1.15);
        shapes.decal(CRACK, vec(0, ground(1), 0), 1.0, radius, 2, 3, 11, 5, 0, 0.0);
        shapes.burst(FEATHER, 6, vec(0, 0.4, 0), radius * 0.75, 0.4, 1.0, 0.8, 2, 6, 10, 5);
        return effect;
    }

    // ------------------------------------------------------------ 하늘 부수기

    /** 하늘 부수기: 돌진 궤적의 진홍·자홍 빛줄기, 그 길을 따라 차례로 솟는 흑요석 가시, 도착점의 섬광과 충격파. */
    public static DisplayEffect skyBreaker(Vector3f end, double hitRadius, long seed) {
        DisplayEffect effect = new DisplayEffect("sky_breaker", 28);
        float yaw = DisplayShapes.yawOf(end.x, end.z);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        double length = Math.sqrt(end.x * end.x + end.z * end.z);
        Vector3f from = vec(0, 1.0, 0);
        Vector3f to = new Vector3f(end).add(0, 1.0F, 0);
        shapes.beam(BEAM_CRIMSON, from, to, 1.6, 2, 2, 6, 8);
        shapes.beam(BEAM_ARCANE, from, to, 0.7, 2, 2, 8, 8);
        int steps = Math.max(2, (int) Math.round(length / 1.3));
        for (int step = 1; step <= steps; step++) {
            double along = length * step / steps;
            for (int side : new int[] {-1, 1}) {
                Vector3f base = shapes.local(side * Math.min(hitRadius, 1.2) * 0.75, 0.0, along);
                shapes.solidSpike(SPIKE_BLOCK, SPIKE_CORE, base, yaw + side * Math.PI / 2.0, shapes.random(8, 20),
                        0.3, shapes.random(1.9, 2.7), 2 + step, 3, 14 + step, 5);
            }
        }
        Vector3f landing = new Vector3f(end).add(0, ground(3), 0);
        shapes.decal(SHOCKWAVE_CRIMSON, landing, 1.0, (hitRadius + 0.5) * 2.0, 4, 4, 8, 4, 30, 1.15);
        shapes.decal(CRACK, new Vector3f(end).add(0, ground(1), 0), 1.0, 4.4, 4, 3, 20, 6, 0, 0.0);
        shapes.pop(FLASH_CRIMSON, new Vector3f(end).add(0, 1.0F, 0), 3.0, 4, 2, 6, 4);
        return effect;
    }

    // ------------------------------------------------------------ 마도 폭격

    /** 마도 폭격 1단계: 솟아오르는 발밑에 자홍 마법진 두 겹이 반대로 돌고, 룬 넷이 떠오릅니다. */
    public static DisplayEffect arcaneLaunch(long seed) {
        DisplayEffect effect = new DisplayEffect("arcane_bombardment_launch", 16);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(CIRCLE_ARCANE, vec(0, ground(3), 0), 1.0, 4.0, 2, 3, 10, 5, 150, 0.0);
        shapes.decal(CIRCLE_ARCANE, vec(0, ground(6), 0), 0.6, 2.2, 3, 3, 10, 5, -190, 0.0);
        for (int rune = 0; rune < 4; rune++) {
            double angle = Math.PI / 2.0 * rune;
            Vector3f at = vec(Math.sin(angle) * 1.7, 0.4, Math.cos(angle) * 1.7);
            effect.part(RUNE, Pose.of(at, new Quaternionf(), vec(0, 0, 1)))
                    .to(3 + rune, 3, Pose.of(new Vector3f(at).add(0, 0.7F, 0), new Quaternionf(), vec(0.6, 0.6, 1)))
                    .to(9 + rune, 4, Pose.of(new Vector3f(at).add(0, 2.0F, 0), new Quaternionf(), vec(0, 0, 1)));
        }
        return effect;
    }

    /** 마도 폭격 2단계: 하늘에서 내리꽂히는 마탄, 착탄 섬광과 보라 충격파, 불티와 균열. 좌표는 착탄 지점 기준. */
    public static DisplayEffect arcaneImpact(double blastRadius, long seed) {
        DisplayEffect effect = new DisplayEffect("arcane_bombardment_impact", 24);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        effect.part(BOLT, Pose.of(vec(0, 12, 0), new Quaternionf(), vec(1.6, 1.6, 1)))
                .to(2, 3, Pose.of(vec(0, 0.8, 0), new Quaternionf(), vec(2.0, 2.0, 1)))
                .to(5, 1, Pose.of(vec(0, 0.8, 0), new Quaternionf(), vec(0, 0, 1)));
        shapes.beam(BEAM_ARCANE, vec(0, 12, 0), vec(0, 0.4, 0), 1.2, 2, 3, 5, 5);
        shapes.pop(FLASH_ARCANE, vec(0, 1.0, 0), blastRadius * 1.6, 5, 2, 7, 4);
        shapes.decal(SHOCKWAVE_VIOLET, vec(0, ground(4), 0), 1.0, (blastRadius + 0.4) * 2.0, 5, 5, 10, 4, 40, 1.15);
        shapes.decal(CIRCLE_ARCANE, vec(0, ground(2), 0), 1.0, blastRadius * 1.6, 5, 3, 12, 6, 90, 0.0);
        shapes.decal(CRACK, vec(0, ground(0), 0), 1.0, blastRadius * 1.3, 5, 3, 18, 6, 0, 0.0);
        shapes.burst(FLAME, 10, vec(0, 0.8, 0), blastRadius * 1.1, 0.7, 2.2, 1.4, 5, 6, 12, 6);
        shapes.burst(BOLT, 6, vec(0, 0.6, 0), blastRadius * 0.9, 0.5, 1.2, 0.8, 5, 5, 11, 5);
        return effect;
    }

    // ------------------------------------------------------------ 악마 배리어

    /** 악마 배리어: 육각 방벽판 여섯 장이 몸 주위로 모여 돌고, 발밑 마법진과 머리 위 룬이 함께 돕니다. 시전자를 따라갑니다. */
    public static DisplayEffect demonBarrier(long seed) {
        DisplayEffect effect = new DisplayEffect("demon_barrier", 42);
        int panels = 6;
        for (int index = 0; index < panels; index++) {
            double angle = Math.PI * 2.0 * index / panels;
            effect.part(BARRIER, barrierPanel(angle, 2.6, 0.0))
                    .to(2, 4, barrierPanel(angle + 0.3, 1.1, 1.0))
                    .to(8, 12, barrierPanel(angle + 0.3 + Math.PI / 3.0, 1.1, 1.0))
                    .to(20, 12, barrierPanel(angle + 0.3 + Math.PI * 2.0 / 3.0, 1.1, 1.0))
                    .to(34, 7, barrierPanel(angle + 0.3 + Math.PI, 1.9, 0.0));
        }
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(CIRCLE_ARCANE, vec(0, ground(3), 0), 1.0, 3.0, 2, 4, 32, 8, 240, 0.0);
        for (int index = 0; index < 3; index++) {
            double angle = Math.PI * 2.0 * index / 3.0;
            DisplayEffect.Part rune = effect.part(RUNE, Pose.of(vec(0, 2.2, 0), new Quaternionf(), vec(0, 0, 1)));
            double turn = angle;
            for (int tick = 4; tick <= 28; tick += 8) {
                rune.to(tick, 8, Pose.of(vec(Math.sin(turn) * 0.7, 2.45, Math.cos(turn) * 0.7), new Quaternionf(), vec(0.45, 0.45, 1)));
                turn += Math.PI * 2.0 / 3.0;
            }
            rune.to(34, 6, Pose.of(vec(0, 2.9, 0), new Quaternionf(), vec(0, 0, 1)));
        }
        return effect;
    }

    private static Pose barrierPanel(double angle, double radius, double openness) {
        return Pose.of(
                vec(Math.sin(angle) * radius, 1.05, Math.cos(angle) * radius),
                new Quaternionf().rotateY((float) angle),
                vec(1.3 * openness, 1.5 * openness, 1)
        );
    }

    // ------------------------------------------------------------ 지옥불 낙인

    /**
     * 지옥불 낙인: 바닥에 오망성 인장이 새겨져 천천히 돌고, 장판이 피해를 주는 박자(1초)마다 충격파가 한 번씩
     * 퍼집니다. 가장자리에서 불꽃이 일렁이고 불티가 솟습니다. 장판이 끝나면 인장이 오므라들며 사라집니다.
     */
    public static DisplayEffect hellfireBrand(double radius, int durationTicks, long seed) {
        int lifetime = Math.max(20, durationTicks);
        DisplayEffect effect = new DisplayEffect("hellfire_brand", lifetime);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        int end = lifetime - 6;
        double diameter = radius * 2.0;
        DisplayEffect.Part sigil = effect.part(SIGIL, Pose.of(vec(0, ground(2), 0), new Quaternionf(), vec(0, 1, 0)));
        sigil.to(2, 5, Pose.of(vec(0, ground(2), 0), new Quaternionf().rotateY(0.5F), vec(diameter, 1, diameter)));
        float turn = 0.5F;
        for (int tick = 7; tick + 10 <= end; tick += 10) {
            turn += 0.35F;
            sigil.to(tick, 10, Pose.of(vec(0, ground(2), 0), new Quaternionf().rotateY(turn), vec(diameter, 1, diameter)));
        }
        sigil.to(end, 6, Pose.of(vec(0, ground(2), 0), new Quaternionf().rotateY(turn + 0.6F), vec(0, 1, 0)));
        // 피해 박자마다 퍼지는 충격파
        for (int pulse = 20; pulse + 8 <= end; pulse += 20) {
            effect.part(SHOCKWAVE_CRIMSON, Pose.of(vec(0, ground(5), 0), new Quaternionf(), vec(0, 1, 0)))
                    .to(pulse, 1, Pose.of(vec(0, ground(5), 0), new Quaternionf(), vec(diameter * 0.3, 1, diameter * 0.3)))
                    .to(pulse + 1, 6, Pose.of(vec(0, ground(5), 0), new Quaternionf().rotateY(0.6F), vec(diameter * 1.05, 1, diameter * 1.05)))
                    .to(pulse + 7, 0, Pose.of(vec(0, ground(5), 0), new Quaternionf(), vec(0, 1, 0)));
        }
        // 가장자리 불꽃: 크기를 번갈아 바꿔 일렁입니다.
        int flames = 8;
        for (int index = 0; index < flames; index++) {
            double angle = Math.PI * 2.0 * (index + 0.5) / flames;
            Vector3f base = vec(Math.sin(angle) * radius * 0.8, 0.0, Math.cos(angle) * radius * 0.8);
            DisplayEffect.Part flame = effect.part(FLAME, Pose.of(new Vector3f(base).add(0, 0.3F, 0), new Quaternionf(), vec(0, 0, 1)));
            boolean tall = true;
            for (int tick = 3 + index % 4; tick + 6 <= end; tick += 6) {
                double size = tall ? shapes.random(1.0, 1.4) : shapes.random(0.6, 0.8);
                flame.to(tick, 6, Pose.of(new Vector3f(base).add(0, (float) size / 2.0F, 0), new Quaternionf(), vec(size * 0.8, size, 1)));
                tall = !tall;
            }
            flame.to(end, 6, Pose.of(new Vector3f(base).add(0, 0.2F, 0), new Quaternionf(), vec(0, 0, 1)));
        }
        // 솟는 불티: 아래에서 다시 나타나 위로 오르기를 되풀이합니다.
        for (int index = 0; index < 6; index++) {
            double angle = shapes.random(0, Math.PI * 2.0);
            double reach = shapes.random(0.3, 0.9) * radius;
            Vector3f low = vec(Math.sin(angle) * reach, 0.2, Math.cos(angle) * reach);
            Vector3f high = new Vector3f(low).add(0, (float) shapes.random(1.6, 2.6), 0);
            DisplayEffect.Part ember = effect.part(FLAME, Pose.of(low, new Quaternionf(), vec(0, 0, 1)));
            for (int tick = 4 + index * 3; tick + 14 <= end; tick += 16) {
                ember.to(tick, 0, Pose.of(low, new Quaternionf(), vec(0.35, 0.35, 1)))
                        .to(tick + 1, 13, Pose.of(high, new Quaternionf(), vec(0.05, 0.05, 1)));
            }
            ember.to(end, 1, Pose.of(low, new Quaternionf(), vec(0, 0, 1)));
        }
        return effect;
    }

    // ------------------------------------------------------------ 영혼 흡수

    /** 영혼 흡수: 청록 빛줄기를 따라 영혼 사슬이 앞으로 뻗었다가 빨려 들어오고, 영혼 불꽃이 시전자 쪽으로 흘러옵니다. */
    public static DisplayEffect soulDrain(float yaw, double range, long seed) {
        DisplayEffect effect = new DisplayEffect("soul_drain", 22);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        Vector3f hand = shapes.local(0, 1.15, 0.5);
        Vector3f far = shapes.local(0, 1.15, range);
        shapes.beam(BEAM_SOUL, hand, far, 0.9, 2, 3, 12, 6);
        Vector3f axis = new Vector3f(far).sub(hand);
        int links = Math.max(4, (int) Math.round(range * 1.3));
        for (int link = 0; link < links; link++) {
            double along = 0.8 + (range - 0.8) * link / (links - 1);
            Quaternionf rotation = DisplayShapes.alongAxis(axis).rotateY(link % 2 == 0 ? 0.0F : (float) (Math.PI / 4.0));
            Vector3f at = shapes.local(0, 1.15, along);
            effect.part(CHAIN, Pose.of(hand, rotation, vec(0, 0, 0)))
                    .to(2 + link / 3, 3, Pose.of(at, rotation, vec(0.45, 0.9, 0.45)))
                    .to(9 + (links - link) / 3, 6, Pose.of(hand, rotation, vec(0, 0, 0)));
        }
        for (int orb = 0; orb < 5; orb++) {
            Vector3f from = shapes.local(shapes.random(-0.6, 0.6), 1.1 + shapes.random(-0.4, 0.4), range * shapes.random(0.5, 1.0));
            effect.part(SOUL, Pose.of(from, new Quaternionf(), vec(0, 0, 1)))
                    .to(4 + orb * 2, 2, Pose.of(from, new Quaternionf(), vec(0.8, 0.8, 1)))
                    .to(6 + orb * 2, 7, Pose.of(shapes.local(0, 1.3, 0.3), new Quaternionf(), vec(0.2, 0.2, 1)))
                    .to(13 + orb * 2, 1, Pose.of(shapes.local(0, 1.3, 0.3), new Quaternionf(), vec(0, 0, 1)));
        }
        return effect;
    }

    // ------------------------------------------------------------ 공포의 포효

    /** 공포의 포효: 서로 다른 높이의 충격파 세 겹이 차례로 퍼지고, 발밑이 갈라지며, 머리에서 보라 섬광이 터집니다. */
    public static DisplayEffect roarOfDread(double radius, long seed) {
        DisplayEffect effect = new DisplayEffect("roar_of_dread", 20);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        double diameter = radius * 2.0;
        shapes.decal(SHOCKWAVE_VIOLET, vec(0, ground(4), 0), 1.0, diameter, 2, 5, 7, 4, 30, 1.12);
        shapes.decal(SHOCKWAVE_CRIMSON, vec(0, 0.6, 0), 0.8, diameter * 0.85, 4, 5, 9, 4, -30, 1.12);
        shapes.decal(SHOCKWAVE_VIOLET, vec(0, 1.2, 0), 0.6, diameter * 0.7, 6, 5, 11, 4, 20, 1.12);
        shapes.decal(CRACK, vec(0, ground(1), 0), 1.0, diameter * 0.75, 2, 3, 13, 6, 0, 0.0);
        shapes.pop(FLASH_ARCANE, vec(0, 1.9, 0), 2.4, 2, 2, 4, 4);
        return effect;
    }

    // ------------------------------------------------------------ 파멸의 손아귀

    /**
     * 파멸의 손아귀: 대상 주위 땅이 갈라지며 발톱 다섯이 솟아 안쪽으로 움켜쥡니다. 처형에 성공하면 핏빛 섬광과 파편이
     * 터지고 폭발 반경에 충격파가 퍼집니다. 좌표는 대상 위치 기준.
     */
    public static DisplayEffect gripOfDoom(boolean executed, double blastRadius, long seed) {
        DisplayEffect effect = new DisplayEffect("grip_of_doom", executed ? 26 : 20);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(CRACK, vec(0, ground(1), 0), 1.0, 3.4, 2, 3, 14, 5, 0, 0.0);
        int claws = 5;
        for (int index = 0; index < claws; index++) {
            double angle = Math.PI * 2.0 * index / claws;
            Vector3f base = vec(Math.sin(angle) * 1.5, 0.0, Math.cos(angle) * 1.5);
            Vector3f grip = vec(Math.sin(angle) * 0.75, 0.0, Math.cos(angle) * 0.75);
            // 발톱 텍스처는 끝이 한쪽으로 휘므로, 휜 쪽이 대상을 향하게 바깥 방향에서 반 바퀴 돌립니다.
            Quaternionf open = DisplayShapes.rotation(angle + Math.PI, -30.0, 0.0);
            Quaternionf closed = DisplayShapes.rotation(angle + Math.PI, 28.0, 0.0);
            effect.part(CLAW, Pose.of(new Vector3f(base).add(0, -0.3F, 0), open, vec(0.5, 0, 0.5)))
                    .to(2, 3, Pose.of(new Vector3f(base).add(open.transform(vec(0, 1.3, 0))), open, vec(0.8, 2.6, 0.8)))
                    .to(6, 2, Pose.of(new Vector3f(grip).add(closed.transform(vec(0, 1.3, 0))), closed, vec(0.85, 2.7, 0.85)))
                    .to(14, 5, Pose.of(new Vector3f(grip).add(0, -0.3F, 0), closed, vec(0.5, 0, 0.5)));
        }
        if (executed) {
            shapes.pop(FLASH_CRIMSON, vec(0, 1.1, 0), 3.4, 8, 2, 11, 4);
            shapes.decal(SHOCKWAVE_CRIMSON, vec(0, ground(4), 0), 1.0, (blastRadius + 0.3) * 2.0, 8, 5, 13, 4, 30, 1.15);
            shapes.burst(SHARD, 12, vec(0, 1.0, 0), blastRadius, 0.6, 1.8, 1.2, 8, 6, 16, 6);
            shapes.burst(FLAME, 6, vec(0, 0.8, 0), blastRadius * 0.7, 0.6, 1.4, 0.6, 8, 5, 14, 5);
        }
        return effect;
    }

    // ------------------------------------------------------------ 지옥의 단두대

    /** 지옥의 단두대: 착지점 위로 거대한 단두대 칼날이 내려꽂히고, 땅이 갈라져 가시가 솟으며 충격파와 불티가 퍼집니다. */
    public static DisplayEffect hellGuillotine(float yaw, double radius, long seed) {
        DisplayEffect effect = new DisplayEffect("hell_guillotine", 24);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        // 칼날은 시선과 수직으로 서서(판이 시선을 마주 보게) 내려옵니다.
        Quaternionf facing = shapes.localRotation(0.0, 0.0, 0.0);
        effect.part(BLADE, Pose.of(vec(0, 10.0, 0), facing, vec(3.4, 3.4, 1)))
                .to(2, 3, Pose.of(vec(0, 1.5, 0), facing, vec(3.4, 3.4, 1)))
                .to(12, 6, Pose.of(vec(0, 0.2, 0), facing, vec(3.4, 0, 1)));
        shapes.beam(BEAM_CRIMSON, vec(0, 10.0, 0), vec(0, 0.3, 0), 0.8, 2, 3, 5, 4);
        shapes.pop(FLASH_CRIMSON, vec(0, 0.8, 0), 3.2, 5, 2, 7, 4);
        shapes.decal(SHOCKWAVE_CRIMSON, vec(0, ground(4), 0), 1.0, radius * 2.0, 5, 4, 9, 4, 20, 1.12);
        shapes.decal(CRACK, vec(0, ground(1), 0), 1.0, radius * 1.6, 5, 3, 18, 6, 0, 0.0);
        int spikes = 10;
        for (int index = 0; index < spikes; index++) {
            double angle = Math.PI * 2.0 * index / spikes + shapes.random(-0.15, 0.15);
            Vector3f base = vec(Math.sin(angle) * radius * 0.6, 0.0, Math.cos(angle) * radius * 0.6);
            shapes.solidSpike(SPIKE_BLOCK, SPIKE_CORE, base, angle, shapes.random(15, 30), 0.28,
                    shapes.random(1.5, 2.3), 5, 2, 13, 6);
        }
        shapes.burst(FLAME, 8, vec(0, 0.4, 0), radius * 0.8, 0.6, 1.4, 0.8, 5, 5, 11, 5);
        return effect;
    }

    // ------------------------------------------------------------ 균열참

    /**
     * 균열참: 몸 앞을 110° 부채꼴로 쓸고 지나가는 검 궤적, 내려찍은 자리의 섬광·충격파·균열, 그리고 땅을 가르며
     * 앞으로 뻗어 나가는 균열을 따라 {@code interval}틱마다 한 번씩 폭발 기둥·충격파·가시·불티가 터집니다.
     *
     * <p>폭발 횟수와 간격은 서버가 피해를 넣는 일정({@link DemonLordState.RiftCleave})과 같게 짭니다.
     */
    public static DisplayEffect riftCleave(float yaw, double slamOffset, double spacing, int count, int interval,
            double waveRadius, double slamRadius, long seed) {
        int slamTick = 4;
        int lastWave = slamTick + interval * Math.max(0, count);
        DisplayEffect effect = new DisplayEffect("rift_cleave", lastWave + 16);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);

        // 검 궤적 플립북: 흐려지는 정도가 다른 110° 부채꼴 궤적 네 장을 한 틱씩 바꿔 끼웁니다(디스플레이는 투명도를
        // 보간하지 못하므로 크기로 없애지 않습니다). 장이 바뀔 때마다 오른쪽에서 왼쪽으로 돌아가며 조금씩 내려가
        // 비스듬히 베어 내리는 것처럼 보입니다. 판은 옆으로 35° 기울인 사선이라, 완전 세로와 달리 시전자의
        // 1인칭 시점에서도 판이 옆으로 누워 보이지 않고 궤적이 보입니다.
        double swing = (slamOffset + slamRadius * 0.5) * 2.0;
        double[] turn = {-45.0, -15.0, 10.0, 28.0};
        double[] height = {1.5, 1.32, 1.14, 0.98};
        for (int frame = 0; frame < SWING_ARC_FRAMES.size(); frame++) {
            Quaternionf rotation = shapes.localRotation(turn[frame], 0.0, -35.0);
            Vector3f at = shapes.local(0, height[frame], 0);
            int shown = 2 + frame;
            effect.part(SWING_ARC_FRAMES.get(frame), Pose.of(at, rotation, vec(0, 1, 0)))
                    .to(shown, 0, Pose.of(at, rotation, vec(swing, 1, swing)))
                    .to(shown + 1, 0, Pose.of(at, rotation, vec(0, 1, 0)));
        }

        Vector3f slam = shapes.local(0, 0, slamOffset);
        shapes.pop(FLASH_CRIMSON, new Vector3f(slam).add(0, 0.8F, 0), 2.8, slamTick, 1, slamTick + 2, 4);
        shapes.decal(SHOCKWAVE_CRIMSON, new Vector3f(slam).add(0, ground(4), 0), 0.6, slamRadius * 2.0, slamTick, 4, slamTick + 4, 3, 30, 1.12);
        shapes.decal(CRACK, new Vector3f(slam).add(0, ground(1), 0), 0.8, slamRadius * 1.6, slamTick, 2, lastWave + 8, 6, 0, 0.0);

        if (count > 0) {
            // 앞으로 뻗는 땅의 균열: 파동이 터질 때마다 그 자리까지 길어집니다.
            Quaternionf along = shapes.localRotation(0.0, 0.0, 0.0);
            DisplayEffect.Part rift = effect.part(RIFT,
                    Pose.of(shapes.local(0, ground(2), slamOffset), along, vec(1.3, 1, 0)));
            for (int index = 1; index <= count; index++) {
                double reach = spacing * index + 0.6;
                rift.to(slamTick + interval * (index - 1) + 1, interval,
                        Pose.of(shapes.local(0, ground(2), slamOffset + reach / 2.0), along, vec(1.3, 1, reach)));
            }
            double full = spacing * count + 0.6;
            rift.to(lastWave + 8, 6, Pose.of(shapes.local(0, ground(2), slamOffset + full / 2.0), along, vec(0, 1, full)));
        }

        for (int index = 1; index <= count; index++) {
            int tick = slamTick + interval * index;
            Vector3f centre = shapes.local(0, 0, slamOffset + spacing * index);
            shapes.beam(BEAM_CRIMSON, new Vector3f(centre), new Vector3f(centre).add(0, 3.2F, 0), 1.3, tick, 2, tick + 3, 4);
            shapes.pop(FLASH_CRIMSON, new Vector3f(centre).add(0, 0.8F, 0), waveRadius * 1.6, tick, 1, tick + 2, 3);
            shapes.decal(SHOCKWAVE_CRIMSON, new Vector3f(centre).add(0, ground(4) + index * GROUND_STEP, 0), 0.5, waveRadius * 2.0,
                    tick, 3, tick + 3, 3, 30, 1.1);
            shapes.solidSpike(SPIKE_BLOCK, SPIKE_CORE, centre, yaw + shapes.random(-0.6, 0.6), shapes.random(5, 18),
                    0.3, shapes.random(1.2, 1.8), tick, 2, tick + 7, 4);
            shapes.burst(FLAME, 3, new Vector3f(centre).add(0, 0.4F, 0), waveRadius * 0.6, 0.6, 1.8, 0.5, tick, 4, tick + 5, 4);
        }
        return effect;
    }

    // ------------------------------------------------------------ 심연 소용돌이

    /**
     * 심연 소용돌이: 공중에 떠 있는 검은 핵과 그 아래 바닥에서 도는 나선 원반, 가장자리에서 핵으로 빨려 드는 파편,
     * 1초마다 안쪽으로 오므라드는 보라 고리. 끝날 때 핵이 한 번 부풀었다가 섬광과 함께 꺼집니다. 좌표는 중심 기준.
     */
    public static DisplayEffect abyssVortex(double radius, int durationTicks, long seed) {
        int end = Math.max(20, durationTicks);
        DisplayEffect effect = new DisplayEffect("abyss_vortex", end + 10);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        double diameter = radius * 2.0;

        // 바닥의 나선 원반: 계속 돕니다.
        DisplayEffect.Part disk = effect.part(VORTEX, Pose.of(vec(0, ground(3), 0), new Quaternionf(), vec(0, 1, 0)));
        disk.to(2, 5, Pose.of(vec(0, ground(3), 0), new Quaternionf().rotateY(-1.2F), vec(diameter, 1, diameter)));
        float turn = -1.2F;
        for (int tick = 7; tick + 6 <= end; tick += 6) {
            turn -= 1.0F;
            disk.to(tick, 6, Pose.of(vec(0, ground(3), 0), new Quaternionf().rotateY(turn), vec(diameter, 1, diameter)));
        }
        disk.to(end, 5, Pose.of(vec(0, ground(3), 0), new Quaternionf().rotateY(turn - 2.0F), vec(0, 1, 0)));
        // 두 번째, 작고 반대로 도는 원반
        DisplayEffect.Part inner = effect.part(VORTEX, Pose.of(vec(0, ground(6), 0), new Quaternionf(), vec(0, 1, 0)));
        inner.to(3, 5, Pose.of(vec(0, ground(6), 0), new Quaternionf().rotateY(1.0F), vec(diameter * 0.5, 1, diameter * 0.5)));
        float innerTurn = 1.0F;
        for (int tick = 8; tick + 6 <= end; tick += 6) {
            innerTurn -= 1.6F;
            inner.to(tick, 6, Pose.of(vec(0, ground(6), 0), new Quaternionf().rotateY(innerTurn), vec(diameter * 0.5, 1, diameter * 0.5)));
        }
        inner.to(end, 4, Pose.of(vec(0, ground(6), 0), new Quaternionf().rotateY(innerTurn - 2.0F), vec(0, 1, 0)));

        // 공중의 검은 핵: 숨 쉬듯 부풀었다 줄고, 끝에 한 번 부풀어 꺼집니다.
        DisplayEffect.Part core = effect.part(VOID_CORE, Pose.of(vec(0, 1.6, 0), new Quaternionf(), vec(0, 0, 1)));
        core.to(2, 4, Pose.of(vec(0, 1.6, 0), new Quaternionf(), vec(2.2, 2.2, 1)));
        boolean big = false;
        for (int tick = 6; tick + 5 <= end; tick += 5) {
            double size = big ? 2.4 : 1.9;
            core.to(tick, 5, Pose.of(vec(0, 1.6, 0), new Quaternionf().rotateZ(big ? 0.4F : -0.4F), vec(size, size, 1)));
            big = !big;
        }
        core.to(end, 3, Pose.of(vec(0, 1.6, 0), new Quaternionf(), vec(3.2, 3.2, 1)))
                .to(end + 3, 2, Pose.of(vec(0, 1.6, 0), new Quaternionf(), vec(0, 0, 1)));
        shapes.pop(FLASH_ARCANE, vec(0, 1.6, 0), 4.0, end + 3, 2, end + 5, 4);

        // 1초마다 안쪽으로 오므라드는 고리(끌어당김을 보여 줍니다)
        for (int pulse = 4; pulse + 12 <= end; pulse += 20) {
            effect.part(SHOCKWAVE_VIOLET, Pose.of(vec(0, ground(8), 0), new Quaternionf(), vec(0, 1, 0)))
                    .to(pulse, 1, Pose.of(vec(0, ground(8), 0), new Quaternionf(), vec(diameter * 1.1, 1, diameter * 1.1)))
                    .to(pulse + 1, 10, Pose.of(vec(0, ground(8), 0), new Quaternionf().rotateY(-1.5F), vec(0.6, 1, 0.6)))
                    .to(pulse + 11, 0, Pose.of(vec(0, ground(8), 0), new Quaternionf(), vec(0, 1, 0)));
        }

        // 가장자리에서 핵으로 빨려 드는 파편: 되풀이해서 나타납니다.
        for (int index = 0; index < 10; index++) {
            DisplaySprite sprite = index % 3 == 0 ? SHARD : index % 3 == 1 ? BOLT : FEATHER;
            DisplayEffect.Part mote = effect.part(sprite, Pose.of(vec(0, 1.6, 0), new Quaternionf(), vec(0, 0, 1)));
            for (int tick = 3 + index * 2; tick + 10 <= end; tick += 12) {
                double angle = shapes.random(0, Math.PI * 2.0);
                double reach = radius * shapes.random(0.75, 1.05);
                Vector3f rim = vec(Math.sin(angle) * reach, shapes.random(0.3, 1.8), Math.cos(angle) * reach);
                Vector3f swirl = vec(Math.sin(angle + 1.2) * reach * 0.45, 1.4, Math.cos(angle + 1.2) * reach * 0.45);
                mote.to(tick, 0, Pose.of(rim, new Quaternionf(), vec(0.45, 0.45, 1)))
                        .to(tick + 1, 5, Pose.of(swirl, new Quaternionf().rotateZ(1.5F), vec(0.35, 0.35, 1)))
                        .to(tick + 6, 4, Pose.of(vec(0, 1.6, 0), new Quaternionf().rotateZ(3.0F), vec(0, 0, 1)));
            }
        }
        return effect;
    }

    // ------------------------------------------------------------ 마수 소환

    /** 마수 소환: 발밑에 지옥불 인장이 돌며 진홍 기둥이 치솟고, 섬광과 불티 속에서 마수가 나타납니다. 좌표는 마수 발밑 기준. */
    public static DisplayEffect summonFiend(long seed) {
        DisplayEffect effect = new DisplayEffect("summon_fiend", 26);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(SIGIL, vec(0, ground(2), 0), 0.5, 3.6, 2, 4, 18, 7, 200, 0.0);
        shapes.decal(SHOCKWAVE_CRIMSON, vec(0, ground(5), 0), 0.5, 4.4, 6, 4, 10, 3, 40, 1.12);
        shapes.beam(BEAM_CRIMSON, vec(0, 0, 0), vec(0, 5.0, 0), 2.2, 3, 3, 7, 5);
        shapes.beam(BEAM_ARCANE, vec(0, 0, 0), vec(0, 5.5, 0), 0.9, 3, 3, 8, 5);
        shapes.pop(FLASH_CRIMSON, vec(0, 1.1, 0), 3.2, 6, 2, 8, 4);
        shapes.burst(FLAME, 8, vec(0, 0.3, 0), 1.8, 0.7, 2.0, 0.6, 6, 6, 13, 5);
        shapes.burst(FEATHER, 5, vec(0, 1.2, 0), 1.6, 0.4, 1.0, 1.0, 6, 7, 15, 6);
        return effect;
    }

    /** 마수가 돌아갈 때: 보라 충격파와 섬광이 짧게 터지며 사라집니다. */
    public static DisplayEffect fiendDismiss(long seed) {
        DisplayEffect effect = new DisplayEffect("fiend_dismiss", 14);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(SHOCKWAVE_VIOLET, vec(0, ground(4), 0), 0.5, 3.0, 2, 4, 6, 3, 30, 1.1);
        shapes.pop(FLASH_ARCANE, vec(0, 1.0, 0), 2.2, 2, 2, 4, 4);
        shapes.burst(FEATHER, 5, vec(0, 1.0, 0), 1.4, 0.35, 1.2, 1.0, 2, 6, 9, 5);
        return effect;
    }

    // ------------------------------------------------------------ 흡혈 참격(패시브)

    /** 흡혈 참격: 평타가 닿은 자리에서 진홍 충격파가 베는 범위만큼 퍼지고, 핏빛 파편이 마왕 쪽으로 튑니다. */
    public static DisplayEffect bloodCleave(double radius, long seed) {
        DisplayEffect effect = new DisplayEffect("blood_cleave", 10);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(SHOCKWAVE_CRIMSON, vec(0, ground(7), 0), 0.5, radius * 2.0, 2, 3, 5, 3, 25, 1.08);
        shapes.pop(FLASH_CRIMSON, vec(0, 1.0, 0), 1.4, 2, 1, 3, 3);
        shapes.burst(SHARD, 4, vec(0, 1.0, 0), radius * 0.6, 0.35, 0.8, 0.5, 2, 4, 6, 3);
        return effect;
    }

    // ------------------------------------------------------------ 메아리

    /** 옥좌 증강의 재시전(메아리) 표시: 작은 자홍 마법진이 한 번 돌며 퍼집니다. */
    public static DisplayEffect echo(long seed) {
        DisplayEffect effect = new DisplayEffect("echo", 14);
        new DisplayShapes(effect, 0.0F, seed).decal(CIRCLE_ARCANE, vec(0, ground(3), 0), 0.6, 3.0, 2, 4, 8, 5, 120, 0.0);
        return effect;
    }
}
