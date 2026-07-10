package com.coldfront96.emergentciv.logging;

import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Lightweight configuration for {@link StateSnapshotLogger}.
 *
 * <p>Phase 1 keeps this to plain, overridable defaults rather than a full
 * NeoForge config spec: the snapshot cadence and log-file location can each be
 * overridden with a JVM system property, otherwise sensible defaults apply.</p>
 *
 * <ul>
 *   <li>{@code emergentciv.snapshot.interval} &mdash; ticks between snapshots
 *       (default {@value #DEFAULT_INTERVAL_TICKS}).</li>
 *   <li>{@code emergentciv.snapshot.path} &mdash; absolute path of the JSONL log
 *       file (default {@code <run-dir>/logs/settler_snapshots.jsonl}).</li>
 * </ul>
 *
 * TODO(Phase 2): Promote these to a real config file once the training-data
 * pipeline firms up.
 */
public final class StateSnapshotConfig {

    public static final int DEFAULT_INTERVAL_TICKS = 100;

    public static final String INTERVAL_PROPERTY = "emergentciv.snapshot.interval";
    public static final String PATH_PROPERTY = "emergentciv.snapshot.path";

    /** Default log location relative to the game/run directory. */
    public static final String DEFAULT_LOG_SUBPATH = "logs/settler_snapshots.jsonl";

    private StateSnapshotConfig() {
    }

    /** Ticks between snapshots; never returns less than 1. */
    public static int intervalTicks() {
        String override = System.getProperty(INTERVAL_PROPERTY);
        if (override != null) {
            try {
                return Math.max(1, Integer.parseInt(override.trim()));
            } catch (NumberFormatException ignored) {
                // Fall through to the default on a malformed override.
            }
        }
        return DEFAULT_INTERVAL_TICKS;
    }

    /** Resolved path of the JSONL log file. */
    public static Path logPath() {
        String override = System.getProperty(PATH_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        return FMLPaths.GAMEDIR.get().resolve(DEFAULT_LOG_SUBPATH);
    }
}
