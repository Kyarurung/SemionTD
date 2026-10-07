package kim.biryeong.semiontd.augment;

import eu.pb4.sgui.api.ClickType;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import kim.biryeong.semiontd.game.RoundPhase;
import kim.biryeong.semiontd.ui.augment.AugmentOfferGui;
import kim.biryeong.semiontd.ui.augment.AugmentOfferGuiFixture;
import kim.biryeong.semiontd.ui.augment.AugmentOfferLayout;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.ContainerInput;

public final class AugmentOfferPresentationTest extends AugmentControllerFixture {
    private static final String COMMAND = "/semiontd augment ";

    @GameTest
    public void threeColumnGuiPacketsKeepLiveCommandsTooltipsAndStaleInputGuards(GameTestHelper context) {
        for (String schedule : List.of("SSS", "GGG", "PPP")) {
            var online = context.makeMockServerPlayerInLevel();
            var game = prepare(context, online, schedule);
            var originalConnection = online.connection;
            try {
                int tier = schedule.equals("SSS") ? 1 : schedule.equals("GGG") ? 2 : 3;
                String rarity = tier == 1 ? "silver" : tier == 2 ? "gold" : "prismatic";
                force(game, online, "beneficial_effect_" + tier + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var packets = new ArrayList<Packet<?>>();
                online.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(online.level().getServer(),
                        new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), online,
                        net.minecraft.server.network.CommonListenerCookie.createInitial(online.getGameProfile(), false)) {
                    @Override public void send(Packet<?> packet) {
                        packets.add(packet);
                    }
                };
                var player = game.players().get(online.getUUID());
                var state = player.augments();
                var offer = state.currentOffer().orElseThrow();
                var screen = game.augmentService().offerScreen(game, player, offer, true);
                require(screen.columns() == 3 && screen.cards().size() == 3 && screen.buttons().size() == 9,
                        "Three cards, three independent rerolls and three navigation actions must remain.");
                try (var fixture = new AugmentOfferGuiFixture(online, screen, revealed(),
                        () -> game.phase() == RoundPhase.PREPARE_AND_SUMMON
                                && game.currentTick() < offer.deadlineTickExclusive()
                                && state.currentOffer().map(current -> current.revision() == offer.revision()).orElse(false),
                        command -> game.augmentService().handle(game, online, command.substring(COMMAND.length()), false), true)) {
                    var gui = fixture.gui;
                    require(gui.openOffer(), "A prepared title must open the actual SGUI container.");
                    require(packets.stream().anyMatch(ClientboundOpenScreenPacket.class::isInstance), "The GUI must send an open-screen packet.");
                    require(packets.stream().anyMatch(ClientboundContainerSetContentPacket.class::isInstance), "The GUI must send its interactive slots.");
                    for (int cardIndex = 0; cardIndex < 3; cardIndex++) {
                        String command = gui.actionCommand(cardIndex);
                        require(command.startsWith(COMMAND + "session ")
                                        && command.contains(" draft " + offer.revision() + " " + cardIndex + " "),
                                "Each card keeps the live session, offer revision and slot.");
                        var card = AugmentCatalog.find(offer.cardIds().get(cardIndex)).orElseThrow();
                        var lore = gui.getGuiElement(cardIndex * 3).getItemStack().get(DataComponents.LORE);
                        require(lore != null && lore.lines().stream().map(net.minecraft.network.chat.Component::getString)
                                        .collect(java.util.stream.Collectors.joining("\n"))
                                        .contains(AugmentDescriptions.describe(card, game.augmentConfig())),
                                "The card hit area retains its complete configured tooltip.");
                    }
                    for (int slot = 0; slot < 45; slot++) require(gui.getGuiElement(slot) != null, "Every card pixel region needs a click target.");
                    gui.click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                    require(state.selections().size() == 1, "The real SGUI slot callback must invoke the live service selection.");
                    gui.click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                    require(gui.activate(1) == 0, "Closed GUI callbacks cannot select another card.");
                    require(game.augmentService().handle(game, online, gui.actionCommand(0).substring(COMMAND.length()), false) == 0,
                            "Replayed selection commands are rejected by the service.");
                    require(game.augmentService().handle(game, online, gui.actionCommand(1).substring(COMMAND.length()), false) == 0,
                            "Other stale card commands cannot grant another augment.");
                    require(state.selections().size() == 1 && state.rerollsRemaining(0) == 5,
                            "Selecting one card cannot spend a reroll or grant twice.");
                    require(fixture.rendererClosed(), "Selection must release its renderer.");
                }
            } finally {
                online.connection = originalConnection;
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void missingRendererRevealWindowInvalidStateAndReloadBlockGuiInput(GameTestHelper context) {
        var online = context.makeMockServerPlayerInLevel();
        var game = prepare(context, online);
        try {
            force(game, online, "beneficial_effect_1 reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            var player = game.players().get(online.getUUID());
            var offer = player.augments().currentOffer().orElseThrow();
            var screen = game.augmentService().offerScreen(game, player, offer, true);
            AtomicInteger executed = new AtomicInteger();
            var unavailable = new AugmentOfferGui(online, screen, revealed(), () -> true,
                    command -> executed.incrementAndGet(), true);
            require(!unavailable.openOffer() && !unavailable.isOpen(), "A mock player without a BetterHud renderer must not open an empty GUI.");
            require(unavailable.activate(0) == 0 && executed.get() == 0, "Missing rendering cannot authorize a selection.");
            try (var fixture = new AugmentOfferGuiFixture(online, screen, System.nanoTime() + 60_000_000_000L,
                    () -> true, command -> executed.incrementAndGet(), true)) {
                require(fixture.gui.openOffer(), "A present renderer permits the revealing screen.");
                fixture.gui.click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                require(executed.get() == 0, "Reveal animation cannot be skipped by a click.");
            }
            AtomicBoolean valid = new AtomicBoolean(true);
            try (var fixture = new AugmentOfferGuiFixture(online, screen, revealed(), valid::get,
                    command -> executed.incrementAndGet(), true)) {
                require(fixture.gui.openOffer(), "The valid mature GUI opens.");
                valid.set(false);
                fixture.gui.click(0, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                require(executed.get() == 0, "An invalidated offer cannot execute a command.");
                fixture.gui.onTick();
                require(!fixture.gui.isOpen() && fixture.rendererClosed(), "Invalid state closes and cleans up the GUI.");
            }
            try (var fixture = new AugmentOfferGuiFixture(online, screen, revealed(), () -> true,
                    command -> executed.incrementAndGet(), true)) {
                require(fixture.gui.openOffer(), "The available renderer opens.");
                fixture.loseRenderer();
                fixture.gui.onTick();
                require(!fixture.gui.isOpen() && fixture.rendererClosed(), "Reload or renderer loss closes a previously stable animation frame.");
                require(fixture.gui.activate(0) == 0 && executed.get() == 0, "Closed reload screens cannot execute.");
            }
            require(player.augments().selections().isEmpty() && player.augments().rerollsRemaining(0) == 5,
                    "Presentation failures never change augment state.");
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void rerollButtonSpendsOnceAndDisabledButtonCannotExecute(GameTestHelper context) {
        var online = context.makeMockServerPlayerInLevel();
        var game = prepare(context, online);
        try {
            force(game, online, "beneficial_effect_1 reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            var player = game.players().get(online.getUUID());
            var offer = player.augments().currentOffer().orElseThrow();
            var screen = game.augmentService().offerScreen(game, player, offer, true);
            try (var fixture = new AugmentOfferGuiFixture(online, screen, revealed(), () -> true,
                    command -> game.augmentService().handle(game, online, command.substring(COMMAND.length()), false), true)) {
                require(fixture.gui.openOffer(), "The reroll screen opens.");
                fixture.gui.click(AugmentOfferLayout.REROLL_SLOT, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                require(player.augments().rerollsRemaining(0) == 4, "The SGUI reroll button spends exactly once.");
                require(player.augments().currentOffer().orElseThrow().revision() != offer.revision(), "Reroll must replace the visible offer revision.");
                fixture.gui.click(AugmentOfferLayout.REROLL_SLOT, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                require(player.augments().rerollsRemaining(0) == 4, "A duplicate reroll callback cannot spend twice.");
                require(game.augmentService().handle(game, online, fixture.gui.actionCommand(0).substring(COMMAND.length()), false) == 0,
                        "Cards from the old GUI are stale after reroll.");
            }
            AtomicInteger commands = new AtomicInteger();
            var current = player.augments().currentOffer().orElseThrow();
            try (var fixture = new AugmentOfferGuiFixture(online,
                    game.augmentService().offerScreen(game, player, current, false), revealed(), () -> true,
                    command -> commands.incrementAndGet(), false)) {
                require(fixture.gui.openOffer(), "A disabled reroll still permits viewing cards.");
                fixture.gui.click(AugmentOfferLayout.REROLL_SLOT, ClickType.MOUSE_LEFT, ContainerInput.PICKUP);
                require(fixture.gui.activate(3) == 0 && commands.get() == 0, "Disabled reroll has no executable callback.");
            }
        } finally {
            game.close();
        }
        context.succeed();
    }

    @GameTest
    public void nativeDialogBodyKeepsCardIdentitySlotRerollsTooltipsAndStaleGuards(GameTestHelper context) {
        for (String schedule : List.of("SSS", "GGG", "PPP")) {
            var online = context.makeMockServerPlayerInLevel();
            var game = prepare(context, online, schedule);
            try {
                String rarity = schedule.equals("SSS") ? "silver" : schedule.equals("GGG") ? "gold" : "prismatic";
                force(game, online, "reserve_diamonds_" + rarity + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var player = game.players().get(online.getUUID());
                var state = player.augments();
                for (int slot = 0; slot < 3; slot++) {
                    var offer = state.currentOffer().orElseThrow();
                    var screen = game.augmentService().offerScreen(game, player, offer, true);
                    var dialog = kim.biryeong.semiontd.ui.augment.AugmentCardDialog.dialog(screen);
                    require(dialog.common().body().size() == 1
                                    && dialog.common().body().getFirst() instanceof net.minecraft.server.dialog.body.PlainMessage,
                            "The production dialog exposes cards through a clickable text body.");
                    var styles = new java.util.HashMap<String, net.minecraft.network.chat.Style>();
                    kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(screen).visit((style, text) -> {
                        if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand click) {
                            styles.put(click.command(), style);
                        }
                        return java.util.Optional.empty();
                    }, net.minecraft.network.chat.Style.EMPTY);
                    for (int cardSlot = 0; cardSlot < 3; cardSlot++) {
                        String command = screen.buttons().get(cardSlot).command();
                        require(styles.containsKey(command) && command.contains(" " + offer.cardIds().get(cardSlot) + " "),
                                "Every card slice carries the current card identity and session-scoped action.");
                        var hover = styles.get(command).getHoverEvent();
                        require(hover instanceof net.minecraft.network.chat.HoverEvent.ShowText tooltip
                                        && tooltip.value().getString().equals(screen.buttons().get(cardSlot).description()),
                                "Sliced cards must retain their complete production tooltip.");
                    }
                    String reroll = screen.buttons().get(slot + 3).command();
                    require(screen.canReroll().get(slot) && styles.containsKey(reroll), "An available slot reroll is clickable in the body.");
                    require(game.augmentService().handle(game, online, reroll.substring(COMMAND.length()), false) == 1,
                            "The real body command rerolls its slot.");
                    var after = state.currentOffer().orElseThrow();
                    for (int other = 0; other < 3; other++) {
                        require(other == slot || after.cardIds().get(other).equals(offer.cardIds().get(other)),
                                "A body reroll cannot change either neighboring card.");
                    }
                    require(state.rerollsRemaining(slot) == 4, "Each independently used slot is charged once.");
                    require(game.augmentService().handle(game, online, reroll.substring(COMMAND.length()), false) == 0,
                            "Repeated native callbacks cannot charge again.");
                    require(game.augmentService().handle(game, online, screen.buttons().get(slot).command().substring(COMMAND.length()), false) == 0,
                            "The prior card action is stale after replacement.");
                }
                require(state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)), "The three body controls have independent balances.");
                var current = state.currentOffer().orElseThrow();
                var disabled = game.augmentService().offerScreen(game, player, current, false);
                var commands = new java.util.HashSet<String>();
                kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(disabled).visit((style, text) -> {
                    if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand click) commands.add(click.command());
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                for (int slot = 0; slot < 3; slot++) require(!commands.contains(disabled.buttons().get(slot + 3).command()),
                        "Disabled reroll glyphs have no executable action.");
                int selectedSlot = java.util.stream.IntStream.range(0, 3)
                        .filter(slot -> AugmentService.modes(current.cardIds().get(slot)).isEmpty()).findFirst().orElseThrow();
                String select = disabled.buttons().get(selectedSlot).command();
                require(commands.contains(select) && game.augmentService().handle(game, online, select.substring(COMMAND.length()), false) == 1,
                        "Disabling reroll does not disable card selection.");
                require(game.augmentService().handle(game, online, select.substring(COMMAND.length()), false) == 0
                                && state.selections().size() == 1 && state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)),
                        "Selection grants exactly once without spending another reroll.");
            } finally {game.close();}
        }
        context.succeed();
    }

    private static long revealed() {
        return System.nanoTime() - AugmentOfferLayout.REVEAL_NANOS - 1_000_000_000L;
    }
}
