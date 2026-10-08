package kim.biryeong.semiontd.game.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kim.biryeong.semiontd.buildguide.BuildAction;
import kim.biryeong.semiontd.buildguide.BuildActionType;
import kim.biryeong.semiontd.game.GridPosition;

public final class MatchReplayFixture {
    public static final String RESOURCE = "/replay/match-893854454113494679.json";
    private final JsonObject document;
    private final Map<String, JsonObject> towers = new LinkedHashMap<>();
    private final Map<String, JsonObject> edges = new LinkedHashMap<>();

    public MatchReplayFixture(JsonObject document) {
        this.document = Objects.requireNonNull(document);
        if (document.get("schema_version").getAsInt() != 1
                || !"ROUND_AND_PER_PARTICIPANT_SEQUENCE_ONLY".equals(document.get("timing").getAsString())) {
            throw new IllegalArgumentException("Unsupported replay schema or timing contract");
        }
        if (!document.get("match_id").getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("match_id must be a decimal string");
        }
        Long.parseLong(document.get("match_id").getAsString());
        document.getAsJsonObject("catalog").getAsJsonArray("towers").forEach(value -> {
            JsonObject tower = value.getAsJsonObject();
            towers.put(tower.get("id").getAsString(), tower);
        });
        document.getAsJsonObject("catalog").getAsJsonArray("upgrades").forEach(value -> {
            JsonObject edge = value.getAsJsonObject();
            edges.put(edge.get("fromTowerId").getAsString() + "->" + edge.get("id").getAsString(), edge);
        });
    }

