package com.bettercontent.betterworldmanagement.worldshaping.client;

import com.bettercontent.betterworldmanagement.worldshaping.AxiomCatalog;
import com.bettercontent.betterworldmanagement.worldshaping.AxiomView;
import com.bettercontent.betterworldmanagement.worldshaping.WorldAxiomNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import static com.bettercontent.betterworldmanagement.worldshaping.client.AxiomEngraving.*;

/** Server-authoritative World Axiom draft editor. Selection is never predicted locally. */
public final class WorldAxiomScreen extends Screen {
    private AxiomView view;
    private AxiomLayout.Layout layout;
    private List<AxiomLayout.LabelPlace> atlasLabels = List.of();
    private static final float ATLAS_LABEL_SCALE = .8f;
    private int selected, branch, detailScroll, waitingTicks;
    private boolean inspector, tools;
    private int archiveBook, archivePage;
    private String[][] archiveDraft;
    private EditBox archiveField;
    private String pending = "";
    private String claimKind = "monument", claimCurrency = "impact";
    private static final String[] CURRENCIES = {"impact", "tempo", "work", "mobility", "endurance", "robustness", "renewal", "control"};
    private final Screen returnTo;

    public WorldAxiomScreen(AxiomView view) {
        super(Component.literal("World Axioms"));
        this.view = view;
        this.returnTo = net.minecraft.client.Minecraft.getInstance().screen;
    }

    public void update(AxiomView next) {
        if (next.generation() < view.generation() || (next.generation() == view.generation() && next.revision() < view.revision())) return;
        view = next;
        pending = "";
        waitingTicks = 0;
        if (minecraft != null && font != null) rebuild();
    }

