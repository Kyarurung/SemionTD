package kim.biryeong.semiontd.tower.demonlord;

import java.util.Optional;
import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.tower.ProductionTowerCatalog;
import kim.biryeong.semiontd.tower.TowerType;

/**
 * [스킬 배정] 창의 거래 규칙.
 *
 * <p>가격은 예전 제단 값을 그대로 씁니다. 1티어 구매가는 1티어 제단의 설치 비용, 업그레이드는
 * 제단 업그레이드 비용이라 {@code tower_balance.json}을 바꾸면 여기에도 바로 반영됩니다.
 * 다이아로만 사고 타워 수는 차지하지 않습니다.
 *
 * <p>전투 중에는 바꿀 수 없습니다. 핫바가 스킬로 덮여 있는 동안 슬롯을 흔들면 쥐고 있던 키가
 * 다른 스킬로 바뀌어 버립니다.
 */
public final class DemonLordSkillShop {
    public enum Result {
        SUCCESS("완료했습니다."),
        IN_COMBAT("전투 중에는 스킬을 바꿀 수 없습니다."),
        NOT_ENOUGH_DIAMOND("다이아가 부족합니다."),
        SLOT_OCCUPIED("이미 스킬이 들어 있는 슬롯입니다."),
        SKILL_ALREADY_SLOTTED("이미 다른 슬롯에 있는 스킬입니다."),
        SLOT_EMPTY("빈 슬롯입니다."),
        MAX_TIER("이미 최고 티어입니다."),
        PASSIVE_ALREADY_SLOTTED("이미 다른 자리에 있는 패시브입니다."),
        PACT_LOCKED("파멸의 계약은 시작된 뒤에는 뺄 수 없습니다."),
        UNAVAILABLE("지금은 스킬을 배정할 수 없습니다.");

        private final String message;

        Result(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    private DemonLordSkillShop() {
    }

    /** 이 스킬을 1티어로 사는 값. */
    public static long purchaseCost(DemonLordSkill skill) {
        return Math.max(0L, resolved(skill, 1).mineralCost());
    }

    /** {@code tier}에서 한 단계 올리는 값. 최고 티어면 0. */
    public static long upgradeCost(DemonLordSkill skill, int tier) {
        if (skill == null || tier >= DemonLordSkill.MAX_TIER) {
            return 0L;
        }
        return Math.max(0L, TowerBalanceRuntime.upgradeCost(
                resolved(skill, tier), DemonLordTowers.tower(skill, tier + 1).id()));
    }

    /** 스킬 수치를 읽을 운반체 타입. 설정이 다시 읽히면 카탈로그 쪽이 새 값입니다. */
    public static TowerType resolved(DemonLordSkill skill, int tier) {
        TowerType base = DemonLordTowers.tower(skill, tier);
        return ProductionTowerCatalog.find(base.id())
                .map(ProductionTowerCatalog.CatalogEntry::type)
                .orElse(base);
    }

    public static Result buy(DemonLordState state, PlayerEconomy economy, DemonLordBinding binding, DemonLordSkill skill) {
        Result blocked = blocked(state, economy, binding);
        if (blocked != null) {
            return blocked;
        }
        if (skill == null) {
            return Result.UNAVAILABLE;
        }
        DemonLordLoadout loadout = state.loadout();
        if (loadout.slot(binding).isPresent()) {
            return Result.SLOT_OCCUPIED;
        }
        if (loadout.contains(skill)) {
            return Result.SKILL_ALREADY_SLOTTED;
        }
        long cost = purchaseCost(skill);
        if (!economy.spendDiamond(cost)) {
            return Result.NOT_ENOUGH_DIAMOND;
        }
        loadout.assign(binding, skill, cost);
        state.markLoadoutDirty();
        return Result.SUCCESS;
    }

    public static Result upgrade(DemonLordState state, PlayerEconomy economy, DemonLordBinding binding) {
        Result blocked = blocked(state, economy, binding);
        if (blocked != null) {
            return blocked;
        }
        Optional<DemonLordLoadout.Slot> slot = state.loadout().slot(binding);
        if (slot.isEmpty()) {
            return Result.SLOT_EMPTY;
        }
        if (slot.get().maxTier()) {
            return Result.MAX_TIER;
        }
        long cost = upgradeCost(slot.get().skill(), slot.get().tier());
        if (!economy.spendDiamond(cost)) {
            return Result.NOT_ENOUGH_DIAMOND;
        }
        state.loadout().upgrade(binding, cost);
        state.markLoadoutDirty();
        return Result.SUCCESS;
    }

    /** 슬롯을 비우고 그 슬롯에 낸 다이아를 전부 돌려줍니다. */
    public static Result remove(DemonLordState state, PlayerEconomy economy, DemonLordBinding binding) {
        Result blocked = blocked(state, economy, binding);
        if (blocked != null) {
            return blocked;
        }
        Optional<DemonLordLoadout.Slot> removed = state.loadout().remove(binding);
        if (removed.isEmpty()) {
            return Result.SLOT_EMPTY;
        }
        economy.addDiamond(removed.get().paid());
        state.markLoadoutDirty();
        return Result.SUCCESS;
    }

    public static Result buyPassive(DemonLordState state, PlayerEconomy economy, DemonLordPassiveSlot slot,
            DemonLordPassive passive) {
        if (state == null || economy == null || slot == null || passive == null) {
            return Result.UNAVAILABLE;
        }
        if (state.inCombat()) {
            return Result.IN_COMBAT;
        }
        DemonLordLoadout loadout = state.loadout();
        if (loadout.passive(slot).isPresent()) {
            return Result.SLOT_OCCUPIED;
        }
        if (loadout.hasPassive(passive)) {
            return Result.PASSIVE_ALREADY_SLOTTED;
        }
        long cost = passive.cost();
        if (!economy.spendDiamond(cost)) {
            return Result.NOT_ENOUGH_DIAMOND;
        }
        loadout.assignPassive(slot, passive, cost);
        if (passive == DemonLordPassive.DOOM_PACT) {
            state.resetPactCount();
        }
        state.markLoadoutDirty();
        return Result.SUCCESS;
    }

    /** 패시브를 빼고 그 자리에 낸 다이아를 전부 돌려줍니다. */
    public static Result removePassive(DemonLordState state, PlayerEconomy economy, DemonLordPassiveSlot slot) {
        if (state == null || economy == null || slot == null) {
            return Result.UNAVAILABLE;
        }
        if (state.inCombat()) {
            return Result.IN_COMBAT;
        }
        Optional<DemonLordLoadout.PassiveEntry> current = state.loadout().passive(slot);
        if (current.isPresent() && current.get().passive() == DemonLordPassive.DOOM_PACT && state.pactRoundsServed() > 0) {
            return Result.PACT_LOCKED;
        }
        Optional<DemonLordLoadout.PassiveEntry> removed = state.loadout().removePassive(slot);
        if (removed.isEmpty()) {
            return Result.SLOT_EMPTY;
        }
        if (removed.get().passive() == DemonLordPassive.DOOM_PACT) {
            state.resetPactCount();
        }
        economy.addDiamond(removed.get().paid());
        state.markLoadoutDirty();
        return Result.SUCCESS;
    }

    private static Result blocked(DemonLordState state, PlayerEconomy economy, DemonLordBinding binding) {
        if (state == null || economy == null || binding == null) {
            return Result.UNAVAILABLE;
        }
        return state.inCombat() ? Result.IN_COMBAT : null;
    }
}
