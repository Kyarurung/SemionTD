package kim.biryeong.semiontd.job;

import kim.biryeong.semiontd.tower.magicschool.MagicSchoolCurriculum;

final class JobMagicSchoolLifecycle implements JobLifecycle {
    @Override
    public void onMatchStarted(JobContext context) {
        MagicSchoolCurriculum.clear(context.player().uuid());
    }

    @Override
    public void onEliminated(JobContext context) {
        MagicSchoolCurriculum.clear(context.player().uuid());
    }

    @Override
    public void onMatchClosed(JobContext context) {
        MagicSchoolCurriculum.clear(context.player().uuid());
    }
}
