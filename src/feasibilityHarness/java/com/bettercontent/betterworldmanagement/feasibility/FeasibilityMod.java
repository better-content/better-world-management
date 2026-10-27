package com.bettercontent.betterworldmanagement.feasibility;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegisterGameTestsEvent;
import java.util.List;
import com.bettercontent.betterworldmanagement.worldshaping.AxiomLifecycleNativeTests;
import com.bettercontent.betterworldmanagement.worldshaping.AxiomSuccessionNativeTests;
import com.bettercontent.betterworldmanagement.worldshaping.exploration.ExplorationEntryGameTests;
import com.bettercontent.betterworldmanagement.worldshaping.exploration.ExplorationNativeGameTests;
import com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceNativeGameTests;
import com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceReproductionNativeGameTests;
import com.bettercontent.betterworldmanagement.worldshaping.world.WorldPolicyNativeTests;

/** Native gates only, not an alternative shipping implementation. */
@Mod(FeasibilityMod.ID)
public final class FeasibilityMod {
    public static final String ID = "better_world_management_feasibility";

    public FeasibilityMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::registerTests);
        NativeFixtureRunner.install(FeasibilityMod::classes);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        for (Class<?> fixture : classes()) event.register(fixture);
    }

    /** Separate processes prevent shared-world fixtures from contaminating one another. */
    public static List<Class<?>> classes() {
        return switch (System.getProperty("world_axioms.campaign", "capacity")) {
            case "capacity" -> List.of(HexereiCapacityGate.class);
            case "recipes" -> List.of(RecipeAxiomGameTests.class);
            case "inheritance" -> List.of(InheritanceNativeGameTests.class);
            case "reproduction" -> {
                if (!Boolean.getBoolean("world_axioms.nativeFixtures") || System.getProperty("world_axioms.test", "").isBlank())
                    throw new IllegalArgumentException("Reproduction requires the dedicated native runner and one exact world_axioms.test name");
                yield List.of(InheritanceReproductionNativeGameTests.class);
            }
            case "exploration" -> List.of(ExplorationNativeGameTests.class, ExplorationEntryGameTests.class);
            case "world" -> List.of(WorldPolicyNativeTests.class);
            case "lifecycle" -> List.of(AxiomLifecycleNativeTests.class);
            case "succession" -> {
                if (!Boolean.getBoolean("world_axioms.nativeFixtures") || System.getProperty("world_axioms.test", "").isBlank())
                    throw new IllegalArgumentException("Succession requires the dedicated native runner and one exact world_axioms.test name");
                yield List.of(AxiomSuccessionNativeTests.class);
            }
            default -> throw new IllegalArgumentException("Unknown isolated native campaign");
        };
    }
}
