package com.coldfront96.emergentciv.entity;

import com.coldfront96.emergentciv.entity.component.NeedsComponent;
import com.coldfront96.emergentciv.gate.ResourceKind;
import com.coldfront96.emergentciv.goal.GatherResourceGoal;
import com.coldfront96.emergentciv.goal.SimpleWanderGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.level.Level;

import java.util.EnumSet;
import java.util.Set;

/**
 * Phase 1 Settler entity.
 *
 * A stone-age villager stand-in with a decaying {@link NeedsComponent}
 * (hunger, energy, social) and a scripted {@link SimpleWanderGoal} that seeks
 * nearby resources once a need crosses its threshold. No AI/LLM control is
 * wired up here.
 *
 * TODO(Phase 2): This class is the intended attachment point for the external
 * agent-mind bridge. When that lands, expect the bridge to observe
 * {@link #getNeeds()} and either replace or augment the goals registered in
 * {@link #registerGoals()} below.
 */
public class SettlerEntity extends PathfinderMob {

    /** Default action label reported before a settler has done anything noteworthy. */
    public static final String IDLE_ACTION = "idle";

    private final NeedsComponent needs = new NeedsComponent();

    /**
     * The most recent scripted action this settler took, recorded by its goals
     * and the proximity handler and surfaced as {@code action_taken} in the
     * {@code StateSnapshotLogger} traces.
     */
    private String lastAction = IDLE_ACTION;

    /**
     * Resource kinds this settler has gathered at least once. Seed of the future
     * discovery system; for Phase 1 it only drives the {@code new_discovery}
     * flag on the first gather of each kind. Kept in-memory (not persisted) on
     * purpose while the discovery model is still being designed.
     */
    private final Set<ResourceKind> discovered = EnumSet.noneOf(ResourceKind.class);

    /** Latched when a gather introduces a not-yet-seen resource kind; drained by the logger. */
    private boolean pendingDiscovery = false;

    public SettlerEntity(EntityType<? extends SettlerEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Scripted, threshold-driven placeholder AI. Not LLM-driven.
        // TODO(Phase 2): replace/augment with the external agent-mind bridge.
        // Prefer actually gathering a reachable resource; fall back to wandering
        // (exploring) toward more resources when none is in range.
        this.goalSelector.addGoal(1, new GatherResourceGoal(this));
        this.goalSelector.addGoal(2, new SimpleWanderGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
    }

    public NeedsComponent getNeeds() {
        return needs;
    }

    /** The most recent scripted action label (see {@link #IDLE_ACTION}). */
    public String getLastAction() {
        return lastAction;
    }

    /** Records a free-form action label (used by goals/handlers other than gathering). */
    public void setLastAction(String action) {
        this.lastAction = action;
    }

    /**
     * Records a successful gather: updates the action label and, the first time
     * a given kind is gathered, latches the discovery flag drained by the
     * snapshot logger.
     */
    public void recordGather(ResourceKind kind) {
        this.lastAction = kind.actionLabel();
        if (discovered.add(kind)) {
            this.pendingDiscovery = true;
        }
    }

    /** Returns and clears the pending-discovery flag. Read once per snapshot. */
    public boolean consumeDiscoveryFlag() {
        boolean discovery = pendingDiscovery;
        this.pendingDiscovery = false;
        return discovery;
    }

    /** Whether this settler currently has a need urgent enough to seek resources for. */
    public boolean shouldSeekResources() {
        return needs.hasCriticalNeed();
    }

    /**
     * Used by {@link SimpleWanderGoal} to bias movement toward wood/stone/berry
     * resources once a need is critical. Phase 1 keeps this intentionally
     * simple (random wander with a resource-ward bias); it is not pathing to a
     * specific known resource location yet.
     */
    public BlockPos getWanderOrigin() {
        return this.blockPosition();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.put("Needs", needs.save(new CompoundTag()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("Needs")) {
            needs.load(compound.getCompound("Needs"));
        }
    }
}
