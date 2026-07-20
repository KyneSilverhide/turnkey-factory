package dev.aurelien.prefab.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlockEntity;
import dev.aurelien.prefab.block.LevelerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = PrefabMod.MODID, value = Dist.CLIENT)
public class GhostRenderer {
    // Distance depuis laquelle on cherche des contrôleurs/niveleuses. Doit couvrir le pire cas : un
    // contrôleur avec dimensions ET décalage du fantôme au maximum (cf. ControllerBlockEntity.MAX_HORIZONTAL/
    // MAX_HEIGHT/OFFSET_MAX) peut avoir un coin de bâtiment à ~150 blocs de la position du bloc contrôleur
    // lui-même — sans cette marge, le fantôme disparaîtrait quand le joueur se tient au bord du bâtiment.
    private static final int RENDER_RADIUS = 160; // blocs
    // Reparcourir tous les block entities des chunks proches est coûteux (jusqu'à ~21×21 chunks à ce rayon) :
    // on ne le refait que toutes les RESCAN_INTERVAL frames au lieu de chaque frame (60+ fois/s) — la
    // position d'un contrôleur/niveleuse ne change de toute façon jamais entre deux poses.
    private static final int RESCAN_INTERVAL = 10;

    private static int scanCooldown = 0;
    private static ClientLevel cachedLevel = null;
    private static List<ControllerBlockEntity> cachedControllers = List.of();
    private static List<LevelerBlockEntity> cachedLevelers = List.of();

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();

        if (level != cachedLevel || --scanCooldown <= 0) {
            scanCooldown = RESCAN_INTERVAL;
            cachedLevel = level;
            rescan(level, cam);
        }

        List<ControllerBlockEntity> controllers = cachedControllers;
        List<LevelerBlockEntity> levelers = cachedLevelers;
        if (controllers.isEmpty() && levelers.isEmpty()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        for (ControllerBlockEntity be : controllers) {
            if (be.isRemoved()) continue; // le cache n'est rafraîchi que toutes les RESCAN_INTERVAL frames
            boolean obstructed = be.isObstructed();
            float r = obstructed ? 1.0f : 0.25f;
            float g = obstructed ? 0.25f : 1.0f;

            // Zone de sécurité réservée (déco) : contour cyan discret.
            LevelRenderer.renderLineBox(pose, vc, be.reservedBox(), 0.3f, 0.7f, 1.0f, 0.5f);
            // Bâtiment (coque) : vert si libre, rouge si obstrué.
            LevelRenderer.renderLineBox(pose, vc, be.innerBox(), r, g, 0.3f, 0.9f);

            for (BlockPos p : be.collisions()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vc, cell, 1.0f, 0.1f, 0.1f, 1.0f);
            }

            // Sol de l'usine : remplace la couche de terrain existante, donc on montre CHAQUE cellule qui
            // va disparaître — vert si c'est du terrain naturel (attendu), rouge sinon (probablement posé
            // par un joueur, cf. NaturalTerrain.isNaturalGround).
            for (BlockPos p : be.floorSafe()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vc, cell, 0.3f, 1.0f, 0.3f, 0.6f);
            }
            for (BlockPos p : be.floorUnsafe()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vc, cell, 1.0f, 0.1f, 0.1f, 1.0f);
            }
        }

        for (LevelerBlockEntity be : levelers) {
            if (be.isRemoved()) continue; // le cache n'est rafraîchi que toutes les RESCAN_INTERVAL frames
            // Grille PLATE à la hauteur cible (pas un volume) : une case fine par colonne de l'empreinte —
            // au-dessus de cette grille = retiré, en dessous = remblayé.
            int y = be.targetY();
            for (int x = be.footprintMinX(); x <= be.footprintMaxX(); x++) {
                for (int z = be.footprintMinZ(); z <= be.footprintMaxZ(); z++) {
                    AABB cell = new AABB(x, y, z, x + 1, y + 0.02, z + 1);
                    LevelRenderer.renderLineBox(pose, vc, cell, 0.3f, 0.9f, 1.0f, 0.6f);
                }
            }
            // Une boîte rouge par bloc qui sera retiré (fantôme, plafonné côté serveur).
            for (BlockPos p : be.removalPreview()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vc, cell, 1.0f, 0.1f, 0.1f, 1.0f);
            }
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /**
     * Parcourt une seule fois les block entities des chunks proches (au lieu de deux scans quasi
     * identiques) et met à jour les deux caches. Appelé au plus une fois toutes les
     * {@value #RESCAN_INTERVAL} frames (cf. {@link #onRenderLevel}), jamais à chaque frame.
     */
    private static void rescan(ClientLevel level, Vec3 cam) {
        List<ControllerBlockEntity> controllers = new ArrayList<>();
        List<LevelerBlockEntity> levelers = new ArrayList<>();
        int camChunkX = Mth.floor(cam.x) >> 4;
        int camChunkZ = Mth.floor(cam.z) >> 4;
        int chunkRadius = (RENDER_RADIUS >> 4) + 1;
        double radiusSqr = (double) RENDER_RADIUS * RENDER_RADIUS;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(camChunkX + dx, camChunkZ + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    // Distance à la boîte RÉSERVÉE (bâtiment + marge), pas au bloc contrôleur lui-même :
                    // sur un grand bâtiment, le joueur peut être à côté d'un mur mais à plus de RENDER_RADIUS
                    // du contrôleur — le fantôme doit quand même rester visible tant qu'on est près de la
                    // structure qu'il matérialise.
                    if (be instanceof ControllerBlockEntity controller
                            && distToBoxSqr(controller.reservedBox(), cam) <= radiusSqr) {
                        controllers.add(controller);
                    } else if (be instanceof LevelerBlockEntity leveler
                            && leveler.getBlockPos().distToCenterSqr(cam.x, cam.y, cam.z) <= radiusSqr) {
                        levelers.add(leveler);
                    }
                }
            }
        }
        cachedControllers = controllers;
        cachedLevelers = levelers;
    }

    /** Distance au carré entre {@code point} et le point le plus proche de {@code box} (0 si à l'intérieur). */
    private static double distToBoxSqr(AABB box, Vec3 point) {
        double dx = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }
}
