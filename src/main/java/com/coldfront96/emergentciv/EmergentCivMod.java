package com.coldfront96.emergentciv;

import com.coldfront96.emergentciv.registry.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * EmergentCiv - Phase 1: world/gameplay systems only.
 *
 * This mod is the foundation of a multi-phase emergent civilization simulation.
 * Phase 1 scope: Settler entity, decaying needs, stone-age resource gate, and a
 * scripted placeholder AI goal. NO external AI/LLM integration happens here.
 *
 * TODO(Phase 2): Introduce an "agent-mind bridge" module that lets an external
 * AI process observe {@link com.coldfront96.emergentciv.entity.component.NeedsComponent}
 * state and drive Settler decisions/actions in place of (or alongside) the
 * scripted goals registered in Phase 1. Do not wire that bridge here yet.
 */
@Mod(EmergentCivMod.MOD_ID)
public class EmergentCivMod {

    public static final String MOD_ID = "emergentciv";

    public EmergentCivMod(IEventBus modEventBus) {
        ModEntities.ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);

        NeoForge.EVENT_BUS.register(this);
    }
}
