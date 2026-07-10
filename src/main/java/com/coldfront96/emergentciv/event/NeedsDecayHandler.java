package com.coldfront96.emergentciv.event;

import com.coldfront96.emergentciv.EmergentCivMod;
import com.coldfront96.emergentciv.entity.SettlerEntity;
import com.coldfront96.emergentciv.entity.component.NeedsComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Drives {@link NeedsComponent} decay based on the day/night cycle rather
 * than raw tick count, so decay rate can later be tied to time-of-day
 * (e.g. faster energy decay at night). Phase 1 keeps the rate flat per
 * in-game day; only the trigger cadence is day/night-driven.
 *
 * TODO(Phase 2): The agent-mind bridge may want to observe or influence decay
 * rates (e.g. an agent choosing to rest to slow energy decay). Keep this
 * handler's per-day decay amounts easy to override/replace.
 */
@EventBusSubscriber(modid = EmergentCivMod.MOD_ID)
public final class NeedsDecayHandler {

    /** One Minecraft day is 24000 ticks; we apply decay once per in-game day. */
    private static final long TICKS_PER_DAY = 24000L;

    private NeedsDecayHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        long dayTime = serverLevel.getDayTime();
        if (dayTime % TICKS_PER_DAY != 0L) {
            return;
        }

        for (Entity entity : serverLevel.getAllEntities()) {
            if (entity instanceof SettlerEntity settler) {
                applyDailyDecay(settler);
            }
        }
    }

    private static void applyDailyDecay(SettlerEntity settler) {
        NeedsComponent needs = settler.getNeeds();
        needs.decayHunger(NeedsComponent.DEFAULT_HUNGER_DECAY_PER_DAY);
        needs.decayEnergy(NeedsComponent.DEFAULT_ENERGY_DECAY_PER_DAY);
        needs.decaySocial(NeedsComponent.DEFAULT_SOCIAL_DECAY_PER_DAY);
    }
}
