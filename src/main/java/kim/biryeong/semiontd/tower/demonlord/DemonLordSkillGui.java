package kim.biryeong.semiontd.tower.demonlord;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.Optional;
import kim.biryeong.semiontd.game.PlayerEconomy;
import kim.biryeong.semiontd.tower.TowerType;
import kim.biryeong.semiontd.tower.description.TowerDescriptionRegistry;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;

/**
 * [스킬 배정] 상자 창.
 *
 * <p>첫 화면은 키 슬롯 일곱 개(1·2·3·4·마검 우클릭·F·Q)입니다. 빈 슬롯을 누르면 그 자리에 넣을
 * 스킬을 고르고, 찬 슬롯을 누르면 업그레이드하거나 빼서 전액 환불받습니다. 어느 키에 무엇을
 * 둘지 플레이어가 직접 고르므로, 예전처럼 지은 순서가 키를 정하지 않습니다.
 */
public final class DemonLordSkillGui extends SimpleGui {
    private static final int FIRST_BINDING_SLOT = 10;

    private final ServerPlayer owner;
    private final PlayerEconomy economy;
    private View view = View.overview();

    private record View(DemonLordBinding binding, boolean picking) {
        static View overview() {
            return new View(null, false);
        }
    }

    public DemonLordSkillGui(ServerPlayer owner, PlayerEconomy economy) {
        super(MenuType.GENERIC_9x4, owner, false);
        this.owner = owner;
        this.economy = economy;
        setTitle(Component.literal("스킬 배정"));
        setLockPlayerInventory(true);
        refresh();
    }

    private void refresh() {
        for (int slot = 0; slot < getSize(); slot++) {
            clearSlot(slot);
        }
        DemonLordState state = DemonLordStates.get(owner.getUUID());
        if (state == null) {
            setSlot(13, new GuiElementBuilder(Items.BARRIER)
                    .setName(Component.literal("마왕 상태가 없습니다.").withStyle(ChatFormatting.RED)));
            return;
        }
        setSlot(4, header(state));
        if (view.binding() == null) {
            drawOverview(state);
        } else if (view.picking()) {
            drawPicker(state, view.binding());
        } else {
            drawManage(state, view.binding());
        }
    }

    private GuiElementBuilder header(DemonLordState state) {
        GuiElementBuilder builder = new GuiElementBuilder(Items.DIAMOND)
                .setName(Component.literal("보유 다이아 " + economy.diamond()).withStyle(ChatFormatting.AQUA))
                .addLoreLineRaw(gray("스킬은 타워 수를 차지하지 않습니다."))
                .addLoreLineRaw(gray("같은 스킬은 한 슬롯에만 둘 수 있습니다."))
                .addLoreLineRaw(gray("스킬을 빼면 그 슬롯에 낸 다이아를 전부 돌려받습니다."));
        if (state.inCombat()) {
            builder.addLoreLineRaw(Component.literal("전투 중에는 바꿀 수 없습니다.").withStyle(ChatFormatting.RED));
        }
        return builder;
    }

    private void drawOverview(DemonLordState state) {
        int slot = FIRST_BINDING_SLOT;
        for (DemonLordBinding binding : DemonLordBinding.values()) {
            DemonLordBinding target = binding;
            Optional<DemonLordLoadout.Slot> owned = state.loadout().slot(binding);
            GuiElementBuilder builder;
            if (owned.isEmpty()) {
                builder = new GuiElementBuilder(Items.GRAY_STAINED_GLASS_PANE)
                        .setName(Component.literal("[" + binding.label() + "] 빈 슬롯").withStyle(ChatFormatting.GRAY))
                        .addLoreLineRaw(keyHint(binding))
                        .addLoreLineRaw(white("클릭: 이 슬롯에 넣을 스킬 고르기"))
                        .setCallback((index, type, action) -> open(new View(target, true)));
            } else {
                DemonLordLoadout.Slot current = owned.get();
                builder = new GuiElementBuilder(current.skill().item())
                        .setName(Component.literal("[" + binding.label() + "] " + tierName(current))
                                .withStyle(ChatFormatting.LIGHT_PURPLE))
                        .addLoreLineRaw(keyHint(binding))
                        .addLoreLineRaw(gray("쿨타임 " + seconds(DemonLordSkillShop.resolved(current.skill(), current.tier()))
                                + "초 · 낸 다이아 " + current.paid()))
                        .addLoreLineRaw(white("클릭: 업그레이드 / 빼기"))
                        .hideDefaultTooltip()
                        .setCallback((index, type, action) -> open(new View(target, false)));
            }
            setSlot(slot++, builder);
        }
        setSlot(31, closeButton());
    }

