package kim.biryeong.dantashader.shaderfx.impl;

import eu.pb4.polymer.resourcepack.api.PackResource;
import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class BetterHudTextShaderCompatibility {
    public static final String OVERLAY = "betterhud_26_3/assets/minecraft/shaders/core/text.";
    private static final String MARKER = "#define DANTA_BETTERHUD_TEXT_COMPAT 1";
    private static final String MAIN = "void main() {";
    private static final String ALPHA_TEST = "if (color.a < 0.1) {";
    private static final String EXTENSION = "#extension GL_ARB_separate_shader_objects : require";
    private static final Pattern RESERVED_LOCATION = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(8|9|10|11|12)\\s*\\)");

    private BetterHudTextShaderCompatibility() {
    }

    public static void register(ResourcePackBuilder builder, String fragment) {
        String vertex;
        try (var stream = BetterHudTextShaderCompatibility.class.getResourceAsStream(
                "/assets/minecraft/shaders/core/text.vsh")) {
            if (stream == null) throw new IllegalStateException("Missing danta text vertex shader.");
            vertex = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read danta text vertex shader.", exception);
        }
        builder.addResourceConverter((path, resource) -> {
            if (!path.equals(OVERLAY + "vsh") && !path.equals(OVERLAY + "fsh")) return resource;
            try (var stream = resource.getStream()) {
                String shader = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                String merged = path.endsWith(".vsh") ? mergeVertex(shader, vertex) : mergeFragment(shader, fragment);
                return PackResource.of(merged.getBytes(StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to compose BetterHud and danta text shaders: " + path, exception);
            }
        });
    }

    public static String mergeVertex(String betterHud, String danta) {
        if (betterHud.contains(MARKER)) return betterHud;
        checkContract(betterHud);
        requireOnce(betterHud, "layout(location = 4) out float applyColor;");
        requireOnce(betterHud, "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);");
        String declarations = namespace(between(danta, "#include <minecraft:hud.glsl>", MAIN));
        declarations += "layout(location = 12) flat out int dantaBetterHud;\n";
        String shader = replaceOnce(betterHud, EXTENSION, EXTENSION + "\n" + MARKER);
        shader = replaceOnce(shader, "uniform sampler2D Sampler0;", "");
        shader = replaceOnce(shader, MARKER, MARKER + "\n" + "uniform sampler2D Sampler0;");
        shader = replaceOnce(shader, MAIN, "\n" + declarations + "\n" + MAIN + "\n"
                + "    dantaEffectId = dantaFrames = dantaFps = dantaBetterHud = 0;\n"
                + "    dantaFrameheight = 0.0;");
        String tail = namespace(danta.substring(danta.indexOf("    vec4 col = round"), danta.lastIndexOf('}')));
        String anchor = "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);";
        return replaceOnce(shader, anchor, anchor + "\n"
                + "    dantaBetterHud = Position.y >= ui.y && ProjMat[3].x == -1\n"
                + "            && (((int(Position.y) >> HEIGHT_BIT) >> MAX_BIT) & 1) == 1 ? 1 : 0;\n"
                + "    if (dantaBetterHud == 0) {\n" + tail + "    }\n");
    }

    public static String mergeFragment(String betterHud, String danta) {
        if (betterHud.contains(MARKER)) return betterHud;
        checkContract(betterHud);
        requireOnce(betterHud, "vec4 calculateFinalColor(vec4 color)");
        String declarations = namespace(between(danta, "#include <minecraft:globals.glsl>", MAIN));
        declarations += "layout(location = 12) flat in int dantaBetterHud;\n";
        String shader = replaceOnce(betterHud, EXTENSION, EXTENSION + "\n" + MARKER);
        shader = replaceOnce(shader, MAIN, "\n" + declarations + "\n" + MAIN);
        String body = danta.substring(danta.indexOf(MAIN));
        String effects = namespace(between(body, "    ivec2 texSize = textureSize(Sampler0, 0).xy;", ALPHA_TEST));
        return replaceOnce(shader, ALPHA_TEST, "    if (dantaBetterHud == 0) {\n" + effects + "    }\n\n" + ALPHA_TEST);
    }

    private static void checkContract(String source) {
        requireOnce(source, "#version 330");
        requireOnce(source, EXTENSION);
        requireOnce(source, MAIN);
        if (RESERVED_LOCATION.matcher(source).find()) {
            throw new IllegalArgumentException("BetterHud uses a reserved danta text shader varying location.");
        }
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        if (from < 0) throw new IllegalArgumentException("Missing text shader section: " + start);
        int to = source.indexOf(end, from + start.length());
        if (to < 0) throw new IllegalArgumentException("Missing text shader section end: " + end);
        return source.substring(from, to);
    }

    private static String namespace(String source) {
        String result = source;
        String[] oldNames = {"effectId", "frames", "fps", "frameheight", "corners"};
        String[] newNames = {"dantaEffectId", "dantaFrames", "dantaFps", "dantaFrameheight", "dantaCorners"};
        for (int index = 0; index < oldNames.length; index++) {
            result = result.replaceAll("\\b" + oldNames[index] + "\\b", newNames[index]);
        }
        for (int location = 4; location <= 7; location++) {
            result = result.replace("layout(location = " + location + ")", "layout(location = " + (location + 4) + ")");
        }
        return result;
    }

    private static String replaceOnce(String source, String token, String replacement) {
        requireOnce(source, token);
        int index = source.indexOf(token);
        return source.substring(0, index) + replacement + source.substring(index + token.length());
    }

    private static void requireOnce(String source, String token) {
        int index = source.indexOf(token);
        if (index < 0 || source.indexOf(token, index + token.length()) >= 0) {
            throw new IllegalArgumentException("Unsupported text shader contract: " + token);
        }
    }
}
