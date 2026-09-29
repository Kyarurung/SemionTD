package kim.biryeong.semiontd.tower.plant;

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
 * 식물 빌더 연출. 마왕·침공군과 같은 디스플레이 엔진(직접 그린 텍스처 판)을 쓰며, 타워가 공격마다 띄우므로 판 수를 적게
 * 잡고 타워 예산({@link DisplayEffect#towerBudget()})으로 셉니다. 예산이 차면 그 연출은 건너뜁니다.
 *
 * <p>피해는 연출과 같은 틱(틱 0)에 들어가므로, 판은 틱 0 자세부터 보이거나 틱 2에 바로 커지게 짭니다.
 */
public final class PlantDisplayVfx {
    private static final String DIR = "semiontd/vfx/plant";
    private static final String INVASION_DIR = "semiontd/vfx/invasion";

    static final DisplaySprite WATER_DROP = DisplaySprite.billboard("water_drop", DIR);
    static final DisplaySprite WATER_RING = DisplaySprite.flat("water_ring", DIR);
    static final DisplaySprite WATER_FLASH = DisplaySprite.billboard("water_flash", DIR);
    static final DisplaySprite VINE_RING = DisplaySprite.flat("vine_ring", DIR);
    static final DisplaySprite PETAL_TULIP = DisplaySprite.billboard("petal_tulip", DIR);
    static final DisplaySprite PETAL_LILAC = DisplaySprite.billboard("petal_lilac", DIR);
    static final DisplaySprite PETAL_RING = DisplaySprite.flat("petal_ring", DIR);
    static final DisplaySprite TULIP_FLASH = DisplaySprite.billboard("tulip_flash", DIR);
    static final DisplaySprite POLLEN = DisplaySprite.billboard("pollen", DIR);
    static final DisplaySprite LEAF = DisplaySprite.billboard("leaf", DIR);
    static final DisplaySprite LEAF_FLASH = DisplaySprite.billboard("leaf_flash", DIR);
    static final DisplaySprite HEAL_RING = DisplaySprite.flat("heal_ring", DIR);
    static final DisplaySprite SAND_PUFF = DisplaySprite.billboard("sand_puff", DIR);
    static final DisplaySprite DUST_PUFF = DisplaySprite.billboard("dust_puff", DIR);
    static final DisplaySprite SPORE = DisplaySprite.billboard("spore", DIR);
    static final DisplaySprite SPORE_RING = DisplaySprite.flat("spore_ring", DIR);
    static final DisplaySprite SPORE_FLASH = DisplaySprite.billboard("spore_flash", DIR);
    static final DisplaySprite BAMBOO_LEAF = DisplaySprite.billboard("bamboo_leaf", DIR);
    /** 바닥 고리에 겹쳐 세우는 반투명 원기둥 벽. */
    static final DisplaySprite WALL_WATER = DisplaySprite.cylinder("wall_water", DIR);
    static final DisplaySprite WALL_TULIP = DisplaySprite.cylinder("wall_tulip", DIR);
    static final DisplaySprite WALL_LEAF = DisplaySprite.cylinder("wall_leaf", DIR);
    static final DisplaySprite WALL_SPORE = DisplaySprite.cylinder("wall_spore", DIR);
    static final DisplaySprite POLLEN_STREAK = DisplaySprite.cross("pollen_streak", DIR);
    static final DisplaySprite WIND_STREAK = DisplaySprite.cross("wind_streak", DIR);
    static final DisplaySprite WIND_ARC = DisplaySprite.flat("wind_arc", DIR);

    /** 리소스팩에 넣을 텍스처. */
    public static final List<DisplaySprite> SPRITES = List.of(
            WATER_DROP, WATER_RING, WATER_FLASH, VINE_RING, PETAL_TULIP, PETAL_LILAC, PETAL_RING, TULIP_FLASH, POLLEN,
            LEAF, LEAF_FLASH, HEAL_RING, SAND_PUFF, DUST_PUFF, SPORE, SPORE_RING, SPORE_FLASH, BAMBOO_LEAF,
            WALL_WATER, WALL_TULIP, WALL_LEAF, WALL_SPORE, POLLEN_STREAK, WIND_STREAK, WIND_ARC);

    // 침공군 흙먼지 고리를 그대로 빌려 씁니다(리소스팩에는 침공군 목록으로 이미 들어갑니다).
    private static final DisplaySprite DUST_RING = DisplaySprite.flat("dust_ring", INVASION_DIR);

    private PlantDisplayVfx() {
    }

    private static DisplayEffect effect(String id, int lifetime) {
        return new DisplayEffect("plant_" + id, lifetime).towerBudget();
    }

    static void play(ServerLevel level, DisplayEffect effect, Vec3 origin) {
        if (level != null && origin != null) {
            effect.spawn(level, origin);
        }
    }

    static long seed(ServerLevel level) {
        return level == null ? 0L : level.getGameTime();
    }

    // ------------------------------------------------------------------ 물병 식물

    /**
     * 물병 식물의 곡사 포격. 기준점은 타워 발밑이고 {@code end}는 착탄 지점(기준점에서의 상대 위치)입니다.
     *
     * <p>포물선 위의 물방울 줄기가 틱 0부터 한꺼번에 보이고, 꼬리(타워 쪽)부터 차례로 사라지며 물이 날아간 것처럼
     * 보입니다. 착탄 자리의 물 고리·섬광·물방울과 포충낭 덩굴 고리는 피해와 같은 때 바로 퍼집니다.
     */
    public static DisplayEffect pitcherLob(Vector3f end, double arcHeight, double radius, boolean snare, long seed) {
        DisplayEffect effect = effect("pitcher_lob", 20);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        Vector3f start = vec(0, 1.3, 0);
        Vector3f control = new Vector3f(start).add(end).mul(0.5F).add(0, (float) Math.max(1.0, arcHeight), 0);
        int drops = 7;
        for (int index = 1; index <= drops; index++) {
            float t = index / (float) drops;
            Vector3f at = bezier(start, control, end, t).add(0, 0.3F * (1.0F - t), 0);
            float size = 0.3F + 0.12F * t;
            int vanish = 2 + index;
            Quaternionf roll = new Quaternionf();
            effect.part(WATER_DROP, Pose.of(at, roll, vec(size, size, 1)))
                    .to(vanish, 2, Pose.of(new Vector3f(at).add(0, -0.3F, 0), roll, vec(0, 0, 1)));
        }
        Vector3f impact = new Vector3f(end);
        shapes.decal(WATER_RING, new Vector3f(impact).add(0, ground(2), 0), 0.4, radius * 2.0, 2, 3, 6, 3, 25, 1.1);
        shapes.cylinder(WALL_WATER, new Vector3f(impact).add(0, ground(1), 0), 0.4, radius * 2.0, 0.9, 2, 3, 6, 4, 30, 1.1);
        shapes.pop(WATER_FLASH, new Vector3f(impact).add(0, 0.5F, 0), 1.3, 2, 1, 3, 3);
        shapes.burst(WATER_DROP, 4, new Vector3f(impact).add(0, 0.3F, 0), radius * 0.45, 0.3, 1.1, 0.9, 2, 4, 7, 3);
        if (snare) {
            shapes.decal(VINE_RING, new Vector3f(impact).add(0, ground(1), 0), 0.6, radius * 1.7, 2, 3, 13, 4, 15, 0.0);
        }
        return effect;
    }

    private static Vector3f bezier(Vector3f a, Vector3f b, Vector3f c, float t) {
        float u = 1.0F - t;
        return new Vector3f(a).mul(u * u).add(new Vector3f(b).mul(2.0F * u * t)).add(new Vector3f(c).mul(t * t));
    }

    // ------------------------------------------------------------------ 라일락

    /**
     * 라일락 꽃가루: 맞은 자리에서 {@code yaw} 방향 {@code coneDegrees} 부채꼴을 고르게 나눠, 꽃가루 줄기 아홉 가닥이
     * 반경 끝까지 곧게 뻗습니다. 한 가닥 걸러 끝에 꽃가루 뭉치가 터집니다. 기준점은 맞은 적의 발밑입니다.
     */
    public static DisplayEffect lilacCone(float yaw, double radius, double coneDegrees, long seed) {
        DisplayEffect effect = effect("lilac_cone", 16);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        double half = Math.toRadians(Math.min(170.0, Math.max(10.0, coneDegrees)) / 2.0);
        int streaks = 9;
        for (int index = 0; index < streaks; index++) {
            double angle = -half + 2.0 * half * index / (streaks - 1);
            double reach = radius * shapes.random(0.88, 1.0);
            Vector3f from = shapes.local(Math.sin(angle) * 0.3, 0.7, Math.cos(angle) * 0.3);
            Vector3f to = shapes.local(Math.sin(angle) * reach, 0.45, Math.cos(angle) * reach);
            int lag = index % 2;
            shapes.beam(POLLEN_STREAK, from, to, 0.55, 2 + lag, 2, 7 + lag, 4);
            if (index % 2 == 0) {
                shapes.pop(POLLEN, new Vector3f(to).add(0, 0.2F, 0), 0.8, 4 + lag, 2, 8, 4);
            }
        }
        return effect;
    }

    // ------------------------------------------------------------------ 튤립 계열

    /** 튤립 계열의 자기 중심 광역: 꽃잎 고리가 반경까지 번지고 꽃잎이 사방으로 흩어집니다. 기준점은 타워 발밑입니다. */
    public static DisplayEffect tulipNova(double radius, long seed) {
        DisplayEffect effect = effect("tulip_nova", 14);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(PETAL_RING, vec(0, ground(3), 0), 0.6, radius * 2.0, 2, 4, 7, 3, 40, 1.1);
        shapes.cylinder(WALL_TULIP, vec(0, ground(1), 0), 0.6, radius * 2.0, 1.2, 2, 4, 7, 4, 40, 1.1);
        shapes.pop(TULIP_FLASH, vec(0, 1.0, 0), 1.4, 2, 1, 3, 3);
        shapes.burst(PETAL_TULIP, 8, vec(0, 0.8, 0), radius * 0.8, 0.45, 0.9, 0.6, 2, 5, 8, 4);
        return effect;
    }

    // ------------------------------------------------------------------ 잔디 지원

    /** 잔디 지원 펄스: 지원 타워 발밑에서 새싹 고리가 반경까지 번집니다. */
    public static DisplayEffect meadowPulse(double radius, long seed) {
        DisplayEffect effect = effect("meadow_pulse", 16);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(HEAL_RING, vec(0, ground(1), 0), 0.8, radius * 2.0, 2, 8, 10, 4, 20, 0.0);
        shapes.cylinder(WALL_LEAF, vec(0, ground(0), 0), 0.8, radius * 2.0, 0.7, 2, 8, 10, 4, 20, 1.0);
        return effect;
    }

    /** 회복받은 타워: 잎 두 장이 몸에서 떠오르고 작은 초록 반짝임이 한 번 터집니다. 기준점은 타워 발밑입니다. */
    public static DisplayEffect meadowHeal(long seed) {
        DisplayEffect effect = effect("meadow_heal", 16);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.pop(LEAF_FLASH, vec(0, 1.1, 0), 0.9, 2, 1, 3, 3);
        for (int index = 0; index < 2; index++) {
            double side = index == 0 ? -0.35 : 0.35;
            Vector3f from = vec(side, 0.6, shapes.random(-0.2, 0.2));
            Vector3f to = new Vector3f(from).add((float) (side * 0.5), 1.1F, 0);
            Quaternionf roll = new Quaternionf().rotateZ((float) shapes.random(0, Math.PI * 2));
            effect.part(LEAF, Pose.of(from, roll, vec(0, 0, 1)))
                    .to(2 + index, 2, Pose.of(from, roll, vec(0.45, 0.45, 1)))
                    .to(4 + index, 8, Pose.of(to, new Quaternionf(roll).rotateZ(1.2F), vec(0.4, 0.4, 1)))
                    .to(12 + index, 2, Pose.of(to, roll, vec(0, 0, 1)));
        }
        return effect;
    }

    // ------------------------------------------------------------------ 사암

    /** 사암 오라에 공속이 느려진 적: 발밑에서 모래 먼지가 두 번 피어오릅니다. 기준점은 적의 발밑입니다. */
    public static DisplayEffect sandSlow(long seed) {
        DisplayEffect effect = effect("sand_slow", 14);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        for (int index = 0; index < 2; index++) {
            Vector3f from = vec(shapes.random(-0.3, 0.3), 0.2, shapes.random(-0.3, 0.3));
            Vector3f to = new Vector3f(from).add(0, 0.6F, 0);
            Quaternionf roll = new Quaternionf().rotateZ((float) shapes.random(0, Math.PI * 2));
            effect.part(SAND_PUFF, Pose.of(from, roll, vec(0, 0, 1)))
                    .to(2 + index * 2, 3, Pose.of(from, roll, vec(0.7, 0.7, 1)))
                    .to(5 + index * 2, 6, Pose.of(to, roll, vec(0, 0, 1)));
        }
        return effect;
    }

    // ------------------------------------------------------------------ 균사 지뢰

    /** 지뢰 점화: 버섯 자리에서 붉은 섬광이 바로 한 번 번쩍입니다. 기준점은 지뢰 발밑입니다. */
    public static DisplayEffect mineFuse(long seed) {
        DisplayEffect effect = effect("mine_fuse", 8);
        Quaternionf roll = new Quaternionf();
        Vector3f at = vec(0, 0.45, 0);
        effect.part(SPORE_FLASH, Pose.of(at, roll, vec(1.1, 1.1, 1)))
                .to(2, 2, Pose.of(at, new Quaternionf(roll).rotateZ(0.6F), vec(1.5, 1.5, 1)))
                .to(4, 3, Pose.of(at, roll, vec(0, 0, 1)));
        return effect;
    }

    /** 지뢰 폭발: 포자 구름 고리가 폭발 반경까지 번지고, 섬광과 함께 포자가 솟았다 흩날립니다. 기준점은 지뢰 발밑입니다. */
    public static DisplayEffect mineBurst(double radius, long seed) {
        DisplayEffect effect = effect("mine_burst", 18);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(SPORE_RING, vec(0, ground(2), 0), 0.6, radius * 2.0, 2, 4, 9, 4, 25, 1.12);
        shapes.cylinder(WALL_SPORE, vec(0, ground(1), 0), 0.6, radius * 2.0, 1.6, 2, 4, 9, 5, 25, 1.12);
        shapes.pop(SPORE_FLASH, vec(0, 0.8, 0), Math.min(3.0, 1.2 + radius * 0.4), 2, 1, 3, 3);
        shapes.burst(SPORE, 8, vec(0, 0.5, 0), radius * 0.7, 0.35, 1.6, 0.8, 2, 6, 11, 5);
        shapes.burst(DUST_PUFF, 4, vec(0, 0.3, 0), radius * 0.4, 0.9, 0.6, 0.2, 2, 5, 8, 4);
        return effect;
    }

    // ------------------------------------------------------------------ 판다

    /**
     * 판다 몸에 붙어 따라가는 돌진 연출({@link DisplayEffect#follow}). 등 뒤로 바람 줄기 네 가닥이 늘어지고, 몸 앞에
     * 바람 초승달 두 장이 {@code durationTicks} 동안 버팁니다. 방향은 돌진을 시작할 때의 {@code yaw}로 고정합니다.
     */
    public static DisplayEffect pandaDash(float yaw, int durationTicks, long seed) {
        int last = 2 + Math.max(1, durationTicks);
        DisplayEffect effect = effect("panda_dash", last + 4);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        double[][] lines = {{-0.45, 0.5}, {0.45, 0.5}, {-0.3, 1.2}, {0.3, 1.2}};
        for (double[] line : lines) {
            shapes.beam(WIND_STREAK, shapes.local(line[0], line[1], -0.5), shapes.local(line[0] * 1.2, line[1], -2.6),
                    0.32, 2, 1, last, 3);
        }
        for (int index = 0; index < 2; index++) {
            double size = index == 0 ? 2.2 : 1.6;
            Vector3f at = shapes.local(0, index == 0 ? 0.9 : 0.35, 0.95 - size * 0.48);
            Quaternionf rotation = shapes.localRotation(0, 0, 0);
            effect.part(WIND_ARC, Pose.of(at, rotation, vec(0, 1, 0)))
                    .to(2, 1, Pose.of(at, rotation, vec(size, 1, size)))
                    .to(last, 3, Pose.of(at, rotation, vec(0, 1, 0)));
        }
        return effect;
    }

    /**
     * 돌진 경로에 남는 흙먼지. 판다가 지나가는 때에 맞춰 발밑에서 차례로 피어오릅니다. 기준점은 돌진 시작 발밑입니다.
     */
    public static DisplayEffect pandaDashTrail(float yaw, double distance, int durationTicks, long seed) {
        int puffs = 5;
        DisplayEffect effect = effect("panda_dash_trail", 2 + durationTicks + 12);
        DisplayShapes shapes = new DisplayShapes(effect, yaw, seed);
        for (int index = 0; index < puffs; index++) {
            int at = 2 + index * Math.max(1, durationTicks) / puffs;
            Vector3f foot = shapes.local(shapes.random(-0.35, 0.35), 0.25, distance * index / puffs);
            Vector3f risen = new Vector3f(foot).add(0, 0.45F, 0);
            Quaternionf roll = new Quaternionf().rotateZ((float) shapes.random(0, Math.PI * 2));
            effect.part(DUST_PUFF, Pose.of(foot, roll, vec(0, 0, 1)))
                    .to(at, 2, Pose.of(foot, roll, vec(0.8, 0.8, 1)))
                    .to(at + 2, 8, Pose.of(risen, roll, vec(0, 0, 1)));
        }
        return effect;
    }

    /** 엔티티에 붙여 띄웁니다. 예산이 차면 띄우지 않습니다. */
    static void follow(DisplayEffect effect, net.minecraft.world.entity.Entity entity) {
        if (entity != null) {
            effect.follow(entity);
        }
    }

    /** 판다 돌진이 적을 들이받은 자리: 흙먼지 고리와 대나무 잎이 튑니다. 기준점은 판다의 발밑입니다. */
    public static DisplayEffect pandaImpact(double radius, long seed) {
        DisplayEffect effect = effect("panda_impact", 12);
        DisplayShapes shapes = new DisplayShapes(effect, 0.0F, seed);
        shapes.decal(DUST_RING, vec(0, ground(2), 0), 0.5, radius * 2.0, 2, 3, 6, 3, 20, 1.1);
        shapes.burst(BAMBOO_LEAF, 4, vec(0, 0.7, 0), radius * 0.6, 0.4, 0.9, 0.7, 2, 4, 7, 3);
        shapes.burst(DUST_PUFF, 3, vec(0, 0.3, 0), radius * 0.4, 0.8, 0.4, 0.1, 2, 4, 6, 3);
        return effect;
    }
}
