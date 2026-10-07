package kim.biryeong.semiontd.ui.augment;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import java.util.function.BooleanSupplier;
import java.util.function.ToIntFunction;
import kim.biryeong.semiontd.augment.AugmentService;
import kim.biryeong.semiontd.ui.SemionText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.resources.Identifier;

public final class AugmentOfferGui extends SimpleGui {
    private final AugmentService.Screen screen;
    private final BooleanSupplier valid;
    private final ToIntFunction<String> execute;
    private final long animationStarted;
    private final AugmentHudTitle title;
    private final boolean canReroll;
    private int renderedFrame = -1;
    private boolean dispatching;
    private boolean closed;
    private boolean ready;

    public AugmentOfferGui(ServerPlayer player, AugmentService.Screen screen, long animationStarted,
                           BooleanSupplier valid, ToIntFunction<String> execute, boolean canReroll) {
        this(player, screen, animationStarted, valid, execute, canReroll, AugmentHudTitle.create(player, screen));
    }

    AugmentOfferGui(ServerPlayer player, AugmentService.Screen screen, long animationStarted,
                    BooleanSupplier valid, ToIntFunction<String> execute, boolean canReroll, AugmentHudTitle title) {
        super(MenuType.GENERIC_9x6, player, false);
        if (screen.cards().size() != 3) throw new IllegalArgumentException("Three cards required");
        this.screen = screen;
        this.animationStarted = animationStarted;
        this.valid = valid;
        this.execute = execute;
        this.canReroll = canReroll;
        this.title = title;
        setLockPlayerInventory(true);
        setTitle(SemionText.mini(screen.title()));
        for (int slot = 0; slot < 45; slot++) {
            int card = AugmentOfferLayout.cardAt(slot);
            var button = screen.buttons().get(card);
            var element = new GuiElementBuilder(Items.PAPER)
                    .setName(SemionText.mini(screen.cards().get(card).definition().rarity()
                            .markup(screen.cards().get(card).definition().displayName())))
                    .setComponent(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("air"))
                    .setCallback((index, type, action, gui) -> activate(card));
            for (String line : button.description().split("\n")) element.addLoreLine(Component.literal(line));
            element.addLoreLine(Component.literal("카드 영역을 클릭하여 선택").withStyle(ChatFormatting.YELLOW));
            setSlot(slot, element);
        }
        var reroll = screen.buttons().get(3);
        var rerollElement = new GuiElementBuilder(canReroll ? Items.SUNFLOWER : Items.BARRIER)
                .setName(Component.literal(reroll.label()).withStyle(canReroll ? ChatFormatting.GOLD : ChatFormatting.GRAY))
                .addLoreLine(Component.literal(reroll.description()));
        if (canReroll) rerollElement.setCallback((slot, type, action, gui) -> activate(3));
        setSlot(AugmentOfferLayout.REROLL_SLOT, rerollElement);
        int[] navigationSlots = {45, 47, 53};
        for (int i = 4; i < screen.buttons().size() && i < 7; i++) {
            int action = i;
            var button = screen.buttons().get(i);
            setSlot(navigationSlots[i - 4], new GuiElementBuilder(i == 6 ? Items.BARRIER : Items.BOOK)
                    .setName(Component.literal(button.label()))
                    .setCallback((slot, type, input, gui) -> activate(action)));
        }
        updateTitle();
    }

    public String actionCommand(int action) {
        return screen.buttons().get(action).command();
    }

    public boolean openOffer() {
        if (!ready) {
            if (title != null) title.close();
            closed = true;
            return false;
        }
        return open();
    }

    public int activate(int action) {
        if (closed || dispatching || !ready || !isOpen() || !valid.getAsBoolean()
                || System.nanoTime() - animationStarted < AugmentOfferLayout.REVEAL_NANOS) return 0;
        if (action < 0 || action >= screen.buttons().size() || action == 3 && !canReroll) return 0;
        dispatching = true;
        close();
        return execute.applyAsInt(actionCommand(action));
    }

    private void updateTitle() {
        if (title == null || !title.available()) {
            ready = false;
            return;
        }
        long elapsed = Math.max(0, System.nanoTime() - animationStarted);
        int frame = (int) Math.min(9, elapsed / AugmentOfferLayout.STEP_NANOS);
        if (frame == renderedFrame) return;
        Component rendered = title.render(elapsed);
        ready = rendered != null && !rendered.getString().isEmpty();
        if (ready) {
            setTitle(rendered);
            renderedFrame = frame;
        }
    }

    @Override
    public void onTick() {
        if (!valid.getAsBoolean()) close();
        else {
            updateTitle();
            if (!ready) close();
        }
    }

    @Override
    public void onRemoved() {
        closed = true;
        if (title != null) title.close();
    }

    @Override
    public void onManualClose() {
        onRemoved();
    }

    @Override
    public void onPlayerClose(boolean success) {
        if (success) onRemoved();
    }
}
