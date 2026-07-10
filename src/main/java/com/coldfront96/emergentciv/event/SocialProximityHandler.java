package com.coldfront96.emergentciv.event;

import com.coldfront96.emergentciv.EmergentCivMod;
import com.coldfront96.emergentciv.entity.SettlerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Restores a small amount of the social need when two settlers are near each
 * other. This is the deliberately dead-simple seed of the future
 * social/observation system: a periodic proximity tick, no messaging or
 * relationships yet.
 *
 * <p>On each check any pair of settlers within {@link #SOCIAL_RADIUS} blocks has
 * a little social restored for both, and their {@code action_taken} is marked
 * as socializing so the snapshot traces capture the interaction.</p>
 *
 * TODO(Phase 2): The agent-mind bridge will replace this with real observation
 * and messaging between settlers. Keep the proximity check as the seam it
 * plugs into.
 */
@EventBusSubscriber(modid = EmergentCivMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class SocialProximityHandler {

    /** How often (in ticks) to run the proximity sweep. */
    private static final long CHECK_INTERVAL_TICKS = 40L;

    /** Two settlers closer than this (blocks) are considered "together". */
    private static final double SOCIAL_RADIUS = 5.0D;
    private static final double SOCIAL_RADIUS_SQR = SOCIAL_RADIUS * SOCIAL_RADIUS;

    /** Social restored to each settler in a nearby pair, per check. */
    private static final float SOCIAL_RESTORE_PER_CHECK = 1.0F;

    private static final String SOCIALIZE_ACTION = "socialize";

    private SocialProximityHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % CHECK_INTERVAL_TICKS != 0L) {
            return;
        }

        List<SettlerEntity> settlers = new ArrayList<>();
        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof SettlerEntity settler && settler.isAlive()) {
                settlers.add(settler);
            }
        }
        if (settlers.size() < 2) {
            return;
        }

        // O(n^2) is fine for the small settler counts of Phase 1. Each unordered
        // pair within range restores social to both exactly once.
        for (int i = 0; i < settlers.size(); i++) {
            SettlerEntity a = settlers.get(i);
            for (int j = i + 1; j < settlers.size(); j++) {
                SettlerEntity b = settlers.get(j);
                if (a.distanceToSqr(b) <= SOCIAL_RADIUS_SQR) {
                    restoreSocial(a);
                    restoreSocial(b);
                }
            }
        }
    }

    private static void restoreSocial(SettlerEntity settler) {
        settler.getNeeds().restoreSocial(SOCIAL_RESTORE_PER_CHECK);
        settler.setLastAction(SOCIALIZE_ACTION);
    }
}
