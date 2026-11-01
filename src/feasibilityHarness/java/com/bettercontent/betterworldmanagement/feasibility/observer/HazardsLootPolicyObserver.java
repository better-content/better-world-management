package com.bettercontent.betterworldmanagement.feasibility.observer;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Nonshipping observation memory only. No inventory, chunk, simulation or policy calls. */
public final class HazardsLootPolicyObserver {
    private static final String NUCLEAR = "com.bettercontent.betterchemlibhazards.sim.NuclearSurfaceScanner";
    private static final String GAS = "com.bettercontent.betterchemlibhazards.sim.GasEscapeHandler";
    private static final long LIMIT = 1_000_000;
    private static ServerLevel watchedLevel;
    private static BlockPos[] positions = new BlockPos[0];
    private static long[][] counts = new long[0][0];
    private static long[][] firstTicks = new long[0][0];
    private static long[][] lastTicks = new long[0][0];
    private static boolean incomplete;

    private HazardsLootPolicyObserver() {}

    public static synchronized void begin(ServerLevel level, List<BlockPos> watched) {
        if (watchedLevel != null || !level.getServer().isSameThread() || watched.isEmpty()
                || watched.size() > 8 || watched.stream().distinct().count() != watched.size()) {
            throw new IllegalStateException("Exclusive main-thread <=8-position observation required");
        }
        positions = watched.stream().map(BlockPos::immutable).toArray(BlockPos[]::new);
        counts = new long[positions.length][6];
        firstTicks = new long[positions.length][6];
        lastTicks = new long[positions.length][6];
        incomplete = false;
        watchedLevel = level;
    }

    /** Called only by the actual policy RETURN injector. Bounded stack inspection follows exact position match. */
    public static synchronized void observe(Object holder, boolean pending) {
        if (watchedLevel == null || !(holder instanceof BlockEntity blockEntity)
                || blockEntity.getLevel() != watchedLevel) return;
        int position = index(blockEntity.getBlockPos());
        if (position < 0) return;
        if (!watchedLevel.getServer().isSameThread()) { incomplete = true; return; }
        String caller;
        try {
            caller = StackWalker.getInstance().walk(frames -> frames.limit(24)
                    .map(StackWalker.StackFrame::getClassName)
                    .filter(name -> name.equals(NUCLEAR) || name.equals(GAS)).findFirst().orElse("unknown"));
        } catch (RuntimeException | LinkageError error) {
            incomplete = true;
            return; // Observation failure never changes the native policy return or invokes production work.
        }
        int lane = (caller.equals(NUCLEAR) ? 0 : caller.equals(GAS) ? 2 : 4) + (pending ? 0 : 1);
        if (counts[position][lane] >= LIMIT) { incomplete = true; return; }
        long tick = watchedLevel.getGameTime();
        if (counts[position][lane] == 0) firstTicks[position][lane] = tick;
        counts[position][lane]++;
        lastTicks[position][lane] = tick;
    }

    public static synchronized long count(BlockPos pos, boolean nuclear, boolean pending) {
        int index = index(pos);
        return index < 0 ? 0 : counts[index][(nuclear ? 0 : 2) + (pending ? 0 : 1)];
    }

    public static synchronized boolean complete() { return watchedLevel != null && !incomplete; }

    public static synchronized String summary() {
        StringBuilder text = new StringBuilder("scope=first_24_public_stack_frames_exact_scanner_class incomplete=")
                .append(incomplete);
        for (int p = 0; p < positions.length; p++) {
            text.append(" pos=").append(positions[p]);
            for (int lane = 0; lane < 6; lane++) text.append(" lane").append(lane).append('=')
                    .append(counts[p][lane]).append('@').append(firstTicks[p][lane]).append("..").append(lastTicks[p][lane]);
        }
        return text.toString();
    }

    public static synchronized void end(ServerLevel owner) {
        if (watchedLevel != owner) return;
        watchedLevel = null;
        positions = new BlockPos[0];
        counts = firstTicks = lastTicks = new long[0][0];
    }

    private static int index(BlockPos pos) {
        for (int i = 0; i < positions.length; i++) if (positions[i].equals(pos)) return i;
        return -1;
    }
}
