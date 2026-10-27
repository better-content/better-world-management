package com.bettercontent.betterworldmanagement.worldshaping.exploration;

import com.bettercontent.betterdimensionfonts.trade.DimensionalFontMapTrades;
import com.bettercontent.betteroregeology.registry.ModBlocks;
import com.bettercontent.celestial.calendar.CalendarSavedData;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;

/** Typed calls to the pinned owner APIs; this helper is linked only when an optional perk is used. */
final class ExplorationNative {
    private ExplorationNative() {}
    static Set<Block> surfaceSamples() {
        Set<Block> result = new HashSet<>();
        for (var entry : ModBlocks.surfaceSampleEntries()) result.add(entry.getValue().get());
        return Set.copyOf(result);
    }
    static MerchantOffer fontOffer(ServerLevel level, BlockPos origin, Item currency, Set<String> allowed) {
        return DimensionalFontMapTrades.authoredSellerOffer(level, origin, 0, currency, Set.of(), allowed);
    }
    static String forecast(MinecraftServer server) {
        var calendar = CalendarSavedData.get(server).calendar();
        long today = Math.floorDiv(server.overworld().getDayTime(), 24000L);
        return calendar.nextEvent() + " — night " + calendar.nextDay() + " ("
                + Math.max(0L, calendar.nextDay() - today) + " days)";
    }
}
