package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.config.TowerBalanceRuntime;
import kim.biryeong.semiontd.entity.monster.Monster;
import kim.biryeong.semiontd.tower.demonlord.DemonLordState;
import kim.biryeong.semiontd.tower.demonlord.DemonLordService;
import kim.biryeong.semiontd.tower.demonlord.DemonLordStates;
import kim.biryeong.semiontd.tower.demonlord.DemonLordTowers;

final class JobDemonLordLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        DemonLordStates.clear(context.player().uuid());
        // 레벨은 한 경기 안에서만 유지됩니다. 새 경기는 여기서만 잊습니다.
        DemonLordStates.resetProgression(context.player().uuid());
        DemonLordStates.getOrCreate(context.player().uuid());
    }

    /**
     * 라운드 시작은 준비 단계입니다. 여기서는 상태만 있는지 확인하고 전투로는 넣지 않습니다.
     *
     * <p>전투 진입은 웨이브가 실제로 시작될 때({@code PlayerLane#markWaveStarted})입니다.
     * 준비 단계에 전투로 들어가면 핫바가 스킬로 덮여 타워를 살 수 없습니다.
     */
    @Override
    public void onRoundStarted(JobContext context, int round) {
        DemonLordStates.getOrCreate(context.player().uuid());
    }

    /**
     * Kills are the builder's entire scaling curve, so experience is weighted by how tanky the
     * victim was rather than being a flat per-kill number.
     */
    @Override
    public void onMonsterKilled(JobContext context, Monster monster, long mineralReward) {
        if (monster == null) {
            return;
        }
        DemonLordState state = DemonLordStates.get(context.player().uuid());
        if (state == null || !state.inCombat()) {
            return;
        }
        double perMaxHealth = TowerBalanceRuntime.ability(
                DemonLordTowers.GLOBAL_CONFIG_ID, "experiencePerMaxHealth", 0.02);
        state.addExperience(Math.max(0.0, monster.maxHealth() * perMaxHealth));
    }

    /**
     * 라운드를 넘기기만 해도 조금씩 자랍니다.
     *
     * <p>처치가 유일한 성장 수단이면 한 번 밀리기 시작한 마왕은 영영 따라잡지 못합니다. 몹을
     * 하나도 못 잡은 라운드에도 기본치를 주어, 뒤처진 판에서도 복구할 여지를 남깁니다.
     * 직접 잡는 편이 여전히 훨씬 빠릅니다.
     */
    @Override
    public void onRoundEnded(JobContext context, int round) {
        DemonLordState state = DemonLordStates.get(context.player().uuid());
        if (state == null) {
            return;
        }
        double passive = TowerBalanceRuntime.ability(
                DemonLordTowers.GLOBAL_CONFIG_ID, "passiveExperiencePerRound", 6.0);
        state.addExperience(Math.max(0.0, passive));
    }

    @Override
    public void onEliminated(JobContext context) {
        DemonLordService.clearPlayerState(context.player().uuid());
    }
}
