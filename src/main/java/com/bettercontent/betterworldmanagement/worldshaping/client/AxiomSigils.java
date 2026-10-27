package com.bettercontent.betterworldmanagement.worldshaping.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Twenty-four ID-bound, native-pixel engravings on an immutable 16×16 canvas. No atlas assets. */
public final class AxiomSigils {
    private static final Map<String,List<String>> SIGILS;
    static {
        Map<String,List<String>> map = new LinkedHashMap<>();
        add(map,"iron_law", c -> { // broad anvil, narrow waist, heavy foot
            c.path(1,3,14,3,12,6,9,6,9,10,12,12,3,12,6,10,6,6,3,6,1,3);
            c.line(3,14,12,14); c.line(4,4,12,4);
        });
        add(map,"eternal_engine", c -> { // eight-tooth wheel and axle
            c.circle(8,8,5); c.circle(8,8,2);
            c.line(7,1,9,1); c.line(7,15,9,15); c.line(1,7,1,9); c.line(15,7,15,9);
            c.line(3,2,4,3); c.line(12,3,13,2); c.line(2,13,3,12); c.line(12,12,13,13);
        });
        add(map,"alloy_testament", c -> { // two stacked cast billets
            c.path(2,5,5,2,13,2,13,6,10,8,2,8,2,5,10,5,13,2);
            c.line(10,5,10,8); c.path(2,10,5,9,13,9,13,12,10,14,2,14,2,10,10,10,13,9);
            c.line(10,10,10,14);
        });
        add(map,"captive_hearth", c -> { // masonry hearth with a separate flame
            c.path(2,14,2,3,13,3,13,14); c.line(1,1,14,1); c.line(1,14,14,14);
            c.path(6,12,5,9,8,5,8,8,10,7,11,10,9,12,6,12);
        });
        add(map,"ark", c -> { // horned cattle head and broad muzzle
            c.path(1,2,2,5,5,6,10,6,13,5,14,2); c.line(1,2,4,3); c.line(11,3,14,2);
            c.path(4,6,4,10,5,13,10,13,11,10,11,6); c.line(5,10,10,10);
            c.dot(6,8); c.dot(9,8); c.dot(6,12); c.dot(9,12);
        });
        add(map,"seed_covenant", c -> { // paired leaves, stem and seed below soil
            c.line(8,4,8,12); c.path(8,8,3,7,2,3,6,4,8,8,12,6,13,2,10,3,8,8);
            c.line(2,12,13,12); c.path(7,14,8,13,9,14,8,15,7,14);
        });
        add(map,"patient_hive", c -> { // tiered skep, entrance and flight marks
            c.path(3,13,3,7,5,3,10,3,12,7,12,13,3,13); c.line(2,14,13,14);
            c.line(4,6,11,6); c.line(3,9,12,9); c.path(6,13,6,11,9,11,9,13);
            c.dot(1,5); c.dot(14,3);
        });
        add(map,"familiars_name", c -> { // pointed wolf ears, eyes and muzzle
            c.path(3,8,2,2,6,5,9,5,13,2,12,8,13,11,8,14,2,11,3,8);
            c.line(4,8,5,8); c.line(10,8,11,8); c.path(6,11,9,11,8,13,6,11);
        });
        add(map,"cartographers_memory", c -> { // compass rose on its ring
            c.circle(8,8,5); c.path(8,1,10,7,15,8,10,9,8,15,6,9,1,8,6,7,8,1);
            c.line(8,4,8,11);
        });
        add(map,"font_atlas", c -> { // square spiral / indexed Font portal
            c.path(2,13,2,2,13,2,13,13,5,13,5,5,10,5,10,10,8,10,8,8);
            c.line(1,15,14,15);
        });
        add(map,"prospectors_eye", c -> { // diagonal spyglass with lens bands
            c.path(2,11,10,3,14,7,6,15,2,11); c.line(8,5,12,9); c.line(4,9,8,13);
            c.line(1,9,3,7); c.dot(13,2);
        });
        add(map,"celestial_almanac", c -> { // calendar tablet with one bright star
            c.path(2,14,2,3,13,3,13,14,2,14); c.line(2,6,13,6);
            c.line(5,1,5,4); c.line(10,1,10,4); c.path(8,7,9,9,11,10,9,11,8,13,7,11,5,10,7,9,8,7);
        });
        add(map,"communal_archive", c -> { // open book and spine
            c.path(1,3,5,3,8,5,11,3,14,3,14,12,11,12,8,14,5,12,1,12,1,3);
            c.line(8,5,8,14); c.line(3,6,5,6); c.line(3,9,5,9); c.line(10,7,12,6); c.line(10,10,12,9);
        });
        add(map,"fortified_beginnings", c -> { // crenellated wall, no doorway
            c.path(1,14,1,4,4,4,4,7,6,7,6,4,9,4,9,7,11,7,11,4,14,4,14,14,1,14);
            c.line(1,10,14,10); c.line(5,10,5,14); c.line(10,10,10,14);
        });
        add(map,"settled_arrival", c -> { // village gateway with a clear path
            c.path(2,14,2,5,8,1,13,5,13,14); c.line(1,5,14,5);
            c.path(5,14,5,8,10,8,10,14); c.line(7,11,7,15); c.line(9,13,9,15);
        });
        add(map,"founders_exchange", c -> { // minted coin and balance stroke
            c.circle(8,8,6); c.line(8,3,8,13); c.path(11,5,6,5,5,7,10,9,10,11,5,11);
        });
        add(map,"sacrificial_tradition", c -> { // dagger: long diagonal blade and crossguard
            c.path(3,11,3,8,11,1,10,6,6,10,3,11); c.line(2,8,7,13); c.line(4,11,1,14); c.dot(1,15);
        });
        add(map,"spirit_awakening", c -> { // hovering spirit with eyes and a wisp tail
            c.path(3,11,3,6,5,2,10,2,12,6,12,11,10,10,9,14,7,11,5,13,5,10,3,11);
            c.dot(6,6); c.dot(9,6); c.line(7,9,8,9);
        });
        add(map,"chalk_covenant", c -> { // tipped chalk stick over a drawn ritual mark
            c.path(3,9,8,2,11,4,6,11,3,9); c.line(7,4,9,6);
            c.path(2,14,5,12,8,14,11,12,14,14); c.dot(12,8);
        });
        add(map,"patient_alchemist", c -> { // bubbling cauldron, handles and two feet
            c.path(3,7,3,11,5,13,10,13,12,11,12,7,3,7); c.line(1,6,14,6);
            c.path(3,8,1,8,1,10,3,10); c.path(12,8,14,8,14,10,12,10);
            c.line(5,13,4,15); c.line(10,13,11,15); c.circle(5,3,1); c.dot(10,1); c.dot(12,3);
        });
        add(map,"shaped_horizon", c -> { // mountains beneath a rising sun
            c.path(1,13,5,6,8,10,11,4,15,13,1,13); c.path(3,10,5,9,7,11);
            c.circle(4,3,2); c.line(1,15,14,15);
        });
        add(map,"turning_year", c -> { // autumn leaf with branching veins
            c.path(3,13,2,8,5,8,4,3,8,5,12,1,12,6,15,6,12,10,12,13,7,12,3,13);
            c.line(2,15,11,6); c.line(6,11,5,8); c.line(9,8,12,8);
        });
        add(map,"deep_compact", c -> { // faceted underground ore, bounded strata
            c.line(1,2,14,2); c.line(2,4,5,4); c.line(11,4,14,4);
            c.path(3,10,6,6,11,7,13,11,9,14,5,13,3,10); c.path(6,6,7,10,13,11);
            c.line(7,10,5,13); c.line(7,10,9,14);
        });
        add(map,"chosen_omen", c -> { // crescent moon and an isolated omen star
            c.path(9,1,5,2,2,5,1,9,3,13,7,15,11,13,13,10,9,11,6,9,5,6,6,3,9,1);
            c.line(12,1,12,5); c.line(10,3,14,3);
        });
        SIGILS = Map.copyOf(map);
    }
    private AxiomSigils() {}
    public static Set<String> ids() { return SIGILS.keySet(); }
    public static List<String> rows(String id) {
        List<String> pixels = SIGILS.get(id);
        if (pixels == null) throw new IllegalArgumentException("Unknown axiom sigil: " + id);
        return pixels;
    }
    private static void add(Map<String,List<String>> map, String id, Consumer<Canvas> draw) {
        Canvas c = new Canvas(); draw.accept(c);
        if (map.put(id,c.rows()) != null) throw new IllegalStateException("Duplicate sigil ID");
    }
    private static final class Canvas {
        final boolean[][] pixels = new boolean[16][16];
        void dot(int x, int y) {
            if (x < 0 || x >= 16 || y < 0 || y >= 16) throw new IllegalArgumentException("Sigil escapes 16×16");
            pixels[y][x] = true;
        }
        void line(int x, int y, int tx, int ty) {
            int dx=Math.abs(tx-x), sx=x<tx?1:-1, dy=-Math.abs(ty-y), sy=y<ty?1:-1, error=dx+dy;
            while(true) { dot(x,y); if(x==tx && y==ty) break; int e=2*error; if(e>=dy){error+=dy;x+=sx;} if(e<=dx){error+=dx;y+=sy;} }
        }
        void path(int... coordinates) {
            for(int i=2;i<coordinates.length;i+=2) line(coordinates[i-2],coordinates[i-1],coordinates[i],coordinates[i+1]);
        }
        void circle(int cx,int cy,int radius) {
            int x=cx+radius,y=cy;
            for(int i=1;i<=24;i++){double a=i*Math.PI/12;int tx=cx+(int)Math.round(Math.cos(a)*radius),ty=cy+(int)Math.round(Math.sin(a)*radius);line(x,y,tx,ty);x=tx;y=ty;}
        }
        List<String> rows() {
            List<String> rows = new ArrayList<>();
            for(boolean[] row:pixels){StringBuilder text=new StringBuilder(16);for(boolean pixel:row)text.append(pixel?'#':'.');rows.add(text.toString());}
            return List.copyOf(rows);
        }
    }
}
