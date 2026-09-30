package kim.biryeong.semiontd.tower.blueprint;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 계정에 저장된 설계도의 서버 쪽 사본. 프로필을 읽을 때 채우고, 바꿀 때는 이 사본을 고친 뒤 계정에 저장합니다.
 *
 * <p>경기 중 실제 타워로 쓰는 것은 {@link BlueprintStates}이고, 경기가 시작하면 여기 목록으로 그 경기의 설계도를 만듭니다.
 */
public final class BlueprintLibrary {
    private static final Map<UUID, List<BlueprintDesign>> DESIGNS = new HashMap<>();

    private BlueprintLibrary() {
    }

    public static synchronized void load(UUID owner, List<BlueprintDesign> designs) {
        DESIGNS.put(owner, new ArrayList<>(designs == null ? List.of() : designs));
    }

    public static synchronized List<BlueprintDesign> designs(UUID owner) {
        return List.copyOf(DESIGNS.getOrDefault(owner, List.of()));
    }

    /** 새 설계를 목록 끝에 더할 수 있는지 봅니다. 문제가 없으면 비어 있습니다. */
    public static Optional<String> check(UUID owner, BlueprintDesign design) {
        if (BlueprintStates.sanitizeName(design.name()).isEmpty()) {
            return Optional.of("이름은 1~16자여야 합니다.");
        }
        Optional<String> invalid = BlueprintPricing.validate(design.stats());
        if (invalid.isPresent()) {
            return invalid;
        }
        if (BlueprintVisuals.find(design.visualSourceId()).isEmpty()) {
            return Optional.of("고를 수 없는 겉모습입니다: " + design.visualSourceId());
        }
        if (designs(owner).size() >= BlueprintPricing.maxBlueprints()) {
            return Optional.of("설계도는 " + BlueprintPricing.maxBlueprints() + "장까지 저장할 수 있습니다. 먼저 하나를 지우세요.");
        }
        return Optional.empty();
    }

    /** 검사를 통과한 설계를 더하고, 저장할 새 목록을 돌려줍니다. */
    public static synchronized List<BlueprintDesign> add(UUID owner, BlueprintDesign design) {
        List<BlueprintDesign> owned = DESIGNS.computeIfAbsent(owner, ignored -> new ArrayList<>());
        owned.add(design.withName(BlueprintStates.sanitizeName(design.name())));
        return List.copyOf(owned);
    }

    /** index번째(0부터) 설계를 지웁니다. 없으면 비어 있고, 있으면 저장할 새 목록입니다. */
    public static synchronized Optional<List<BlueprintDesign>> remove(UUID owner, int index) {
        List<BlueprintDesign> owned = DESIGNS.get(owner);
        if (owned == null || index < 0 || index >= owned.size()) {
            return Optional.empty();
        }
        owned.remove(index);
        return Optional.of(List.copyOf(owned));
    }

    /** 경기 시작 때 저장된 설계도로 그 경기의 설계도를 만듭니다. 지금 한도에 안 맞는 설계는 건너뛰고 이유를 돌려줍니다. */
    public static List<String> installForMatch(UUID owner) {
        BlueprintStates.clear(owner);
        List<String> skipped = new ArrayList<>();
        for (BlueprintDesign design : designs(owner)) {
            BlueprintStates.Creation creation = BlueprintStates.create(owner, design.name(), design.stats(), design.visualSourceId());
            if (!creation.success()) {
                skipped.add(design.name() + ": " + creation.message());
            }
        }
        return skipped;
    }

    public static synchronized void forget(UUID owner) {
        DESIGNS.remove(owner);
    }
}
