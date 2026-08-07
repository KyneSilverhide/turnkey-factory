package dev.aurelien.prefab.client;

import dev.aurelien.prefab.PrefabMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Modèle procédural du canon mobile de la tourelle (le socle statique est le modèle de bloc JSON,
 * cf. {@code models/block/turret.json}). Convention BlockEntityRenderer standard, pas d'inversion
 * façon {@code EntityModel} : +Y = haut, coordonnées de {@code addBox}/{@code PartPose} en unités
 * de pixel (1/16 de bloc), origine (0,0,0) = coin bas du bloc. « Avant » du canon = -Z au repos
 * (lacet/tangage à zéro) — cf. {@link TurretRenderer} pour le calcul de la visée vers la cible.
 */
public final class TurretModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "turret"), "main");

    public static final String TURNTABLE = "turntable";
    public static final String BARREL = "barrel";
    public static final String COG = "cog";

    private TurretModel() {}

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Plateau tournant (lacet) : posé SUR le socle statique (y = 16..19/16), pas encastré
        // dedans — une face coplanaire avec le sommet du cube (y=16) provoquerait du z-fighting
        // avec le modèle de bloc statique.
        PartDefinition turntable = root.addOrReplaceChild(TURNTABLE,
                CubeListBuilder.create().texOffs(0, 0).addBox(-5, 0, -5, 10, 3, 10),
                PartPose.offset(8f, 16f, 8f));

        // Support fixe du canon, posé sur le plateau tournant : ne pivote qu'avec le lacet.
        turntable.addOrReplaceChild("mount",
                CubeListBuilder.create().texOffs(0, 13).addBox(-3, 0, -3, 6, 4, 6),
                PartPose.offset(0f, 3f, 0f));

        // Canon (tangage), articulé au sommet du support.
        PartDefinition barrel = turntable.addOrReplaceChild(BARREL,
                CubeListBuilder.create().texOffs(0, 23).addBox(-1.5f, -1.5f, -10f, 3f, 3f, 10f),
                PartPose.offset(0f, 7f, 0f));

        barrel.addOrReplaceChild("muzzle",
                CubeListBuilder.create().texOffs(0, 36).addBox(-2, -2, -12, 4, 4, 2),
                PartPose.ZERO);

        // Engrenage visible : uniquement dessiné pour la variante Create (cf. ITurret#cogAngle),
        // tourne indépendamment du canon (pas un enfant de turntable) — sa rotation vient de la
        // vitesse du réseau cinétique, pas de la visée.
        root.addOrReplaceChild(COG,
                CubeListBuilder.create().texOffs(0, 46).addBox(-7, -1, -7, 14, 1, 14),
                PartPose.offset(8f, 16f, 8f));

        return LayerDefinition.create(mesh, 64, 64);
    }
}
