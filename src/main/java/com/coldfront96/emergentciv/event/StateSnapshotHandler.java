package com.coldfront96.emergentciv.event;

import com.coldfront96.emergentciv.EmergentCivMod;
import com.coldfront96.emergentciv.logging.StateSnapshotConfig;
import com.coldfront96.emergentciv.logging.StateSnapshotLogger;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Drives {@link StateSnapshotLogger} on the configured tick cadence (see
 * {@link StateSnapshotConfig#intervalTicks()}), snapshotting every active
 * settler in each server level once per interval.
 *
 * <p>Purely a scheduling seam for Phase 1 training-data capture; the logger
 * holds all the serialization logic.</p>
 */
@EventBusSubscriber(modid = EmergentCivMod.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class StateSnapshotHandler {

    private StateSnapshotHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % StateSnapshotConfig.intervalTicks() != 0L) {
            return;
        }
        StateSnapshotLogger.logActiveSettlers(serverLevel);
    }
}
