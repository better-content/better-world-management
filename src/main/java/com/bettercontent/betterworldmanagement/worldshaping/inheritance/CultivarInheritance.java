package com.bettercontent.betterworldmanagement.worldshaping.inheritance;

import com.bettercontent.betterbumblezonecrops.CultivarCatalog;

/** Typed optional-mod boundary; loaded only after the cultivar mod is present. */
final class CultivarInheritance {
    private CultivarInheritance() {}
    static boolean allowed(String id) {
        var cultivar = CultivarCatalog.bySeed(id);
        return cultivar != null && CultivarCatalog.resolves(cultivar);
    }
}
