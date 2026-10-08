package kim.biryeong.semiontd.ui.augment;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import kim.biryeong.semiontd.SemionTd;
import net.fabricmc.loader.api.FabricLoader;

public final class AugmentHudResources {
    static final String HUD_NAME = "semion_augment_offer";
    private static final int[] COLORS = {0xBBCAD9, 0xE8BF54, 0xBBA2FA};

    private AugmentHudResources() { }

    public static void initialize() {
        if (FabricLoader.getInstance().isModLoaded("betterhud")) BetterHudResourceHook.register();
    }

    static void write(Path root) throws IOException {
        Path assets = root.resolve("assets/semion_augment");
        Files.createDirectories(assets);
        boolean icons = writeIcons(assets);
        StringBuilder images = new StringBuilder();
        StringBuilder layouts = new StringBuilder();
        StringBuilder hud = new StringBuilder(HUD_NAME + ":\n  default: false\n  tick: 1\n  layouts:\n");
        int entry = 0;
        for (int rarity = 0; rarity < 3; rarity++) {
            for (int stage = 0; stage < 4; stage++) {
                String frame = "semion_augment_frame_" + rarity + "_" + stage;
                ImageIO.write(frame(rarity, stage), "PNG", assets.resolve(frame + ".png").toFile());
                image(images, frame, frame + ".png");
                for (int card = 0; card < 3; card++) {
                    String name = "semion_augment_" + rarity + "_" + card + "_" + stage;
                    layouts.append(name).append(":\n  conditions:\n");
                    condition(layouts, "    ", "rarity", "semion_augment_rarity", rarity);
                    condition(layouts, "    ", "stage", "semion_augment_step_" + card, stage);
                    layouts.append("  images:\n    frame:\n      name: ").append(frame)
                            .append("\n      layer: 0\n");
                    if (icons) {
                        for (int role = 0; role < 3; role++) {
                            layouts.append("    icon_").append(role).append(":\n      name: semion_augment_icon_")
                                    .append(rarity).append('_').append(role)
                                    .append("\n      x: 15\n      y: 5\n      scale: 0.05741626794258373\n      layer: 1\n      conditions:\n");
                            condition(layouts, "        ", "role", "semion_augment_role_" + card, role);
                        }
                    }
                    layouts.append("  texts:\n");
                    text(layouts, "title", "[string:semion_augment_title_" + card + "]", 4, 33, 3, 7);
                    text(layouts, "rarity", "[string:semion_augment_rarity_name] · [string:semion_augment_role_name_" + card + "]", 4, 50, 1, 7);
                    text(layouts, "summary", "[string:semion_augment_summary_" + card + "]", 4, 59, 7, 7);
                    if (!icons) text(layouts, "pending", "이미지 대기", 8, 13, 1, 7);
                    hud.append("    ").append(++entry).append(":\n      name: ").append(name)
                            .append("\n      gui:\n        x: 50\n        y: 0\n      pixel:\n        x: ")
                            .append(card * 54 - 1).append("\n        y: ").append(12 - (3 - stage) * 2).append('\n');
                }
            }
            if (icons) for (int role = 0; role < 3; role++) image(images,
                    "semion_augment_icon_" + rarity + "_" + role, "icon_" + rarity + "_" + role + ".png");
        }
        write(root.resolve("images/semion-augment.yml"), images.toString());
        write(root.resolve("layouts/semion-augment.yml"), layouts.toString());
        write(root.resolve("huds/semion-augment.yml"), hud.toString());
        write(root.resolve("texts/semion-augment.yml"), "semion_augment_font:\n  merge-default-bitmap: true\n  use-unifont: true\n");
        if (!icons) SemionTd.LOGGER.info("Augment BetterHud frames ready; original icon atlas is not present.");
    }

    private static void image(StringBuilder result, String name, String file) {
        result.append(name).append(":\n  type: single\n  file: semion_augment/").append(file).append('\n');
    }

    private static void condition(StringBuilder result, String indent, String id, String variable, int expected) {
        result.append(indent).append(id).append(":\n").append(indent).append("  first: 'number:")
                .append(variable).append("'\n").append(indent).append("  second: '").append(expected)
                .append("'\n").append(indent).append("  operation: '=='\n");
    }

    private static void text(StringBuilder result, String id, String value, int x, int y, int lines, int spacing) {
        result.append("    ").append(id).append(":\n      name: semion_augment_font\n      pattern: \"")
                .append(value).append("\"\n      x: ").append(x).append("\n      y: ").append(y)
                .append("\n      scale: 0.5\n      color: '#FFFFFF'\n      layer: 2\n      outline: false\n      align: left\n      line: ")
                .append(lines).append("\n      split-width: 92\n      line-width: ").append(spacing).append('\n');
    }

    static BufferedImage frame(int rarity, int stage) {
        BufferedImage image = new BufferedImage(54, 90, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0x101A2D));
            graphics.fillRect(1, 0, 52, 90);
            graphics.setColor(new Color(COLORS[rarity]));
            graphics.drawRect(1, 0, 51, 89);
            if (rarity > 0) graphics.drawRect(3, 2, 47, 85);
            if (rarity == 2) {
                for (int y = 8; y < 87; y += 16) {
                    graphics.drawLine(1, y, 5, y + 4);
                    graphics.drawLine(5, y + 4, 1, y + 8);
                    graphics.drawLine(52, y, 48, y + 4);
                    graphics.drawLine(48, y + 4, 52, y + 8);
                }
            }
            if (stage < 3) {
                graphics.setColor(new Color(255, 255, 255, 90));
                graphics.fillRect(2, 6 + stage * 31, 50, 5);
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static boolean writeIcons(Path assets) throws IOException {
        try (var input = AugmentHudResources.class.getResourceAsStream("/semiontd/ui/augment-icons.png")) {
            if (input == null) return false;
            var atlas = ImageIO.read(input);
            if (atlas == null || atlas.getWidth() != atlas.getHeight() || atlas.getWidth() % 3 != 0)
                throw new IOException("Augment icon atlas must be a square 3x3 grid");
            int cell = atlas.getWidth() / 3;
            for (int rarity = 0; rarity < 3; rarity++) for (int role = 0; role < 3; role++) {
                var icon = new BufferedImage(418, 418, BufferedImage.TYPE_INT_ARGB);
                var graphics = icon.createGraphics();
                try {
                    graphics.drawImage(atlas, 0, 0, 418, 418, role * cell, rarity * cell,
                            (role + 1) * cell, (rarity + 1) * cell, null);
                } finally {
                    graphics.dispose();
                }
                ImageIO.write(icon, "PNG", assets.resolve("icon_" + rarity + "_" + role + ".png").toFile());
            }
            return true;
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}
