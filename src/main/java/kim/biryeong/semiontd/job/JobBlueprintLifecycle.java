package kim.biryeong.semiontd.job;

import java.util.List;
import kim.biryeong.semiontd.SemionTd;
import kim.biryeong.semiontd.tower.blueprint.BlueprintLibrary;
import kim.biryeong.semiontd.tower.blueprint.BlueprintStates;

final class JobBlueprintLifecycle implements JobLifecycle {
    /** 계정에 저장된 설계도로 이 경기의 설계도를 만듭니다. 지금 한도에 안 맞는 것은 건너뜁니다. */
    @Override
    public void onMatchStarted(JobContext context) {
        List<String> skipped = BlueprintLibrary.installForMatch(context.player().uuid());
        BlueprintStates.bindPlayer(context.player());
        if (!skipped.isEmpty()) {
            kim.biryeong.semiontd.SemionTd.LOGGER.info("Skipped {} blueprint(s) for {}: {}",
                    skipped.size(), context.player().uuid(), skipped);
        }
    }

    @Override
    public void onEliminated(JobContext context) {
        BlueprintStates.clear(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        BlueprintStates.clear(context.player().uuid());
    }
}
