package com.coldfront96.emergentciv.logging;

import com.coldfront96.emergentciv.entity.SettlerEntity;
import com.coldfront96.emergentciv.entity.component.NeedsComponent;
import com.coldfront96.emergentciv.gate.ResourceKind;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serializes each active {@link SettlerEntity}'s state to a fixed JSON schema and
 * appends it as one object per line (JSONL) to a local log file.
 *
 * <p>This is a Phase 1 utility whose sole job is to emit clean training-data
 * traces from the <em>scripted</em> bots — no LLM/agent-mind bridge is involved.
 * Positions in the {@code perception} block are relative to the settler, not
 * absolute, so traces are translation-invariant. Cadence and file location come
 * from {@link StateSnapshotConfig}; the driving tick handler is
 * {@code StateSnapshotHandler}.</p>
 *
 * <p>Hunger/health deltas and the {@code died} flag are computed against the
 * previous snapshot of the same settler, which this class remembers per UUID.</p>
 *
 * TODO(Phase 2): These traces are the intended offline training input for the
 * external agent-mind bridge. Keep the schema stable so downstream tooling does
 * not churn.
 */
public final class StateSnapshotLogger {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    /** Half-extent (blocks) of the cube scanned for perceived resource blocks. */
    private static final int BLOCK_PERCEPTION_RADIUS = 4;
    /** Radius (blocks) of the box scanned for perceived entities. */
    private static final double ENTITY_PERCEPTION_RADIUS = 8.0D;
    private static final int MAX_NEARBY_BLOCKS = 16;
    private static final int MAX_NEARBY_ENTITIES = 16;

    /** Per-settler [hunger, health] from the previous snapshot, for delta/death computation. */
    private static final Map<UUID, float[]> PREVIOUS_STATE = new HashMap<>();

    private StateSnapshotLogger() {
    }

