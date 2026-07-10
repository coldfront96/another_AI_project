package com.coldfront96.emergentciv.gate;

import com.coldfront96.emergentciv.EmergentCivMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Stone-age resource/crafting gate.
 *
 * Phase 1 settlers are restricted to a single "stone age" tech tier: wood,
 * stone, and berries. Anything outside those tags is considered off-limits
 * for Settler interaction, gathering, and crafting checks.
 *
 * TODO(Phase 2): The agent-mind bridge may eventually want to query or extend
 * this gate (e.g. to reason about "what can I use") rather than only having
 * scripted goals consult it as in Phase 1.
 */
public final class StoneAgeResourceGate {

    public static final TagKey<Item> STONE_AGE_WOOD =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(EmergentCivMod.MOD_ID, "stone_age_wood"));

    public static final TagKey<Item> STONE_AGE_STONE =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(EmergentCivMod.MOD_ID, "stone_age_stone"));

    public static final TagKey<Item> STONE_AGE_BERRIES =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(EmergentCivMod.MOD_ID, "stone_age_berries"));

    private StoneAgeResourceGate() {
    }

    /** Whether a settler is permitted to interact with/gather/craft the given item at this tech tier. */
    public static boolean isAllowed(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(STONE_AGE_WOOD) || stack.is(STONE_AGE_STONE) || stack.is(STONE_AGE_BERRIES);
    }
}
