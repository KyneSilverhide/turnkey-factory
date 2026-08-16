package dev.aurelien.prefab.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.block.ControllerBlockEntity;
import dev.aurelien.prefab.block.LamplighterBlockEntity;
import dev.aurelien.prefab.block.LevelerBlockEntity;
import dev.aurelien.prefab.block.StarterHouseBlockEntity;
import dev.aurelien.prefab.block.TexturizerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
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
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = PrefabMod.MODID, value = Dist.CLIENT)
public class GhostRenderer {
    // Marge de sécurité ajoutée au pire cas calculé par renderRadius() (cf. sa javadoc) — dérivée de
    // l'écart observé à l'origine entre le pire cas théorique (~136 blocs) et le rayon retenu (160).
    private static final int RENDER_RADIUS_MARGIN = 24; // blocs
    // Reparcourir tous les block entities des chunks proches est coûteux (jusqu'à ~21×21 chunks à ce rayon) :
    // on ne le refait que toutes les RESCAN_INTERVAL frames au lieu de chaque frame (60+ fois/s) — la
    // position d'un contrôleur/niveleuse ne change de toute façon jamais entre deux poses.
    private static final int RESCAN_INTERVAL = 10;

    private static int scanCooldown = 0;
    private static ClientLevel cachedLevel = null;
    private static List<ControllerBlockEntity> cachedControllers = List.of();
    private static List<LevelerBlockEntity> cachedLevelers = List.of();
    private static List<TexturizerBlockEntity> cachedTexturizers = List.of();
    private static List<LamplighterBlockEntity> cachedLamplighters = List.of();
    private static List<StarterHouseBlockEntity> cachedStarterHouses = List.of();

