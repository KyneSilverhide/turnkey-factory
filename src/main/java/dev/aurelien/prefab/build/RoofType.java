package dev.aurelien.prefab.build;

import net.minecraft.network.chat.Component;

/**
 * Forme du toit, choisie dans le GUI du contrôleur.
 * <ul>
 *   <li>{@link #FLAT} : toit plat (dalle + parapet + aérations) — implémentation historique.</li>
 *   <li>{@link #PITCHED} : toit à deux pentes (escaliers de tuiles + pignons triangulaires), posé
 *       par-dessus le plafond plat existant. Le faîte court le long du plus grand côté.</li>
 * </ul>
 */
public enum RoofType {
    FLAT,
    PITCHED;

    public RoofType next() {
        RoofType[] v = values();
        return v[(ordinal() + 1) % v.length];
    }

    public static RoofType byOrdinal(int o) {
        RoofType[] v = values();
        return v[Math.floorMod(o, v.length)];
    }

    public Component label() {
        return Component.translatable(this == FLAT ? "gui.turnkey_factory.roof.flat" : "gui.turnkey_factory.roof.pitched");
    }
}
