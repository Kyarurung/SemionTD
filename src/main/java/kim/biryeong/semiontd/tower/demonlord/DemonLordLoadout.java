package kim.biryeong.semiontd.tower.demonlord;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * 어느 키 슬롯에 어떤 스킬이 몇 티어로 들어 있는지.
 *
 * <p>스킬은 레인에 짓는 타워가 아니라 [스킬 배정] 창에서 키 슬롯(1~4, 마검 우클릭, F, Q)에 직접
 * 사는 것입니다. 타워 수를 차지하지 않고, 같은 스킬은 한 슬롯에만 둘 수 있습니다.
 *
 * <p>{@link Slot#paid()}는 이 슬롯에 지금까지 낸 다이아 전부입니다. 빼면 이 값을 그대로 돌려주므로,
 * 업그레이드에 낸 값까지 전액 환불됩니다.
 */
public final class DemonLordLoadout {
    public record Slot(DemonLordSkill skill, int tier, long paid) {
        public Slot {
            if (skill == null) {
                throw new IllegalArgumentException("skill");
            }
            tier = Math.max(1, Math.min(DemonLordSkill.MAX_TIER, tier));
            paid = Math.max(0L, paid);
        }

        public boolean maxTier() {
            return tier >= DemonLordSkill.MAX_TIER;
        }
    }

    private final EnumMap<DemonLordBinding, Slot> slots = new EnumMap<>(DemonLordBinding.class);

    public DemonLordLoadout() {
    }

    public DemonLordLoadout(Map<DemonLordBinding, Slot> restored) {
        if (restored != null) {
            restored.forEach((binding, slot) -> {
                if (binding != null && slot != null && bindingOf(slot.skill()).isEmpty()) {
                    slots.put(binding, slot);
                }
            });
        }
    }

    public Optional<Slot> slot(DemonLordBinding binding) {
        return binding == null ? Optional.empty() : Optional.ofNullable(slots.get(binding));
    }

    public Optional<DemonLordBinding> bindingOf(DemonLordSkill skill) {
        return slots.entrySet().stream()
                .filter(entry -> entry.getValue().skill() == skill)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public boolean contains(DemonLordSkill skill) {
        return bindingOf(skill).isPresent();
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** 슬롯 순서(1 → Q)대로 정렬된 사본. */
    public Map<DemonLordBinding, Slot> view() {
        return new EnumMap<>(slots);
    }

    /** 빈 슬롯에 1티어로 넣습니다. 슬롯이 차 있거나 같은 스킬이 이미 있으면 거절합니다. */
    boolean assign(DemonLordBinding binding, DemonLordSkill skill, long paid) {
        if (binding == null || skill == null || slots.containsKey(binding) || contains(skill)) {
            return false;
        }
        slots.put(binding, new Slot(skill, 1, paid));
        return true;
    }

    /** 한 티어 올리고 낸 값을 누적합니다. */
    boolean upgrade(DemonLordBinding binding, long paid) {
        Slot current = slots.get(binding);
        if (current == null || current.maxTier()) {
            return false;
        }
        slots.put(binding, new Slot(current.skill(), current.tier() + 1, current.paid() + Math.max(0L, paid)));
        return true;
    }

    /** 슬롯을 비우고 빠진 내용을 돌려줍니다. 환불액은 {@link Slot#paid()}입니다. */
    Optional<Slot> remove(DemonLordBinding binding) {
        return binding == null ? Optional.empty() : Optional.ofNullable(slots.remove(binding));
    }
}
