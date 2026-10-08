package kim.biryeong.semiontd.vfx;

import eu.pb4.polymer.virtualentity.api.ElementHolder;
import eu.pb4.polymer.virtualentity.api.attachment.ChunkAttachment;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import eu.pb4.polymer.virtualentity.api.elements.ItemDisplayElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 디스플레이 엔티티로 짠 연출 하나.
 *
 * <p>파티클을 흩뿌리는 대신, 직접 그린 텍스처 판({@link DisplaySprite})을 입힌 아이템 디스플레이 여러 개(파츠)를
 * 키프레임으로 움직입니다.
 * 클라이언트가 변환(위치·회전·크기)을 틱 사이에서 부드럽게 보간하므로, 서버는 키프레임이 바뀌는 틱에만
 * 목표 자세와 보간 시간을 보냅니다. 엔티티는 Polymer 가상 엔티티라 서버 월드에는 아무것도 생기지 않습니다.
 *
 * <p>이 클래스 자체는 순수한 데이터(파츠와 키프레임)입니다. {@link #spawn}이나 {@link #follow}를 불러야
 * 실제로 보입니다. 그래서 같은 데이터를 시험과 미리보기 페이지 내보내기에도 그대로 씁니다.
 *
 * <p>좌표는 연출 기준점(시전 위치 또는 따라가는 엔티티의 발) 기준 블록 단위입니다. 스프라이트 모델은 원점
 * 가운데에 1×1 판으로 그려지므로, 자세의 위치는 그 판의 중심이고 크기는 판의 가로·세로(블록)입니다.
 */
public final class DisplayEffect {
    /** 동시에 떠 있을 수 있는 연출 수. 넘치면 새 연출을 건너뛰어 클라이언트가 버벅이지 않게 합니다. */
    public static final int MAX_ACTIVE = 48;
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    /** 유닛(침공군) 공격·능력 연출의 예산. 스킬 연출과 따로 세어 서로 자리를 뺏지 않습니다. */
    public static final int MAX_UNIT_ACTIVE = 96;
    private static final AtomicInteger UNIT_ACTIVE = new AtomicInteger();
    /** 타워(식물 등) 공격·능력 연출의 예산. 스킬·유닛 연출과 따로 셉니다. */
    public static final int MAX_TOWER_ACTIVE = 96;
    private static final AtomicInteger TOWER_ACTIVE = new AtomicInteger();

    /** 어느 예산으로 세는지. */
    private enum Budget {
        SKILL, UNIT, TOWER;

        AtomicInteger counter() {
            return switch (this) {
                case SKILL -> ACTIVE;
                case UNIT -> UNIT_ACTIVE;
                case TOWER -> TOWER_ACTIVE;
            };
        }

        int limit() {
            return switch (this) {
                case SKILL -> MAX_ACTIVE;
                case UNIT -> MAX_UNIT_ACTIVE;
                case TOWER -> MAX_TOWER_ACTIVE;
            };
        }
    }
    private static final float CULL_SIZE = 32.0F;

    private final String id;
    private final int lifetime;
    private final List<Part> parts = new ArrayList<>();
    private Budget budget = Budget.SKILL;

    public DisplayEffect(String id, int lifetime) {
        this.id = id;
        this.lifetime = Math.max(1, lifetime);
    }

    public String id() {
        return id;
    }

    /** 유닛 연출 예산({@link #MAX_UNIT_ACTIVE})으로 셉니다. 몹이 자주 띄우는 짧은 연출에 씁니다. */
    public DisplayEffect unitBudget() {
        this.budget = Budget.UNIT;
        return this;
    }

    /** 타워 연출 예산({@link #MAX_TOWER_ACTIVE})으로 셉니다. 타워가 공격마다 띄우는 짧은 연출에 씁니다. */
    public DisplayEffect towerBudget() {
        this.budget = Budget.TOWER;
        return this;
    }

    public int lifetime() {
        return lifetime;
    }

    public List<Part> parts() {
        return List.copyOf(parts);
    }

    /** 새 파츠. {@code initial}이 틱 0의 자세이고, 그 뒤는 {@link Part#to}로 이어 붙입니다. */
    public Part part(DisplaySprite sprite, Pose initial) {
        Part part = new Part(sprite, initial);
        parts.add(part);
        return part;
    }

    /**
     * 기준점을 중심으로 통째로 {@code factor}배 키운 사본. 위치와 크기가 함께 커집니다.
     *
     * <p>범위 수치를 따로 받지 않는 연출(발밑 마법진, 소환진 등)에 스킬 범위 스탯을 반영할 때 씁니다.
     * 땅에 깐 장({@link #GROUND_BAND} 이하 높이)은 높이를 그대로 두어, 줄여도 땅에 파묻히거나 키워도 떠 보이지 않게 합니다.
     */
    /** 이 높이 이하에 놓인 장은 땅에 깐 것으로 봅니다({@link DisplayShapes#ground}). */
    public static final float GROUND_BAND = 0.1F;

    public DisplayEffect scaled(double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0 || Math.abs(factor - 1.0) < 1.0e-6) {
            return this;
        }
        float f = (float) factor;
        DisplayEffect copy = new DisplayEffect(id, lifetime);
        copy.budget = budget;
        for (Part part : parts) {
            Part scaledPart = null;
            for (Frame frame : part.frames) {
                Pose pose = frame.pose();
                Vector3f moved = new Vector3f(pose.translation()).mul(f);
                if (Math.abs(pose.translation().y) <= GROUND_BAND) {
                    moved.y = pose.translation().y;
                }
                Pose resized = new Pose(moved, pose.rotation(),
                        new Vector3f(pose.scale()).mul(f), pose.right());
                if (scaledPart == null) {
                    scaledPart = copy.part(part.sprite, resized);
                } else {
                    scaledPart.frames.add(new Frame(frame.tick(), frame.duration(), resized));
                }
            }
        }
        return copy;
    }

    public static int activeCount() {
        return ACTIVE.get();
    }

    /** 고정된 자리에 띄웁니다. 예산을 넘으면 띄우지 않고 {@code false}입니다. */
    public boolean spawn(ServerLevel level, Vec3 origin) {
        if (parts.isEmpty() || !reserve(budget)) {
            return false;
        }
        ChunkAttachment.ofTicking(new Runtime(this), level, origin);
        return true;
    }

    /** 엔티티를 따라다니게 띄웁니다(날개·방어막처럼 몸에 붙는 연출). */
    public boolean follow(Entity entity) {
        if (parts.isEmpty() || !reserve(budget)) {
            return false;
        }
        EntityAttachment.ofTicking(new Runtime(this), entity);
        return true;
    }

    private static boolean reserve(Budget budget) {
        AtomicInteger counter = budget.counter();
        int limit = budget.limit();
        while (true) {
            int current = counter.get();
            if (current >= limit) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void release() {
        budget.counter().decrementAndGet();
    }

    /** 미리보기 페이지용 JSON. 좌표는 기준점 기준, 회전은 쿼터니언 (x, y, z, w)입니다. */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\"id\":\"").append(id).append("\",\"lifetime\":").append(lifetime).append(",\"parts\":[");
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            parts.get(index).appendJson(json);
        }
        return json.append("]}").toString();
    }

    /** 파츠의 한 자세. 마인크래프트 디스플레이 변환의 이동·왼쪽 회전·크기에 그대로 들어갑니다. */
    /**
     * 디스플레이 변환 = 이동 · 왼쪽 회전 · 크기 · 오른쪽 회전. 오른쪽 회전은 크기보다 먼저 모델에 걸리므로,
     * 정육면체를 모서리가 위로 서게 돌린 뒤 세로로 늘려 뾰족한 가시를 만드는 데 씁니다.
     */
    public record Pose(Vector3f translation, Quaternionf rotation, Vector3f scale, Quaternionf right) {
        public Pose {
            translation = new Vector3f(translation);
            rotation = new Quaternionf(rotation);
            scale = new Vector3f(scale);
            right = new Quaternionf(right);
        }

        public static Pose of(Vector3f translation, Quaternionf rotation, Vector3f scale) {
            return new Pose(translation, rotation, scale, new Quaternionf());
        }

        public Pose moved(Vector3f translation) {
            return new Pose(translation, rotation, scale, right);
        }

        public Pose rotated(Quaternionf rotation) {
            return new Pose(translation, rotation, scale, right);
        }

        public Pose scaled(Vector3f scale) {
            return new Pose(translation, rotation, scale, right);
        }

        public Pose withRight(Quaternionf right) {
            return new Pose(translation, rotation, scale, right);
        }

        /** 같은 자리에서 크기만 0으로 줄인 자세. 사라지거나 나타날 때 씁니다. */
        public Pose hidden() {
            return scaled(new Vector3f());
        }
    }

    /** {@code tick}에 {@code pose}로 향하기 시작해 {@code duration}틱 동안 보간합니다. */
    public record Frame(int tick, int duration, Pose pose) {
    }

    public final class Part {
        private final DisplaySprite sprite;
        private final List<Frame> frames = new ArrayList<>();

        private Part(DisplaySprite sprite, Pose initial) {
            this.sprite = sprite;
            frames.add(new Frame(0, 0, initial));
        }

        /**
         * {@code tick}부터 {@code duration}틱에 걸쳐 {@code pose}로 옮깁니다.
         *
         * <p>클라이언트가 처음 자세를 받은 뒤에 보간을 시작해야 하므로 틱 2 이전의 키프레임은 틱 2로 미룹니다.
         */
        public Part to(int tick, int duration, Pose pose) {
            int start = Math.max(2, Math.min(tick, lifetime));
            frames.add(new Frame(start, Math.max(0, duration), pose));
            frames.sort(Comparator.comparingInt(Frame::tick));
            return this;
        }

        public DisplaySprite sprite() {
            return sprite;
        }

        public List<Frame> frames() {
            return List.copyOf(frames);
        }

        public Pose last() {
            return frames.getLast().pose();
        }

        private void appendJson(StringBuilder json) {
            json.append("{\"sprite\":\"").append(sprite.name())
                    .append("\",\"shape\":\"").append(sprite.shape().name().toLowerCase(java.util.Locale.ROOT))
                    .append("\",\"billboard\":").append(sprite.billboard()).append(",\"frames\":[");
            for (int index = 0; index < frames.size(); index++) {
                Frame frame = frames.get(index);
                if (index > 0) {
                    json.append(',');
                }
                Pose pose = frame.pose();
                json.append("{\"t\":").append(frame.tick()).append(",\"d\":").append(frame.duration())
                        .append(",\"p\":[").append(num(pose.translation().x)).append(',').append(num(pose.translation().y))
                        .append(',').append(num(pose.translation().z))
                        .append("],\"q\":[").append(num(pose.rotation().x)).append(',').append(num(pose.rotation().y))
                        .append(',').append(num(pose.rotation().z)).append(',').append(num(pose.rotation().w))
                        .append("],\"s\":[").append(num(pose.scale().x)).append(',').append(num(pose.scale().y))
                        .append(',').append(num(pose.scale().z))
                        .append("],\"r\":[").append(num(pose.right().x)).append(',').append(num(pose.right().y))
                        .append(',').append(num(pose.right().z)).append(',').append(num(pose.right().w)).append("]}");
            }
            json.append("]}");
        }
    }

    private static String num(float value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value);
    }

    /** 실제로 띄운 연출. 매 틱 그 틱의 키프레임을 적용하고, 수명이 다하면 스스로 사라집니다. */
    private static final class Runtime extends ElementHolder {
        private final DisplayEffect effect;
        private final List<ItemDisplayElement> elements = new ArrayList<>();
        private final int[] cursors;
        private int age;
        private boolean released;

        private Runtime(DisplayEffect effect) {
            this.effect = effect;
            this.cursors = new int[effect.parts.size()];
            for (Part part : effect.parts) {
                ItemStack stack = new ItemStack(Items.PAPER);
                stack.set(DataComponents.ITEM_MODEL, part.sprite.itemModel());
                ItemDisplayElement element = new ItemDisplayElement(stack);
                element.setItemDisplayContext(ItemDisplayContext.NONE);
                if (part.sprite.billboard()) {
                    element.setBillboardMode(Display.BillboardConstraints.CENTER);
                }
                element.setDisplaySize(CULL_SIZE, CULL_SIZE);
                element.setShadowRadius(0.0F);
                element.setShadowStrength(0.0F);
                element.setViewRange(2.0F);
                // 직접 그린 빛나는 텍스처라 주변 밝기와 상관없이 항상 최대 밝기로 그립니다.
                element.setBrightness(new Brightness(15, 15));
                apply(element, part.frames.getFirst());
                elements.add(element);
                addElement(element);
            }
            for (int index = 0; index < cursors.length; index++) {
                cursors[index] = 1;
            }
        }

        @Override
        protected void onTick() {
            if (released) {
                return;
            }
            age++;
            if (age > effect.lifetime) {
                released = true;
                effect.release();
                destroy();
                return;
            }
            for (int index = 0; index < elements.size(); index++) {
                List<Frame> frames = effect.parts.get(index).frames;
                Frame due = null;
                while (cursors[index] < frames.size() && frames.get(cursors[index]).tick() <= age) {
                    due = frames.get(cursors[index]);
                    cursors[index]++;
                }
                if (due != null) {
                    ItemDisplayElement element = elements.get(index);
                    apply(element, due);
                    // 키프레임은 서버 틱으로 짰지만 보간 시간은 클라이언트가 자기 틱(초당 20)으로 셉니다. 전투 배속으로
                    // 서버가 빨라지면 보간만 느려져 다음 키프레임에 밀리므로, 같은 실제 시간이 되도록 줄여 보냅니다.
                    element.setInterpolationDuration(kim.biryeong.semiontd.game.ClientTickScale.toClientTicks(
                            getAttachment() == null ? null : getAttachment().getWorld().getServer(),
                            getAttachment() == null ? null : getAttachment().getWorld(), due.duration()));
                    element.startInterpolation();
                }
            }
        }

        @Override
        public void destroy() {
            if (!released) {
                released = true;
                effect.release();
            }
            super.destroy();
        }

        private static void apply(ItemDisplayElement element, Frame frame) {
            element.setInterpolationDuration(frame.duration());
            element.setTranslation(frame.pose().translation());
            element.setLeftRotation(frame.pose().rotation());
            element.setScale(frame.pose().scale());
            element.setRightRotation(frame.pose().right());
        }
    }
}
