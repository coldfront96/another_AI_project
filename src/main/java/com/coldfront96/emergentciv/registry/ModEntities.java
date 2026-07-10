package com.coldfront96.emergentciv.registry;

import com.coldfront96.emergentciv.EmergentCivMod;
import com.coldfront96.emergentciv.entity.SettlerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Central registry for Phase 1 entities and their vanilla attribute maps.
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, EmergentCivMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SettlerEntity>> SETTLER =
            ENTITY_TYPES.register("settler", () -> EntityType.Builder.of(SettlerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("settler"));

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SETTLER.get(), SettlerEntity.createAttributes().build());
    }
}
