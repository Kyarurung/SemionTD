package kim.biryeong.semiontd.skybox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.pb4.polymer.resourcepack.api.ResourcePackBuilder;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.List;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

final class SemionSkyboxLibraryTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @TempDir
    Path tempDir;

    @Test
    void loadsAtlasAndNormalizesFogMarkerAlpha() throws Exception {
        BufferedImage source = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFF8040);
        source.setRGB(1, 0, 0x00000000);
        ImageIO.write(source, "png", tempDir.resolve("Red Nebula.png").toFile());

        SemionSkyboxLibrary library = SemionSkyboxLibrary.load(tempDir, LoggerFactory.getLogger("skybox-test"));

        assertEquals(1, library.skyboxes().size());
        SemionSkybox skybox = library.skyboxes().getFirst();
        assertEquals("red_nebula", skybox.id());
        assertEquals("Red Nebula", skybox.displayName());
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(skybox.textureData()));
        assertEquals(SemionSkyboxLibrary.FOG_BYPASS_ALPHA, normalized.getRGB(0, 0) >>> 24);
        assertEquals(0, normalized.getRGB(1, 0) >>> 24);
    }

    @Test
    void rejectsTexturesThatDoNotUseTwoToOneAtlas() throws Exception {
        BufferedImage source = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(source, "png", tempDir.resolve("invalid.png").toFile());

        SemionSkyboxLibrary library = SemionSkyboxLibrary.load(tempDir, LoggerFactory.getLogger("skybox-test"));

        assertTrue(library.isEmpty());
    }

    @Test
    void resourcePackContainsPerSkyboxModelAndFogShaders() throws Exception {
        BufferedImage source = new BufferedImage(8, 4, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0xFFFFFFFF);
        ImageIO.write(source, "png", tempDir.resolve("space.png").toFile());
        SemionSkyboxLibrary library = SemionSkyboxLibrary.load(tempDir, LoggerFactory.getLogger("skybox-test"));
        CapturingBuilder builder = new CapturingBuilder();
        for (String name : List.of("entity", "item")) {
            builder.addStringData("assets/minecraft/shaders/core/" + name + ".vsh", modernVertex());
            builder.addStringData("assets/minecraft/shaders/core/" + name + ".fsh", modernFragment());
        }

        SemionSkyboxResourcePack.addToResourcePack(library, builder, LoggerFactory.getLogger("skybox-test"));

        for (String name : List.of("entity", "item")) {
            assertTrue(builder.getStringData("assets/minecraft/shaders/core/" + name + ".fsh").contains("252.0 / 255.0"));
            assertTrue(builder.getStringData("assets/minecraft/shaders/core/" + name + ".vsh").contains("layout(location = 8) out vec4 semionSkyboxOriginalColor;"));
        }
        assertNull(builder.getData("assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.fsh"));
        assertNotNull(builder.getData("assets/semion-td/textures/item/skybox/space.png"));
        assertNotNull(builder.getData("assets/semion-td/models/item/skybox/space.json"));
        assertNotNull(builder.getData("assets/semion-td/items/skybox/space.json"));
    }

    @Test
    void vertexShaderMergePreservesExistingHudPatch() {
        String patched = SemionSkyboxResourcePack.patchVertexShader(modernVertex(), LoggerFactory.getLogger("skybox-test"));
        assertTrue(patched.contains("//Hud"));
        assertTrue(patched.contains("layout(location = 8) out vec4 semionSkyboxOriginalColor;"));
        assertTrue(patched.indexOf("semionSkyboxOriginalColor = Color;") < patched.indexOf("if (make_hud())"));
        assertEquals(patched, SemionSkyboxResourcePack.patchVertexShader(patched, LoggerFactory.getLogger("skybox-test")));
    }

    @Test
    void fragmentMergePreservesOitPhasesAndOrdinaryLighting() {
        String patched = SemionSkyboxResourcePack.patchFragmentShader(modernFragment(), LoggerFactory.getLogger("skybox-test"));
        assertTrue(patched.contains("executeAlphaOnlyPhase(gl_FragCoord.z, color.a);"));
        assertTrue(patched.contains("sampleColorForAccumulation(color)"));
        assertTrue(patched.contains("layout(location = 2) in vec4 vertexColor;"));
        assertTrue(patched.contains("semionSkyboxMarker ? semionSkyboxOriginalColor : vertexColor"));
        assertTrue(patched.contains("semionSkyboxMarker ? vec4(1.0) : lightMapColor"));
        assertTrue(patched.contains("return semionSkyboxMarker ? color : apply_fog("));
        assertTrue(patched.indexOf("bool semionSkyboxMarker;") < patched.indexOf("vec4 calculateFinalColor("));
        assertEquals(patched, SemionSkyboxResourcePack.patchFragmentShader(patched, LoggerFactory.getLogger("skybox-test")));
    }

    @Test
    void skyboxOverlayPreservesSampledRgbWithModernMixOrder() {
        String source = modernFragment()
                .replace("layout(location = 3) in vec4 lightMapColor;",
                        "layout(location = 3) in vec4 lightMapColor;\nlayout(location = 4) in vec4 overlayColor;")
                .replace("vec4 calculateFinalColor(vec4 color) {",
                        "vec4 calculateFinalColor(vec4 color) {\n    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);");
        String patched = SemionSkyboxResourcePack.patchFragmentShader(source, LoggerFactory.getLogger("skybox-test"));
        var match = java.util.regex.Pattern.compile(
                "semionSkyboxMarker \\? vec4\\(([^)]*)\\) : overlayColor").matcher(patched);
        assertTrue(match.find(), "Skybox overlay override must exist in the actual mixed color expression");
        String[] values = match.group(1).split(",");
        double[] overlay = new double[4];
        for (int index = 0; index < 4; index++) {
            overlay[index] = Double.parseDouble(values.length == 1 ? values[0].trim() : values[index].trim());
        }
        double[] sample = {0.17, 0.43, 0.81};
        for (int channel = 0; channel < 3; channel++) {
            double mixed = overlay[channel] * (1.0 - overlay[3]) + sample[channel] * overlay[3];
            assertEquals(sample[channel], mixed, 1.0E-9, "Modern overlay mix must retain sky texture channel " + channel);
        }
        assertTrue(patched.contains(" : overlayColor"), "Ordinary entity overlay remains unchanged");
        assertTrue(patched.contains("executeAlphaOnlyPhase(gl_FragCoord.z, color.a);"));
        assertTrue(patched.contains("sampleColorForAccumulation(color)"));
    }

    @Test
    void incompatibleOrConflictingShadersFailPackGeneration() {
        assertThrows(IllegalStateException.class, () -> SemionSkyboxResourcePack.patchVertexShader(null, LoggerFactory.getLogger("skybox-test")));
        assertThrows(IllegalStateException.class, () -> SemionSkyboxResourcePack.patchFragmentShader("#version 150", LoggerFactory.getLogger("skybox-test")));
        assertThrows(IllegalStateException.class, () -> SemionSkyboxResourcePack.patchVertexShader(modernVertex().replace("location = 2", "location = 8"), LoggerFactory.getLogger("skybox-test")));
    }

    @Test
    void alphaOnlyPhaseRetainsMarkerOutsideGuardedColorHelper() {
        String guarded = modernFragment().replace("vec4 calculateFinalColor", "#ifndef OIT_ALPHA_ONLY\nvec4 calculateFinalColor")
                .replace("void main()", "#endif\nvoid main()");
        String patched = SemionSkyboxResourcePack.patchFragmentShader(guarded, LoggerFactory.getLogger("skybox-test"));
        assertTrue(patched.indexOf("bool semionSkyboxMarker;") < patched.indexOf("#ifndef OIT_ALPHA_ONLY"));
        assertFalse(patched.contains("\\n"));
        assertTrue(patched.indexOf("semion_skybox_fog(vec4 color") > patched.indexOf("#ifndef OIT_ALPHA_ONLY"));
        assertTrue(patched.contains("executeAlphaOnlyPhase(gl_FragCoord.z, color.a);"));
    }
    private static String modernVertex() {
        return """
                #version 330
                layout(location = 2) out vec4 vertexColor;
                //Hud
                void main() {
                    texCoord0 = UV0;
                    if (make_hud()) {
                        return;
                    }
                }
                """;
    }

    private static String modernFragment() {
        return """
                #version 330
                #include <minecraft:oit.glsl>
                uniform sampler2D Sampler0;
                layout(location = 2) in vec4 vertexColor;
                layout(location = 3) in vec4 lightMapColor;
                layout(location = 5) in vec2 texCoord0;
                vec4 calculateFinalColor(vec4 color) {
                    #ifdef OIT_ACCUMULATE
                    color = sampleColorForAccumulation(color);
                    #endif
                    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance,
                            FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart,
                            FogRenderDistanceEnd, FogColor);
                }
                void main() {
                    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
                    #ifdef OIT_ALPHA_ONLY
                    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
                    #else
                    color *= lightMapColor;
                    fragColor = calculateFinalColor(color);
                    #endif
                }
                """;
    }
    @Test
    void serviceUsesReplacementLibrary() {
        SemionSkybox replacement = new SemionSkybox(
                "replacement",
                "Replacement",
                net.minecraft.resources.Identifier.fromNamespaceAndPath("semion-td", "skybox/replacement"),
                new byte[] {1}
        );
        SemionSkyboxService service = new SemionSkyboxService(
                SemionSkyboxLibrary.empty(),
                new kim.biryeong.semiontd.game.SemionGameManager()
        );

        service.replaceLibrary(new SemionSkyboxLibrary(List.of(replacement)));

        assertEquals(List.of(replacement), service.availableSkyboxes());
    }

    private static final class CapturingBuilder implements ResourcePackBuilder {
        private final Map<String, byte[]> data = new HashMap<>();

        @Override
        public boolean addData(String path, eu.pb4.polymer.resourcepack.api.PackResource value) {
            data.put(path, value.readAllBytes());
            return true;
        }

        @Override
        public boolean copyAssets(String modId) {
            return false;
        }

        @Override
        public boolean copyFromPath(Path path, String targetPrefix, boolean override, String source) {
            return false;
        }

        @Override
        public byte @Nullable [] getData(String path) {
            return data.get(path);
        }

        @Override
        public eu.pb4.polymer.resourcepack.api.PackResource getResource(String path) {
            byte[] value = data.get(path);
            return value == null ? null : eu.pb4.polymer.resourcepack.api.PackResource.of(value);
        }

        @Override
        public byte @Nullable [] getDataOrSource(String path) {
            return data.get(path);
        }

        @Override
        public void forEachResource(BiConsumer<String, eu.pb4.polymer.resourcepack.api.PackResource> consumer) {
            data.forEach((path, value) -> consumer.accept(path, eu.pb4.polymer.resourcepack.api.PackResource.of(value)));
        }

        @Override
        public boolean addAssetsSource(String modId) {
            return false;
        }

        @Override
        public void addResourceConverter(ResourcePackBuilder.ResourceConverter converter) {
        }

        @Override
        public void addPreFinishTask(Consumer<ResourcePackBuilder> consumer) {
        }
    }
}