    private void drawPicker(DemonLordState state, DemonLordBinding binding) {
        DemonLordSkill[] skills = DemonLordSkill.values();
        for (int i = 0; i < skills.length; i++) {
            DemonLordSkill skill = skills[i];
            int slot = (i < 5 ? 11 : 15) + i;
            Optional<DemonLordBinding> elsewhere = state.loadout().bindingOf(skill);
            long cost = DemonLordSkillShop.purchaseCost(skill);
            boolean affordable = economy.diamond() >= cost;
            GuiElementBuilder builder = new GuiElementBuilder(skill.item())
                    .setName(Component.literal(skill.displayName()).withStyle(ChatFormatting.YELLOW))
                    .hideDefaultTooltip();
            describe(builder, DemonLordSkillShop.resolved(skill, 1));
            if (elsewhere.isPresent()) {
                builder.addLoreLineRaw(Component.literal("이미 [" + elsewhere.get().label() + "] 슬롯에 있습니다.")
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                builder.addLoreLineRaw(Component.literal("구매 " + cost + " 다이아")
                                .withStyle(affordable ? ChatFormatting.AQUA : ChatFormatting.RED))
                        .addLoreLineRaw(white("클릭: [" + binding.label() + "] 슬롯에 구매"))
                        .setCallback((index, type, action) -> {
                            if (report(DemonLordSkillShop.buy(state, economy, binding, skill))) {
                                open(View.overview());
                            }
                        });
            }
            setSlot(slot, builder);
        }
        setSlot(31, backButton());
    }

    private void drawManage(DemonLordState state, DemonLordBinding binding) {
        Optional<DemonLordLoadout.Slot> owned = state.loadout().slot(binding);
        if (owned.isEmpty()) {
            open(View.overview());
            return;
        }
        DemonLordLoadout.Slot current = owned.get();
        GuiElementBuilder info = new GuiElementBuilder(current.skill().item())
                .setName(Component.literal("[" + binding.label() + "] " + tierName(current))
                        .withStyle(ChatFormatting.LIGHT_PURPLE))
                .hideDefaultTooltip();
        describe(info, DemonLordSkillShop.resolved(current.skill(), current.tier()));
        setSlot(13, info);

        if (current.maxTier()) {
            setSlot(11, new GuiElementBuilder(Items.NETHER_STAR)
                    .setName(Component.literal("최고 티어입니다.").withStyle(ChatFormatting.GOLD)));
        } else {
            long cost = DemonLordSkillShop.upgradeCost(current.skill(), current.tier());
            GuiElementBuilder upgrade = new GuiElementBuilder(Items.EXPERIENCE_BOTTLE)
                    .setName(Component.literal("업그레이드 → " + (current.tier() + 1) + "티어")
                            .withStyle(ChatFormatting.GREEN))
                    .addLoreLineRaw(Component.literal("비용 " + cost + " 다이아")
                            .withStyle(economy.diamond() >= cost ? ChatFormatting.AQUA : ChatFormatting.RED));
            describe(upgrade, DemonLordSkillShop.resolved(current.skill(), current.tier() + 1));
            upgrade.setCallback((index, type, action) -> {
                report(DemonLordSkillShop.upgrade(state, economy, binding));
                refresh();
            });
            setSlot(11, upgrade);
        }

        setSlot(15, new GuiElementBuilder(Items.LAVA_BUCKET)
                .setName(Component.literal("빼기").withStyle(ChatFormatting.RED))
                .addLoreLineRaw(Component.literal("환불 " + current.paid() + " 다이아 (전액)").withStyle(ChatFormatting.AQUA))
                .setCallback((index, type, action) -> {
                    if (report(DemonLordSkillShop.remove(state, economy, binding))) {
                        open(View.overview());
                    }
                }));
        setSlot(31, backButton());
    }

    private void open(View next) {
        view = next;
        refresh();
    }

    private boolean report(DemonLordSkillShop.Result result) {
        boolean success = result == DemonLordSkillShop.Result.SUCCESS;
        owner.displayClientMessage(success
                ? SemionText.prefixedMini("<green>스킬 배정을 바꿨습니다.</green>")
                : SemionText.prefixedError(result.message()), false);
        owner.playNotifySound(success ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.VILLAGER_NO,
                SoundSource.PLAYERS, 0.7f, 1.0f);
        return success;
    }

    private static void describe(GuiElementBuilder builder, TowerType type) {
        for (String line : TowerDescriptionRegistry.describe(type).orElse(type.description())) {
            builder.addLoreLineRaw(SemionText.mini(line));
        }
    }

    private static String tierName(DemonLordLoadout.Slot slot) {
        return slot.skill().displayName() + " " + slot.tier() + "티어";
    }

    private static String seconds(TowerType type) {
        return String.format("%.1f", DemonLordTowers.cooldownTicks(type) / 20.0);
    }

    private static Component keyHint(DemonLordBinding binding) {
        String hint = switch (binding) {
            case SLOT_1, SLOT_2, SLOT_3, SLOT_4 -> binding.label() + "번 키를 누르면 바로 시전합니다.";
            case RIGHT_CLICK -> "마검을 든 채 우클릭해 조준 시전합니다.";
            case OFFHAND -> "F(손 바꾸기) 키로 시전합니다.";
            case DROP -> "Q(버리기) 키로 시전합니다.";
        };
        return gray(hint);
    }

    private GuiElementBuilder backButton() {
        return new GuiElementBuilder(Items.ARROW)
                .setName(Component.literal("← 슬롯 목록").withStyle(ChatFormatting.WHITE))
                .setCallback((index, type, action) -> open(View.overview()));
    }

    private GuiElementBuilder closeButton() {
        return new GuiElementBuilder(Items.BARRIER)
                .setName(Component.literal("닫기").withStyle(ChatFormatting.RED))
                .setCallback((index, type, action) -> close());
    }

    private static Component gray(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }

    private static Component white(String text) {
        return Component.literal(text).withStyle(ChatFormatting.WHITE);
    }
}
