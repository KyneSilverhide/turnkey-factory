package dev.aurelien.prefab.client;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.TurretCombat;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Modèle procédural de la partie mobile de la tourelle (le socle statique est le modèle de bloc
 * JSON, cf. {@code models/block/turret.json}). Convention BlockEntityRenderer standard, pas
 * d'inversion façon {@code EntityModel} : +Y = haut, coordonnées de {@code addBox}/{@code PartPose}
 * en unités de pixel (1/16 de bloc), origine (0,0,0) = coin bas du bloc. « Avant » du canon = -Z au
 * repos (lacet/tangage à zéro) — cf. {@link TurretRenderer} pour le calcul de la visée.
 * <p>
 * Silhouette de mitrailleuse sur affût, et non plus un simple tube posé sur un cube : plaque de
 * base, deux joues verticales formant le berceau, boîte à munitions à l'arrière, puis un ensemble
 * mobile en tangage (boîtier de culasse + couvre-alimentation + manchon de refroidissement ajouré +
 * canon + frein de bouche + guidon). Le tout reste volontairement en gros volumes lisibles à
 * l'échelle Minecraft — la lecture « mitrailleuse » vient de l'étagement des diamètres le long du
 * canon, pas d'un détail fin qui bouillerait à distance.
 * <p>
 * Deux pivots seulement, comme avant : {@link #TURNTABLE} (lacet) et son enfant {@link #BARREL}
 * (tangage). Tout ce qui doit suivre la visée est rattaché à l'un des deux ; {@link #COG} est
 * indépendant (entraîné par le réseau cinétique, cf. {@code ITurret#cogAngle}).
 * <p>
 * Empilements à fleur (le dessous d'une pièce exactement sur le dessus d'une autre) : sans danger
 * ici, les deux faces coplanaires sont opposées, donc celle du dessous est éliminée par le
 * backface culling. Le z-fighting n'apparaît qu'entre deux faces coplanaires orientées dans le
 * MÊME sens — c'est ce piège-là qui est traité sur {@link #COG} plus bas.
 */
public final class TurretModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "turret"), "main");

    public static final String TURNTABLE = "turntable";
    public static final String BARREL = "barrel";
    public static final String COG = "cog";

    /** Hauteur du plateau tournant : il est posé SUR le socle statique, jamais encastré dedans. */
    private static final float TURNTABLE_Y = 16f;

    private TurretModel() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ----- Affût (lacet) : posé SUR le socle statique (y=16), jamais encastré dedans. -----
        PartDefinition turntable = root.addOrReplaceChild(TURNTABLE,
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, 0, -6, 12, 2, 12)     // plaque de base
                        .texOffs(76, 0).addBox(-4, 2, -4, 8, 2, 8),     // couronne surélevée
                PartPose.offset(8f, TURNTABLE_Y, 8f));

        // Joues du berceau : le canon bascule entre les deux. Écart de 0.5px avec le boîtier de
        // culasse (±2.5) pour qu'il pivote sans jamais s'interpénétrer avec elles.
        turntable.addOrReplaceChild("cheek_left",
                CubeListBuilder.create().texOffs(0, 16).addBox(-5, 4, -3, 2, 5, 6), PartPose.ZERO);
        turntable.addOrReplaceChild("cheek_right",
                CubeListBuilder.create().texOffs(16, 16).addBox(3, 4, -3, 2, 5, 6), PartPose.ZERO);

        // Boîte à munitions montée sur l'affût (elle suit le lacet, pas le tangage — c'est la
        // caisse, pas l'arme). Reculée à z=5 pour laisser 1px de dégagement derrière la culasse.
        turntable.addOrReplaceChild("ammo_box",
                CubeListBuilder.create().texOffs(32, 16).addBox(-3, 4, 5, 6, 5, 5), PartPose.ZERO);

        // ----- Ensemble mobile (tangage), articulé entre les joues. -----
        PartDefinition barrel = turntable.addOrReplaceChild(BARREL,
                CubeListBuilder.create()
                        .texOffs(48, 0).addBox(-2.5f, -2.5f, -4, 5, 5, 8)    // boîtier de culasse
                        .texOffs(54, 16).addBox(-2, -2, -11, 4, 4, 7)        // manchon ajouré
                        .texOffs(0, 32).addBox(-1, -1, -15, 2, 2, 4)         // canon nu
                        .texOffs(12, 32).addBox(-1.5f, -1.5f, -17, 3, 3, 2), // frein de bouche
                // Le pivot de tangage EST l'axe du canon : on le dérive de la hauteur de bouche
                // partagée avec le serveur plutôt que de reposer un 23 en dur ici. Déplacer le
                // canon sans déplacer l'origine des tirs ferait partir les tracers à côté du modèle
                // (cf. TurretCombat#muzzlePos, qui sert aussi au test de ligne de vue).
                PartPose.offset(0f, TurretCombat.MUZZLE_HEIGHT_PX - TURNTABLE_Y, 0f));

        // Couvre-alimentation sur la culasse et guidon sur le manchon : les deux petits volumes qui
        // font lire « arme » plutôt que « tuyau », posés à fleur (cf. javadoc de classe).
        barrel.addOrReplaceChild("feed_cover",
                CubeListBuilder.create().texOffs(76, 16).addBox(-2, 2.5f, -2, 4, 2, 5), PartPose.ZERO);
        barrel.addOrReplaceChild("sight",
                CubeListBuilder.create().texOffs(22, 32).addBox(-0.5f, 2, -8, 1, 2, 1), PartPose.ZERO);

        // ----- Engrenage cinétique (variante Create uniquement, cf. ITurret#cogAngle). -----
        // Au CENTRE du bloc (y=8), pas sur le dessus : il formerait sinon une couronne au même
        // endroit que l'affût et disparaîtrait sous l'arme. Ici il ceinture le corps du bloc, comme
        // la meule (millstone) — la lecture attendue d'un engrenage qui s'engrène avec un Large
        // Cogwheel voisin (cf. TurretCreateBlock, ICogWheel).
        //
        // Le moyeu est en pratique invisible (entièrement contenu dans le cube opaque du modèle de
        // bloc, donc éliminé au test de profondeur) : seules les dents, qui dépassent de 2px des
        // faces latérales, sont vues. Il reste modélisé pour que les dents tiennent à quelque chose.
        PartDefinition cog = root.addOrReplaceChild(COG,
                CubeListBuilder.create().texOffs(0, 48).addBox(-6, -2, -6, 12, 4, 12),
                PartPose.offset(8f, 8f, 8f));

        // 8 dents espacées de 45° = les 4 dents dans l'axe, plus la même pièce tournée d'un huitième
        // de tour. Avec un rayon de 10, une dent ne sort d'une face que tant qu'elle est à ±37° de
        // l'axe de cette face : à 45° d'écart il y a toujours au moins une dent dehors, alors qu'avec
        // seulement 4 dents (90° d'écart) la couronne clignoterait — 16° par tour sans aucune dent
        // visible.
        cog.addOrReplaceChild("teeth_axis", cogTeeth(), PartPose.ZERO);
        cog.addOrReplaceChild("teeth_diagonal", cogTeeth(),
                PartPose.offsetAndRotation(0f, 0f, 0f, 0f, Mth.PI / 4f, 0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    /**
     * Les 4 dents alignées sur les axes, réutilisé tel quel pour les 4 dents diagonales (même pièce,
     * tournée de 45° par son {@code PartPose}). Aucune face ne tombe pile sur ±8, le plan des faces
     * du cube statique : chaque dent le traverse (5→10) au lieu de s'y arrêter, sinon z-fighting.
     * Toutes partagent un seul aplat de texture (cf. {@code tools/gen_turret_model_texture.py}),
     * elles sont trop petites pour mériter une région d'UV par face.
     */
    private static CubeListBuilder cogTeeth() {
        return CubeListBuilder.create().texOffs(26, 32)
                .addBox(-2, -2, -10, 4, 4, 5)
                .addBox(-2, -2, 5, 4, 4, 5)
                .addBox(5, -2, -2, 5, 4, 4)
                .addBox(-10, -2, -2, 5, 4, 4);
    }
}
