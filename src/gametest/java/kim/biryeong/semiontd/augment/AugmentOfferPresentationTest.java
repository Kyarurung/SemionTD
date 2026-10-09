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
    @GameTest
    public void cardScopeLabelsShowOnlyDedicatedMarker(GameTestHelper context) {
        for (var card : AugmentCatalog.definitions()) {
            String label = kim.biryeong.semiontd.ui.augment.AugmentCardDialog.categoryLabel(card);
            require(!label.contains("실버") && !label.contains("골드") && !label.contains("프리즘"), "Rarity words must not label cards.");
            if (card.requiredJobId() == null) {
                require(label.isEmpty(), "Common cards have no upper label.");
            } else {
                require(label.equals("전용"), "Dedicated cards show only the dedicated marker, without a job name.");

            }
        }
        context.succeed();
    }

    @GameTest
    public void bareAugmentAliasRequiresNoSubcommandOrOperatorPermission(GameTestHelper context) {
        var server = context.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var command = dispatcher.getRoot().getChild("증강");
        require(command != null && command.getCommand() != null, "The bare augment alias must have an executable action.");
        for (int level = 0; level <= 2; level++) {
            var source = server.createCommandSourceStack().withPermission(
                    net.minecraft.server.permissions.LevelBasedPermissionSet.forLevel(
                            net.minecraft.server.permissions.PermissionLevel.byId(level)));
            require(command.canUse(source), "Players do not need operator permission to reopen augments.");
            var parsed = dispatcher.parse("증강", source);
            require(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty()
                            && parsed.getContext().getCommand() != null,
                    "The exact bare command must parse without a subcommand.");
        }
        context.succeed();
    }

    private static final String COMMAND = "/semiontd augment ";

    @GameTest
    public void threeColumnGuiPacketsKeepLiveCommandsAndStaleInputGuardsWithoutTooltips(GameTestHelper context) {
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
                require(screen.columns() == 3 && screen.cards().size() == 3 && screen.buttons().size() == 7,
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
                                "The card metadata retains the complete configured description.");
                    }
                    for (int slot = 0; slot < 45; slot++) require(gui.getGuiElement(slot) != null, "Every card pixel region needs a click target.");
                    for (int slot = 0; slot < 54; slot++) {
                        var element = gui.getGuiElement(slot);
                        if (element == null) continue;
                        var tooltip = element.getItemStack().get(DataComponents.TOOLTIP_DISPLAY);
                        require(tooltip != null && tooltip.hideTooltip(), "Augment slots suppress hover tooltips.");
                    }
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
    public void nativeDialogBodyKeepsCardIdentityNormalFontCounterAndStaleGuardsWithoutTooltips(GameTestHelper context) {
        for (String schedule : List.of("SSS", "GGG", "PPP")) {
            var online = context.makeMockServerPlayerInLevel();
            var game = prepare(context, online, schedule);
            try {
                String rarity = schedule.equals("SSS") ? "silver" : schedule.equals("GGG") ? "gold" : "prismatic";
                force(game, online, "reserve_diamonds_" + rarity + " reserve_income_" + rarity + " reserve_production_" + rarity);
                advance(game, online, 20);
                var player = game.players().get(online.getUUID());
                var state = player.augments();
                for (int slot = 0; slot < 1; slot++) {
                    var offer = state.currentOffer().orElseThrow();
                    var screen = game.augmentService().offerScreen(game, player, offer, true);
                    var dialog = kim.biryeong.semiontd.ui.augment.AugmentCardDialog.dialog(screen);
                    require(dialog.common().body().size() == 1
                                    && dialog.common().body().getFirst() instanceof net.minecraft.server.dialog.body.PlainMessage,
                            "The production dialog exposes cards through a clickable text body.");
                    require(dialog.common().title().getStyle().getHoverEvent() == null,
                            "The augment title has no hover tooltip.");
                    require(dialog.actions().stream().allMatch(action -> action.button().tooltip().isEmpty()),
                            "Navigation controls have no hover tooltips.");
                    for (int count : List.of(5, 0)) {
                        var counted = new AugmentService.Screen(screen.title(), screen.body(), screen.cards(),
                                screen.buttons(), screen.columns(), count, count > 0);
                        var found = new java.util.concurrent.atomic.AtomicInteger();
                        kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(counted).visit((style, text) -> {
                            require(style.getHoverEvent() == null, "All augment body spans are tooltip-free.");
                            if (text.equals(Integer.toString(count))) {
                                require(style.getFont().equals(net.minecraft.network.chat.Style.EMPTY.getFont()),
                                        "The remaining count uses the normal UI font.");
                                require(style.getClickEvent() == null,
                                        "The ordinary-font counter cannot override its aligned reroll hit region.");
                                found.incrementAndGet();
                            }
                            return java.util.Optional.empty();
                        }, net.minecraft.network.chat.Style.EMPTY);
                        require(found.get() == 1, "The remaining count appears exactly once as normal text.");
                    }
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
                        require(styles.get(command).getHoverEvent() == null,
                                "Card controls must not expose hover tooltips.");
                    }
                    String reroll = screen.buttons().get(3).command();
                    require(screen.canReroll() && styles.containsKey(reroll), "The shared reroll is clickable in the body.");
                    require(game.augmentService().handle(game, online, reroll.substring(COMMAND.length()), false) == 1,
                            "The real body command replaces all three cards.");
                    var after = state.currentOffer().orElseThrow();
                    for (int other = 0; other < 3; other++) {
                        require(!offer.cardIds().contains(after.cardIds().get(other)),
                                "A body reroll changes every card and cannot retain an old card.");
                    }
                    require(state.rerollsRemaining(slot) == 4, "Each independently used slot is charged once.");
                    require(game.augmentService().handle(game, online, reroll.substring(COMMAND.length()), false) == 0,
                            "Repeated native callbacks cannot charge again.");
                    require(game.augmentService().handle(game, online, screen.buttons().get(slot).command().substring(COMMAND.length()), false) == 0,
                            "The prior card action is stale after replacement.");
                }
                require(state.rerollsRemainingBySlot().equals(List.of(4, 4, 4)), "The single body control has one shared balance.");
                var current = state.currentOffer().orElseThrow();
                var disabled = game.augmentService().offerScreen(game, player, current, false);
                var commands = new java.util.HashSet<String>();
                kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(disabled).visit((style, text) -> {
                    if (style.getClickEvent() instanceof net.minecraft.network.chat.ClickEvent.RunCommand click) commands.add(click.command());
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                require(!commands.contains(disabled.buttons().get(3).command()),
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

    @GameTest
    public void cardInteractionLayersKeepAllThreeIdentitiesAndVisualsCannotOverrideThem(GameTestHelper context) {
        for (int selectedSlot = 0; selectedSlot < 3; selectedSlot++) {
            var online = context.makeMockServerPlayerInLevel();
            var game = prepare(context, online);
            var originalConnection = online.connection;
            var packets = collectPackets(online);
            try {
                addTarget(game, online);
                force(game, online, "finishing_fire_1 tactical_designation_1_cover beneficial_effect_1");
                advance(game, online, 20);
                var player = game.players().get(online.getUUID());
                var state = player.augments();
                var offer = state.currentOffer().orElseThrow();
                var screen = game.augmentService().offerScreen(game, player, offer, true);
                int[] actionCharacters = {0};
                kim.biryeong.semiontd.ui.augment.AugmentCardDialog.body(screen).visit((style, text) -> {
                    if (style.getClickEvent() != null) {
                        require(style.getFont() instanceof net.minecraft.network.chat.FontDescription.Resource font
                                        && kim.biryeong.semiontd.ui.rp.SemionUiFont.usesFont(font.id()),
                                "Only card-aligned spacing regions carry commands; artwork and labels cannot override them.");
                        text.codePoints().forEach(codePoint -> {
                            require(kim.biryeong.semiontd.ui.rp.SemionUiFont.preciseAdvance(codePoint) > 0,
                                    "An interactive span never moves the cursor backwards.");
                            actionCharacters[0]++;
                        });
                    }
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                require(actionCharacters[0] > 0, "The full cards remain interactive.");
                String selectedId = offer.cardIds().get(selectedSlot);
                String command = screen.buttons().get(selectedSlot).command();
                packets.clear();
                require(game.augmentService().handle(game, online, command.substring(COMMAND.length()), false) == 1,
                        "Each card region dispatches its live selection action.");
                require(state.selections().size() == 1 && state.selections().getFirst().augmentId().equals(selectedId)
                                && state.currentOffer().isEmpty() && state.rerollsRemaining() == 5,
                        "The selected card identity is preserved for every slot without spending a reroll.");
                require(clearPackets(packets) == 1 && showPackets(packets) == 0,
                        "A committed selection closes the dialog without reopening it.");
                packets.clear();
                require(game.augmentService().handle(game, online, command.substring(COMMAND.length()), false) == 0,
                        "Replaying a card cannot grant another augment.");
                advance(game, online, 25);
                require(clearPackets(packets) == 1 && showPackets(packets) == 0 && state.selections().size() == 1,
                        "Duplicate callbacks and scheduled updates cannot reopen the dialog or grant twice.");
            } finally {
                online.connection = originalConnection;
                game.close();
            }
        }
        context.succeed();
    }

    @GameTest
    public void dialogClosePreservesOfferAndRerollFailuresWhileExpiryRejectsLateSelection(GameTestHelper context) {
        var online = context.makeMockServerPlayerInLevel();
        var game = prepare(context, online);
        var originalConnection = online.connection;
        var packets = collectPackets(online);
        try {
            force(game, online, "beneficial_effect_1 reserve_income_silver reserve_production_silver");
            advance(game, online, 20);
            var player = game.players().get(online.getUUID());
            var state = player.augments();
            var offer = state.currentOffer().orElseThrow();
            var screen = game.augmentService().offerScreen(game, player, offer, true);
            String staleCard = screen.buttons().getFirst().command().substring(COMMAND.length());
            packets.clear();
            require(handle(game, online, "ui close") == 1, "Explicit close succeeds.");
            require(clearPackets(packets) == 1 && state.currentOffer().isPresent()
                            && state.selections().isEmpty() && state.rerollsRemaining() == 5,
                    "Closing a dialog consumes neither selection nor reroll.");
            packets.clear();
            require(game.augmentService().handle(game, online,
                    screen.buttons().get(3).command().substring(COMMAND.length()), false) == 1,
                    "Reroll succeeds through its actual command.");
            require(clearPackets(packets) == 0 && showPackets(packets) > 0
                            && state.selections().isEmpty() && state.rerollsRemaining() == 4,
                    "Reroll keeps the offer dialog open without selecting.");
            packets.clear();
            require(game.augmentService().handle(game, online, staleCard, false) == 0,
                    "An old card command is rejected after reroll.");
            require(clearPackets(packets) == 0 && state.currentOffer().isPresent()
                            && state.selections().isEmpty() && state.rerollsRemaining() == 4,
                    "Stale input cannot close an active offer or spend another action.");
            var current = state.currentOffer().orElseThrow();
            String lateCard = game.augmentService().offerScreen(game, player, current, true)
                    .buttons().getFirst().command().substring(COMMAND.length());
            setField(game, "tickCounter", current.deadlineTickExclusive());
            packets.clear();
            require(game.augmentService().handle(game, online, lateCard, false) == 0,
                    "An expired click is rejected rather than acknowledged as a successful selection.");
            require(clearPackets(packets) > 0 && showPackets(packets) == 0
                            && state.currentOffer().isEmpty() && state.selections().size() == 1
                            && state.rerollsRemaining() == 4,
                    "The existing timeout policy resolves the offer once and leaves the dialog closed.");
        } finally {
            online.connection = originalConnection;
            game.close();
        }
        context.succeed();
    }

    private static ArrayList<Packet<?>> collectPackets(net.minecraft.server.level.ServerPlayer online) {
        var packets = new ArrayList<Packet<?>>();
        online.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(online.level().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), online,
                net.minecraft.server.network.CommonListenerCookie.createInitial(online.getGameProfile(), false)) {
            @Override public void send(Packet<?> packet) {
                packets.add(packet);
            }
        };
        eu.pb4.polymer.common.api.PolymerCommonUtils.setHasResourcePack(online,
                eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.getMainUuid(), true);
        require(eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils.hasMainPack(online),
                "The packet fixture represents a client that loaded the card resource pack.");
        return packets;
    }

    private static long clearPackets(List<Packet<?>> packets) {
        return packets.stream().filter(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.class::isInstance).count();
    }

    private static long showPackets(List<Packet<?>> packets) {
        return packets.stream().filter(net.minecraft.network.protocol.common.ClientboundShowDialogPacket.class::isInstance).count();
    }

    private static long revealed() {
        return System.nanoTime() - AugmentOfferLayout.REVEAL_NANOS - 1_000_000_000L;
    }
}