    public static MatchReplayFixture load() {
        var stream = Objects.requireNonNull(MatchReplayFixture.class.getResourceAsStream(RESOURCE), RESOURCE);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new MatchReplayFixture(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public JsonObject document() {
        return document.deepCopy();
    }

    public List<RecordedAction> actions(String slot) {
        JsonObject participant = document.getAsJsonArray("participants").asList().stream()
                .map(value -> value.getAsJsonObject())
                .filter(value -> slot.equals(value.get("slot").getAsString()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown participant " + slot));
        List<RecordedAction> result = new ArrayList<>();
        int previousRound = 0;
        for (var value : participant.getAsJsonArray("actions")) {
            JsonObject action = value.getAsJsonObject();
            int sequence = action.get("sequence").getAsInt();
            int round = action.get("round").getAsInt();
            if (sequence != result.size() || round < 1 || round < previousRound) {
                throw new IllegalArgumentException(slot + "/" + sequence + ": invalid action order");
            }
            previousRound = round;
            GridPosition position = position(action);
            BuildActionType type = BuildActionType.valueOf(action.get("action_type").getAsString());
            if (type.name().startsWith("TOWER_") && position == null) {
                throw new IllegalArgumentException(slot + "/" + sequence + ": missing tower position");
            }
            String mode = action.get("position_mode").getAsString();
            if (!BuildAction.POSITION_ABSOLUTE.equals(mode) && !BuildAction.POSITION_LANE_RELATIVE.equals(mode)) {
                throw new IllegalArgumentException("Unknown position mode " + mode);
            }
            BuildAction build = new BuildAction(round, type, action.get("subject_id").getAsString(), position,
                    nonNegativeLong(action, "cost"), nonNegativeLong(action, "income_gain"),
                    action.get("scheduled_round").getAsInt(), action.get("target_team").getAsString(),
                    action.get("target_lane_id").getAsInt(), mode);
            result.add(new RecordedAction(slot, sequence, build));
        }
        return List.copyOf(result);
    }

    public Layout reconstructTowerInputs(List<RecordedAction> actions, Map<Location, PlacedTower> initial) {
        Map<Location, PlacedTower> state = new LinkedHashMap<>(initial);
        List<CostDifference> differences = new ArrayList<>();
        long spent = 0;
        long refunded = 0;
        for (RecordedAction recorded : actions) {
            BuildAction action = recorded.action();
            if (!action.type().name().startsWith("TOWER_")) {
                throw refusal(recorded, "unsupported layout action " + action.type());
            }
            Location location = new Location(recorded.slot(), action.positionMode(), action.position());
            PlacedTower previous = state.get(location);
            switch (action.type()) {
                case TOWER_PLACE -> {
                    if (state.keySet().stream().anyMatch(key -> sameColumn(key, location))) {
                        throw refusal(recorded, "occupied column; death/movement must be supplied explicitly");
                    }
                    JsonObject type = towers.get(action.subjectId());
                    if (type == null) {
                        throw refusal(recorded, "unknown historical tower " + action.subjectId());
                    }
                    compareCost(recorded, type.get("mineralCost").getAsLong(), differences);
                    state.put(location, new PlacedTower(recorded.slot() + "/placement/" + recorded.sequence(),
                            action.subjectId(), action.cost()));
                    spent = Math.addExact(spent, action.cost());
                }
                case TOWER_UPGRADE -> {
                    if (previous == null) {
                        throw refusal(recorded, "no source tower; lifecycle/identity state is unavailable");
                    }
                    JsonObject edge = edges.get(previous.type() + "->" + action.subjectId());
                    if (edge == null) {
                        throw refusal(recorded, "unknown historical edge " + previous.type() + "->" + action.subjectId());
                    }
                    compareCost(recorded, edge.get("mineralCost").getAsLong(), differences);
                    state.put(location, new PlacedTower(previous.scenarioId(), edge.get("toTowerId").getAsString(),
                            Math.addExact(previous.paid(), action.cost())));
                    spent = Math.addExact(spent, action.cost());
                }
                case TOWER_SELL -> {
                    if (previous == null || !previous.type().equals(action.subjectId())) {
                        throw refusal(recorded, "sale source does not match known positional tower");
                    }
                    if (action.cost() != 0) {
                        throw refusal(recorded, "sale cost must be zero; refund is income_gain");
                    }
                    refunded = Math.addExact(refunded, action.incomeGain());
                    state.remove(location);
                }
                default -> throw refusal(recorded, "unsupported layout action " + action.type());
            }
        }
        return new Layout(Map.copyOf(state), spent, refunded, List.copyOf(differences));
    }

    public static GridPosition absolutePosition(RecordedAction recorded, GridPosition laneOrigin) {
        BuildAction action = recorded.action();
        GridPosition position = Objects.requireNonNull(action.position(), "Action has no position");
        if (!action.hasLaneRelativePosition()) {
            return position;
        }
        Objects.requireNonNull(laneOrigin, "Lane origin must be supplied; the live API does not expose it");
        return new GridPosition(Math.addExact(position.x(), laneOrigin.x()),
                Math.addExact(position.y(), laneOrigin.y()), Math.addExact(position.z(), laneOrigin.z()));
    }

    private static boolean sameColumn(Location first, Location second) {
        return first.slot().equals(second.slot()) && first.mode().equals(second.mode())
                && first.position().x() == second.position().x() && first.position().z() == second.position().z();
    }

    private static void compareCost(RecordedAction action, long catalogCost, List<CostDifference> differences) {
        if (action.action().cost() != catalogCost) {
            differences.add(new CostDifference(action.slot(), action.sequence(), catalogCost, action.action().cost()));
        }
    }

    private static IllegalArgumentException refusal(RecordedAction action, String reason) {
        return new IllegalArgumentException(action.slot() + "/" + action.sequence() + ": " + reason);
    }

    private static long nonNegativeLong(JsonObject object, String key) {
        long value = Long.parseLong(object.get(key).getAsString());
        if (value < 0) {
            throw new IllegalArgumentException("Negative " + key);
        }
        return value;
    }

    private static GridPosition position(JsonObject action) {
        boolean x = !action.get("position_x").isJsonNull();
        boolean y = !action.get("position_y").isJsonNull();
        boolean z = !action.get("position_z").isJsonNull();
        if (x != y || y != z) {
            throw new IllegalArgumentException("Partial coordinates");
        }
        return x ? new GridPosition(action.get("position_x").getAsInt(),
                action.get("position_y").getAsInt(), action.get("position_z").getAsInt()) : null;
    }

    public record RecordedAction(String slot, int sequence, BuildAction action) {
    }

    public record Location(String slot, String mode, GridPosition position) {
    }

    public record PlacedTower(String scenarioId, String type, long paid) {
    }

    public record CostDifference(String slot, int sequence, long catalogCost, long recordedCost) {
    }

    public record Layout(Map<Location, PlacedTower> towers, long spent, long refunded, List<CostDifference> costDifferences) {
    }
}
