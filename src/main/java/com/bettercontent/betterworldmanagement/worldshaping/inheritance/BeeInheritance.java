package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import com.accbdd.complicated_bees.bees.Chromosome;
import com.accbdd.complicated_bees.bees.GeneticHelper;
import com.accbdd.complicated_bees.bees.Genome;
import com.accbdd.complicated_bees.bees.Species;
import com.accbdd.complicated_bees.bees.gene.GeneSpecies;
import com.accbdd.complicated_bees.registry.SpeciesRegistration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.Set;
import static com.bettercontent.betterworldmanagement.worldshaping.inheritance.InheritanceAdapters.*;

/** Pinned Complicated Bees genome API; never preserves age, mate, analysis, display or other item tags. */
final class BeeInheritance {
    private BeeInheritance() {}

    static CompoundTag capture(ItemStack stack) {
        require(!stack.isEmpty() && stack.hasTag()
            && stack.getTag().contains(GeneticHelper.CHROMOSOME_A, Tag.TAG_COMPOUND)
            && stack.getTag().contains(GeneticHelper.CHROMOSOME_B, Tag.TAG_COMPOUND), "Bee must have a complete native genome");
        CompoundTag out = new CompoundTag();
        // Native API reconstructs the genome; only canonical native gene fields leave this adapter.
        Genome genome = GeneticHelper.getGenome(stack.copy());
        out.put("primary", genome.getPrimary().serialize());
        out.put("secondary", genome.getSecondary().serialize());
        validate(out);
        // Reject incomplete/unknown old genome fields rather than allowing native defaults to fill gaps.
        require(out.getCompound("primary").equals(stack.getTag().getCompound(GeneticHelper.CHROMOSOME_A))
            && out.getCompound("secondary").equals(stack.getTag().getCompound(GeneticHelper.CHROMOSOME_B)), "Noncanonical or incomplete bee genome");
        return out;
    }

    static void validate(CompoundTag data) {
        keys(data, Set.of("primary", "secondary"));
        typed(data, "primary", Tag.TAG_COMPOUND); typed(data, "secondary", Tag.TAG_COMPOUND);
        require(data.sizeInBytes() <= 14000, "Bee genome too large");
        for (String key : Set.of("primary", "secondary")) {
            CompoundTag raw = data.getCompound(key);
            var registered = new Chromosome().getGenes();
            require(raw.getAllKeys().equals(registered.keySet().stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.toSet())), "Bee chromosome must contain exactly registered genes");
            for (String gene : raw.getAllKeys()) {
                typed(raw, gene, Tag.TAG_COMPOUND);
                CompoundTag fields = raw.getCompound(gene);
                require(fields.getAllKeys().equals(registered.get(new ResourceLocation(gene)).serialize().getAllKeys()), "Unknown or missing native gene field");
                for (String field : fields.getAllKeys()) bounded(fields.get(field), 0);
                typed(fields, "dominant", Tag.TAG_BYTE);
                require(fields.getByte("dominant") == 0 || fields.getByte("dominant") == 1, "Invalid dominance flag");
                require(fields.contains("data"), "Missing gene data");
                bounded(fields.get("data"), 0);
            }
            CompoundTag species = raw.getCompound(GeneSpecies.ID.toString());
            typed(species, "data", Tag.TAG_STRING);
            ResourceLocation location = ResourceLocation.tryParse(species.getString("data"));
            require(location != null, "Invalid bee species ID");
            var registry = GeneticHelper.getRegistryAccess().registryOrThrow(SpeciesRegistration.SPECIES_REGISTRY_KEY);
            require(registry.containsKey(location) && registry.get(location) != Species.INVALID, "Unregistered bee species");
            Chromosome chromosome = Chromosome.deserialize(raw.copy());
            require(chromosome.serialize().equals(raw), "Bee genome rejected canonical native roundtrip");
            require(chromosome.getGene(GeneSpecies.ID).get() != null && chromosome.getGene(GeneSpecies.ID).get() != Species.INVALID, "Invalid native bee species");
        }
    }

    static ItemStack restore(String caste, CompoundTag data) {
        validate(data);
        require(caste.equals("princess") || caste.equals("drone"), "Unsupported bee caste");
        var item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("complicated_bees", caste));
        require(item != null && item != net.minecraft.world.item.Items.AIR, "Pinned bee item missing");
        ItemStack out = GeneticHelper.setGenome(new ItemStack(item), new Genome(Chromosome.deserialize(data.getCompound("primary").copy()), Chromosome.deserialize(data.getCompound("secondary").copy())));
        require(capture(out).equals(data), "Native bee reconstruction mismatch");
        return out;
    }

    private static void bounded(Tag tag, int depth) {
        require(depth <= 3, "Gene nesting exceeds limit");
        if (tag instanceof NumericTag number) require(Double.isFinite(number.getAsDouble()) && Math.abs(number.getAsDouble()) <= 100000, "Unbounded gene value");
        else if (tag instanceof StringTag string) require(string.getAsString().length() <= 256, "Gene string too long");
        else if (tag instanceof IntArrayTag array) {
            int[] values = array.getAsIntArray();
            require(values.length == 2 && java.util.Arrays.stream(values).allMatch(v -> v >= 0 && v <= 128), "Invalid native territory dimensions");
        }
        else if (tag instanceof CompoundTag compound) {
            // Some native genes serialize structured data. Canonical roundtrip below is the whitelist.
            require(compound.getAllKeys().size() <= 16, "Too many gene fields");
            for (String key : compound.getAllKeys()) bounded(compound.get(key), depth + 1);
        } else throw invalid("Unsupported gene data type");
    }
}
