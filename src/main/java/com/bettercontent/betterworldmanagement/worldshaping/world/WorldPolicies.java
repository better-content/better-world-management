package com.bettercontent.betterworldmanagement.worldshaping.world;

import com.bettercontent.betterworldmanagement.worldshaping.WorldAxiomService;
import com.bettercontent.celestial.calendar.CalendarSavedData;
import com.bettercontent.spiritcommerce.trader.WanderingTraderScheduleData;
import sereneseasons.season.SeasonHandler;
import sereneseasons.season.SeasonSavedData;
import sereneseasons.season.SeasonTime;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;

/** Successor-only server adapters. Call before health/receipt commit, then durably save the world. */
public final class WorldPolicies {
    private WorldPolicies() {}

    public static BlockPos adjustLanding(MinecraftServer server, ServerLevel level, BlockPos proposed, String biome) {
        if (!WorldAxiomService.has(server, "settled_arrival")) return proposed;
        if (!eligibleLanding(level, proposed, biome))
            throw new IllegalStateException("Settled Arrival candidate lacks a loaded safe native village piece in selected biome " + biome);
        return proposed;
    }

    /** Candidate filter for the existing asynchronous bounded landing search. Never locates or activates chunks. */
    public static boolean eligibleLanding(ServerLevel level, BlockPos feet, String biome) {
        if (!WorldAxiomService.has(level.getServer(), "settled_arrival")) return true;
        return eligibleVillagePiece(level, feet, biome);
    }

    static boolean eligibleVillagePiece(ServerLevel level, BlockPos feet, String biome) {
        if (!level.dimension().equals(Level.OVERWORLD) || feet.getY() <= level.getMinBuildHeight()
                || feet.getY() + 1 >= level.getMaxBuildHeight() || !level.getWorldBorder().isWithinBounds(feet)) return false;
        // BiomeManager's fuzzy quart sampling and collision support can inspect adjacent blocks.
        // Require their small existing chunk neighbourhood too, rather than letting getters load it.
        for (int x : new int[]{feet.getX() - 4, feet.getX() + 4})
            for (int z : new int[]{feet.getZ() - 4, feet.getZ() + 4})
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return false;
        var chunk = level.getChunkSource().getChunkNow(feet.getX() >> 4, feet.getZ() >> 4);
        if (chunk == null) return false;
        BlockPos below = feet.below();
        BlockState support = chunk.getBlockState(below);
        if (!chunk.getBlockState(feet).isAir() || !chunk.getBlockState(feet.above()).isAir()
                || !chunk.getFluidState(feet).isEmpty() || !chunk.getFluidState(feet.above()).isEmpty()
                || !chunk.getFluidState(below).isEmpty() || hazardous(support)
                || !support.isFaceSturdy(level, below, Direction.UP)) return false;
        if (!level.getBiome(feet).unwrapKey().map(k -> k.location().toString().equals(biome)).orElse(false)) return false;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        // Native StructureManager.getStructureWithPieceAt may synchronously fetch referenced start chunks.
        // Inspect the SAME native references/starts/containing-piece predicate, but only via getChunkNow.
        int inspected = 0;
        for (var entry : chunk.getAllReferences().entrySet()) {
            var key = registry.getResourceKey(entry.getKey());
            if (key.isEmpty() || !registry.getHolderOrThrow(key.get()).is(StructureTags.VILLAGE)) continue;
            for (long reference : entry.getValue()) {
                if (++inspected > 64) return false;
                ChunkPos startPos = new ChunkPos(reference);
                var startChunk = level.getChunkSource().getChunkNow(startPos.x, startPos.z);
                if (startChunk == null) continue;
                var start = startChunk.getStartForStructure(entry.getKey());
                if (start != null && start.isValid() && (level.structureManager().structureHasPieceAt(feet, start)
                        || level.structureManager().structureHasPieceAt(below, start))) return true;
            }
        }
        return false;
    }

    private static boolean hazardous(BlockState state) {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW);
    }

    public static void apply(ServerLevel level, BlockPos anchor, String transaction) {
        MinecraftServer server = level.getServer();
        if (!level.dimension().equals(Level.OVERWORLD)) throw new IllegalArgumentException("World policies require overworld");
        List<String> effects = List.of("turning_year", "founders_exchange", "chosen_omen", "communal_archive");
        if (effects.stream().noneMatch(id -> WorldAxiomService.has(server, id))) return;
        WorldPolicyData data = WorldPolicyData.get(level);
        CompoundTag journal = data.entry(transaction);
        for (String id : effects) {
            if (!WorldAxiomService.has(server, id)) continue;
            // Native owner receipts live WITH their state. Revisit them and the pinned archive plan
            // even if WM's receipt reached disk first in an interrupted multi-file world save.
            if (id.equals("turning_year") && journal.getBoolean(id)) continue;
            CompoundTag config = WorldAxiomService.configuration(server, id);
            switch (id) {
                case "turning_year" -> season(level, config.getString("season"), journal, data);
                case "founders_exchange" -> {
                    WanderingTraderScheduleData.get(level).chooseFirst(transaction, config.getString("currency"));
                }
                case "chosen_omen" -> {
                    CalendarSavedData.get(server).chooseFirst(transaction, config.getString("event"));
                }
                case "communal_archive" -> CommunalArchive.apply(level, anchor, config, journal, data);
                default -> throw new IllegalStateException("Unknown world policy");
            }
            journal.putBoolean(id, true);
            data.setDirty();
        }
    }

    private static void season(ServerLevel level, String season, CompoundTag journal, WorldPolicyData data) {
        int ordinal = switch (season) {
            case "early_spring" -> 0;
            case "early_summer" -> 3;
            case "early_autumn" -> 6;
            case "early_winter" -> 9;
            default -> throw new IllegalArgumentException("Unknown starting season " + season);
        };
        SeasonSavedData nativeData = SeasonHandler.getSeasonSavedData(level);
        int duration = SeasonTime.ZERO.getSubSeasonDuration();
        int target = Math.multiplyExact(ordinal, duration);
        int current = nativeData.seasonCycleTicks;
        if (!journal.getBoolean("seasonPrepared")) {
            journal.putInt("seasonOriginal", current);
            journal.putInt("seasonTarget", target);
            journal.putBoolean("seasonPrepared", true);
            data.setDirty();
            // Stable pre-applied snapshot must reach disk BEFORE touching third-party SavedData.
            level.getDataStorage().save();
        }
        if (journal.getInt("seasonTarget") != target) throw new IllegalStateException("Season plan changed during replay");
        int original = journal.getInt("seasonOriginal");
        if (current == original) {
            nativeData.seasonCycleTicks = target;
            nativeData.setDirty();
            level.getDataStorage().save();
        } else if (current < target || current >= target + duration) {
            throw new IllegalStateException("Ambiguous starting-season recovery; refusing to reset progressed native calendar");
        }
        // An already changed native clock in the intended subseason is left running, never reset.
        SeasonHandler.sendSeasonUpdate(level);
    }

    /** Read-only almanac: never tick, force, reroll or consume the calendar here. */
    public static String forecast(MinecraftServer server) {
        if (!WorldAxiomService.has(server, "celestial_almanac")) return "";
        var calendar = CalendarSavedData.get(server).calendar();
        return calendar.nextEvent().id() + " — day " + calendar.nextDay();
    }
}
