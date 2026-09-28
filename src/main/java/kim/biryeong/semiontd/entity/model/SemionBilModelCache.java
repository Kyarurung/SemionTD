package kim.biryeong.semiontd.entity.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tomalbrc.bil.core.model.Model;
import de.tomalbrc.bil.file.loader.AjModelLoader;
import de.tomalbrc.bil.file.loader.BbModelLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

public final class SemionBilModelCache {
    /** 이름이 이것으로 끝나는 큐브는 스스로 빛납니다(눈빛·룬처럼 어두운 데서도 밝게 보이는 부분). */
    public static final String GLOW_SUFFIX = "_glow";
    /** 빛나는 큐브에 주는 밝기(0~15). 모델 요소의 light_emission으로 들어갑니다. */
    public static final int GLOW_LIGHT = 15;

    private static final Map<String, Optional<Model>> MODELS = new ConcurrentHashMap<>();

    private SemionBilModelCache() {
    }

    public static Optional<Model> load(String modelId) {
        String normalizedId = normalize(modelId);
        if (normalizedId == null) {
            return Optional.empty();
        }
        return MODELS.computeIfAbsent(normalizedId, SemionBilModelCache::loadUncached);
    }

    private static Optional<Model> loadUncached(String modelId) {
        ResourceLocation id = ResourceLocation.tryParse(modelId);
        if (id == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(loadBbModel(id));
        } catch (RuntimeException ignored) {
            try {
                return Optional.of(AjModelLoader.load(id));
            } catch (RuntimeException ignoredAgain) {
                return Optional.empty();
            }
        }
    }

    /**
     * BIL의 BbModelLoader.load(id)와 같은 경로에서 .bbmodel을 읽되, {@link #GLOW_SUFFIX}로 끝나는 큐브에 light_emission을
     * 넣은 뒤 넘깁니다. 블록벤치 자유 모델 형식은 큐브 발광 값을 저장하지 않으므로 이름으로 표시합니다.
     */
    private static Model loadBbModel(ResourceLocation id) {
        String path = String.format("/model/%s/%s.bbmodel", id.getNamespace(), id.getPath());
        try (InputStream stream = BbModelLoader.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("Model doesn't exist: " + path);
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            applyGlow(json);
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            return new BbModelLoader().load(new ByteArrayInputStream(bytes), id.getPath());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read model " + path, exception);
        }
    }

    /** 이름이 {@link #GLOW_SUFFIX}로 끝나고 발광 값이 없는 요소에 {@link #GLOW_LIGHT}를 줍니다. 바꾼 요소 수를 돌려줍니다. */
    public static int applyGlow(JsonObject model) {
        if (!model.has("elements") || !model.get("elements").isJsonArray()) {
            return 0;
        }
        int changed = 0;
        for (JsonElement element : model.getAsJsonArray("elements")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement name = object.get("name");
            if (name == null || !name.isJsonPrimitive() || !name.getAsString().endsWith(GLOW_SUFFIX)) {
                continue;
            }
            if (object.has("light_emission") && object.get("light_emission").getAsInt() > 0) {
                continue;
            }
            object.addProperty("light_emission", GLOW_LIGHT);
            changed++;
        }
        return changed;
    }

    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
