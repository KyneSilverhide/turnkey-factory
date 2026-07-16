package dev.aurelien.prefab.build;

import net.minecraft.network.chat.Component;

/**
 * Thème de matériaux du bâtiment, choisi dans le GUI du contrôleur. Le thème ne change QUE les palettes
 * (cf. {@link BuildStyles#of}), jamais l'algorithme de composition.
 * <ul>
 *   <li>{@link #STONE} : famille pierre (stone bricks, andésite, cobblestone…). Actif.</li>
 *   <li>{@link #BRICK} : famille brique/granite. Réservé (activé à l'étape 2).</li>
 * </ul>
 */
public enum Theme {
    STONE,
    BRICK;

    /** Thèmes proposés à l'utilisateur dans le GUI. */
    public static final Theme[] AVAILABLE = { STONE, BRICK };

    public Theme nextAvailable() {
        Theme[] v = AVAILABLE;
        int i = 0;
        for (int k = 0; k < v.length; k++) if (v[k] == this) { i = k; break; }
        return v[(i + 1) % v.length];
    }

    public static Theme byOrdinal(int o) {
        Theme[] v = values();
        return v[Math.floorMod(o, v.length)];
    }

    public Component label() {
        return Component.translatable(this == STONE ? "gui.turnkey_factory.theme.stone" : "gui.turnkey_factory.theme.brick");
    }
}
