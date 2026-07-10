package com.coldfront96.emergentciv.gate;

import com.coldfront96.emergentciv.entity.component.NeedsComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The resource kinds a Phase 1 settler knows to seek to satisfy a need. Each
 * kind bundles the world-recognition ({@link #matches}), the harvest
 * interaction ({@link #harvest}), the item the settler ends up holding, and
 * which {@link NeedsComponent} restore method it feeds.
 *
 * <p>This is a target-selection heuristic, NOT a permission gate. Per
 * {@code docs/DESIGN.md} ("No artificial safety walls") there is no coded
 * check on what a settler may keep or interact with: a settler is free to
 * attempt any interaction this scan finds, and the only thing deciding the
 * outcome is vanilla's own natural consequences — most relevantly the
 * correct-tool-for-drops rule applied in {@link #harvest}, so e.g. punching
 * stone bare-handed breaks the block but yields nothing, exactly as it would
 * for a player. Failed attempts are deliberately possible; they are recorded
 * in the snapshot traces as training signal.</p>
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
        public ItemStack harvest(Level level, BlockPos pos, BlockState state, ItemStack tool) {
            // Pick the berries: reset the bush to its picked (age 1) state rather
            // than destroying it, mirroring vanilla harvesting. Hand-pickable in
            // vanilla, so the tool never matters here.
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
        public ItemStack harvest(Level level, BlockPos pos, BlockState state, ItemStack tool) {
            return breakBlock(level, pos, state, tool);
        }
    },

    /**
     * Stone-family blocks. Broken to gather; restores energy. Yield follows the
     * block's loot table, so natural stone gives cobblestone (as in vanilla).
     */
    STONE("gather_stone", 20.0F) {
        @Override
        public boolean matches(BlockState state) {
            return state.is(Blocks.STONE) || state.is(Blocks.COBBLESTONE);
        }

        @Override
        public void restore(NeedsComponent needs) {
            needs.restoreEnergy(restoreAmount);
        }

        @Override
        public ItemStack harvest(Level level, BlockPos pos, BlockState state, ItemStack tool) {
            return breakBlock(level, pos, state, tool);
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
     * bush) and returns the item the settler now holds — or
     * {@link ItemStack#EMPTY} when vanilla's rules yield nothing (e.g. breaking
     * stone without a pickaxe). The world interaction happens regardless of
     * yield; there is no permission check here or anywhere upstream. Callers
     * should verify {@link #matches} still holds first.
     *
     * @param tool whatever the settler is holding in its main hand, used for
     *             vanilla's correct-tool-for-drops rule
     */
    public abstract ItemStack harvest(Level level, BlockPos pos, BlockState state, ItemStack tool);

    public String actionLabel() {
        return actionLabel;
    }

    /**
     * Breaks the block and returns the settler's yield by rolling the block's
     * real loot table ({@link Block#getDrops}), gated by vanilla's
     * correct-tool-for-drops rule exactly as a player's block-break is. So
     * mining stone with a pickaxe yields cobblestone (not a "stone" item),
     * silk touch/fortune on the held tool apply through the loot context, and
     * a wrong-tool attempt destroys the block and yields nothing — the same
     * natural consequences a player experiences.
     *
     * <p>Loot rolls can return zero or several stacks, but a settler only has
     * a main hand: the first non-empty stack becomes the held result and any
     * further stacks are popped into the world as item entities (like vanilla
     * block drops) rather than silently discarded. A zero-item roll returns
     * {@link ItemStack#EMPTY}, which {@code GatherResourceGoal} already treats
     * as a failed attempt (no restore, {@code *_failed} action label).</p>
     */
    static ItemStack breakBlock(Level level, BlockPos pos, BlockState state, ItemStack tool) {
        ItemStack gathered = ItemStack.EMPTY;
        boolean drops = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        if (drops && level instanceof ServerLevel serverLevel) {
            for (ItemStack drop : Block.getDrops(state, serverLevel, pos, level.getBlockEntity(pos), null, tool)) {
                if (drop.isEmpty()) {
                    continue;
                }
                if (gathered.isEmpty()) {
                    gathered = drop;
                } else {
                    Block.popResource(level, pos, drop);
                }
            }
        }
        level.destroyBlock(pos, false);
        return gathered;
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
