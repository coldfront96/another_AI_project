package com.coldfront96.emergentciv.goal;

import com.coldfront96.emergentciv.entity.SettlerEntity;
import com.coldfront96.emergentciv.gate.ResourceKind;
import com.coldfront96.emergentciv.gate.StoneAgeResourceGate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.List;

/**
 * Scripted (NOT LLM-driven) resource-gathering goal for Phase 1.
 *
 * <p>Where {@link SimpleWanderGoal} only <em>seeks</em> (wanders toward a
 * plausible nearby spot), this goal closes the survival loop: when a settler
 * has a critical need mappable to a resource, it searches the immediate area
 * for a matching wood/stone/berry block ({@link ResourceKind}), paths to it,
 * and once in reach performs the real interaction — breaking the block or
 * picking the bush — then restores the associated need and holds the gathered
 * item. All harvestable kinds are validated through {@link StoneAgeResourceGate}
 * so only tech-tier-appropriate resources are ever taken.</p>
 *
 * <p>Registered at a higher priority than {@link SimpleWanderGoal}, so a settler
 * that can actually reach a resource does so, and only wanders (to explore for
 * more) when nothing is in range.</p>
 *
 * TODO(Phase 2): Replace the nearest-block scan and fixed gather timing with
 * decisions sourced from the external agent-mind bridge. Keep the critical-need
 * activation check as the seam the bridge plugs into.
 */
public class GatherResourceGoal extends Goal {

    private static final int SEARCH_RADIUS = 8;
    private static final int SEARCH_VERTICAL = 4;
    private static final double REACH_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double GATHER_SPEED_MODIFIER = 1.0D;
    /** Ticks spent "working" the resource before it is harvested. */
    private static final int GATHER_DURATION_TICKS = 30;

    private final SettlerEntity settler;
    private BlockPos targetBlock;
    private ResourceKind targetKind;
    private int gatherTimer;

    public GatherResourceGoal(SettlerEntity settler) {
        this.settler = settler;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(settler.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!settler.shouldSeekResources()) {
            return false;
        }
        List<ResourceKind> kinds = ResourceKind.forCriticalNeeds(settler.getNeeds());
        if (kinds.isEmpty()) {
            // Only social is critical; nothing to gather (handled by proximity).
            return false;
        }
        return findNearestResource(level, kinds);
    }

    @Override
    public boolean canContinueToUse() {
        if (targetBlock == null || targetKind == null) {
            return false;
        }
        if (!(settler.level() instanceof ServerLevel level)) {
            return false;
        }
        // Bail if the need was satisfied elsewhere or the block is gone/changed.
        return settler.shouldSeekResources() && targetKind.matches(level.getBlockState(targetBlock));
    }

    @Override
    public void start() {
        gatherTimer = 0;
        settler.getNavigation().moveTo(
                targetBlock.getX() + 0.5D, targetBlock.getY(), targetBlock.getZ() + 0.5D,
                GATHER_SPEED_MODIFIER);
    }

    @Override
    public void stop() {
        targetBlock = null;
        targetKind = null;
        gatherTimer = 0;
        settler.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (targetBlock == null || targetKind == null) {
            return;
        }
        settler.getLookControl().setLookAt(
                targetBlock.getX() + 0.5D, targetBlock.getY() + 0.5D, targetBlock.getZ() + 0.5D);

        double distanceSqr = settler.distanceToSqr(
                targetBlock.getX() + 0.5D, targetBlock.getY() + 0.5D, targetBlock.getZ() + 0.5D);
        if (distanceSqr > REACH_DISTANCE_SQR) {
            // Keep pathing; re-issue the move order if the navigator stalled.
            if (settler.getNavigation().isDone()) {
                settler.getNavigation().moveTo(
                        targetBlock.getX() + 0.5D, targetBlock.getY(), targetBlock.getZ() + 0.5D,
                        GATHER_SPEED_MODIFIER);
            }
            return;
        }

        settler.getNavigation().stop();
        if (++gatherTimer >= GATHER_DURATION_TICKS) {
            performGather();
        }
    }

    private void performGather() {
        if (!(settler.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(targetBlock);
        if (!targetKind.matches(state)) {
            // Block changed out from under us; drop the target and let canUse re-scan.
            targetBlock = null;
            targetKind = null;
            return;
        }

        ItemStack gathered = targetKind.harvest(level, targetBlock, state);
        // Safety net: never keep a resource the tech-tier gate disallows.
        if (!gathered.isEmpty() && StoneAgeResourceGate.isAllowed(gathered)) {
            settler.setItemInHand(InteractionHand.MAIN_HAND, gathered);
        }
        targetKind.restore(settler.getNeeds());
        settler.recordGather(targetKind);

        // Consumed this target; clearing lets the goal re-evaluate next tick.
        targetBlock = null;
        targetKind = null;
    }

    /**
     * Scans a small box around the settler for the nearest block matching any of
     * the desired kinds, recording it as the target. Returns whether one was found.
     */
    private boolean findNearestResource(ServerLevel level, List<ResourceKind> kinds) {
        BlockPos origin = settler.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        double bestDistanceSqr = Double.MAX_VALUE;
        BlockPos bestPos = null;
        ResourceKind bestKind = null;

        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                for (int dy = -SEARCH_VERTICAL; dy <= SEARCH_VERTICAL; dy++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    ResourceKind match = firstMatch(kinds, state);
                    if (match == null) {
                        continue;
                    }
                    double distanceSqr = origin.distSqr(cursor);
                    if (distanceSqr < bestDistanceSqr) {
                        bestDistanceSqr = distanceSqr;
                        bestPos = cursor.immutable();
                        bestKind = match;
                    }
                }
            }
        }

        this.targetBlock = bestPos;
        this.targetKind = bestKind;
        return bestPos != null;
    }

    private static ResourceKind firstMatch(List<ResourceKind> kinds, BlockState state) {
        for (ResourceKind kind : kinds) {
            if (kind.matches(state)) {
                return kind;
            }
        }
        return null;
    }
}