    /** Snapshots every settler in the given level and appends the lines to the log. */
    public static void logActiveSettlers(ServerLevel level) {
        List<SettlerEntity> settlers = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof SettlerEntity settler) {
                settlers.add(settler);
            }
        }
        if (settlers.isEmpty()) {
            return;
        }

        long tick = level.getGameTime();
        StringBuilder lines = new StringBuilder();
        for (SettlerEntity settler : settlers) {
            lines.append(GSON.toJson(snapshot(level, settler, tick))).append('\n');
        }
        append(lines.toString());
    }

    /** Builds the JSON snapshot object for a single settler. */
    static JsonObject snapshot(ServerLevel level, SettlerEntity settler, long tick) {
        JsonObject root = new JsonObject();
        root.addProperty("timestamp", tick);
        root.addProperty("settler_id", settler.getUUID().toString());
        root.add("self", buildSelf(settler));
        root.add("perception", buildPerception(level, settler));
        root.add("world", buildWorld(level));
        root.addProperty("action_taken", settler.getLastAction());
        root.add("outcome", buildOutcome(settler));
        return root;
    }

    private static JsonObject buildSelf(SettlerEntity settler) {
        NeedsComponent needs = settler.getNeeds();
        JsonObject self = new JsonObject();

        JsonArray pos = new JsonArray();
        pos.add(round2(settler.getX()));
        pos.add(round2(settler.getY()));
        pos.add(round2(settler.getZ()));
        self.add("pos", pos);

        self.addProperty("facing", settler.getDirection().getName());
        self.addProperty("hunger", round2(needs.getHunger()));
        self.addProperty("energy", round2(needs.getEnergy()));
        self.addProperty("social", round2(needs.getSocial()));
        self.addProperty("health", round2(settler.getHealth()));

        ItemStack held = settler.getMainHandItem();
        if (held.isEmpty()) {
            self.add("held_item", JsonNull.INSTANCE);
        } else {
            self.addProperty("held_item", BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
        }
        return self;
    }

    private static JsonObject buildPerception(ServerLevel level, SettlerEntity settler) {
        JsonObject perception = new JsonObject();
        perception.add("nearby_blocks", buildNearbyBlocks(level, settler));
        perception.add("nearby_entities", buildNearbyEntities(level, settler));
        return perception;
    }

    private static JsonArray buildNearbyBlocks(ServerLevel level, SettlerEntity settler) {
        BlockPos origin = settler.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        JsonArray blocks = new JsonArray();

        for (int dy = -BLOCK_PERCEPTION_RADIUS; dy <= BLOCK_PERCEPTION_RADIUS; dy++) {
            for (int dx = -BLOCK_PERCEPTION_RADIUS; dx <= BLOCK_PERCEPTION_RADIUS; dx++) {
                for (int dz = -BLOCK_PERCEPTION_RADIUS; dz <= BLOCK_PERCEPTION_RADIUS; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    // Phase 1 perception is resource-focused: only surface the
                    // wood/stone/berry blocks relevant to the survival loop.
                    if (!ResourceKind.isAnyResource(state)) {
                        continue;
                    }
                    JsonObject block = new JsonObject();
                    block.add("pos", relativePos(dx, dy, dz));
                    block.addProperty("block",
                            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    blocks.add(block);
                    if (blocks.size() >= MAX_NEARBY_BLOCKS) {
                        return blocks;
                    }
                }
            }
        }
        return blocks;
    }

    private static JsonArray buildNearbyEntities(ServerLevel level, SettlerEntity settler) {
        BlockPos origin = settler.blockPosition();
        JsonArray entities = new JsonArray();
        List<Entity> nearby = level.getEntities(settler,
                settler.getBoundingBox().inflate(ENTITY_PERCEPTION_RADIUS));

        for (Entity entity : nearby) {
            BlockPos pos = entity.blockPosition();
            JsonObject json = new JsonObject();
            json.add("pos", relativePos(
                    pos.getX() - origin.getX(),
                    pos.getY() - origin.getY(),
                    pos.getZ() - origin.getZ()));
            json.addProperty("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
            entities.add(json);
            if (entities.size() >= MAX_NEARBY_ENTITIES) {
                break;
            }
        }
        return entities;
    }

    private static JsonObject buildWorld(ServerLevel level) {
        JsonObject world = new JsonObject();
        world.addProperty("time_of_day", timeOfDay(level.getDayTime()));
        world.addProperty("weather", weather(level));
        return world;
    }

    private static JsonObject buildOutcome(SettlerEntity settler) {
        NeedsComponent needs = settler.getNeeds();
        float hunger = needs.getHunger();
        float health = settler.getHealth();

        float[] previous = PREVIOUS_STATE.get(settler.getUUID());
        float hungerDelta = previous == null ? 0.0F : hunger - previous[0];
        float healthDelta = previous == null ? 0.0F : health - previous[1];
        PREVIOUS_STATE.put(settler.getUUID(), new float[]{hunger, health});

        JsonObject outcome = new JsonObject();
        outcome.addProperty("hunger_delta", round2(hungerDelta));
        outcome.addProperty("health_delta", round2(healthDelta));
        outcome.addProperty("died", settler.isDeadOrDying());
        outcome.addProperty("new_discovery", settler.consumeDiscoveryFlag());
        return outcome;
    }

    private static JsonArray relativePos(int dx, int dy, int dz) {
        JsonArray array = new JsonArray();
        array.add(dx);
        array.add(dy);
        array.add(dz);
        return array;
    }

    /** Maps the level's day time to a coarse time-of-day enum name. */
    private static String timeOfDay(long dayTime) {
        long time = Math.floorMod(dayTime, 24000L);
        if (time < 3000L || time >= 23000L) {
            return "DAWN";
        }
        if (time < 9000L) {
            return "DAY";
        }
        if (time < 14000L) {
            return "DUSK";
        }
        return "NIGHT";
    }

    private static String weather(ServerLevel level) {
        if (level.isThundering()) {
            return "THUNDER";
        }
        if (level.isRaining()) {
            return "RAIN";
        }
        return "CLEAR";
    }

    private static synchronized void append(String content) {
        Path path = StateSnapshotConfig.logPath();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOGGER.error("Failed to append settler snapshot to {}", path, e);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