    /**
     * Variante de {@link RenderType#lines()} qui ignore le tampon de profondeur (test « toujours vrai »,
     * pas d'écriture) : les blocs d'obstruction du contrôleur sont souvent enterrés (fondation, blocs
     * cachés sous terre) et un contour testé en profondeur normale y serait invisible, masqué par le
     * terrain — l'utilisateur ne peut alors jamais localiser le bloc fautif. Ce contour-là reste donc
     * visible EN PERMANENCE à travers tout le reste du monde, comme un rayon X, uniquement pour les
     * indicateurs de problème (jamais pour les contours « tout va bien »).
     */
    private static final RenderType OBSTRUCTION_LINES = new RenderType(
            "prefab_obstruction_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            () -> {
                RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                RenderSystem.lineWidth(2.5F);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_ALWAYS);
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                // Même cible que RenderType.lines() (ITEM_ENTITY_TARGET) : en graphismes « fabuleux », les
                // lignes normales du fantôme sont composées depuis ce tampon à part — sans ce même binding,
                // nos lignes « à travers les murs » dessineraient sur la cible principale et se feraient
                // recouvrir par cette composition plus tard, annulant l'effet.
                if (Minecraft.useShaderTransparency()) {
                    Minecraft.getInstance().levelRenderer.getItemEntityTarget().bindWrite(false);
                }
            },
            () -> {
                RenderSystem.lineWidth(1.0F);
                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
                RenderSystem.enableCull();
                if (Minecraft.useShaderTransparency()) {
                    Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
                }
            }
    ) {};

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
        List<TexturizerBlockEntity> texturizers = cachedTexturizers;
        List<LamplighterBlockEntity> lamplighters = cachedLamplighters;
        List<StarterHouseBlockEntity> starterHouses = cachedStarterHouses;
        if (controllers.isEmpty() && levelers.isEmpty() && texturizers.isEmpty() && lamplighters.isEmpty()
                && starterHouses.isEmpty()) {
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

            // Sol de l'usine : remplace la couche de terrain existante, donc on montre CHAQUE cellule qui
            // va disparaître — vert si c'est du terrain naturel (attendu, contour normal suffit).
            for (BlockPos p : be.floorSafe()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vc, cell, 0.3f, 1.0f, 0.3f, 0.6f);
            }
        }

        for (LevelerBlockEntity be : levelers) {
            if (be.isRemoved()) continue; // le cache n'est rafraîchi que toutes les RESCAN_INTERVAL frames
            // Contour plat à la hauteur cible (pas une grille case par case : jusqu'à (2×64+1)² cellules
            // à portée max, bien trop pour un rendu par bloc chaque frame) — montre la portée choisie
            // tout de suite, comme le texturiseur/l'allumeur de réverbères.
            BlockPos anchor = be.originPos();
            BlockPos originAtTarget = new BlockPos(anchor.getX(), be.targetY(), anchor.getZ());
            renderRangeBoundary(pose, vc, originAtTarget, be.range(), 0.3f, 0.9f, 1.0f, 0.6f);
            // Une boîte rouge par bloc qui sera retiré (fantôme, plafonné côté serveur).
            for (BlockPos p : be.removalPreview()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vc, cell, 1.0f, 0.1f, 0.1f, 1.0f);
            }
        }

        for (TexturizerBlockEntity be : texturizers) {
            if (be.isRemoved()) continue; // le cache n'est rafraîchi que toutes les RESCAN_INTERVAL frames
            // Contour plat au niveau du bloc : montre la portée MAXIMALE choisie tout de suite, sans
            // attendre que le plan mette des cellules en file (utile même à l'arrêt, dès le réglage).
            renderRangeBoundary(pose, vc, be.originPos(), be.radius(), 0.7f, 0.3f, 1.0f, 0.8f);
            // Une boîte violette par cellule de sol à venir retexturer (fantôme, plafonné côté serveur).
            for (BlockPos p : be.preview()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vc, cell, 0.7f, 0.3f, 1.0f, 0.8f);
            }
        }

        for (LamplighterBlockEntity be : lamplighters) {
            if (be.isRemoved()) continue; // le cache n'est rafraîchi que toutes les RESCAN_INTERVAL frames
            renderRangeBoundary(pose, vc, be.originPos(), be.range(), 1.0f, 0.85f, 0.3f, 0.8f);
        }

        for (StarterHouseBlockEntity be : starterHouses) {
            if (be.isRemoved()) continue; // le cache n'est rafraîchi que toutes les RESCAN_INTERVAL frames
            // Une seule boîte, sans indicateur d'obstruction : la maison écrase tout ce qui se trouve
            // dans son emprise (cf. StarterHouseBlockEntity#build). Le fantôme répond donc à « qu'est-ce
            // qui va disparaître », pas à « est-ce que ça passe » — d'où la teinte d'avertissement,
            // la même que celle du texte de l'interface.
            LevelRenderer.renderLineBox(pose, vc, be.previewBox(), 1.0f, 0.75f, 0.25f, 0.9f);
        }

        // Flush du tampon "vc" AVANT de démarrer le second (RenderType différent) : getBuffer() sur un
        // nouveau type shared termine implicitement le batch précédent, donc réutiliser "vc" après ce
        // point planterait (BufferBuilder déjà finalisé). D'où cette passe forcément en dernier.
        buffers.endBatch(RenderType.lines());

        // Deuxième passe, tampon à part : les indicateurs de PROBLÈME du contrôleur (obstruction réelle,
        // sol à remplacer suspect) en rouge, rendus à travers le terrain — sans ça, un bloc d'obstruction
        // enterré sous une couche de terre est strictement invisible et le joueur ne peut jamais le
        // localiser (cf. retour utilisateur : « il indique des obstructions mais je ne les vois pas »).
        VertexConsumer vcThrough = buffers.getBuffer(OBSTRUCTION_LINES);
        for (ControllerBlockEntity be : controllers) {
            if (be.isRemoved()) continue;
            for (BlockPos p : be.collisions()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vcThrough, cell, 1.0f, 0.1f, 0.1f, 1.0f);
            }
            for (BlockPos p : be.floorUnsafe()) {
                AABB cell = new AABB(p).deflate(0.02);
                LevelRenderer.renderLineBox(pose, vcThrough, cell, 1.0f, 0.1f, 0.1f, 1.0f);
            }
        }
        buffers.endBatch(OBSTRUCTION_LINES);

        pose.popPose();
    }

    /**
     * Parcourt une seule fois les block entities des chunks proches (au lieu de deux scans quasi
     * identiques) et met à jour les deux caches. Appelé au plus une fois toutes les
     * {@value #RESCAN_INTERVAL} frames (cf. {@link #onRenderLevel}), jamais à chaque frame.
     */
    /**
     * Rayon de recherche (autour de la caméra) des block entities dont on doit garder ou recalculer le
     * fantôme. Doit couvrir le pire cas <strong>actuellement configuré</strong> (cf.
     * {@link dev.aurelien.prefab.config.PrefabServerConfig}, réglable sans redémarrage) : un contrôleur
     * avec dimensions ET décalage au maximum (cf. {@link ControllerBlockEntity#maxHorizontal()}/
     * {@code maxHeight()}/{@code OFFSET_MAX}) peut avoir un coin de bâtiment loin de son propre bloc, et
     * une niveleuse/un texturiseur/un allumeur de réverbères agit jusqu'à sa portée/son rayon maximum
     * autour de son propre bloc — sans cette marge, le fantôme disparaîtrait quand le joueur se tient
     * au bord de la zone. Recalculé à chaque rescan plutôt que mis en cache : coût négligeable face à
     * {@link #RESCAN_INTERVAL}, et reste juste si la config est modifiée en jeu.
     */
    private static int renderRadius() {
        int horizontalReach = ControllerBlockEntity.maxHorizontal() + ControllerBlockEntity.OFFSET_MAX;
        int verticalReach = ControllerBlockEntity.maxHeight() + ControllerBlockEntity.OFFSET_MAX;
        double controllerWorstCase = Math.sqrt(2.0 * horizontalReach * horizontalReach + (double) verticalReach * verticalReach);

        int machineWorstCase = Math.max(LevelerBlockEntity.maxRange(),
                Math.max(TexturizerBlockEntity.maxRadius(), LamplighterBlockEntity.maxRange()));

        return (int) Math.ceil(Math.max(controllerWorstCase, machineWorstCase)) + RENDER_RADIUS_MARGIN;
    }

    private static void rescan(ClientLevel level, Vec3 cam) {
        List<ControllerBlockEntity> controllers = new ArrayList<>();
        List<LevelerBlockEntity> levelers = new ArrayList<>();
        List<TexturizerBlockEntity> texturizers = new ArrayList<>();
        List<LamplighterBlockEntity> lamplighters = new ArrayList<>();
        List<StarterHouseBlockEntity> starterHouses = new ArrayList<>();
        int camChunkX = Mth.floor(cam.x) >> 4;
        int camChunkZ = Mth.floor(cam.z) >> 4;
        int renderRadius = renderRadius();
        int chunkRadius = (renderRadius >> 4) + 1;
        double radiusSqr = (double) renderRadius * renderRadius;

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(camChunkX + dx, camChunkZ + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    // Distance à la boîte RÉSERVÉE (bâtiment + marge), pas au bloc contrôleur lui-même :
                    // sur un grand bâtiment, le joueur peut être à côté d'un mur mais à plus de renderRadius()
                    // du contrôleur — le fantôme doit quand même rester visible tant qu'on est près de la
                    // structure qu'il matérialise.
                    if (be instanceof ControllerBlockEntity controller
                            && distToBoxSqr(controller.reservedBox(), cam) <= radiusSqr) {
                        controllers.add(controller);
                    } else if (be instanceof LevelerBlockEntity leveler
                            && leveler.getBlockPos().distToCenterSqr(cam.x, cam.y, cam.z) <= radiusSqr) {
                        levelers.add(leveler);
                    } else if (be instanceof TexturizerBlockEntity texturizer
                            && texturizer.getBlockPos().distToCenterSqr(cam.x, cam.y, cam.z) <= radiusSqr) {
                        texturizers.add(texturizer);
                    } else if (be instanceof LamplighterBlockEntity lamplighter
                            && lamplighter.getBlockPos().distToCenterSqr(cam.x, cam.y, cam.z) <= radiusSqr) {
                        lamplighters.add(lamplighter);
                    } else if (be instanceof StarterHouseBlockEntity starterHouse
                            && starterHouse.getBlockPos().distToCenterSqr(cam.x, cam.y, cam.z) <= radiusSqr) {
                        starterHouses.add(starterHouse);
                    }
                }
            }
        }
        cachedControllers = controllers;
        cachedLevelers = levelers;
        cachedTexturizers = texturizers;
        cachedLamplighters = lamplighters;
        cachedStarterHouses = starterHouses;
    }

    /**
     * Contour plat (carré, pas cercle : un seul {@link LevelRenderer#renderLineBox} avec une hauteur
     * dégénérée suffit à obtenir un rectangle-fil plutôt qu'un pavé plein — pas besoin d'émettre des
     * sommets à la main) au niveau Y du bloc, bornant la portée choisie. C'est le carré englobant du
     * disque réellement travaillé (distance euclidienne ≤ {@code range}), pas le disque exact : une
     * approximation suffisante pour « jusqu'où ça va », et {@code +1} sur les bords max car les
     * coordonnées de bloc nomment le coin MIN de la cellule.
     */
    private static void renderRangeBoundary(PoseStack pose, VertexConsumer vc, BlockPos origin, int range,
                                             float r, float g, float b, float a) {
        int y = origin.getY();
        AABB box = new AABB(
                origin.getX() - range, y, origin.getZ() - range,
                origin.getX() + range + 1, y + 0.02, origin.getZ() + range + 1
        );
        LevelRenderer.renderLineBox(pose, vc, box, r, g, b, a);
    }

    /** Distance au carré entre {@code point} et le point le plus proche de {@code box} (0 si à l'intérieur). */
    private static double distToBoxSqr(AABB box, Vec3 point) {
        double dx = Math.max(0, Math.max(box.minX - point.x, point.x - box.maxX));
        double dy = Math.max(0, Math.max(box.minY - point.y, point.y - box.maxY));
        double dz = Math.max(0, Math.max(box.minZ - point.z, point.z - box.maxZ));
        return dx * dx + dy * dy + dz * dz;
    }
}
