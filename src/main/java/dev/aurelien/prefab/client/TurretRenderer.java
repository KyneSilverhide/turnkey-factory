package dev.aurelien.prefab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ITurret;
import dev.aurelien.prefab.block.TurretCombat;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Dessine l'arme (affût mobile + canon) par-dessus le socle, dans le bloc du dessus. Le renderer est
 * porté par le BlockEntity du <strong>socle</strong> et non par l'arme : c'est le socle qui détient
 * tout l'état (cible verrouillée, régime du réseau), et l'arme est un bloc sans BlockEntity. Rien
 * n'est dessiné tant qu'aucune arme n'est montée ({@link ITurret#hasWeapon()}). Ne synchronise
 * aucun angle depuis le serveur : {@link ITurret#currentTargetId()} est la seule donnée réseau (cf.
 * sa javadoc), la visée (lacet/tangage) est entièrement recalculée ici à partir de la position live
 * de l'entité ciblée, lissée d'un tick à l'autre pour un mouvement fluide.
 * <p>
 * Générique sur {@code T extends BlockEntity & ITurret} : un seul renderer sert le socle à charbon
 * et le socle cinétique (compat/create) — enregistré deux fois, une fois par
 * {@code BlockEntityType}. Le corps de {@link #render} ne référence jamais directement de type
 * Create : la pièce d'engrenage animée passe par {@link ITurret#cogAngle()}, une méthode
 * d'interface ordinaire, précisément pour que cette classe (toujours chargée, y compris sans
 * Create) n'ait jamais à résoudre {@code KineticBlockEntityRenderer} au chargement.
 */
public class TurretRenderer<T extends BlockEntity & ITurret> implements BlockEntityRenderer<T> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(PrefabMod.MODID, "textures/block/turret_cannon.png");

    /** Vitesse de rotation max, en degrés par tick (~1 tour en une demi-seconde). */
    private static final float MAX_TURN_PER_TICK = 12f;
    private static final float MIN_PITCH = -25f;
    private static final float MAX_PITCH = 65f;

    private final ModelPart turntable;
    private final ModelPart barrel;
    private final ModelPart cog;

    /** État de visée par tourelle (le renderer est un singleton partagé par tous les blocs). */
    private final Map<BlockPos, AimState> aimStates = new HashMap<>();

    private static final class AimState {
        float yaw, pitch, prevYaw, prevPitch;
        long lastTick = Long.MIN_VALUE;
    }

    public TurretRenderer(BlockEntityRendererProvider.Context context) {
        ModelPart root = context.bakeLayer(TurretModel.LAYER);
        this.turntable = root.getChild(TurretModel.TURNTABLE);
        this.barrel = turntable.getChild(TurretModel.BARREL);
        this.cog = root.getChild(TurretModel.COG);
    }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                        int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null || !be.hasWeapon()) return;

        AimState state = aimStates.computeIfAbsent(be.getBlockPos(), p -> new AimState());
        advanceAim(be, level, state);

        float yaw = Mth.rotLerp(partialTick, state.prevYaw, state.yaw);
        float pitch = Mth.lerp(partialTick, state.prevPitch, state.pitch);

        poseStack.pushPose();
        turntable.yRot = yaw * Mth.DEG_TO_RAD;
        barrel.xRot = pitch * Mth.DEG_TO_RAD;

        // packedLight est échantillonné à la position du socle (cube opaque plein, donc lumière ~0
        // dedans) : l'arme est dessinée au-dessus, on réchantillonne au bloc du dessus — celui qu'elle
        // occupe réellement — pour ne pas rendre le modèle tout noir.
        int cannonLight = LevelRenderer.getLightColor(level, be.getBlockPos().above());

        VertexConsumer vertexConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        turntable.render(poseStack, vertexConsumer, cannonLight, packedOverlay);

        // Engrenage Create : NaN = rien à dessiner (tourelle charbon), cf. ITurret#cogAngle. Ne pivote
        // pas avec le canon (pièce indépendante du turntable), sa vitesse vient du réseau cinétique.
        // L'angle est DÉJÀ en radians (Create le convertit lui-même, cf. ITurret#cogAngle) : le
        // repasser par DEG_TO_RAD écrasait la rotation d'un facteur 57 — l'engrenage ne balayait plus
        // que ~6° avant de revenir à zéro au lieu de faire un tour complet.
        float angle = be.cogAngle();
        if (!Float.isNaN(angle)) {
            cog.yRot = angle;
            cog.render(poseStack, vertexConsumer, cannonLight, packedOverlay);
        }

        poseStack.popPose();
    }

    /**
     * Boîte utilisée pour le culling du frustum. Le défaut NeoForge est exactement le cube du bloc
     * (cf. {@code IBlockEntityRendererExtension#getRenderBoundingBox}), or tout ce que dessine ce
     * renderer en déborde largement, et dans les trois directions. Mesuré sur la géométrie de
     * {@link TurretModel} : la bouche du canon est à 17px de l'axe de lacet, donc elle balaye
     * jusqu'à 0.56 bloc au-delà de chaque face latérale du socle, et monte à 1.40 bloc au-dessus du
     * sommet du socle au tangage maximum (+65°). Les dents de l'engrenage Create (2px) sont
     * largement couvertes par là. Les valeurs ci-dessous gardent une marge sur ces deux maxima —
     * sans quoi tout le modèle disparaît d'un coup dès que le cube du socle quitte le champ alors
     * que le canon est encore à l'écran (« pop » en bord d'écran). Une boîte trop large ne coûte
     * qu'un culling un peu moins agressif.
     */
    @Override
    public AABB getRenderBoundingBox(T be) {
        return new AABB(be.getBlockPos()).inflate(1.0, 1.75, 1.0);
    }

    /** Avance la cible d'angle une fois par tick (pas par frame) : {@code partialTick} lisse le reste. */
    private void advanceAim(ITurret be, Level level, AimState state) {
        long gameTime = level.getGameTime();
        if (gameTime == state.lastTick) return;
        state.lastTick = gameTime;
        state.prevYaw = state.yaw;
        state.prevPitch = state.pitch;

        float targetYaw = state.yaw;
        float targetPitch = state.pitch;

        Entity target = level.getEntity(be.currentTargetId());
        if (target instanceof LivingEntity living && living.isAlive()) {
            // Même origine que le tir côté serveur (cf. TurretCombat#muzzlePos), socle compris : le
            // canon doit pointer là où part réellement le tracer, sinon la visée dessinée ment sur la
            // ligne de vue.
            Vec3 origin = Vec3.atBottomCenterOf(be.getBlockPos()).add(0, TurretCombat.MUZZLE_HEIGHT_PX / 16.0, 0);
            Vec3 to = living.getEyePosition(1.0f).subtract(origin);
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            // Convention ModelPart (cf. ModelPart#translateAndRotate -> Quaternionf#rotationZYX) :
            // rotation main droite standard autour de +Y/+X. Canon au repos = -Z (cf. TurretModel),
            // d'où atan2(-dx, -dz) pour amener cet axe local vers la direction (dx, dz) de la cible.
            targetYaw = (float) (Mth.atan2(-to.x, -to.z) * (180.0 / Math.PI));
            targetPitch = Mth.clamp((float) (Mth.atan2(to.y, horiz) * (180.0 / Math.PI)), MIN_PITCH, MAX_PITCH);
        }

        state.yaw = state.prevYaw + Mth.clamp(Mth.wrapDegrees(targetYaw - state.prevYaw), -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        state.pitch = state.prevPitch + Mth.clamp(targetPitch - state.prevPitch, -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
    }
}
