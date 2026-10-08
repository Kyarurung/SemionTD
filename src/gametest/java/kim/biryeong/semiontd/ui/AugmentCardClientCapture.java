package kim.biryeong.semiontd.ui;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.semiontd.ui.augment.AugmentCardDialog;
import kim.biryeong.semiontd.ui.augment.AugmentCardFrames;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import org.lwjgl.sdl.SDLMouse;

final class AugmentCardClientCapture {
    private AugmentCardClientCapture() { }

    static void capture(ClientGameTestContext context) {
        context.getInput().resizeWindow(1600, 1000);
        context.waitFor(client -> client.getResourceManager().getResource(
                net.minecraft.resources.Identifier.parse("semion-td:font/augment_card_dialog.json")).isPresent(), 1200);
        for (int scale : new int[]{3, 2}) {
            context.runOnClient(client -> {
                client.options.guiScale().set(scale);
                client.resizeGui();
            });
            for (String rarity : List.of("silver", "gold", "prismatic")) {
                open(context, rarity);
                context.getInput().setCursorPos(20, 20);
                screenshot(context, "augment-card-" + rarity + "-scale" + scale);
                move(context, 2, 72, 144);
                context.waitTicks(3);
                screenshot(context, "augment-card-" + rarity + "-tooltip-scale" + scale);
                if (rarity.equals("silver")) {
                    for (int remaining = 4; remaining >= 0; remaining--) {
                        move(context, 0, 72, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                        context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                        context.waitTicks(20);
                        command(context, "semioncapture checkslot 0 " + remaining);
                        int expected = 2100 + remaining;
                        context.waitFor(client -> client.player.experienceLevel == expected, 200);
                        geometry(context);
                    }
                    context.getInput().setCursorPos(20, 20);
                    screenshot(context, "augment-card-disabled-scale" + scale);
                    move(context, 0, 72, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                    context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                    context.waitTicks(5);
                    command(context, "semioncapture checkdisabled");
                    context.waitFor(client -> client.player.experienceLevel == 2140, 200);
                    open(context, rarity);
                    for (int slot = 0; slot < 3; slot++) {
                        move(context, slot, 72, (AugmentCardFrames.CARD_ROWS + 1) * 9 + 8);
                        context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                        context.waitTicks(20);
                        command(context, "semioncapture checkslot " + slot + " 4");
                        int expected = 2100 + slot * 10 + 4;
                        context.waitFor(client -> client.player.experienceLevel == expected, 200);
                    }
                    screenshot(context, "augment-card-independent-rerolls-scale" + scale);
                    open(context, rarity);
                }
                move(context, 2, 72, 144);
                context.getInput().pressMouse(SDLMouse.SDL_BUTTON_LEFT);
                context.waitTicks(10);
                command(context, "semioncapture checkselection");
                context.waitFor(client -> client.player.experienceLevel == 2042, 200);
                System.out.println("SEMION_NATIVE_CARD_INPUT rarity=" + rarity + " scale=" + scale + " selected=true");
            }
        }
    }

    private static void open(ClientGameTestContext context, String rarity) {
        command(context, "semioncapture " + rarity);
        context.waitFor(client -> client.player.experienceLevel == 2040 && client.gui.screen() instanceof DialogScreen<?>, 1200);
        context.waitTicks(20);
        geometry(context);
    }

    private static void command(ClientGameTestContext context, String command) {
        context.waitTicks(40);
        context.runOnClient(client -> client.getConnection().sendCommand(command));
    }

    private static void geometry(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            var lines = client.font.split(widget.getMessage(), widget.getWidth() - widget.getPadding() * 2);
            if (lines.size() != AugmentCardDialog.TOTAL_ROWS) throw new AssertionError("Card rows unexpectedly wrapped: " + lines.size());
            for (var line : lines) if (client.font.width(line) != AugmentCardDialog.CONTENT_WIDTH) {
                throw new AssertionError("Actual card text and frame advance mismatch: " + client.font.width(line));
            }
            if (widget.getX() < 0 || widget.getX() + widget.getWidth() > client.gui.screen().width
                    || widget.getY() < 0 || widget.getY() + widget.getHeight() > client.gui.screen().height) {
                throw new AssertionError("Native augment card body clips outside viewport");
            }
            System.out.println("SEMION_NATIVE_CARD_GEOMETRY scale=" + client.options.guiScale().get() + " x=" + widget.getX()
                    + " y=" + widget.getY() + " width=" + widget.getWidth() + " height=" + widget.getHeight());
            hitRegions(client, widget);
        });
    }

