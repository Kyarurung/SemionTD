package kim.biryeong.semiontd.balance.manage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import kim.biryeong.semiontd.balance.manage.BalanceDtos.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

/** Allowlisted public projection, kept separate from the private request journal. */
public final class BalanceUpdateLog {
    private BalanceUpdateLog() {}

    public record Change(String fieldId, String domain, String entityName, String label,
                         String unit, double before, double after) {}
    public record Update(int schemaVersion, String requestId, long appliedAt, List<Change> changes) {}

    public static Component link(String requestId) {
        URI uri = URI.create("https://semiontd.biryeong.kim/patches/" + UUID.fromString(requestId));
        return Component.literal(" [변경 내역 보기]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(new ClickEvent.OpenUrl(uri))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("웹에서 밸런스 변경 전후 값을 확인합니다."))));
    }

    public static void export(Path configDir, List<BalanceDeployment> deployments, List<BalanceField> fields) {
        Map<String, BalanceField> metadata = fields.stream().collect(Collectors.toMap(BalanceField::id, Function.identity()));
        Path directory = configDir.resolve("web_catalog/balance-updates");
        try {
            for (BalanceDeployment deployment : deployments) {
                if (deployment.state() != DeploymentState.APPLIED || deployment.appliedAt() == null || deployment.changes().isEmpty()) {continue;}
                String id = UUID.fromString(deployment.requestId()).toString();
                Path target = directory.resolve(id + ".json");
                if (Files.exists(target)) {continue;}
                List<Change> changes = deployment.changes().stream().map(change -> {
                    BalanceField field = metadata.get(change.fieldId());
                    return new Change(change.fieldId(), field == null ? "other" : field.domain(),
                            field == null ? "기타" : field.entityName(), field == null ? change.fieldId() : field.label(),
                            field == null ? "number" : field.unit(), change.expectedValue(), change.value());
                }).toList();
                Files.createDirectories(directory);
                Path temporary = Files.createTempFile(directory, ".update-", ".tmp");
                try {
                    Files.writeString(temporary, BalanceBundle.GSON.toJson(new Update(1, id, deployment.appliedAt(), changes)));
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
