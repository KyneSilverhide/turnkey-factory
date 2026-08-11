package dev.aurelien.prefab.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Tooltips uniformes du mod : une ligne de résumé toujours visible (« à quoi ça sert »), puis soit
 * une invite à maintenir SHIFT, soit le détail des prérequis si SHIFT est déjà maintenu — jamais les
 * deux à la fois, pour ne pas doubler la hauteur du tooltip en permanence.
 * <p>
 * {@link Screen#hasShiftDown()} est lu directement depuis ces méthodes, appelées depuis des classes
 * communes ({@code Block}/{@code Item}) : c'est sûr parce qu'{@code appendHoverText} n'est jamais
 * exécuté que côté client (rendu du tooltip) — exactement le pattern que vanilla utilise lui-même
 * dans {@code ItemStack#getTooltipLines}.
 */
public final class TooltipHelper {
    private static final String HOLD_SHIFT_KEY = "item.turnkey_factory.tooltip.hold_shift";

    private TooltipHelper() {}

    /**
     * Tooltip d'une machine : résumé (clé {@code <descriptionId>.tooltip.summary}) toujours affiché,
     * puis {@code reqLines} (préparées par l'appelant, qui choisit leur style) affichées seulement si
     * SHIFT est maintenu, sinon une simple invite à le faire. Sans {@code reqLines}, ne montre que le
     * résumé — pas d'invite pour rien.
     */
    public static void machine(List<Component> tooltip, String descriptionId, Component... reqLines) {
        tooltip.add(Component.translatable(descriptionId + ".tooltip.summary").withStyle(ChatFormatting.GRAY));
        if (reqLines.length == 0) return;
        if (Screen.hasShiftDown()) {
            tooltip.addAll(Arrays.asList(reqLines));
        } else {
            tooltip.add(Component.translatable(HOLD_SHIFT_KEY).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /**
     * Cas courant de {@link #machine}: {@code reqCount} lignes de prérequis, toutes en GRAY, générées
     * depuis les clés {@code <descriptionId>.tooltip.req_1}..{@code req_<reqCount>}. La plupart des
     * machines du mod n'ont besoin de rien de plus ; celles dont une ligne a un style différent (ex.
     * l'avertissement DARK_GRAY du lance-flammes) continuent d'appeler {@link #machine} directement.
     */
    public static void machine(List<Component> tooltip, String descriptionId, int reqCount) {
        Component[] reqLines = new Component[reqCount];
        for (int i = 0; i < reqCount; i++) {
            reqLines[i] = Component.translatable(descriptionId + ".tooltip.req_" + (i + 1)).withStyle(ChatFormatting.GRAY);
        }
        machine(tooltip, descriptionId, reqLines);
    }

    /** Tooltip d'une ligne, toujours visible — composants intermédiaires sans détail supplémentaire. */
    public static void simple(List<Component> tooltip, String descriptionId) {
        tooltip.add(Component.translatable(descriptionId + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
