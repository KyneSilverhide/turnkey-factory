package dev.aurelien.prefab.build;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Consommation de durabilité partagée par les outils placés dans la niveleuse et le texturiseur.
 * Un outil enchanté ne casse jamais : au lieu de le laisser descendre à 0 (destruction), on plafonne
 * à 1 point restant. Un outil enchanté coûte trop cher (livres, réparation) pour qu'une machine
 * autonome le détruise sans supervision — l'utilisateur doit le remplacer manuellement.
 */
public final class ToolDurability {
    private ToolDurability() {}

    /** Consomme 1 point de durabilité. Renvoie {@code true} si l'outil vient de casser (jamais pour un outil enchanté). */
    public static boolean damage(ServerLevel server, ItemStack tool) {
        if (tool.isEnchanted()) {
            int remaining = tool.getMaxDamage() - tool.getDamageValue();
            if (remaining <= 1) return false; // protégé : ne descend jamais en dessous de 1 point
            tool.hurtAndBreak(1, server, null, ignored -> {});
            return false;
        }
        tool.hurtAndBreak(1, server, null, ignored -> {});
        return tool.isEmpty();
    }
}
