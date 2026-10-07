package kim.biryeong.semiontd.ui.augment;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import kim.biryeong.semiontd.augment.AugmentDisplayRole;
import kim.biryeong.semiontd.augment.AugmentRarity;
import kim.biryeong.semiontd.ui.rp.AugmentCardIcons;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

public final class AugmentCardFrames {
    public static final int WIDTH = 144;
    public static final int ROW_HEIGHT = 9;
    public static final int CARD_ROWS = 22;
    public static final int BUTTON_ROWS = 2;
    public static final Identifier FONT = Identifier.fromNamespaceAndPath("semion-td", "augment_card_dialog");
    private static final int FIRST_CARD = 0xEA00;
    private static final int FIRST_BUTTON = 0xEC00;
    private static final Style STYLE = Style.EMPTY.withFont(new FontDescription.Resource(FONT))
            .withColor(0xFFFFFF).withShadowColor(0).withBold(false).withItalic(false);

    private AugmentCardFrames() { }

    public static void init() {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(AugmentCardFrames::resources);
    }

    public static Component card(AugmentRarity rarity, AugmentDisplayRole role, int row) {
        return glyph(FIRST_CARD + row * 9 + rarity.ordinal() * 3 + role.ordinal());
    }

    public static Component button(AugmentRarity rarity, boolean enabled, int remaining, int row) {
        if (remaining < 0 || remaining > 5) throw new IllegalArgumentException("Invalid reroll count");
        return glyph(FIRST_BUTTON + (remaining * BUTTON_ROWS + row) * 6 + rarity.ordinal() * 2 + (enabled ? 0 : 1));
    }

    private static Component glyph(int codePoint) {
        return Component.literal(Character.toString(codePoint)).setStyle(STYLE);
    }

    static int color(AugmentRarity rarity) {
        return switch (rarity) {
            case SILVER -> 0xC9D9EF;
            case GOLD -> 0xF3CA78;
            case PRISMATIC -> 0xC9AEFF;
        };
    }