    @Override protected void init() { rebuild(); }
    private void rebuild() {
        clearWidgets();
        layout = AxiomLayout.compute(width, height, branch);
        int[] labelWidths = new int[24];
        for (int i = 0; i < 24; i++) labelWidths[i] = (int) Math.ceil(font.width(AxiomCatalog.nodes().get(i).label()) * ATLAS_LABEL_SCALE);
        atlasLabels = AxiomLayout.labels(layout, labelWidths, 8);
        int footer = height - 31;
        button("Refresh", width - 150, footer, 66, () -> { pending = ""; WorldAxiomNetwork.refresh(); });
        button("Done", width - 78, footer, 60, this::onClose);
        if (tools) { buildTools(); return; }
        boolean showMap = !layout.stacked() || !inspector;
        if (showMap) {
            if (!layout.radial()) {
                var map = layout.map();
                for (int b = 0; b < 6; b++) {
                    int choice = b, col = b % 3, row = b / 3;
                    Button tab = button((b + 1) + " " + shortBranch(b), map.x() + col * (map.width() / 3),
                            map.y() + row * (height < 230 ? 16 : 20), map.width() / 3 - 2, () -> { branch = choice; rebuild(); });
                    if (height < 230) tab.setHeight(16);
                    tab.setTooltip(Tooltip.create(Component.literal(AxiomCatalog.branches().get(b))));
                }
            }
            for (var p : layout.nodes()) {
                AxiomCatalog.Node node = AxiomCatalog.nodes().get(p.index());
                var r = p.bounds();
                Button nodeWidget = new Button(r.x(), r.y(), r.width(), r.height(), Component.literal(node.label()), ignored -> {
                    selected = p.index(); branch = selected / 4; detailScroll = 0;
                    if (layout.stacked()) inspector = true;
                    rebuild();
                }, supplier -> supplier.get()) {
                    @Override public void renderWidget(GuiGraphics g, int mx, int my, float delta) {
                        boolean chosen = view.selected().contains(node.id());
                        int color = MINERAL[p.index() / 4];
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), chosen ? 0xff25493f : PLATE);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), isHoveredOrFocused() || selected == p.index() ? LIGHT : chosen ? VERDIGRIS : color);
                        g.pose().pushPose();
                        g.pose().translate(getX() + (layout.radial() ? getWidth() / 2 : 15), getY() + getHeight() / 2 - 1, 0);
                        float iconScale = layout.radial() ? .82f : Math.min(.85f, getHeight() / 28f);
                        g.pose().scale(iconScale, iconScale, 1);
                        glyph(g, 0, 0, node.id(), chosen ? VERDIGRIS : color);
                        g.pose().popPose();
                        if (!layout.radial()) {
                            g.drawString(font, font.plainSubstrByWidth(node.label(), getWidth() - 61), getX() + 32, getY() + (getHeight() - 8) / 2, LIGHT, false);
                            g.drawString(font, chosen ? "ON" : "1", getX() + getWidth() - 19, getY() + (getHeight() - 8) / 2, chosen ? VERDIGRIS : MUTED, false);
                        } else if (chosen) g.fill(getX() + getWidth() - 5, getY() + 2, getX() + getWidth() - 2, getY() + 5, VERDIGRIS);
                    }
                };
                nodeWidget.setTooltip(Tooltip.create(Component.literal(node.label() + "\n" + (view.selected().contains(node.id()) ? "Allocated" : "1 point") + " · Independent axiom\n" + node.description())));
                addRenderableWidget(nodeWidget);
            }
        }
        if (!layout.stacked() || inspector) buildDetails();
        else button("Inspect selected", 18, footer, Math.min(126, width - 180), () -> { inspector = true; rebuild(); });
    }

    private void buildDetails() {
        var r = layout.detail();
        AxiomCatalog.Node node = AxiomCatalog.nodes().get(selected);
        boolean chosen = view.selected().contains(node.id());
        int y = r.bottom() - 27;
        Button toggle = button(chosen ? "Release point" : "Allocate · 1 point", r.x() + 9, y, r.width() - 18, () -> {
            pending = node.id(); waitingTicks = 0; WorldAxiomNetwork.toggle(node.id()); rebuild();
        });
        toggle.active = mutable() && (chosen || view.selected().size() < view.budget());
        toggle.setTooltip(Tooltip.create(Component.literal(!view.operator() ? "Operator permission required." : view.locked() ? "This draft is sealed." : !pending.isEmpty() ? "Waiting for server confirmation." : !chosen && view.selected().size() >= view.budget() ? "No points remain. Release any other axiom; there are no prerequisites." : "Edits the next-generation draft, not the living world.")));
        if (layout.stacked()) button("< Atlas", 18, height - 31, 72, () -> { inspector = false; rebuild(); });
        addConfigurationWidgets(node, r);
    }

    private static boolean inherited(String id) { return List.of("ark", "seed_covenant", "patient_hive", "familiars_name").contains(id); }
    private static boolean claimable(String id) { return inherited(id) || List.of("cartographers_memory", "font_atlas").contains(id); }
    private static boolean hasTools(String id) {
        return claimable(id) || List.of("shaped_horizon", "turning_year", "deep_compact", "founders_exchange", "chosen_omen", "communal_archive").contains(id);
    }
    private void addConfigurationWidgets(AxiomCatalog.Node node, AxiomLayout.Rect r) {
        if (!hasTools(node.id())) return;
        button("Configuration / inheritance", r.x() + 9, r.bottom() - 51, r.width() - 18, () -> {
            tools = true; archiveDraft = null; archiveField = null; archiveBook = archivePage = 0; rebuild();
        });
    }
    private void buildTools() {
        var node = AxiomCatalog.nodes().get(selected);
        String id = node.id();
        button("< Details", 18, height - 31, 86, () -> { tools = false; archiveField = null; rebuild(); });
        int x = 25, y = 99, w = Math.min(420, width - 50);
        switch (id) {
            case "shaped_horizon" -> choice(x,y,w,"preset", new String[]{"ordinary", "large_biomes"});
            case "turning_year" -> choice(x,y,w,"season", new String[]{"early_spring", "early_summer", "early_autumn", "early_winter"});
            case "chosen_omen" -> choice(x,y,w,"event", new String[]{"thin_veil", "spirit_fair", "wandering_lights", "open_ways"});
            case "deep_compact" -> {
                choice(x,y,w,"deposit", new String[]{"ironstone", "copper_bloom", "tin_quartz", "brassroot"});
                choice(x,y+28,w,"band", new String[]{"shallow", "deep"});
            }
            case "founders_exchange" -> choice(x,y,w,"currency", CURRENCIES);
            case "cartographers_memory" -> button("Map: " + claimKind + " >", x,y,w, () -> {
                String[] kinds = {"monument", "mansion", "mineshaft", "fortress"};
                claimKind = kinds[(java.util.Arrays.asList(kinds).indexOf(claimKind) + 1) % kinds.length]; rebuild();
            });
            case "font_atlas" -> button("Font: " + claimCurrency + " >", x,y,w, () -> {
                claimCurrency = CURRENCIES[(java.util.Arrays.asList(CURRENCIES).indexOf(claimCurrency) + 1) % CURRENCIES.length]; rebuild();
            });
            case "communal_archive" -> buildArchive();
            default -> { }
        }
        if (inherited(id)) {
            Button nominate = button("Nominate aimed animal / held item", x,y,w, () -> submit(() -> WorldAxiomNetwork.nominate(id)));
            nominate.active = mutable() && view.selected().contains(id);
            nominate.setTooltip(Tooltip.create(Component.literal("The server captures and validates the actual animal or held item. Draft nomination does not consume it.")));
        }
        if (claimable(id)) {
            Button claim = button("Claim from active lineage", x,y+32,w, () -> {
                CompoundTag request = new CompoundTag();
                if (id.equals("cartographers_memory")) request.putString("kind", claimKind);
                if (id.equals("font_atlas")) request.putString("currency", claimCurrency);
                submit(() -> WorldAxiomNetwork.claim(id, request));
            });
            claim.active = view.operator() && pending.isEmpty() && view.active().contains(id);
            claim.setTooltip(Tooltip.create(Component.literal("Server checks the active lineage and any prior receipt. This does not claim an editable draft or duplicate a prior claim.")));
        }
    }
    private void submit(Runnable action) { pending = AxiomCatalog.nodes().get(selected).id(); waitingTicks = 0; action.run(); rebuild(); }
    private void choice(int x, int y, int w, String key, String[] choices) {
        String id = AxiomCatalog.nodes().get(selected).id();
        CompoundTag config = view.configuration().getCompound(id);
        String current = config.getString(key);
        Button select = button(key + ": " + (current.isEmpty() ? "server default" : current) + "  >", x,y,w, () -> {
            int index = java.util.Arrays.asList(choices).indexOf(current);
            CompoundTag next = config.copy(); next.putString(key, choices[(index + 1) % choices.length]);
            submit(() -> WorldAxiomNetwork.configure(id,next));
        });
        select.active = mutable() && view.selected().contains(id);
        select.setTooltip(Tooltip.create(Component.literal("Cycle: " + String.join(" / ", choices) + ". Server confirmation required.")));
    }
    private void buildArchive() {
        if (archiveDraft == null) {
            archiveDraft = new String[5][4];
            CompoundTag config = view.configuration().getCompound("communal_archive");
            for (int b = 0; b < 5; b++) {
                String value = config.getString(b == 0 ? "plaque" : "book" + b);
                String[] pages = b == 0 ? new String[]{value} : value.split("\\n", -1);
                for (int p = 0; p < 4; p++) archiveDraft[b][p] = p < pages.length ? pages[p] : "";
            }
        }
        int x = 25, w = width - 50;
        button((archiveBook == 0 ? "Plaque" : "Book " + archiveBook) + " >", x, 99, w / 2 - 3,
                () -> { archiveBook = (archiveBook + 1) % 5; archivePage = 0; rebuild(); });
        Button pageButton = button("Page " + (archivePage + 1) + " / 4 >", x + w / 2, 99, w / 2,
                () -> { archivePage = (archivePage + 1) % 4; rebuild(); });
        pageButton.active = archiveBook > 0;
        archiveField = new EditBox(font, x, 127, w, 20, Component.literal(archiveBook == 0 ? "Plaque text" : "Book page text"));
        archiveField.setMaxLength(256);
        archiveField.setValue(archiveDraft[archiveBook][archivePage]);
        archiveField.setEditable(mutable() && view.selected().contains("communal_archive"));
        archiveField.setResponder(value -> archiveDraft[archiveBook][archivePage] = value);
        addRenderableWidget(archiveField);
        Button save = button("Save", 108, height - 31, Math.max(40, width - 268), () -> {
            CompoundTag config = view.configuration().getCompound("communal_archive").copy();
            config.putString("plaque", archiveDraft[0][0]);
            for (int b = 1; b < 5; b++) {
                int last = 3; while (last > 0 && archiveDraft[b][last].isEmpty()) last--;
                config.putString("book" + b, String.join("\n", java.util.Arrays.copyOf(archiveDraft[b], last + 1)));
            }
            submit(() -> WorldAxiomNetwork.configure("communal_archive",config));
        });
        save.active = mutable() && view.selected().contains("communal_archive");
    }

    private boolean mutable() { return view.operator() && !view.locked() && pending.isEmpty(); }
    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), b -> action.run()).bounds(x, y, w, 20).build());
    }
    private static String shortBranch(int b) {
        return switch (b) { case 0 -> "Forge"; case 1 -> "Life"; case 2 -> "Atlas"; case 3 -> "Hearth"; case 4 -> "Ritual"; default -> "World"; };
    }

    @Override public void tick() {
        if (!pending.isEmpty() && ++waitingTicks == 200) { pending = ""; WorldAxiomNetwork.refresh(); rebuild(); }
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (minecraft != null) minecraft.setScreen(returnTo == this ? null : returnTo); }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        panel(g, layout.frame());
        // Fine etched horizontal rules and a 24-notch allocation register.
        g.fill(18, 56, width - 18, 57, COPPER);
        if (width < 540) {
            g.drawString(font, "AXIOMS", 21, 20, LIGHT, false);
            g.drawString(font, "GEN " + view.generation(), 21, 35, MUTED, false);
        } else {
            g.pose().pushPose();
            g.pose().translate(21, 17, 0);
            g.pose().scale(1.35f, 1.35f, 1);
            g.drawString(font, "THE WORLD REMEMBERS", 0, 0, LIGHT, false);
            g.pose().popPose();
            g.drawString(font, "24 INDEPENDENT AXIOMS / ONE POINT EACH", 21, 39, MUTED, false);
        }
        String budget = view.selected().size() + " / " + view.budget() + " POINTS";
        g.drawString(font, budget, width - font.width(budget) - 23, 20, VERDIGRIS, false);
        int barWidth = Math.min(5, (width / 2 - 24) / 24);
        int start = width - 22 - 24 * (barWidth + 1);
        for (int i = 0; i < 24; i++) g.fill(start + i * (barWidth + 1), 36, start + i * (barWidth + 1) + barWidth, 43,
                i < view.selected().size() ? VERDIGRIS : i < view.budget() ? COPPER : 0xff344145);
        if (tools) {
            String label = AxiomCatalog.nodes().get(selected).label();
            g.drawString(font, font.plainSubstrByWidth(label + " / CONFIGURATION", width - 50), 25, 76, LIGHT, false);
        } else {
            if ((!layout.stacked() || !inspector) && layout.radial()) { renderAtlas(g); renderNodeNames(g); }
            if (!layout.stacked() || inspector) renderDetails(g);
        }
        String status = !view.error().isEmpty() ? view.error() : !pending.isEmpty() ? "Awaiting server confirmation…" : view.locked() ? "SEALED · Read only" : !view.operator() ? "OBSERVER · Read only" : "Independent axioms · next generation";
        g.drawString(font, font.plainSubstrByWidth(status, width - 184), 20, height - 24, !view.error().isEmpty() ? 0xffffad91 : MUTED, false);
        super.render(g, mouseX, mouseY, delta);
        if (mouseY >= height - 34 && mouseX < width - 160) g.renderTooltip(font, Component.literal(status + "\nGeneration " + view.generation() + " · draft revision " + view.revision()), mouseX, mouseY);
    }

    private void renderAtlas(GuiGraphics g) {
        var r = layout.map();
        int cx = r.x() + r.width() / 2, cy = r.y() + r.height() / 2;
        ring(g, cx, cy, 26, COPPER); ring(g, cx, cy, 29, 0xff3c4540);
        String remaining = Integer.toString(view.budget() - view.selected().size());
        g.drawString(font, remaining, cx - font.width(remaining) / 2, cy - 9, LIGHT, false);
        g.drawString(font, "FREE", cx - font.width("FREE") / 2, cy + 4, MUTED, false);
        // No node-to-node edges: these are six independent mineral families, not a prerequisite tree.
        for (int b = 0; b < 6; b++) {
            var tip = layout.nodes().get(b * 4 + 3).bounds();
            int tx = tip.x() + 12, ty = tip.y() + 12;
            double a = Math.atan2(ty - cy, tx - cx);
            int ex = tx + (int) (Math.cos(a) * 19), ey = ty + (int) (Math.sin(a) * 19);
            g.fill(ex - 2, ey - 2, ex + 2, ey + 2, MINERAL[b]);
            String name = (b + 1) + " / " + shortBranch(b).toUpperCase(java.util.Locale.ROOT);
            var caption = AxiomLayout.caption(layout, b, font.width(name), font.lineHeight);
            g.drawString(font, name, caption.x(), caption.y(), MINERAL[b], false);
        }
    }

    private void renderNodeNames(GuiGraphics g) {
        for (var label : atlasLabels) {
            var node = AxiomCatalog.nodes().get(label.index());
            var bounds = label.bounds();
            boolean allocated = view.selected().contains(node.id());
            g.pose().pushPose();
            g.pose().translate(bounds.x(), bounds.y(), 0);
            g.pose().scale(ATLAS_LABEL_SCALE, ATLAS_LABEL_SCALE, 1);
            g.drawString(font, node.label(), 0, 0, allocated ? VERDIGRIS : LIGHT, false);
            g.pose().popPose();
        }
    }

    private void renderDetails(GuiGraphics g) {
        var r = layout.detail();
        panel(g, r);
        var node = AxiomCatalog.nodes().get(selected);
        int top = r.y() + 13;
        if (!layout.stacked()) {
            glyph(g, r.x() + 21, top + 9, node.id(), MINERAL[selected / 4]);
            g.drawString(font, "0" + (selected / 4 + 1) + " / " + shortBranch(selected / 4).toUpperCase(java.util.Locale.ROOT), r.x() + 39, top, MINERAL[selected / 4], false);
        }
        int labelY = top + (layout.stacked() ? 0 : 25);
        List<FormattedCharSequence> label = font.split(Component.literal(node.label()), r.width() - 24);
        for (var line : label) { g.drawString(font, line, r.x() + 12, labelY, LIGHT, false); labelY += 11; }
        g.drawString(font, view.selected().contains(node.id()) ? "ALLOCATED  /  1 POINT" : "INDEPENDENT  /  1 POINT", r.x() + 12, labelY + 3, VERDIGRIS, false);
        int textTop = labelY + 23;
        if (height < 230) textTop = r.y() + 8;
        int textBottom = r.bottom() - (hasTools(node.id()) ? 60 : 36);
        String text = node.description() + "\n\n" + node.configuration()
                + "\n\nLIVING WORLD\n" + (view.active().contains(node.id()) ? "Active in this lineage." : "Not active in this lineage.")
                + "\n\nFORECAST\n" + view.forecast();
        if (view.configuration().contains(node.id())) text += "\n\nNEXT DRAFT CONFIGURATION\n" + view.configuration().getCompound(node.id());
        if (view.activeConfiguration().contains(node.id())) text += "\n\nACTIVE CONFIGURATION\n" + view.activeConfiguration().getCompound(node.id());
        if (height < 230) {
            // At GUI scale four, use the whole remaining viewport as a scrollable inspector.
            // Title and allocation are part of that scroll instead of covering the description.
            g.fill(r.x() + 5, r.y() + 5, r.right() - 5, textBottom, INK);
            text = node.label() + "\n" + (view.selected().contains(node.id()) ? "Allocated · 1 point" : "Independent · 1 point") + "\n\n" + text;
        }
        List<FormattedCharSequence> lines = font.split(Component.literal(text), r.width() - 28);
        int visible = Math.max(0, (textBottom - textTop) / 11);
        detailScroll = Math.max(0, Math.min(detailScroll, Math.max(0, lines.size() - visible)));
        if (textBottom > textTop) {
            g.enableScissor(r.x() + 8, textTop, r.right() - 8, textBottom);
            for (int i = detailScroll; i < Math.min(lines.size(), detailScroll + visible); i++)
                g.drawString(font, lines.get(i), r.x() + 12, textTop + (i - detailScroll) * 11, MUTED, false);
            g.disableScissor();
            if (lines.size() > visible) g.drawString(font, "↕", r.right() - 13, textTop, COPPER, false);
        }
    }

    @Override public boolean mouseScrolled(double mx, double my, double amount) {
        if (!tools && (!layout.stacked() || inspector) && layout.detail().contains(mx, my)) {
            detailScroll = Math.max(0, detailScroll - (int) Math.signum(amount) * 3); return true;
        }
        return super.mouseScrolled(mx, my, amount);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256 && tools) { tools = false; archiveField = null; rebuild(); return true; }
        if (key == 266 || key == 267) { detailScroll = Math.max(0, detailScroll + (key == 267 ? 5 : -5)); return true; }
        if (key == 256 && layout.stacked() && inspector) { inspector = false; rebuild(); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
