package com.coldfront96.emergentciv.goal;

import com.coldfront96.emergentciv.entity.SettlerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Placeholder AI goal for Phase 1.
 *
 * This is scripted (NOT LLM-driven): when a settler's {@code NeedsComponent}
 * reports a critical need, this goal makes the settler wander toward a random
 * nearby point, standing in for "seek resources" behavior until real resource
 * targeting (or the Phase 2 agent-mind bridge) replaces it.
 *
 * TODO(Phase 2): Replace this scripted wander with decisions sourced from the
 * external agent-mind bridge once it exists. Keep the goal's activation
 * condition (critical need check) as the seam the bridge can plug into.
 */
public class SimpleWanderGoal extends Goal {

    private static final double WANDER_SPEED_MODIFIER = 1.0D;
    private static final int WANDER_RADIUS = 16;

    private final SettlerEntity settler;
    private double wanderX;
    private double wanderY;
    private double wanderZ;

    public SimpleWanderGoal(SettlerEntity settler) {
        this.settler = settler;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!settler.shouldSeekResources()) {
            return false;
        }
        Vec3 target = findResourceWardTarget();
        if (target == null) {
            return false;
        }
        this.wanderX = target.x;
        this.wanderY = target.y;
        this.wanderZ = target.z;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !settler.getNavigation().isDone() && settler.shouldSeekResources();
    }

    @Override
    public void start() {
        settler.getNavigation().moveTo(wanderX, wanderY, wanderZ, WANDER_SPEED_MODIFIER);
    }

    @Override
    public void stop() {
        settler.getNavigation().stop();
    }

    /**
     * Picks a random nearby point as a stand-in "resource" destination.
     * Phase 1 does not search the world for actual wood/stone/berry blocks;
     * it only simulates seeking behavior toward a plausible nearby spot.
     */
    private Vec3 findResourceWardTarget() {
        RandomSource random = settler.getRandom();
        Vec3 pos = DefaultRandomPos.getPos(settler, WANDER_RADIUS, 7);
        if (pos != null) {
            return pos;
        }
        BlockPos origin = settler.getWanderOrigin();
        double angle = random.nextDouble() * Math.PI * 2;
        double dx = Math.cos(angle) * WANDER_RADIUS;
        double dz = Math.sin(angle) * WANDER_RADIUS;
        return new Vec3(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
    }
}
