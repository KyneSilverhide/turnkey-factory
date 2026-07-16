package dev.aurelien.prefab.network;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

/**
 * Vérifications communes aux handlers de payloads réseau. Un paquet transporte une position
 * arbitraire choisie par le client : sans contrôle, un client modifié pourrait reconfigurer ou
 * déclencher/annuler la construction de n'importe quel bloc du monde, pas seulement celui dont il a
 * le menu ouvert.
 */
public final class NetworkUtil {
    private NetworkUtil() {}

    /** Même portée que {@link net.minecraft.world.inventory.AbstractContainerMenu#stillValid}. */
    private static final double MAX_REACH_SQR = 64.0;

    /** Vrai si {@code player} est assez proche de {@code pos} pour agir dessus. */
    public static boolean withinReach(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_REACH_SQR;
    }
}
