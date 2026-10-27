package com.bettercontent.betterworldmanagement.harness;

import com.bettercontent.betterworldmanagement.worldshaping.AxiomCatalog;
import com.bettercontent.betterworldmanagement.worldshaping.AxiomView;
import com.bettercontent.betterworldmanagement.worldshaping.client.WorldAxiomScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import com.mojang.blaze3d.platform.NativeImage;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * NON-SHIPPING presentation fixture. Uses the real Screen and real framebuffer, never input injection.
 * None of these displayed projections proves a perk, payment, claim, persistence or networking effect.
 * Root registers a client packet/command and calls request(round, name) on its client main thread.
 * Launch/window/GUI-scale selection stays with the orchestrator; this class never resizes the window.
 */
public final class AxiomVisualHarness {
    private static boolean installed;
    private static int round, settleTicks, titleTicks;
    private static String pending;
    private static boolean standaloneInstalled, autoRequested;
    private static final boolean STANDALONE = Boolean.getBoolean("world_axioms.visualStandalone");
    private static WorldAxiomScreen screen;
    private AxiomVisualHarness() {}

    /** 1: twelve-point mixed draft; 2: first-generation one-point draft; 3: sealed observer all-24. */
    public static AxiomView projection(int round) {
        Set<String> selected = new LinkedHashSet<>();
        CompoundTag draft = new CompoundTag();
        CompoundTag active = new CompoundTag();
        switch (round) {
            case 1 -> {
                selected.addAll(Set.of("iron_law", "ark", "cartographers_memory", "communal_archive", "spirit_awakening", "shaped_horizon", "turning_year", "chosen_omen"));
                put(draft,"shaped_horizon","preset","large_biomes");
                put(draft,"turning_year","season","early_autumn");
                put(draft,"chosen_omen","event","wandering_lights");
                put(draft,"communal_archive","plaque","The world forgets its roads. We remember the way home.");
                return new AxiomView(11, 41, true, false, selected, draft, "",
                        "Presentation fixture only. Living-world forecast is distinct from this editable successor draft.",
                        Set.of("font_atlas", "cartographers_memory", "turning_year"), active);
            }
            case 2 -> {
                selected.add("iron_law");
                return new AxiomView(0, 1, true, false, selected, draft, "",
                        "Presentation fixture: one point allocated; any independent axiom may replace it.", Set.of(), active);
            }
            case 3 -> {
                AxiomCatalog.nodes().forEach(node -> selected.add(node.id()));
                put(active,"deep_compact","deposit","copper_bloom");
                put(active,"deep_compact","band","deep");
                return new AxiomView(23, 99, false, true, selected, draft, "",
                        "Presentation fixture: sealed observer. Inspection remains available; all mutations and claims require operator permission.",
                        Set.copyOf(selected), active);
            }
            default -> throw new IllegalArgumentException("Axiom presentation round must be 1, 2 or 3");
        }
    }
    private static void put(CompoundTag root, String id, String key, String value) {
        CompoundTag node = root.getCompound(id).copy(); node.putString(key,value); root.put(id,node);
    }
    /** Called by the typed Dist.CLIENT setup subscriber only; opt-in and inert otherwise. */
    public static void installStandalone() {
        if (!STANDALONE || standaloneInstalled) return;
        standaloneInstalled = true;
        if (!installed) { MinecraftForge.EVENT_BUS.register(AxiomVisualHarness.class); installed = true; }
        System.out.println("AXIOM_VISUAL standalone installed; waiting for native TitleScreen and resource overlay completion");
    }
    private static String label(String property, String fallback) {
        String value = System.getProperty(property, fallback);
        if (!value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}")) throw new IllegalArgumentException("Invalid presentation label: " + property);
        return value;
    }
    private static void autoRequest(Minecraft mc) {
        if (!standaloneInstalled || autoRequested) return;
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null || mc.font == null || mc.player != null || mc.level != null) {
            titleTicks = 0; return;
        }
        if (++titleTicks < 10) return;
        String nonce = label("world_axioms.visualNonce", ""); // Required: never silently overwrite/reuse evidence.
        String iteration = label("world_axioms.visualIteration", "baseline");
        int scene = Integer.getInteger("world_axioms.visualScene", 1);
        autoRequested = true;
        request(scene, "axiom-" + iteration + "-" + nonce);
    }
    public static void request(int requestedRound, String name) {
        projection(requestedRound); // Validate before replacing any pending capture.
        if (pending != null) throw new IllegalStateException("An Axiom presentation capture is already pending");
        if (name == null || !name.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}"))
            throw new IllegalArgumentException("Invalid capture filename");
        if (!installed) { MinecraftForge.EVENT_BUS.register(AxiomVisualHarness.class); installed = true; }
        round = requestedRound; pending = name.endsWith(".png") ? name : name + ".png";
        settleTicks = 0; screen = null;
        System.out.println("AXIOM_VISUAL presentation-only request round=" + round + " file=" + pending);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        autoRequest(mc);
        if (pending == null) return;
        if (!STANDALONE && (mc.player == null || mc.level == null)) return;
        if (STANDALONE && (mc.player != null || mc.level != null)) throw new IllegalStateException("Standalone presentation must not enter a world");
        if (screen == null) {
            screen = new WorldAxiomScreen(projection(round));
            mc.setScreen(screen);
        }
        if (mc.screen != screen) throw new IllegalStateException("Presentation screen was replaced before capture");
        if (++settleTicks < 20) return;
        String fileName = pending;
        Path directory = mc.gameDirectory.toPath().resolve("screenshots");
        Path image = directory.resolve(fileName);
        Path metadata = directory.resolve(fileName + ".presentation.json");
        final int pixelWidth = mc.getMainRenderTarget().width, pixelHeight = mc.getMainRenderTarget().height;
        final JsonObject evidence = new JsonObject();
        try {
            Files.createDirectories(directory);
            if (Files.exists(image) || Files.exists(metadata)) throw new IllegalStateException("Refusing to overwrite prior visual evidence: " + image);
            evidence.addProperty("presentation_fixture",true);
            evidence.addProperty("gameplay_acceptance",false);
            evidence.addProperty("standalone",STANDALONE);
            evidence.addProperty("world_present",mc.level != null);
            evidence.addProperty("scene",round);
            evidence.addProperty("iteration",label("world_axioms.visualIteration","manual"));
            evidence.addProperty("source_revision_label",label("world_axioms.visualSourceRevision","unspecified"));
            evidence.addProperty("screen_class_sha256",screenClassHash());
            evidence.addProperty("gui_width",mc.getWindow().getGuiScaledWidth());
            evidence.addProperty("gui_height",mc.getWindow().getGuiScaledHeight());
            evidence.addProperty("framebuffer_width",pixelWidth);
            evidence.addProperty("framebuffer_height",pixelHeight);
            evidence.addProperty("screen","WorldAxiomScreen");
            evidence.addProperty("nodes",24);
            evidence.addProperty("status","requested");
            Files.writeString(metadata,evidence + "\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        } catch (Exception failure) { throw new IllegalStateException("Axiom presentation evidence setup failed",failure); }
        pending = null;
        // Screenshot.grab schedules its writer. Existence alone is NOT a completion signal.
        Screenshot.grab(mc.gameDirectory,fileName,mc.getMainRenderTarget(), message -> {
            try {
                if (!Files.isRegularFile(image)) throw new IllegalStateException("Native framebuffer capture failed: " + message.getString());
                try (var input = Files.newInputStream(image); NativeImage png = NativeImage.read(input)) {
                    if (png.getWidth() != pixelWidth || png.getHeight() != pixelHeight)
                        throw new IllegalStateException("PNG dimensions disagree with captured native framebuffer");
                }
                evidence.addProperty("status","png_validated");
                evidence.addProperty("png_bytes",Files.size(image));
                evidence.addProperty("png_sha256",sha256(Files.readAllBytes(image)));
                Files.writeString(directory.resolve(fileName + ".complete.json"),evidence + "\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                System.out.println("AXIOM_VISUAL PNG_VALIDATED presentation_fixture=true gameplay_acceptance=false " + image.toAbsolutePath());
                if (STANDALONE && Boolean.getBoolean("world_axioms.visualExitAfterCapture")) mc.execute(mc::stop);
            } catch (Exception failure) {
                System.err.println("AXIOM_VISUAL FAILED " + image + " " + failure);
                try { Files.writeString(directory.resolve(fileName + ".failure.txt"),failure.toString(),StandardOpenOption.CREATE_NEW); }
                catch (Exception suppressed) { failure.addSuppressed(suppressed); }
                mc.execute(() -> { throw new IllegalStateException("Axiom native PNG validation failed",failure); });
            }
        });
    }
    private static String screenClassHash() throws Exception {
        try (var input = WorldAxiomScreen.class.getResourceAsStream("WorldAxiomScreen.class")) {
            if (input == null) throw new IllegalStateException("Cannot identify actual loaded Screen bytecode");
            return sha256(input.readAllBytes());
        }
    }
    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
