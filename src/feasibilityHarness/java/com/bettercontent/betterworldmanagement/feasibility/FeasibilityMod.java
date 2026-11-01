package com.bettercontent.betterworldmanagement.feasibility;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.common.MinecraftForge;
import java.util.List;
import com.bettercontent.betterworldmanagement.worldshaping.AxiomDisabledNativeTests;
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
        if ("recipes".equals(System.getProperty("world_axioms.campaign"))
                && "queuedwheelpositiveandnegativeafternaturalticks".equals(
                        System.getProperty("world_axioms.test", "").toLowerCase(java.util.Locale.ROOT))) {
            // A server-stop/cancel can prevent delayed GameTest callbacks. Keep
            // a separate test-only owner teardown hook; it never grants PASS.
            MinecraftForge.EVENT_BUS.addListener(RecipeAxiomGameTests::stopOwnedQueuedWheel);
        }
        NativeFixtureRunner.install(FeasibilityMod::classes);
    }

    private void registerTests(RegisterGameTestsEvent event) {
        for (Class<?> fixture : classes()) event.register(fixture);
    }

    /** Separate processes prevent shared-world fixtures from contaminating one another. */
    public static List<Class<?>> classes() {
        return switch (System.getProperty("world_axioms.campaign", "capacity")) {
            case "capacity" -> List.of(HexereiCapacityGate.class);
            case "hazards-support" -> {
                if (!Boolean.getBoolean("world_axioms.nativeFixtures")
                        || !"nativependinglootandrealmaterialization".equals(
                                System.getProperty("world_axioms.test", "").toLowerCase(java.util.Locale.ROOT)))
                    throw new IllegalArgumentException("Hazards support fixtures require the native runner and one exact approved bare test name");
                yield List.of(HazardsLootGuardNativeTests.class);
            }
            case "recipes" -> {
                if (!Boolean.getBoolean("world_axioms.nativeFixtures") || System.getProperty("world_axioms.test", "").isBlank())
                    throw new IllegalArgumentException("Recipe fixtures require the native runner and one exact test name; mixed-batch arena overlap is not qualified");
                yield List.of(RecipeAxiomGameTests.class);
            }
            case "inheritance" -> List.of(InheritanceNativeGameTests.class);
            case "disabled-services" -> {
                if (!Boolean.getBoolean("world_axioms.nativeFixtures") || System.getProperty("world_axioms.test", "").isBlank())
                    throw new IllegalArgumentException("Disabled service controls require the native runner and one exact test name");
                yield List.of(AxiomDisabledNativeTests.class);
            }
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
