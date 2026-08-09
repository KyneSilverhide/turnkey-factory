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
 * Modèle procédural de l'arme de tourelle. Il est dessiné par le renderer du <em>socle</em> (cf.
 * {@link TurretRenderer}), donc <strong>toutes les coordonnées ci-dessous sont relatives au bloc
 * socle</strong> : l'arme occupe le bloc au-dessus, d'où un plateau tournant à y=16 et un canon qui
 * monte au-delà. Convention BlockEntityRenderer standard, pas d'inversion façon {@code EntityModel} :
 * +Y = haut, coordonnées de {@code addBox}/{@code PartPose} en unités de pixel (1/16 de bloc),
 * origine (0,0,0) = coin bas du socle. « Avant » du canon = -Z au repos (lacet/tangage à zéro) — cf.
 * {@link TurretRenderer} pour le calcul de la visée.
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

    /**
     * Seconde arme (lance-flammes), dans la <strong>même</strong> layer et sur la même texture que la
     * mitrailleuse plutôt que dans une layer à part : le renderer choisit le sous-arbre selon l'arme
     * montée (cf. {@link TurretRenderer}), ce qui lui évite un second {@code bakeLayer}, un second
     * fichier de texture et un second {@code VertexConsumer} dans la même frame. Les deux sous-arbres
     * sont bakés en permanence — quelques dizaines de quads inutilisés, contre une vraie duplication
     * de plomberie.
     */
    public static final String TURNTABLE_FLAME = "turntable_flame";
    public static final String BARREL_FLAME = "barrel_flame";

    /** Hauteur du plateau tournant : il est posé SUR le socle statique, jamais encastré dedans —
     *  c'est-à-dire au plancher du bloc arme, qui commence au sommet du socle. */
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

        flamethrower(root);

        // ----- Engrenage cinétique (variante Create uniquement, cf. ITurret#cogAngle). -----
        // Au CENTRE du socle (y=8), pas sur son dessus : il formerait sinon une couronne au même
        // endroit que l'affût et disparaîtrait sous l'arme. Ici il ceinture le corps du socle, comme
        // la meule (millstone) — la lecture attendue d'un engrenage qui s'engrène avec un Large
        // Cogwheel voisin (cf. TurretBaseCreateBlock, ICogWheel), et c'est bien le socle qui est le
        // membre du réseau cinétique.
        //
        // Le moyeu est en pratique invisible (entièrement contenu dans le cube opaque du socle, donc
        // éliminé au test de profondeur) : seules les dents, qui dépassent de 2px des faces
        // latérales, sont vues. Il reste modélisé pour que les dents tiennent à quelque chose.
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
     * Sous-arbre du lance-flammes ({@link #TURNTABLE_FLAME} / {@link #BARREL_FLAME}), alternative à
     * celui de la mitrailleuse — un seul des deux est dessiné, selon l'arme montée.
     * <p>
     * <strong>L'affût est volontairement identique</strong> (mêmes boîtes, mêmes UV que la plaque,
     * la couronne et les joues de la mitrailleuse) : c'est physiquement le même berceau, monté sur le
     * même socle, et lui donner une autre silhouette ferait croire à deux machines différentes. Tout
     * ce qui change est ce qui doit changer : la caisse à munitions cède la place à une bonbonne, et
     * le canon étagé à une buse courte et évasée. La lecture « lance-flammes » vient de là — un tube
     * gros et court, contre un tube fin et long.
     * <p>
     * Le pivot de tangage reprend {@link TurretCombat#MUZZLE_HEIGHT_PX} au même titre que l'autre
     * arme : les deux doivent tirer depuis exactement la hauteur que le serveur utilise pour son test
     * de ligne de vue, sinon le jet dessiné part d'ailleurs que le tir réel.
     */
    private static void flamethrower(PartDefinition root) {
        PartDefinition turntable = root.addOrReplaceChild(TURNTABLE_FLAME,
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-6, 0, -6, 12, 2, 12)     // plaque de base (partagée)
                        .texOffs(76, 0).addBox(-4, 2, -4, 8, 2, 8),     // couronne surélevée (partagée)
                PartPose.offset(8f, TURNTABLE_Y, 8f));

        turntable.addOrReplaceChild("cheek_left",
                CubeListBuilder.create().texOffs(0, 16).addBox(-5, 4, -3, 2, 5, 6), PartPose.ZERO);
        turntable.addOrReplaceChild("cheek_right",
                CubeListBuilder.create().texOffs(16, 16).addBox(3, 4, -3, 2, 5, 6), PartPose.ZERO);

        // Bonbonne de carburant, à la place de la caisse à munitions : plus haute et plus large
        // qu'elle, parce que c'est la pièce qui doit dire « ça marche à la lave » d'un coup d'œil.
        // Suit le lacet et non le tangage — elle est sur l'affût, pas sur l'arme.
        turntable.addOrReplaceChild("fuel_tank",
                CubeListBuilder.create().texOffs(70, 82).addBox(-3.5f, 3, 4, 7, 7, 6), PartPose.ZERO);

        PartDefinition barrel = turntable.addOrReplaceChild(BARREL_FLAME,
                CubeListBuilder.create()
                        .texOffs(0, 82).addBox(-3, -3, -3, 6, 6, 7)      // corps de vanne
                        .texOffs(28, 82).addBox(-2, -2, -9, 4, 4, 6)     // col de la buse
                        .texOffs(50, 82).addBox(-3, -3, -12, 6, 6, 3),   // pavillon évasé
                PartPose.offset(0f, TurretCombat.MUZZLE_HEIGHT_PX - TURNTABLE_Y, 0f));

        // Veilleuse au-dessus de la buse : le petit volume qui explique d'où vient l'allumage.
        barrel.addOrReplaceChild("igniter",
                CubeListBuilder.create().texOffs(98, 82).addBox(-0.5f, 2.5f, -8, 1, 2, 2), PartPose.ZERO);
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
