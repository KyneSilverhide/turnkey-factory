package dev.aurelien.prefab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/**
 * Façade commune aux deux implémentations de tourelle : {@link TurretBlockEntity} (charbon,
 * toujours disponible) et l'implémentation Create (compat/create, réseau cinétique réel — n'existe
 * que si Create est chargé). Java n'autorisant pas l'héritage multiple, ces deux classes ne
 * partagent aucune classe mère commune (l'une étend {@code BlockEntity}, l'autre
 * {@code KineticBlockEntity}) — le ciblage/tir partagé vit dans {@link TurretCombat}, composé par
 * les deux, et cette interface est ce que {@code TurretMenu}, {@code TurretScreen} et
 * {@code TurretRenderer} manipulent pour rester agnostiques de la source d'énergie.
 */
public interface ITurret {
    BlockPos getBlockPos();

    int range();
    boolean active();
    boolean targetHostile();
    boolean targetNeutral();
    boolean targetPlayer();
    /** -1 = aucune cible verrouillée. */
    int currentTargetId();

    void setActive(boolean value);
    void setRange(int r);
    void setTargets(boolean hostile, boolean neutral, boolean player);

    /** Faux si la tourelle est active mais ne peut pas tirer faute d'énergie (couleur d'alerte côté GUI). */
    boolean hasPower();
    /** Ligne de statut énergie affichée dans {@code TurretScreen} (charge de charbon ou vitesse de rotation). */
    Component powerLabel();
    /** Niveau de la jauge d'énergie affichée dans {@code TurretScreen}, dans [0, 1] (charge/capacité
     *  max côté charbon, vitesse/vitesse de référence côté Create). */
    float powerFraction();

    /**
     * Angle (degrés) de la pièce d'engrenage animée dans {@code TurretModel}/{@code TurretRenderer}.
     * {@link Float#NaN} par défaut = pas d'engrenage à dessiner (tourelle charbon) ; seule
     * l'implémentation Create le surcharge avec un angle réel, délégué à
     * {@code KineticBlockEntityRenderer.getAngleForBe} pour rester en phase avec le réseau cinétique
     * adjacent.
     */
    default float cogAngle() {
        return Float.NaN;
    }

    /**
     * Aligne l'état actif de la tourelle sur le signal redstone reçu — plus de bouton Marche/Arrêt
     * dans le GUI, la tourelle s'active tant qu'elle reçoit un signal d'un voisin. Appelé depuis
     * {@code neighborChanged}/{@code onPlace} des deux blocs (charbon et Create).
     */
    static void syncRedstoneState(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ITurret turret) {
            turret.setActive(level.hasNeighborSignal(pos));
        }
    }
}
