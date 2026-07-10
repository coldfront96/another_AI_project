package com.coldfront96.emergentciv.entity.component;

import net.minecraft.nbt.CompoundTag;

/**
 * Stubbed needs component attached to a {@link com.coldfront96.emergentciv.entity.SettlerEntity}.
 *
 * Each need is a decaying float clamped to [0, 100]. Decay is driven by the
 * day/night tick handler (see
 * {@link com.coldfront96.emergentciv.event.NeedsDecayHandler}). Phase 1 only
 * reads these values to drive {@link com.coldfront96.emergentciv.goal.SimpleWanderGoal}
 * threshold checks; nothing here is LLM/AI-driven yet.
 *
 * TODO(Phase 2): This is the primary hook point for the external agent-mind
 * bridge. An outside AI process will eventually read/write these values (and
 * likely richer state) to make decisions on behalf of the Settler instead of
 * the scripted goals used in Phase 1. Keep this class's public surface stable
 * and serializable so the bridge can consume it without a rewrite.
 */
public class NeedsComponent {

    public static final float MIN_VALUE = 0.0F;
    public static final float MAX_VALUE = 100.0F;

    public static final float DEFAULT_HUNGER_DECAY_PER_DAY = 8.0F;
    public static final float DEFAULT_ENERGY_DECAY_PER_DAY = 10.0F;
    public static final float DEFAULT_SOCIAL_DECAY_PER_DAY = 6.0F;

    /** Threshold below which a need is considered "critical" and should trigger seeking behavior. */
    public static final float SEEK_THRESHOLD = 35.0F;

    private float hunger = MAX_VALUE;
    private float energy = MAX_VALUE;
    private float social = MAX_VALUE;

    public float getHunger() {
        return hunger;
    }

    public float getEnergy() {
        return energy;
    }

    public float getSocial() {
        return social;
    }

    public void setHunger(float value) {
        this.hunger = clamp(value);
    }

    public void setEnergy(float value) {
        this.energy = clamp(value);
    }

    public void setSocial(float value) {
        this.social = clamp(value);
    }

    public void decayHunger(float amount) {
        setHunger(this.hunger - amount);
    }

    public void decayEnergy(float amount) {
        setEnergy(this.energy - amount);
    }

    public void decaySocial(float amount) {
        setSocial(this.social - amount);
    }

    public void restoreHunger(float amount) {
        setHunger(this.hunger + amount);
    }

    public void restoreEnergy(float amount) {
        setEnergy(this.energy + amount);
    }

    public void restoreSocial(float amount) {
        setSocial(this.social + amount);
    }

    public boolean isHungerCritical() {
        return hunger < SEEK_THRESHOLD;
    }

    public boolean isEnergyCritical() {
        return energy < SEEK_THRESHOLD;
    }

    public boolean isSocialCritical() {
        return social < SEEK_THRESHOLD;
    }

    /** True if any need has crossed the seek threshold and resource-seeking behavior should kick in. */
    public boolean hasCriticalNeed() {
        return isHungerCritical() || isEnergyCritical() || isSocialCritical();
    }

    private static float clamp(float value) {
        return Math.max(MIN_VALUE, Math.min(MAX_VALUE, value));
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putFloat("Hunger", hunger);
        tag.putFloat("Energy", energy);
        tag.putFloat("Social", social);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.hunger = clamp(tag.getFloat("Hunger"));
        this.energy = clamp(tag.getFloat("Energy"));
        this.social = clamp(tag.getFloat("Social"));
    }
}
