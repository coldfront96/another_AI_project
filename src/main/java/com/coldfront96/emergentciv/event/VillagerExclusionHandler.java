package com.coldfront96.emergentciv.event;

import com.coldfront96.emergentciv.EmergentCivMod;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * Keeps vanilla {@link Villager} entities out of the world entirely: this
 * world's only inhabitants are project-controlled Settlers.
 *
 * <p>Cancelling {@link EntityJoinLevelEvent} is the catch-all seam — it fires
 * for every path by which a villager could come to exist (village structure
 * generation, breeding, zombie-villager curing, spawn eggs, {@code /summon},
 * chunk load of previously saved villagers) — and is deliberately event-based
 * rather than registry removal, so everything else that references the
 * Villager entity type stays stable.</p>
 *
 * <p>Only the entity is suppressed. Generated village structures, job-site
 * blocks (furnace, composter, fletching table, ...), beds, and loot are left
 * completely untouched and remain fully usable under normal vanilla mechanics
 * — deliberately ungated, per {@code docs/DESIGN.md} ("No artificial safety
 * walls").</p>
 *
 * <p>Villager-driven world mechanics fall away as a natural consequence, with
 * no separate handling needed: iron golems are only summoned naturally by
 * villagers themselves (gossip/panic), and zombie sieges (plus village cat
 * spawns) require {@code ServerLevel#isVillage}, which is true only near a
 * villager-<em>occupied</em> point of interest ({@code PoiManager} filters on
 * {@code Occupancy.IS_OCCUPIED}) — unclaimed beds/job sites in generated
 * structures never form a mechanical "village" without a villager to claim
 * them. Player-built iron golems (pumpkin stack) remain possible, as in
 * vanilla.</p>
 */
@EventBusSubscriber(modid = EmergentCivMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class VillagerExclusionHandler {

    private VillagerExclusionHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Villager) {
            event.setCanceled(true);
        }
    }
}
