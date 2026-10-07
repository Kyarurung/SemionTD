package kim.biryeong.semiontd.compat;

import static org.junit.jupiter.api.Assertions.*;

import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import eu.pb4.polymer.resourcepack.api.PackResource;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import kim.biryeong.dantashader.shaderfx.impl.BetterHudTextShaderCompatibility;
import org.junit.jupiter.api.Test;

final class BetterHudTextShaderCompatibilityTest {
    @Test
    void actual26_3ShadersKeepBothVertexContractsAndOriginalProjectionOrder() throws IOException {
        String original = betterHud("vsh");
        String shader = BetterHudTextShaderCompatibility.mergeVertex(original, resource("/assets/minecraft/shaders/core/text.vsh"));
        assertTrue(shader.contains("layout(location = 4) out float applyColor;"));
        assertTrue(shader.contains("layout(location = 8) flat out int dantaEffectId;"));
        assertTrue(shader.contains("layout(location = 11) flat out float dantaFrameheight;"));
        assertTrue(shader.contains("layout(location = 12) flat out int dantaBetterHud;"));
        assertEquals(1, occurrences(shader, "uniform sampler2D Sampler0;"));
        assertTrue(shader.indexOf("uniform sampler2D Sampler0;") < shader.indexOf("#if !defined(IS_GUI)"));
        assertTrue(shader.contains("#CreateLayout"));
        assertTrue(shader.contains("#GenerateOtherMainMethod"));
        assertTrue(shader.indexOf("gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);")
                < shader.indexOf("gl_Position.xy = (coord * 2.0 - 1.0)"));
        assertTrue(shader.contains("col.a == 251.0") && shader.contains("col.a == 252.0") && shader.contains("col.a == 253.0"));
        assertTrue(shader.contains("if (make_hud())"));
        assertTrue(shader.contains("RENDERPEARL_DEPTH_IS_ZERO_TO_ONE"));
        assertTrue(shader.contains("if (dantaBetterHud == 0)"));
        assertEquals(shader, BetterHudTextShaderCompatibility.mergeVertex(shader, resource("/assets/minecraft/shaders/core/text.vsh")));
    }

    @Test
    void fragmentPreservesGeneratedSamplingAndDantaEffectsBeforeOneFogAndOitExit() throws IOException {
        String original = betterHud("fsh").replace("#GenerateOtherMainMethod", "color.a *= clamp(fwidth(texCoord0.x), 0.0, 1.0);");
        String danta = resource("/shaders/rendertype_text.fsh")
                .replace("//%IMPORTS%", "#include <minecraft:shaderfx_utils.glsl>")
                .replace("//%CASES%", "case 123: { color.rgb = vec3(0.25); } break;");
        String shader = BetterHudTextShaderCompatibility.mergeFragment(original, danta);
        assertTrue(shader.contains("layout(location = 8) flat in int dantaEffectId;"));
        assertTrue(shader.contains("layout(location = 12) flat in int dantaBetterHud;"));
        assertTrue(shader.contains("case 123: { color.rgb = vec3(0.25); } break;"));
        assertTrue(shader.contains("isRgbMapEncoded(Sampler0)"));
        assertTrue(shader.contains("if (dantaFrames > 1)"));
        assertTrue(shader.contains("color.a *= clamp(fwidth(texCoord0.x), 0.0, 1.0);"));
        assertTrue(shader.indexOf("color.a *= clamp") < shader.indexOf("if (dantaBetterHud == 0)"));
        assertTrue(shader.indexOf("if (dantaFrames > 1)") < shader.indexOf("if (color.a < 0.1)"));
        assertEquals(1, occurrences(shader, "executeAlphaOnlyPhase(gl_FragCoord.z, color.a);"));
        assertEquals(1, occurrences(shader, "fragColor = calculateFinalColor(color);"));
        assertTrue(shader.contains("#ifdef IS_GRAYSCALE"));
        assertEquals(shader, BetterHudTextShaderCompatibility.mergeFragment(shader, danta));
    }

