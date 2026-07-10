package com.coldfront96.emergentciv.gate;

import com.coldfront96.emergentciv.entity.component.NeedsComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The three stone-age resource kinds a Phase 1 settler can actually gather to
 * satisfy a need. Each kind bundles the world-recognition ({@link #matches}),
 * the harvest interaction ({@link #harvest}), the item the settler ends up
 * holding, and which {@link NeedsComponent} restore method it feeds.
 *
 * <p>This is the block-side companion to {@link StoneAgeResourceGate}: the gate
 * answers "is this <em>item</em> allowed at this tech tier?" while this enum
 * answers "which nearby <em>block</em> should I seek, and what happens when I
 * reach it?". The scripted {@code GatherResourceGoal} drives both.</p>
 *
 * <p>The need mapping is intentionally simple for Phase 1: hunger is fed by
 * eating berries; energy is fed by gathering wood or stone. The social need is
 * not gathered — it is restored by settler proximity (see
 * {@code SocialProximityHandler}).</p>
 *
 * TODO(Phase 2): The agent-mind bridge may want to reason over these kinds
 * (costs, yields, tool requirements) rather than the scripted goal consuming
 * them directly. Keep the enum's public surface stable and data-driven.
 */
public enum ResourceKind {

    /** Ripe berry bushes. Eaten in place to restore hunger; the bush is not destroyed. */
    BERRIES("eat_berries", 30.0F) {
        @Override
        public boolean matches(BlockState state) {
            return state.getBlock() instanceof SweetBerryBushBlock
                    && state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }

        @Override
        public void restore(NeedsComponent needs) {
            needs.restoreHunger(restoreAmount);
        }

        @Override
        public ItemStack harvest(Level level, BlockPos pos, BlockState state) {
            // Pick the berries: reset the bush to its picked (age 1) state rather
            // than destroying it, mirroring vanilla harvesting.
            level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Level.UPDATE_CLIENTS);
            return new ItemStack(Items.SWEET_BERRIES);
        }
    },

    /** Any log. Broken to gather firewood; restores energy. */
    WOOD("gather_wood", 25.0F) {
        @Override
        public boolean matches(BlockState state) {
            return state.is(BlockTags.LOGS);
        }

        @Override
        public void restore(NeedsComponent needs) {
            needs.restoreEnergy(restoreAmount);
        }

        @Override
        public ItemStack harvest(Level level, BlockPos pos, BlockState state) {
            ItemStack gathered = new ItemStack(state.getBlock().asItem());
            level.destroyBlock(pos, false);
            return gathered;
        }
    },

    /** Stone-family blocks. Broken to gather stone; restores energy. */
    STONE("gather_stone", 20.0F) {
        @Override
        public boolean matches(BlockState state) {
            // Kept in step with the stone_age_stone item tag consulted by
            // StoneAgeResourceGate, so anything gathered is also gate-allowed.
            return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE);
        }

        @Override
        public void restore(NeedsComponent needs) {
            needs.restoreEnergy(restoreAmount);
        }

        @Override
        public ItemStack harvest(Level level, BlockPos pos, BlockState state) {
            ItemStack gathered = new ItemStack(state.getBlock().asItem());
            level.destroyBlock(pos, false);
            return gathered;
        }
    };

    /** Short verb label recorded as the settler's {@code action_taken} in snapshots. */
    private final String actionLabel;

    /** Amount restored to the associated need on a successful gather. */
    protected final float restoreAmount;

    ResourceKind(String actionLabel, float restoreAmount) {
        this.actionLabel = actionLabel;
        this.restoreAmount = restoreAmount;
    }

    /** Whether the given block state is a gatherable instance of this resource kind. */
    public abstract boolean matches(BlockState state);

    /** Applies this kind's restore to the settler's needs. */
    public abstract void restore(NeedsComponent needs);

    /**
     * Performs the world-side harvest interaction (break the block or pick the
     * bush) and returns the item the settler now holds. Callers should verify
     * {@link #matches} still holds first.
     */
    public abstract ItemStack harvest(Level level, BlockPos pos, BlockState state);

    public String actionLabel() {
        return actionLabel;
    }

    /** True if {@link #matches} holds for any resource kind. */
    public static boolean isAnyResource(BlockState state) {
        for (ResourceKind kind : values()) {
            if (kind.matches(state)) {
                return true;
            }
        }
        return false;
    }

    /** The first resource kind matching the given state, or {@code null} if none. */
    public static ResourceKind classify(BlockState state) {
        for (ResourceKind kind : values()) {
            if (kind.matches(state)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The resource kinds worth seeking for the settler's currently critical
     * needs, in priority order. Hunger maps to berries; energy maps to wood
     * then stone. A settler whose only critical need is social has nothing to
     * gather and gets an empty list.
     */
    public static List<ResourceKind> forCriticalNeeds(NeedsComponent needs) {
        List<ResourceKind> kinds = new ArrayList<>();
        if (needs.isHungerCritical()) {
            kinds.add(BERRIES);
        }
        if (needs.isEnergyCritical()) {
            kinds.add(WOOD);
            kinds.add(STONE);
        }
        return kinds;
    }
}
