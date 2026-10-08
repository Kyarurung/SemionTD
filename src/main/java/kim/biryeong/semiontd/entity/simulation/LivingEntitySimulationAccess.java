package kim.biryeong.semiontd.entity.simulation;

import net.minecraft.world.phys.AABB;

public interface LivingEntitySimulationAccess {
    void semiontd$prepareLivingTick();
    void semiontd$finishLivingAi(AABB beforeTravelBox);
    void semiontd$finishLivingTick();
}