    @Test
    void finalConverterComposesOnly26_3OverlayRegardlessOfCreationCallbackOrder() throws IOException {
        List<ResourcePackBuilder.ResourceConverter> converters = new ArrayList<>();
        var builder = (ResourcePackBuilder) Proxy.newProxyInstance(ResourcePackBuilder.class.getClassLoader(),
                new Class<?>[]{ResourcePackBuilder.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addResourceConverter")) {
                        converters.add((ResourcePackBuilder.ResourceConverter) args[0]);
                        return null;
                    }
                    throw new AssertionError("Compatibility must not overwrite another creator's files: " + method.getName());
                });
        BetterHudTextShaderCompatibility.register(builder, resource("/shaders/rendertype_text.fsh"));
        assertEquals(1, converters.size());
        var converter = converters.getFirst();
        var vertex = PackResource.of(betterHud("vsh").getBytes(StandardCharsets.UTF_8));
        assertSame(vertex, converter.convert("assets/minecraft/shaders/core/text.vsh", vertex));
        assertSame(vertex, converter.convert("betterhud_26_2/assets/minecraft/shaders/core/text.vsh", vertex));
        var merged = converter.convert(BetterHudTextShaderCompatibility.OVERLAY + "vsh", vertex);
        try (var stream = merged.getStream()) {
            assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("dantaEffectId"));
        }
        var fragment = converter.convert(BetterHudTextShaderCompatibility.OVERLAY + "fsh",
                PackResource.of(betterHud("fsh").getBytes(StandardCharsets.UTF_8)));
        try (var stream = fragment.getStream()) {
            assertTrue(new String(stream.readAllBytes(), StandardCharsets.UTF_8).contains("dantaFrames"));
        }
    }

    @Test
    void actualGenerated449ShadersComposeAfterWhitespaceCompaction() throws IOException {
        String vertex = resource("/compat/betterhud-449-generated/text.vsh");
        String fragment = resource("/compat/betterhud-449-generated/text.fsh");
        assertTrue(vertex.contains("}void main() {"));
        assertTrue(fragment.contains("ColorModulator;if (color.a < 0.1)"));
        String dantaVertex = resource("/assets/minecraft/shaders/core/text.vsh");
        String dantaFragment = resource("/shaders/rendertype_text.fsh")
                .replace("//%IMPORTS%", "#include <minecraft:shaderfx_utils.glsl>")
                .replace("//%CASES%", "case 123: { color.rgb = vec3(0.25); } break;");
        String mergedVertex = BetterHudTextShaderCompatibility.mergeVertex(vertex, dantaVertex);
        String mergedFragment = BetterHudTextShaderCompatibility.mergeFragment(fragment, dantaFragment);
        assertTrue(mergedVertex.contains("case 4:xGui = ui.x * 50.0 / 100.0;layer = 2;break;"));
        assertTrue(mergedVertex.contains("layout(location = 4) out float applyColor;"));
        assertTrue(mergedVertex.indexOf("gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);")
                < mergedVertex.indexOf("gl_Position.xy = (coord * 2.0 - 1.0)"));
        assertTrue(mergedFragment.contains("case 123: { color.rgb = vec3(0.25); } break;"));
        assertTrue(mergedFragment.indexOf("vec4 color = texColor * vertexColor * ColorModulator;")
                < mergedFragment.indexOf("if (dantaBetterHud == 0)"));
        assertTrue(mergedFragment.indexOf("if (dantaFrames > 1)") < mergedFragment.indexOf("if (color.a < 0.1)"));
        assertEquals(1, occurrences(mergedFragment, "executeAlphaOnlyPhase(gl_FragCoord.z, color.a);"));
        assertEquals(1, occurrences(mergedFragment, "fragColor = calculateFinalColor(color);"));
        for (String shader : List.of(mergedVertex, mergedFragment)) {
            assertFalse(java.util.regex.Pattern.compile("(?m)^[^\r\n]*\\S[^\r\n]*#").matcher(shader).find(),
                    "Inserted preprocessor directives must start on their own lines after compacted code.");
            assertTrue(shader.contains("layout(location = 12) flat"));
        }
        assertThrows(IllegalArgumentException.class, () -> BetterHudTextShaderCompatibility.mergeVertex(
                vertex + "gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);", dantaVertex));
        assertThrows(IllegalArgumentException.class, () -> BetterHudTextShaderCompatibility.mergeFragment(
                fragment + "if (color.a < 0.1) {discard;}", dantaFragment));
        assertEquals(mergedVertex, BetterHudTextShaderCompatibility.mergeVertex(mergedVertex, dantaVertex));
        assertEquals(mergedFragment, BetterHudTextShaderCompatibility.mergeFragment(mergedFragment, dantaFragment));
    }

    @Test
    void unknownShaderContractsFailInsteadOfSilentlyDroppingEitherRenderer() throws IOException {
        String vertex = betterHud("vsh");
        String danta = resource("/assets/minecraft/shaders/core/text.vsh");
        assertThrows(IllegalArgumentException.class, () -> BetterHudTextShaderCompatibility.mergeVertex(
                vertex.replace("layout(location = 4) out float applyColor;", "layout(location = 8) out float applyColor;"), danta));
        assertThrows(IllegalArgumentException.class, () -> BetterHudTextShaderCompatibility.mergeVertex(
                vertex.replace("void main() {", "void renamedMain() {"), danta));
    }

    private static String betterHud(String extension) throws IOException {
        return resource("/shaders/betterhud_26_3/text." + extension);
    }

    private static String resource(String name) throws IOException {
        try (var stream = BetterHudTextShaderCompatibilityTest.class.getResourceAsStream(name)) {
            assertNotNull(stream, "The real packaged shader must be on the test classpath: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int occurrences(String text, String token) {
        return text.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
