package kim.biryeong.semiontd.summon.invasion;

import static kim.biryeong.semiontd.vfx.DisplayShapes.ground;
import static kim.biryeong.semiontd.vfx.DisplayShapes.vec;

import java.util.List;
import kim.biryeong.semiontd.vfx.DisplayEffect;
import kim.biryeong.semiontd.vfx.DisplayEffect.Pose;
import kim.biryeong.semiontd.vfx.DisplayShapes;
import kim.biryeong.semiontd.vfx.DisplaySprite;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 침공군 유닛의 공격·능력 연출. 마왕 연출과 같은 디스플레이 엔진(직접 그린 텍스처 판)을 쓰되, 몹이 자주 띄우므로
 * 판 수를 적게(2~6장) 잡고 유닛 예산({@link DisplayEffect#unitBudget()})으로 셉니다.
 *
 * <p>모든 시간은 틱이라 게임 배속(서버 틱 속도)이 바뀌어도 애니메이션·피해와 같은 빠르기로 재생됩니다.
 * 첫 자세는 틱 0에 두고 움직임은 틱 2부터 시작합니다(클라이언트가 첫 자세를 받은 뒤 보간하도록).
 */
public final class InvasionVfx {
    private static final String DIR = "semiontd/vfx/invasion";
    private static final String DEMON_LORD_DIR = "semiontd/vfx/demon_lord";

    static final DisplaySprite JAVELIN = DisplaySprite.cross("javelin", DIR);
    static final DisplaySprite SMOKE = DisplaySprite.billboard("smoke", DIR);
    static final DisplaySprite GOLD_ARC = DisplaySprite.flat("gold_arc", DIR);
    static final DisplaySprite TRACER = DisplaySprite.cross("tracer", DIR);
    static final DisplaySprite MUZZLE = DisplaySprite.billboard("muzzle", DIR);
    static final DisplaySprite HOLY_CIRCLE = DisplaySprite.flat("holy_circle", DIR);
    static final DisplaySprite HOLY_FLASH = DisplaySprite.billboard("holy_flash", DIR);
    static final DisplaySprite HOLY_RING = DisplaySprite.flat("holy_ring", DIR);
    static final DisplaySprite NECRO_CIRCLE = DisplaySprite.flat("necro_circle", DIR);
    static final DisplaySprite NECRO_SOUL = DisplaySprite.billboard("necro_soul", DIR);
    static final DisplaySprite NECRO_FLASH = DisplaySprite.billboard("necro_flash", DIR);
    static final DisplaySprite DUST_RING = DisplaySprite.flat("dust_ring", DIR);
    static final DisplaySprite GOLD_FLASH = DisplaySprite.billboard("gold_flash", DIR);
    static final DisplaySprite SLASH_GOBLIN = DisplaySprite.billboard("slash_goblin", DIR);
    static final DisplaySprite SLASH_ELF = DisplaySprite.billboard("slash_elf", DIR);

    /** 이 클래스가 리소스팩에 넣는 텍스처. 마왕 연출 텍스처는 그쪽에서 이미 넣습니다. */
    public static final List<DisplaySprite> SPRITES = List.of(
            JAVELIN, SMOKE, GOLD_ARC, TRACER, MUZZLE, HOLY_CIRCLE, HOLY_FLASH, HOLY_RING,
            NECRO_CIRCLE, NECRO_SOUL, NECRO_FLASH, DUST_RING, GOLD_FLASH, SLASH_GOBLIN, SLASH_ELF);

    // 마왕 연출 텍스처를 그대로 빌려 씁니다(리소스팩에는 마왕 쪽 목록으로 이미 들어갑니다).
    private static final DisplaySprite CRACK = DisplaySprite.flat("crack", DEMON_LORD_DIR);
    private static final DisplaySprite SHOCKWAVE_CRIMSON = DisplaySprite.flat("shockwave_crimson", DEMON_LORD_DIR);
    private static final DisplaySprite FLASH_CRIMSON = DisplaySprite.billboard("flash_crimson", DEMON_LORD_DIR);

    private InvasionVfx() {
    }

    private static DisplayEffect effect(String id, int lifetime) {
        return new DisplayEffect("invasion_" + id, lifetime).unitBudget();
    }

    private static void play(ServerLevel level, DisplayEffect effect, Vec3 origin) {
        if (level != null && origin != null) {
            effect.spawn(level, origin);
        }
    }

    private static long seed(ServerLevel level) {
        return level == null ? 0L : level.getGameTime();
    }

    private static Vector3f offset(Vec3 origin, Vec3 point) {
        return new Vector3f((float) (point.x - origin.x), (float) (point.y - origin.y), (float) (point.z - origin.z));
    }

    // ------------------------------------------------------------------ 엘프 암살자

    /** 은신하거나 드러날 때 피어오르는 연막. */
    public static void stealthPuff(ServerLevel level, Vec3 feet, boolean hiding) {
        play(level, stealth(hiding, seed(level)), feet);
    }

    public static DisplayEffect stealth(boolean hiding, long seed) {
        DisplayEffect effect = effect(hiding ? "stealth" : "reveal", 14);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.burst(SMOKE, 5, vec(0, 0.9, 0), 0.7, 0.9, 0.5, 0.1, 2, 6, 8, 5);
        shapes.pop(SMOKE, vec(0, 1.0, 0), hiding ? 1.6 : 1.2, 2, 3, 6, 6);
        return effect;
    }

    /** 기습 단검: 대상 앞에 사선으로 그어지는 한 줄 칼자국(빌보드라 어느 쪽에서 봐도 보입니다). */
    public static DisplayEffect elfSlash(long seed) {
        DisplayEffect effect = effect("elf_slash", 10);
        straightSlash(effect, SLASH_ELF, vec(0, 1.1, 0), -35.0, 2, 2.4);
        return effect;
    }

    /**
     * 한 줄로 긋는 칼자국 한 장. 판을 {@code rollDeg}만큼 굴려 사선으로 두고, 짧게 나타나 {@code length}까지 뻗은 뒤
     * 두께가 얇아지며 사라집니다.
     */
    private static void straightSlash(DisplayEffect effect, DisplaySprite sprite, Vector3f at, double rollDeg, int start,
            double length) {
        Quaternionf roll = new Quaternionf().rotateZ((float) Math.toRadians(rollDeg));
        effect.part(sprite, Pose.of(at, roll, vec(0.2, length, 1)))
                .to(start, 2, Pose.of(at, roll, vec(length, length, 1)))
                .to(start + 3, 3, Pose.of(at, roll, vec(length * 1.12, 0, 1)));
    }

    // ------------------------------------------------------------------ 고블린 정찰병

    /** 비전투 타워를 단숨에 끝장낼 때: 직선 칼자국 두 줄이 X자로 엇갈리고 타워가 먼지 속에 무너집니다. */
    public static DisplayEffect goblinExecute(long seed) {
        DisplayEffect effect = effect("goblin_execute", 16);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        straightSlash(effect, SLASH_GOBLIN, vec(0, 1.1, 0), 45.0, 2, 2.2);
        straightSlash(effect, SLASH_GOBLIN, vec(0, 1.1, 0), -45.0, 4, 2.2);
        shapes.burst(SMOKE, 6, vec(0, 0.6, 0), 1.1, 0.8, 0.5, 0.2, 5, 6, 10, 5);
        return effect;
    }

    // ------------------------------------------------------------------ 암흑 신관

    /** 공격: 대상 자리에 분홍 섬광이 터지고 범위만큼 고리가 번집니다. */
    public static DisplayEffect priestBlast(double radius, long seed) {
        DisplayEffect effect = effect("priest_blast", 14);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.pop(HOLY_FLASH, vec(0, 1.0, 0), 2.2, 2, 2, 5, 4);
        shapes.decal(HOLY_RING, vec(0, ground(1), 0), 0.6, radius * 2.0, 2, 4, 7, 4, 30, 1.12);
        return effect;
    }

    /** 치유: 치유받은 아군 발밑의 성스러운 마법진과 위로 오르는 빛. 모션 없이 연출만 나옵니다. */
    public static DisplayEffect priestHeal(long seed) {
        DisplayEffect effect = effect("priest_heal", 18);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(HOLY_CIRCLE, vec(0, ground(0), 0), 0.4, 1.8, 2, 4, 12, 5, 90, 0.0);
        effect.part(HOLY_FLASH, Pose.of(vec(0, 0.4, 0), new Quaternionf(), vec(0, 0, 1)))
                .to(2, 3, Pose.of(vec(0, 0.9, 0), new Quaternionf(), vec(0.9, 0.9, 1)))
                .to(8, 8, Pose.of(vec(0, 2.2, 0), new Quaternionf().rotateZ(0.8F), vec(0, 0, 1)));
        return effect;
    }

    // ------------------------------------------------------------------ 드워프 총병

    /** 총구 화염과, 총구에서 관통 끝까지 뻗는 탄 궤적. {@code from}/{@code to}는 연출 기준점 기준입니다. */
    public static DisplayEffect dwarfShot(Vector3f muzzle, Vector3f end, long seed) {
        DisplayEffect effect = effect("dwarf_shot", 10);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.pop(MUZZLE, muzzle, 1.1, 2, 1, 3, 3);
        shapes.beam(TRACER, muzzle, end, 0.3, 2, 1, 5, 4);
        return effect;
    }

    // ------------------------------------------------------------------ 트롤 투창병

    /** 손을 떠난 투창이 {@code flightTicks}에 걸쳐 {@code to}까지 날아갑니다(날아가는 방향으로 눕힘). */
    public static DisplayEffect trollJavelin(Vector3f from, Vector3f to, int flightTicks) {
        int flight = Math.max(1, flightTicks);
        DisplayEffect effect = effect("troll_javelin", flight + 6);
        Vector3f delta = new Vector3f(to).sub(from);
        Quaternionf along = DisplayShapes.alongAxis(delta);
        Vector3f size = vec(0.35, 2.4, 0.35);
        effect.part(JAVELIN, Pose.of(from, along, size))
                .to(2, flight, Pose.of(to, along, size))
                .to(2 + flight + 2, 0, Pose.of(to, along, vec(0, 0, 0)));
        return effect;
    }

    // ------------------------------------------------------------------ 오크 전사

    /** 광폭화: 붉은 섬광과 충격파가 한 번 터집니다. */
    public static DisplayEffect orcBerserk(long seed) {
        DisplayEffect effect = effect("orc_berserk", 16);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.pop(FLASH_CRIMSON, vec(0, 1.4, 0), 2.6, 2, 3, 6, 5);
        shapes.decal(SHOCKWAVE_CRIMSON, vec(0, ground(1), 0), 0.8, 4.0, 2, 5, 8, 5, 30, 1.12);
        return effect;
    }

    // ------------------------------------------------------------------ 강령술사

    /** 소환 시전: 발밑 초록 마법진이 소환이 끝날 때까지 돕니다. */
    public static DisplayEffect necroCast(int durationTicks, long seed) {
        int duration = Math.max(8, durationTicks);
        DisplayEffect effect = effect("necro_cast", duration + 8);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(NECRO_CIRCLE, vec(0, ground(0), 0), 0.6, 3.6, 2, 5, duration, 6, 160, 0.0);
        return effect;
    }

    /** 해골 한 마리가 솟는 자리: 작은 마법진과 솟는 영혼 불꽃. */
    public static DisplayEffect necroRise(long seed) {
        DisplayEffect effect = effect("necro_rise", 16);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(NECRO_CIRCLE, vec(0, ground(1), 0), 0.3, 1.4, 2, 3, 10, 4, 120, 0.0);
        effect.part(NECRO_SOUL, Pose.of(vec(0, 0.2, 0), new Quaternionf(), vec(0, 0, 1)))
                .to(2, 3, Pose.of(vec(0, 0.7, 0), new Quaternionf(), vec(0.7, 0.9, 1)))
                .to(8, 6, Pose.of(vec(0, 1.8, 0), new Quaternionf(), vec(0, 0, 1)));
        return effect;
    }

    /** 공격: 대상 자리에 초록 섬광. */
    public static DisplayEffect necroBolt(long seed) {
        DisplayEffect effect = effect("necro_bolt", 10);
        new DisplayShapes(effect, 0.0F, seed).pop(NECRO_FLASH, vec(0, 1.0, 0), 1.8, 2, 2, 5, 3);
        return effect;
    }

    // ------------------------------------------------------------------ 공성 골렘 · 오우거 투사

    /** 내려찍기: 흙먼지 충격파와 땅 균열. {@code radius}는 피해 범위입니다. */
    public static DisplayEffect groundSlam(double radius, long seed) {
        DisplayEffect effect = effect("ground_slam", 18);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(DUST_RING, vec(0, ground(2), 0), 0.6, radius * 2.0, 2, 4, 8, 5, 20, 1.12);
        shapes.decal(CRACK, vec(0, ground(1), 0), 0.8, radius * 1.4, 2, 3, 12, 5, 0, 0.0);
        shapes.burst(SMOKE, 5, vec(0, 0.3, 0), radius * 0.8, 0.9, 0.4, 0.1, 2, 6, 9, 5);
        return effect;
    }

    // ------------------------------------------------------------------ 군단장

    /**
     * 소드스태프 내려베기: 머리 위에서 앞쪽 땅까지 세로로 떨어지는 금빛 궤적과, 날이 닿은 앞 땅의 섬광.
     * 궤적 판을 바라보는 방향의 세로면에 세우고, 가장 밝은 칼끝(텍스처 오른쪽)이 아래 앞쪽에 오도록 돌립니다.
     * {@code yaw}는 공격자가 대상을 보는 방향입니다.
     */
    public static DisplayEffect commanderSlash(float yaw, long seed) {
        DisplayEffect effect = effect("commander_slash", 12);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        Quaternionf vertical = shapes.localRotation(0, 0, -90);
        Vector3f pivot = shapes.local(0, 1.9, 0.2);
        double size = 5.0;
        effect.part(GOLD_ARC, Pose.of(pivot, vertical, vec(size * 0.4, 1, size * 0.4)))
                .to(2, 2, Pose.of(pivot, vertical, vec(size, 1, size)))
                .to(6, 4, Pose.of(pivot, vertical, vec(size * 1.05, 1, 0)));
        shapes.pop(GOLD_FLASH, shapes.local(0, 0.3, 2.2), 1.6, 3, 2, 6, 3);
        return effect;
    }

    // ------------------------------------------------------------------ 띄우기

    static void playAt(ServerLevel level, DisplayEffect effect, Vec3 origin) {
        play(level, effect, origin);
    }

    static Vector3f relative(Vec3 origin, Vec3 point) {
        return offset(origin, point);
    }
}
