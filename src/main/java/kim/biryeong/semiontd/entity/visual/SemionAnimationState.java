package kim.biryeong.semiontd.entity.visual;

public enum SemionAnimationState {
    IDLE("idle"),
    WALK("walk"),
    ATTACK("attack"),
    HEAL("heal"),
    /** 스킬 시전(정원사 등). 공격·치유처럼 한 번 돌고 끝나는 동작입니다. */
    SKILL("skill");

    private final String animationId;

    SemionAnimationState(String animationId) {
        this.animationId = animationId;
    }

    public String animationId() {
        return animationId;
    }
}
