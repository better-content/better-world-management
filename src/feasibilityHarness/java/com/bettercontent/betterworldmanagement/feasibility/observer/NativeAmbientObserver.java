package com.bettercontent.betterworldmanagement.feasibility.observer;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** Optional NONSHIPPING observations only; no native residency/activation or gameplay acceptance proof. */
public final class NativeAmbientObserver {
    private static final boolean ENABLED = Boolean.getBoolean("world_axioms.ambientObserve");
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ArrayDeque<String> RECENT = new ArrayDeque<>(64);
    private static boolean sealed;
    private static final ThreadLocal<long[]> DEPTH = ThreadLocal.withInitial(() -> new long[2]);
    // Retained context<=8 per thread, no target fields/accessors. Native exceptional unwinds
    // have no RETURN injection: every correlation is explicitly unverified HEAD context.
    private static final ThreadLocal<Map<String, Object[]>> FRAMES = ThreadLocal.withInitial(LinkedHashMap::new);
    static {
        if (ENABLED) {
            try { Runtime.getRuntime().addShutdownHook(new Thread(NativeAmbientObserver::dump, "axioms-ambient-observer-dump")); }
            catch (Throwable error) { rethrowFatal(error); }
        }
    }
    private NativeAmbientObserver() {}

    public static void heatHead(Level level, BlockPos holder) { head("heat", level, holder, false); }
    public static void heatReturn(Object value) { returned("heat", value); }
    public static void windHead(BlockPos chunkCenter) { head("wind", null, chunkCenter, true); }
    public static void windReturn(Object value) { returned("wind", value); }

    private static void head(String stage, Level level, BlockPos pos, boolean chunkCoordinates) {
        if (!ENABLED) return;
        try {
            int index = stage.equals("heat") ? 0 : 1;
            long depth = ++DEPTH.get()[index];
            long id = SEQUENCE.incrementAndGet();
            long heatDepth = DEPTH.get()[0] - (stage.equals("heat") ? 1 : 0);
            Object[] heat = FRAMES.get().get("heat:" + heatDepth);
            Level observed = level != null ? level : heat == null ? null : (Level) heat[1];
            BlockPos center = pos == null ? null : pos.immutable();
            boolean contextRetained = FRAMES.get().size() < 8;
            if (contextRetained) FRAMES.get().put(stage + ":" + depth,
                    new Object[]{id, observed, center, System.nanoTime()});
            JsonObject row = base(stage, "HEAD", id, depth);
            row.addProperty("context_retained", contextRetained);
            row.addProperty("context_proof", "head_context_only_exception_unwind_unverified");
            if (heat != null) row.addProperty("heat_context_call", (Long) heat[0]);
            row.addProperty("coord_space", chunkCoordinates ? "chunk" : "block");
            if (center != null) {
                row.addProperty("arg_x", center.getX()); row.addProperty("arg_y", center.getY()); row.addProperty("arg_z", center.getZ());
                int cx = chunkCoordinates ? center.getX() : center.getX() >> 4;
                int cz = chunkCoordinates ? center.getZ() : center.getZ() >> 4;
                row.addProperty("center_chunk_x", cx); row.addProperty("center_chunk_z", cz);
                if (heat != null && heat[2] != null) {
                    BlockPos holder = (BlockPos) heat[2];
                    row.addProperty("heat_holder_x", holder.getX()); row.addProperty("heat_holder_y", holder.getY()); row.addProperty("heat_holder_z", holder.getZ());
                    row.addProperty("delta_from_heat_chunk_x", (long) cx - (holder.getX() >> 4));
                    row.addProperty("delta_from_heat_chunk_z", (long) cz - (holder.getZ() >> 4));
                }
                if (observed instanceof ServerLevel serverLevel) {
                    row.addProperty("dimension", serverLevel.dimension().location().toString());
                    boolean main = serverLevel.getServer().isSameThread();
                    row.addProperty("server_thread", main);
                    if (main) {
                        row.addProperty("game_time_observed", serverLevel.getGameTime());
                        row.add("checks", observe(serverLevel, cx, cz));
                    } else row.addProperty("checks_skipped", "not_server_thread_no_chunk_apis_called");
                } else row.addProperty("checks_skipped", observed == null ? "missing_heat_level_context" : "not_server_level");
            } else row.addProperty("checks_skipped", "null_argument");
            retain(row); // HEAD retained BEFORE returning to the actual possibly-blocking target.
        } catch (Throwable error) { observationError(stage, error); }
    }