    static BufferedImage cards() {
        BufferedImage icons = null;
        try (var stream = AugmentCardFrames.class.getResourceAsStream(AugmentCardIcons.SOURCE)) {
            if (stream != null) icons = ImageIO.read(stream);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load augment card artwork", exception);
        }
        var image = new BufferedImage((WIDTH - 1) * 9, CARD_ROWS * ROW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int height = image.getHeight();
            for (int variant = 0; variant < 9; variant++) {
                int x = variant * (WIDTH - 1);
                Color accent = new Color(color(AugmentRarity.values()[variant / 3]));
                graphics.setPaint(new GradientPaint(x, 0, new Color(0x202B43), x, height, new Color(0x0A1020)));
                graphics.fillRect(x, 0, WIDTH - 1, height);
                graphics.setColor(new Color(0x3C4964));
                graphics.drawRect(x, 0, WIDTH - 2, height - 1);
                graphics.setColor(accent);
                graphics.drawRect(x + 2, 2, WIDTH - 6, height - 5);
                graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 68));
                graphics.drawRect(x + 5, 5, WIDTH - 12, height - 11);
                for (int side : new int[]{0, 1}) {
                    int edge = side == 0 ? x + 2 : x + WIDTH - 4;
                    int direction = side == 0 ? 1 : -1;
                    graphics.setColor(accent);
                    graphics.drawLine(edge, 10, edge + direction * 8, 2);
                    graphics.drawLine(edge, height - 11, edge + direction * 8, height - 3);
                }
                graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 26));
                graphics.fillOval(x + 37, 30, 68, 68);
                graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 105));
                graphics.drawOval(x + 36, 29, 70, 70);
                graphics.setColor(accent);
                graphics.fillPolygon(new int[]{x + 71, x + 75, x + 71, x + 67}, new int[]{5, 9, 13, 9}, 4);
                graphics.drawLine(x + 17, 121, x + 57, 121);
                graphics.drawLine(x + 85, 121, x + 125, 121);
                graphics.fillPolygon(new int[]{x + 71, x + 74, x + 71, x + 68}, new int[]{118, 121, 124, 121}, 4);
                if (icons != null) {
                    AugmentCardIcons.validateDimensions(icons.getWidth(), icons.getHeight());
                    int cell = icons.getWidth() / 3;
                    int sourceX = variant % 3 * cell;
                    int sourceY = variant / 3 * cell;
                    int left = cell, top = cell, right = -1, bottom = -1;
                    for (int iy = 0; iy < cell; iy++) {
                        for (int ix = 0; ix < cell; ix++) {
                            if ((icons.getRGB(sourceX + ix, sourceY + iy) >>> 24) == 0) continue;
                            left = Math.min(left, ix);
                            top = Math.min(top, iy);
                            right = Math.max(right, ix);
                            bottom = Math.max(bottom, iy);
                        }
                    }
                    if (right >= left && bottom >= top) {
                        double scale = 58.0 / Math.max(right - left + 1, bottom - top + 1);
                        int width = (int) Math.round((right - left + 1) * scale);
                        int iconHeight = (int) Math.round((bottom - top + 1) * scale);
                        int targetX = x + (143 - width) / 2;
                        int targetY = 64 - iconHeight / 2;
                        graphics.drawImage(icons, targetX, targetY, targetX + width, targetY + iconHeight,
                                sourceX + left, sourceY + top, sourceX + right + 1, sourceY + bottom + 1, null);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    static BufferedImage buttons() {
        var image = new BufferedImage((WIDTH - 1) * 6, BUTTON_ROWS * ROW_HEIGHT * 6, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
            for (int remaining = 0; remaining <= 5; remaining++) {
                graphics.translate(0, remaining * 18);
                for (int variant = 0; variant < 6; variant++) {
                    int x = variant * (WIDTH - 1);
                    boolean enabled = variant % 2 == 0;
                    Color accent = enabled ? new Color(color(AugmentRarity.values()[variant / 2])) : new Color(0x65738A);
                    graphics.setPaint(new GradientPaint(x, 0, new Color(enabled ? 0x37415A : 0x192333), x, 18, new Color(0x101827)));
                    graphics.fillRect(x, 0, WIDTH - 1, 18);
                    graphics.setColor(accent);
                    graphics.drawRect(x, 0, WIDTH - 2, 17);
                    graphics.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
                    graphics.drawRect(x + 2, 2, WIDTH - 6, 13);
                    graphics.setColor(accent);
                    graphics.setStroke(new BasicStroke(1.5F));
                    graphics.drawArc(x + 46, 4, 9, 9, 40, 285);
                    graphics.fillPolygon(new int[]{x + 56, x + 51, x + 55}, new int[]{3, 5, 8}, 3);
                    graphics.drawString(remaining + " / 5", x + 69, 12);
                }
                graphics.translate(0, -remaining * 18);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void resources(ResourcePackBuilder builder) {
        var providers = new JsonArray();
        add(builder, providers, "cards", cards(), CARD_ROWS, 9, FIRST_CARD);
        add(builder, providers, "buttons", buttons(), BUTTON_ROWS * 6, 6, FIRST_BUTTON);
        var font = new JsonObject();
        font.add("providers", providers);
        builder.addData("assets/semion-td/font/augment_card_dialog.json", font.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void add(ResourcePackBuilder builder, JsonArray providers, String name, BufferedImage image,
                            int rows, int columns, int first) {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", output);
            builder.addData("assets/semion-td/textures/font/augment_dialog_" + name + ".png", output.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot build augment card frames", exception);
        }
        var bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", "semion-td:font/augment_dialog_" + name + ".png");
        bitmap.addProperty("height", ROW_HEIGHT);
        bitmap.addProperty("ascent", 7);
        var chars = new JsonArray();
        for (int row = 0; row < rows; row++) {
            var text = new StringBuilder();
            for (int column = 0; column < columns; column++) text.appendCodePoint(first + row * columns + column);
            chars.add(text.toString());
        }
        bitmap.add("chars", chars);
        providers.add(bitmap);
    }
}
