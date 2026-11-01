package com.bettercontent.betterworldmanagement.feasibility;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.joefoxe.hexerei.data.recipes.MixingCauldronRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Exercise the real pinned Hexerei JSON serializer in a real Forge server.
 * Vanilla item surrogates deliberately isolate ingredient cardinality from
 * Aether/pack recipe policy. This does not prove any moon-dust craft works.
 * The obsolete ten-position design is an explicit native rejection control.
 * Its historical failure evidence is retained; passing this control does NOT
 * accept Patient Alchemist. The corrected five-input physical craft is tested
 * separately by RecipeAxiomGameTests.
 */
@GameTestHolder(FeasibilityMod.ID)
@PrefixGameTestTemplate(false)
public final class HexereiCapacityGate {
    private HexereiCapacityGate() {}

    private static JsonObject recipe(int count) {
        JsonObject json = new JsonObject();
        JsonObject output = new JsonObject();
        output.addProperty("item", "minecraft:clay_ball");
        output.addProperty("count", 4);
        json.add("output", output);
        JsonObject liquid = new JsonObject();
        liquid.addProperty("fluid", "minecraft:water");
        liquid.addProperty("amount", 1000);
        json.add("liquid", liquid);
        json.add("liquidOutput", liquid.deepCopy());
        json.addProperty("fluidLevelsConsumed", 250);
        JsonArray inputs = new JsonArray();
        for (int i = 0; i < count; i++) {
            JsonObject input = new JsonObject();
            input.addProperty("item", i < 8 ? "minecraft:redstone" : "minecraft:amethyst_shard");
            inputs.add(input);
        }
        json.add("ingredients", inputs);
        return json;
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void eightIngredientNativeControl(GameTestHelper helper) {
        var parsed = MixingCauldronRecipe.Serializer.INSTANCE.fromJson(
                new ResourceLocation(FeasibilityMod.ID, "eight_control"), recipe(8));
        helper.assertTrue(parsed.getIngredients().size() == 8,
                "Native eight-input control must preserve every input");
        helper.assertTrue(parsed.getIngredients().stream().noneMatch(ingredient -> ingredient.isEmpty()),
                "Native control contains an empty ingredient");
        System.out.println("WLM_FEASIBILITY hexerei_eight_input_control=PASS");
        helper.succeed();
    }

    @GameTest(templateNamespace = "better_world_management", template = "empty", timeoutTicks = 40)
    public static void obsoleteTenIngredientNativeRejection(GameTestHelper helper) {
        try {
            MixingCauldronRecipe.Serializer.INSTANCE.fromJson(
                    new ResourceLocation(FeasibilityMod.ID, "obsolete_ten"), recipe(10));
        } catch (IndexOutOfBoundsException expected) {
            expected.printStackTrace();
            System.out.println("WLM_NATIVE_COMPAT hexerei_obsolete_ten=EXPECTED_REJECTION native_exception="
                    + expected.getClass().getName() + " message=" + expected.getMessage()
                    + " product_acceptance=false corrected_five_input_craft_required=true");
            helper.succeed();
            return;
        }
        helper.fail("Pinned eight-position serializer unexpectedly accepted ten inputs; investigate artifact identity, do not count product acceptance");
    }
}
