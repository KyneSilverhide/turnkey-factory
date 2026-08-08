package dev.aurelien.prefab.build;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Consommation de durabilité partagée par les outils placés dans la niveleuse et le texturiseur.
 * Un outil enchanté ne casse jamais : au lieu de le laisser descendre à 0 (destruction), on plafonne
 * à 1 point restant. Un outil enchanté coûte trop cher (livres, réparation) pour qu'une machine
 * autonome le détruise sans supervision — l'utilisateur doit le remplacer manuellement.
 * <p>
 * Ceci ne dispense PAS la machine de son coût d'entretien : une fois le plancher atteint, {@link
 * #damage} renvoie {@code true} (« ne peut plus continuer »), exactement comme un outil non enchanté
 * qui vient de casser — la machine s'arrête (statut « outil manquant/à réparer ») au lieu de tourner
 * indéfiniment sur un outil gelé à 1 point. Le joueur doit réparer l'outil à l'enclume (ou en fournir
 * un neuf) pour reprendre ; seul l'outil lui-même est préservé, pas le fonctionnement de la machine.
 */
public final class ToolDurability {
    private ToolDurability() {}

    /**
     * Consomme 1 point de durabilité. Renvoie {@code true} si l'outil vient de casser (outil non
     * enchanté) OU si un outil enchanté est au plancher (1 point restant) et ne peut plus être entamé —
     * dans les deux cas, l'appelant doit arrêter la machine et signaler un outil manquant/à réparer.
     */
    public static boolean damage(ServerLevel server, ItemStack tool) {
        if (tool.isEnchanted()) {
            int remaining = tool.getMaxDamage() - tool.getDamageValue();
            if (remaining <= 1) return true; // plancher atteint : la machine s'arrête, l'outil est préservé
            tool.hurtAndBreak(1, server, null, ignored -> {});
            return false;
        }
        tool.hurtAndBreak(1, server, null, ignored -> {});
        return tool.isEmpty();
    }
}