    private static void move(ClientGameTestContext context, int slot, int x, int y) {
        double[] target = new double[2];
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            var screen = client.gui.screen();
            target[0] = (widget.getX() + widget.getPadding() + slot * (AugmentCardDialog.CARD_WIDTH + AugmentCardDialog.GAP) + x + .5)
                    * client.getWindow().getScreenWidth() / screen.width;
            target[1] = (widget.getY() + widget.getPadding() + y + .5) * client.getWindow().getScreenHeight() / screen.height;
        });
        context.getInput().setCursorPos(target[0], target[1]);
        context.runOnClient(client -> {
            var widget = cardWidget(client);
            int expectedX = widget.getX() + widget.getPadding() + slot * (AugmentCardDialog.CARD_WIDTH + AugmentCardDialog.GAP) + x;
            int expectedY = widget.getY() + widget.getPadding() + y;
            if ((int) Math.floor(client.mouseHandler.getScaledXPos(client.getWindow())) != expectedX
                    || (int) Math.floor(client.mouseHandler.getScaledYPos(client.getWindow())) != expectedY) {
                throw new AssertionError("Actual SDL cursor did not land on the requested native card pixel");
            }
        });
    }

    private static void hitRegions(Minecraft client, FocusableTextWidget widget) {
        for (int row = 0; row < AugmentCardDialog.TOTAL_ROWS; row++) {
            for (int slot = 0; slot < 3; slot++) {
                for (int x : new int[]{1, 72, 142}) {
                    var style = styleAt(client, widget, slot * 156 + x, row * 9 + 4);
                    var click = style == null ? null : style.getClickEvent();
                    if (row < AugmentCardFrames.CARD_ROWS) {
                        if (!(click instanceof net.minecraft.network.chat.ClickEvent.RunCommand command)
                                || !command.command().matches(".* draft [0-9]+ " + slot + " .*")) {
                            throw new AssertionError("Bitmap/text card hit region lost its slot command: row=" + row + " slot=" + slot + " x=" + x);
                        }
                    } else if (row == AugmentCardFrames.CARD_ROWS && click != null) {
                        throw new AssertionError("The visual gap below a card must not activate it");
                    } else if (click instanceof net.minecraft.network.chat.ClickEvent.RunCommand command
                            && !command.command().matches(".* reroll [0-9]+ " + slot + " .*")) {
                        throw new AssertionError("Reroll frame points at a different slot");
                    }
                }
            }
            for (int gap : new int[]{150, 306}) {
                var style = styleAt(client, widget, gap, row * 9 + 4);
                if (style != null && style.getClickEvent() != null) throw new AssertionError("Card-column gap is clickable");
            }
        }
        System.out.println("SEMION_NATIVE_CARD_HIT_REGIONS scale=" + client.options.guiScale().get()
                + " rows=" + AugmentCardDialog.TOTAL_ROWS + " overlay=true gaps=true");
    }

    private static net.minecraft.network.chat.Style styleAt(Minecraft client, FocusableTextWidget widget, int x, int y) {
        var finder = new net.minecraft.client.gui.ActiveTextCollector.ClickableStyleFinder(client.font,
                widget.getX() + widget.getPadding() + x, widget.getY() + widget.getPadding() + y);
        widget.visitLines(finder);
        return finder.result();
    }

    private static FocusableTextWidget cardWidget(Minecraft client) {
        if (!(client.gui.screen() instanceof DialogScreen<?> dialog)) throw new AssertionError("Native augment dialog is not open");
        try {
            var body = DialogScreen.class.getDeclaredField("bodyScroll");
            body.setAccessible(true);
            var content = ScrollableLayout.class.getDeclaredField("content");
            content.setAccessible(true);
            var widgets = new ArrayList<net.minecraft.client.gui.components.AbstractWidget>();
            ((Layout) content.get(body.get(dialog))).visitWidgets(widgets::add);
            return widgets.stream().filter(FocusableTextWidget.class::isInstance).map(FocusableTextWidget.class::cast)
                    .filter(widget -> widget.getMessage().getString().codePoints().anyMatch(point -> point >= 0xEA00 && point < 0xEB00))
                    .findFirst().orElseThrow(() -> new AssertionError("Actual card frame text widget missing"));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Cannot inspect native card layout", exception);
        }
    }

    private static void screenshot(ClientGameTestContext context, String name) {
        context.runOnClient(client -> client.gui.toastManager().clear());
        var file = context.takeScreenshot(TestScreenshotOptions.of(name).withSize(1600, 1000).disableCounterPrefix());
        if (!Files.isRegularFile(file)) throw new AssertionError("Actual native augment framebuffer missing");
        System.out.println("SEMION_CAPTURE_SCREENSHOT=" + file.toAbsolutePath());
    }
}