    private static void returned(String stage, Object value) {
        if (!ENABLED) return;
        try {
            int index = stage.equals("heat") ? 0 : 1;
            long depth = DEPTH.get()[index];
            Object[] frame = FRAMES.get().remove(stage + ":" + depth);
            DEPTH.get()[index] = Math.max(0, depth - 1);
            if (frame == null) return; // Never invent a pair after context cap/exceptional unwind.
            JsonObject row = base(stage, "RETURN", (Long) frame[0], depth);
            row.addProperty("elapsed_ns", System.nanoTime() - (Long) frame[3]);
            row.addProperty("native_result_text", String.valueOf(value));
            retain(row); // No repeated chunk reads at RETURN, total<=49 per invocation.
        } catch (Throwable error) { observationError(stage, error); }
    }

    /** Union of sparse Weather25 and contiguous5x5, exactly49 distinct offsets. */
    public static Set<Long> offsets() {
        Set<Long> result = new LinkedHashSet<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) result.add(pack(3 * x, 3 * z));
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) result.add(pack(x, z));
        return Collections.unmodifiableSet(result);
    }
    private static long pack(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    private static JsonArray observe(ServerLevel level, int cx, int cz) {
        JsonArray checks = new JsonArray();
        for (long offset : offsets()) {
            int dx = (int) (offset >> 32), dz = (int) offset;
            JsonObject check = new JsonObject();
            long x = (long) cx + dx, z = (long) cz + dz;
            check.addProperty("chunk_x", x); check.addProperty("chunk_z", z);
            check.addProperty("weather_sparse", dx % 3 == 0 && dz % 3 == 0);
            check.addProperty("contiguous5", Math.abs(dx) <= 2 && Math.abs(dz) <= 2);
            if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE || z < Integer.MIN_VALUE || z > Integer.MAX_VALUE) {
                check.addProperty("error", "coordinate_overflow"); checks.add(check); continue;
            }
            try {
                var chunk = level.getChunkSource().getChunkNow((int) x, (int) z);
                check.addProperty("chunk_now_null", chunk == null);
                if (chunk != null) {
                    check.addProperty("identity_token", System.identityHashCode(chunk));
                    check.addProperty("returned_chunk_x", chunk.getPos().x); check.addProperty("returned_chunk_z", chunk.getPos().z);
                    check.addProperty("status_text", String.valueOf(chunk.getStatus()));
                    check.addProperty("full_status_text", String.valueOf(chunk.getFullStatus()));
                    check.addProperty("scope", "reference_only_currentlyLoading_possible_not_activation_proof");
                }
            } catch (Throwable error) {
                rethrowFatal(error); check.addProperty("error", errorText(error));
            }
            checks.add(check);
        }
        return checks;
    }

    private static JsonObject base(String stage, String edge, long id, long depth) {
        JsonObject row = new JsonObject();
        row.addProperty("schema", "world_axioms_ambient_observer_v1"); row.addProperty("diagnostic_only", true);
        row.addProperty("stage", stage); row.addProperty("edge", edge); row.addProperty("call", id);
        row.addProperty("depth_observed", depth); row.addProperty("thread_id", Thread.currentThread().getId());
        row.addProperty("thread", Thread.currentThread().getName()); row.addProperty("monotonic_ns", System.nanoTime());
        return row;
    }
    private static void observationError(String stage, Throwable error) {
        rethrowFatal(error);
        try {
            JsonObject row = base(stage, "OBSERVER_ERROR", SEQUENCE.get(), 0);
            row.addProperty("error", errorText(error)); retain(row);
        } catch (Throwable secondary) { rethrowFatal(secondary); }
    }
    private static String errorText(Throwable error) {
        String text = String.valueOf(error.getMessage());
        return text.length() > 160 ? text.substring(0, 160) : text;
    }
    private static void retain(JsonObject row) {
        String text = row.toString();
        synchronized (RECENT) {
            if (sealed) return;
            if (RECENT.size() == 64) RECENT.removeFirst();
            RECENT.addLast(text);
        }
    }
    private static void dump() {
        // At JVM shutdown: only already-serialized observer memory. No world/FS/tick work.
        String[] rows;
        synchronized (RECENT) { sealed = true; rows = RECENT.toArray(String[]::new); }
        for (String row : rows) {
            try { System.out.println("WLM_AMBIENT_OBSERVER " + row); }
            catch (Throwable error) { rethrowFatal(error); }
        }
    }
    private static void rethrowFatal(Throwable error) {
        if (error instanceof VirtualMachineError fatal) throw fatal;
        if (error instanceof ThreadDeath fatal) throw fatal;
    }
}
