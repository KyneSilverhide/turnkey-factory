package dev.aurelien.prefab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Marqueur porté par les 3 machines de terrain (niveleuse/texturiseur/allumeur de réverbères) : posées
 * côte à côte, elles peuvent partager un centre géométrique commun via {@link #setAsCenter()} plutôt que
 * chacune travailler autour de son propre bloc — utile pour que leurs zones respectives restent
 * concentriques au lieu de se décaler d'une machine à l'autre. Détection volontairement minimale : pas de
 * BFS ni de notion de « groupe » (cf. {@link dev.aurelien.prefab.build.InventoryNetwork} pour ça), juste un
 * pas vers les 4 voisins horizontaux directs à chaque pression du bouton « Centre ». Même esprit que
 * {@link ITurretBase} : une interface plutôt qu'un test de type élargi, pour que chaque implémentation
 * reste indépendante des deux autres.
 */
public interface CenterableMachine {
    BlockPos getBlockPos();

    @Nullable Level getLevel();

    @Nullable BlockPos centerPos();

    /** Fixe le centre ({@code null} = redevient sa propre référence) ; doit invalider et resynchroniser le plan. */
    void setCenterPos(@Nullable BlockPos pos);

    /**
     * Position à utiliser pour tout calcul géométrique à la place de {@link #getBlockPos()}. Résolution
     * volontairement SANS effet de bord (pas d'auto-réparation ici) : cette méthode est appelée depuis le
     * calcul du plan lui-même, donc y déclencher un recalcul en cascade bouclerait. L'auto-réparation
     * (centre cassé → on efface {@link #centerPos()}) vit en tout début de {@code serverTick} dans chaque
     * implémentation, avant le premier appel au plan.
     */
    default BlockPos originPos() {
        BlockPos c = centerPos();
        Level lvl = getLevel();
        if (c != null && lvl != null && lvl.getBlockEntity(c) instanceof CenterableMachine) return c;
        return getBlockPos();
    }

    /**
     * Se désigne comme centre du groupe : redevient sa propre référence, puis impose sa position aux
     * voisins directs (4 côtés horizontaux, pas de propagation en chaîne) qui sont eux-mêmes une machine
     * centrable. Dernier bouton pressé gagne : represser sur un satellite le fait redevenir centre à son
     * tour, et ses voisins (y compris l'ancien centre) deviennent alors ses satellites.
     */
    default void setAsCenter() {
        Level lvl = getLevel();
        if (lvl == null) return;
        setCenterPos(null);
        BlockPos self = getBlockPos();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (lvl.getBlockEntity(self.relative(d)) instanceof CenterableMachine neighbor) {
                neighbor.setCenterPos(self);
            }
        }
    }
}
