package kim.biryeong.semiontd.vfx;

import eu.pb4.polymer.resourcepack.api.PolymerResourcePackUtils;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import kim.biryeong.semiontd.SemionTd;
import org.slf4j.Logger;

/**
 * 연출 텍스처를 리소스팩에 넣습니다.
 *
 * <p>아이템 모델의 텍스처는 블록 아틀라스에 있어야 하므로 {@code textures/item/vfx/} 아래에 둡니다
 * (스카이박스와 같은 방식). 라이선스가 있는 {@code assets/semion-td} 원본 폴더는 건드리지 않고,
 * 팩을 만들 때 코드로만 추가합니다.
 */
public final class DisplaySpriteResourcePack {
    private DisplaySpriteResourcePack() {
    }

    public static void register(Supplier<Collection<DisplaySprite>> sprites, Logger logger) {
        PolymerResourcePackUtils.RESOURCE_PACK_AFTER_INITIAL_CREATION_EVENT.register(builder ->
                addToResourcePack(sprites.get(), builder, logger));
    }

    public static void addToResourcePack(Collection<DisplaySprite> sprites, ResourcePackBuilder builder, Logger logger) {
        int added = 0;
        for (DisplaySprite sprite : sprites) {
            byte[] texture = readTexture(sprite);
            if (texture == null) {
                logger.warn("Missing Semion TD VFX texture {}", sprite.texturePath());
                continue;
            }
            String ns = SemionTd.MOD_ID;
            builder.addData("assets/" + ns + "/textures/item/vfx/" + sprite.name() + ".png", texture);
            builder.addStringData("assets/" + ns + "/models/item/vfx/" + sprite.name() + ".json", modelJson(sprite));
            builder.addStringData("assets/" + ns + "/items/vfx/" + sprite.name() + ".json",
                    "{\n  \"model\": {\"type\": \"minecraft:model\", \"model\": \"" + ns + ":item/vfx/" + sprite.name() + "\"}\n}\n");
            added++;
        }
        logger.info("Added {} Semion TD display VFX sprite(s) to the generated resource pack.", added);
    }

    static byte[] readTexture(DisplaySprite sprite) {
        try (InputStream in = DisplaySpriteResourcePack.class.getResourceAsStream(sprite.texturePath())) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException exception) {
            return null;
        }
    }

    /**
     * 두께 없는 판 모델. 16 단위 모델 좌표가 디스플레이에서 가운데 정렬된 1×1 블록이 됩니다.
     * 뒷면 UV를 좌우로 뒤집어, 앞뒤 어느 쪽에서 봐도 월드 기준으로 같은 방향으로 보이게 합니다.
     */
    public static String modelJson(DisplaySprite sprite) {
        String texture = SemionTd.MOD_ID + ":item/vfx/" + sprite.name();
        List<String> elements = switch (sprite.shape()) {
            case FLAT -> List.of(element("[0, 8, 0]", "[16, 8, 16]",
                    "\"up\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#0\"}, \"down\": {\"uv\": [0, 16, 16, 0], \"texture\": \"#0\"}"));
            case UPRIGHT -> List.of(uprightXY());
            case CROSS -> List.of(uprightXY(), element("[8, 0, 0]", "[8, 16, 16]",
                    "\"east\": {\"uv\": [16, 0, 0, 16], \"texture\": \"#0\"}, \"west\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#0\"}"));
            // 입체는 면마다 명암이 달라야 모서리가 보이므로 이 모양만 면 음영을 켭니다.
            case CUBE -> List.of(element("[0, 0, 0]", "[16, 16, 16]", String.join(", ",
                    face("north"), face("south"), face("east"), face("west"), face("up"), face("down")))
                    .replace("\"shade\": false", "\"shade\": true"));
            case CYLINDER -> cylinderPanels();
        };
        return "{\n  \"textures\": {\"0\": \"" + texture + "\", \"particle\": \"#0\"},\n"
                + "  \"elements\": [\n    " + String.join(",\n    ", elements) + "\n  ]\n}\n";
    }

    /**
     * 16각 원기둥 벽. 두께 없는 판 16장을 둘레에 세웁니다. 아이템 모델의 요소 회전은 22.5° 단위(±45° 이내)만 되므로,
     * 판마다 가장 가까운 네 방향(남·동·북·서) 중 하나에 세운 뒤 나머지 각만큼 돌립니다. 판 k는 텍스처의 가로
     * k번째 칸(16분의 1)을 쓰므로 텍스처가 둘레를 한 바퀴 감습니다. 안쪽 면도 같은 칸을 좌우로 뒤집어 붙입니다.
     */
    static List<String> cylinderPanels() {
        double apothem = 8.0 * Math.cos(Math.PI / 16.0);
        double half = 8.0 * Math.sin(Math.PI / 16.0) + 0.02;
        List<String> panels = new java.util.ArrayList<>();
        for (int k = 0; k < 16; k++) {
            int base = Math.floorMod((int) Math.floor((k * 22.5 + 45.0) / 90.0), 4);
            double rest = k * 22.5 - base * 90.0;
            if (rest > 45.0) {
                rest -= 360.0;
            }
            String lo = fmt(8.0 - half);
            String hi = fmt(8.0 + half);
            String out = fmt(8.0 + apothem);
            String in = fmt(8.0 - apothem);
            String uv = "[" + k + ", 0, " + (k + 1) + ", 16]";
            String uvBack = "[" + (k + 1) + ", 0, " + k + ", 16]";
            String element = switch (base) {
                case 0 -> element("[" + lo + ", 0, " + out + "]", "[" + hi + ", 16, " + out + "]",
                        "\"south\": {\"uv\": " + uv + ", \"texture\": \"#0\"}, \"north\": {\"uv\": " + uvBack + ", \"texture\": \"#0\"}");
                case 1 -> element("[" + out + ", 0, " + lo + "]", "[" + out + ", 16, " + hi + "]",
                        "\"east\": {\"uv\": " + uv + ", \"texture\": \"#0\"}, \"west\": {\"uv\": " + uvBack + ", \"texture\": \"#0\"}");
                case 2 -> element("[" + lo + ", 0, " + in + "]", "[" + hi + ", 16, " + in + "]",
                        "\"north\": {\"uv\": " + uv + ", \"texture\": \"#0\"}, \"south\": {\"uv\": " + uvBack + ", \"texture\": \"#0\"}");
                default -> element("[" + in + ", 0, " + lo + "]", "[" + in + ", 16, " + hi + "]",
                        "\"west\": {\"uv\": " + uv + ", \"texture\": \"#0\"}, \"east\": {\"uv\": " + uvBack + ", \"texture\": \"#0\"}");
            };
            if (Math.abs(rest) > 1.0e-6) {
                element = element.substring(0, element.length() - 1)
                        + ", \"rotation\": {\"angle\": " + fmt(rest) + ", \"axis\": \"y\", \"origin\": [8, 8, 8]}}";
            }
            panels.add(element);
        }
        return panels;
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String face(String side) {
        return "\"" + side + "\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#0\"}";
    }

    private static String uprightXY() {
        return element("[0, 0, 8]", "[16, 16, 8]",
                "\"south\": {\"uv\": [0, 0, 16, 16], \"texture\": \"#0\"}, \"north\": {\"uv\": [16, 0, 0, 16], \"texture\": \"#0\"}");
    }

    private static String element(String from, String to, String faces) {
        return "{\"from\": " + from + ", \"to\": " + to + ", \"shade\": false, \"faces\": {" + faces + "}}";
    }
}
