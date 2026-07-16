package dev.aurelien.prefab.build;

import dev.aurelien.prefab.compat.CreateCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compose un bâtiment rectangulaire à partir de modules réutilisables.
 *
 * <p>Décomposition verticale (absorbe la hauteur variable) : sol → soubassement → corps (module
 * répété, fenêtres pavées) → corniche → toit. Décomposition horizontale : angles fixes + mur répété
 * + features insérées (porte centrée façade avant, fenêtres espacées). Aucun préfab par dimension :
 * tout est pavé/composé à la volée.</p>
 */
public final class BuildPlanner {
    private BuildPlanner() {}

    public record Placement(BlockPos pos, BlockState state) {}

    // Porte (façade avant ET arrière — les deux murs le long de l'axe de {@code facing})
    private static final int DOOR_HALF_WIDTH = 1; // 3 blocs de large
    private static final int DOOR_HEIGHT = 4;     // 4 blocs de haut
    // Fenêtres (pavage horizontal et vertical du corps)
    private static final int WINDOW_H_SPACING = 4;
    private static final int WINDOW_V_SPACING = 4;
    private static final int WINDOW_HEIGHT = 2;

    /**
     * @param free rempli avec les positions purement décoratives (colonnes, avant-toit, parapet/passerelle,
     *             aérations/lucarnes de toit) : {@link dev.aurelien.prefab.block.ControllerBlockEntity} les
     *             exempte de coût en matériaux, contrairement à la coque (murs/sol/toit) elle-même.
     */
    public static Map<BlockPos, BlockState> planMap(BlockPos min, BlockPos max, Direction facing,
                                                    BuildStyle style, RoofType roofType, BlockPos controller, Theme theme,
                                                    Set<BlockPos> free) {
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = classify(pos, min, max, facing, style, roofType, air);
                    if (st != null) {                    // null = cellule intérieure préservée (non touchée)
                        map.put(pos, st);
                    }
                }
            }
        }

        if (roofType == RoofType.PITCHED) {
            // Pas de plafond plat (cf. classify) : on ouvre le volume jusqu'au toit et on traverse de
            // poutres (au niveau de l'ancien plafond) pour accrocher les lanternes.
            addCeilingBeams(map, min, max, style);
            addPitchedRoof(map, min, max, style); // « chapeau » à deux pentes au-dessus des murs
            pitchedSkylights(map, min, max, style); // lucarnes vitrées intégrées dans la pente
        }

        ExteriorDecorator.decorate(map, min, max, facing, roofType, style, controller, theme, free); // extérieur : colonnes, caisses, toit…
        interiorCageLamps(map, min, max, theme); // éclairage intérieur (Create Deco), thémé pierre/brique

        if (CreateCompat.isLoaded()) {
            // Remplace tous les glass panes par le weathered iron window pane de Create
            BlockState createPane = CreateCompat.windowPane();
            if (!createPane.isAir()) {
                map.replaceAll((p, st) -> st.getBlock() == Blocks.GLASS_PANE ? createPane : st);
            }
        }

        return map;
    }

    /**
     * Nombre de blocs que le toit occupe AU-DESSUS de {@code maxY} (0 pour un toit plat). Sert à étendre
     * vers le haut la zone réservée (fantôme, collision, nettoyage) afin d'englober l'apex du toit pentu.
     */
    public static int roofTopExtension(BlockPos min, BlockPos max, RoofType roofType) {
        if (roofType != RoofType.PITCHED) return 0;
        int spanX = max.getX() - min.getX();
        int spanZ = max.getZ() - min.getZ();
        int half = Math.min(spanX, spanZ) / 2;
        return half + 2; // apex tuile = maxY+1+half ; le parapet pignon le coiffe d'un bloc → +2
    }

    /**
     * Toit à deux pentes, look « usine » :
     * <ul>
     *   <li>le faîte court le long du PLUS GRAND côté ; les pentes (escaliers de tuiles) descendent vers
     *       les deux longs murs et <b>débordent d'un bloc</b> au-dessus des piliers (avant-toit) ;</li>
     *   <li>aux deux extrémités du faîte, un <b>mur-parapet</b> (façade/arrière) monte d'un bloc AU-DESSUS
     *       de la ligne des tuiles, masquant la tranche du toit (silhouette industrielle, pas maison) ;</li>
     *   <li>pas de plafond plat : le volume est ouvert, traversé de poutres (cf. {@link #addCeilingBeams}).</li>
     * </ul>
     */
    private static void addPitchedRoof(Map<BlockPos, BlockState> map, BlockPos min, BlockPos max, BuildStyle style) {
        int minX = min.getX(), maxX = max.getX();
        int maxY = max.getY();
        int minZ = min.getZ(), maxZ = max.getZ();
        int spanX = maxX - minX, spanZ = maxZ - minZ;
        boolean ridgeAlongX = spanX >= spanZ; // faîte le long du plus grand côté

        BlockState ridge = style.roofRidge().pick(min);
        if (ridgeAlongX) {
            int half = spanZ / 2, centerZ = (minZ + maxZ) / 2;
            // Tuiles + avant-toit pour les rangées INTERNES (les extrémités du faîte sont des parapets).
            for (int x = minX + 1; x < maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int k = Math.abs(z - centerZ);
                    Direction up = (z < centerZ) ? Direction.SOUTH : Direction.NORTH;
                    placeTile(map, x, z, maxY + 1 + (half - k), k, up, style, ridge);
                }
                eave(map, x, minZ - 1, maxY, Direction.SOUTH, style);
                eave(map, x, maxZ + 1, maxY, Direction.NORTH, style);
            }
            // Mur-parapet à chaque extrémité du faîte, prolongé jusqu'aux bords (couvre l'avant-toit).
            gable(map, minX, true, minZ, maxZ, centerZ, half, maxY, style, Direction.WEST);
            gable(map, maxX, true, minZ, maxZ, centerZ, half, maxY, style, Direction.EAST);
        } else {
            int half = spanX / 2, centerX = (minX + maxX) / 2;
            for (int z = minZ + 1; z < maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int k = Math.abs(x - centerX);
                    Direction up = (x < centerX) ? Direction.EAST : Direction.WEST;
                    placeTile(map, x, z, maxY + 1 + (half - k), k, up, style, ridge);
                }
                eave(map, minX - 1, z, maxY, Direction.EAST, style);
                eave(map, maxX + 1, z, maxY, Direction.WEST, style);
            }
            gable(map, minZ, false, minX, maxX, centerX, half, maxY, style, Direction.NORTH);
            gable(map, maxZ, false, minX, maxX, centerX, half, maxY, style, Direction.SOUTH);
        }
    }

    /** Tuile de pente : escalier orienté face au faîte ({@code up}), ou bloc de faîtage plein au sommet. */
    private static void placeTile(Map<BlockPos, BlockState> map, int x, int z, int topY,
                                  int k, Direction up, BuildStyle style, BlockState ridge) {
        BlockPos top = new BlockPos(x, topY, z);
        if (k == 0) {
            map.put(top, ridge);
        } else {
            map.put(top, style.roofStair().pick(top)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, up)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM));
        }
    }

    /** Tuile d'avant-toit : un escalier hors emprise, posé une marche plus bas que le bord de la pente. */
    private static void eave(Map<BlockPos, BlockState> map, int x, int z, int maxY, Direction up, BuildStyle style) {
        BlockPos p = new BlockPos(x, maxY, z); // une marche sous la tuile de bord (qui est à maxY+1)
        map.put(p, style.roofStair().pick(p)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, up)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM));
    }

    /**
     * Mur-parapet triangulaire d'extrémité. Grille de barreaux de fer dans la zone centrale haute,
     * avec au moins 3 blocs de marge de mur de chaque côté latéral ({@code d ≤ half-3}) et 1 bloc
     * de marge en haut (sous le parapet). La grille peut se réduire à 1-2 blocs sur les petits bâtiments.
     */
    private static void gable(Map<BlockPos, BlockState> map, int fixed, boolean alongZ,
                              int lo, int hi, int center, int half, int maxY, BuildStyle style, Direction out) {
        BlockState pane = style.window().pick(new BlockPos(fixed, maxY, center));
        for (int c = lo - 1; c <= hi + 1; c++) {
            int d = Math.abs(c - center);
            int topY = maxY + 1 + (half - d);
            int parapetTop = topY + 1;
            int yLow = (c < lo || c > hi) ? maxY : maxY + 1;
            int windowY = (d <= half - 2 && d % 2 == 0) ? maxY + 2 : Integer.MIN_VALUE;
            for (int y = yLow; y <= parapetTop; y++) {
                BlockPos p = alongZ ? new BlockPos(fixed, y, c) : new BlockPos(c, y, fixed);
                if (y == windowY) {
                    map.put(p, pane);
                } else if (d <= Math.min(half - 3, 4) && y >= maxY + 4 && y < Math.min(parapetTop - 1, maxY + 9)) {
                    // Grille iron bars : max 9 blocs de large (d≤4) × max 5 rangées (maxY+4..+8)
                    map.put(p, Blocks.IRON_BARS.defaultBlockState());
                } else {
                    map.put(p, style.wall().pick(p));
                }
            }
        }
    }

    /**
     * Poutres de plafond (toit pentu) : au niveau de l'ancien plafond plat ({@code maxY}), des solives qui
     * traversent le petit côté, espacées de 4 le long du grand côté — alignées sur la grille des lanternes
     * ({@link ExteriorDecorator}) pour qu'elles aient toujours un point d'accroche au-dessus.
     */
    private static void addCeilingBeams(Map<BlockPos, BlockState> map, BlockPos min, BlockPos max, BuildStyle style) {
        int minX = min.getX(), maxX = max.getX();
        int maxY = max.getY();
        int minZ = min.getZ(), maxZ = max.getZ();
        int cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;
        boolean longAxisIsX = (maxX - minX) >= (maxZ - minZ);

        if (longAxisIsX) {                       // solives selon Z (petit côté), espacées en X
            Direction.Axis beamAxis = Direction.Axis.Z;
            BlockState beam = CreateCompat.isLoaded()
                    ? CreateCompat.metalGirder(beamAxis)
                    : withAxis(style.roofBeam().pick(min), beamAxis);
            for (int x = minX + 1; x < maxX; x++) {
                if (Math.floorMod(x - cx, 4) != 0) continue;
                for (int z = minZ + 1; z < maxZ; z++) map.put(new BlockPos(x, maxY, z), beam);
            }
        } else {                                 // solives selon X (petit côté), espacées en Z
            Direction.Axis beamAxis = Direction.Axis.X;
            BlockState beam = CreateCompat.isLoaded()
                    ? CreateCompat.metalGirder(beamAxis)
                    : withAxis(style.roofBeam().pick(min), beamAxis);
            for (int z = minZ + 1; z < maxZ; z++) {
                if (Math.floorMod(z - cz, 4) != 0) continue;
                for (int x = minX + 1; x < maxX; x++) map.put(new BlockPos(x, maxY, z), beam);
            }
        }
    }

    /**
     * Lucarnes sur toit pentu : peu nombreuses mais LARGES (2–3 tuiles le long du faîte). Chaque lucarne :
     * une bande de vitres encadrée aux deux bouts par un escalier de tuile tourné vers elle, capot de slab
     * BOTTOM au-dessus et appui de slab TOP en dessous. Contraintes : jamais à moins de 2 tuiles d'un mur
     * (pignon comme gouttière) ; sur les grands toits, DEUX rangées (haute près du faîte, basse près de
     * la gouttière), sinon une seule à mi-pente.
     */
    private static void pitchedSkylights(Map<BlockPos, BlockState> map, BlockPos min, BlockPos max, BuildStyle style) {
        int minX = min.getX(), maxX = max.getX();
        int maxY = max.getY();
        int minZ = min.getZ(), maxZ = max.getZ();
        int spanX = maxX - minX, spanZ = maxZ - minZ;
        boolean ridgeAlongX = spanX >= spanZ;
        int cx = (minX + maxX) / 2, cz = (minZ + maxZ) / 2;

        BlockState slabBot = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM);
        BlockState slabTop = Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);

        int half = (ridgeAlongX ? spanZ : spanX) / 2;
        if (half < 3) return; // toit trop bas : pas de place pour une lucarne à 2 tuiles des murs
        // Rangées (distance k au faîte) : ≥2 du faîte ET ≥2 de la gouttière (k ≤ half-2).
        int[] rows = (half >= 6) ? new int[]{2, half - 2}      // grand toit → 2 rangées
                : (half >= 4) ? new int[]{half - 2}            // moyen → 1 rangée basse-médiane
                : new int[]{1};                                 // petit → 1 rangée, au mieux
        int alongSpan = ridgeAlongX ? spanX : spanZ;
        int W = (alongSpan >= 14) ? 3 : 2;                     // largeur de la lucarne le long du faîte
        int period = Math.max(W + 4, 7);                       // espacement → peu de lucarnes
        int alongMin = ridgeAlongX ? minX : minZ;
        int alongMax = ridgeAlongX ? maxX : maxZ;
        int alongCenter = ridgeAlongX ? cx : cz;

        // On ancre les lucarnes sur le CENTRE (pas le bord gauche) et on les place par PAIRES symétriques
        // de part et d'autre du centre exact : sinon, filtrer sur le bord gauche décale tout le motif d'un
        // côté (bug visible : lucarnes groupées à gauche, pas alignées avec les fenêtres du mur en dessous).
        int halfW = (W - 1) / 2; // demi-largeur gauche (arrondi bas) ; centre - halfW = bord gauche
        int centerMin = alongMin + 3 + halfW;                   // 2 tuiles de marge au pignon bas
        int centerMax = alongMax - 3 - (W - 1) + halfW;         // 2 tuiles de marge au pignon haut
        if (centerMin > centerMax) return;                      // pas de place, même pour une seule
        int maxOffset = Math.max(alongCenter - centerMin, centerMax - alongCenter);

        for (int o = 0; o <= maxOffset; o += period) {
            int leftC = alongCenter - o;
            if (leftC >= centerMin && leftC <= centerMax) placeDormerRow(map, style, ridgeAlongX, leftC - halfW, W, rows, half, maxY, cx, cz, slabBot, slabTop);
            if (o != 0) {
                int rightC = alongCenter + o;
                if (rightC >= centerMin && rightC <= centerMax) placeDormerRow(map, style, ridgeAlongX, rightC - halfW, W, rows, half, maxY, cx, cz, slabBot, slabTop);
            }
        }
    }

    /** Pose une lucarne (toutes ses rangées {@code rows}, des deux côtés de la pente) à la position le long du faîte {@code a}. */
    private static void placeDormerRow(Map<BlockPos, BlockState> map, BuildStyle style, boolean ridgeAlongX,
                                       int a, int W, int[] rows, int half, int maxY, int cx, int cz,
                                       BlockState slabBot, BlockState slabTop) {
        for (int k : rows) {
            int tileY = maxY + 1 + (half - k);
            for (int side : new int[]{-1, 1}) {
                int cross = (ridgeAlongX ? cz : cx) + side * k;
                placeDormer(map, style, ridgeAlongX, a, W, cross, tileY, slabBot, slabTop);
            }
        }
    }

    /**
     * Pose une lucarne large de {@code W} tuiles le long du faîte, commençant à {@code alongStart}, sur la
     * pente à la position transversale {@code cross} et à l'altitude {@code tileY}.
     */
    private static void placeDormer(Map<BlockPos, BlockState> map, BuildStyle style, boolean ridgeAlongX,
                                    int alongStart, int W, int cross, int tileY, BlockState slabBot, BlockState slabTop) {
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState(); // remplacé par la weathered iron pane si Create (cf. planMap)
        for (int i = 0; i < W; i++) {
            int a = alongStart + i;
            BlockPos paneP = ridgeAlongX ? new BlockPos(a, tileY, cross) : new BlockPos(cross, tileY, a);
            map.put(paneP, pane);
            map.put(paneP.above(), slabBot);  // capot
            map.put(paneP.below(), slabTop);  // appui
        }
        // Escaliers d'extrémité (le long du faîte), tournés vers la lucarne.
        int aL = alongStart - 1, aR = alongStart + W;
        Direction faceL = ridgeAlongX ? Direction.EAST : Direction.SOUTH; // vers +along
        Direction faceR = ridgeAlongX ? Direction.WEST : Direction.NORTH; // vers -along
        BlockPos left  = ridgeAlongX ? new BlockPos(aL, tileY, cross) : new BlockPos(cross, tileY, aL);
        BlockPos right = ridgeAlongX ? new BlockPos(aR, tileY, cross) : new BlockPos(cross, tileY, aR);
        map.put(left,  style.roofStair().pick(left) .setValue(BlockStateProperties.HORIZONTAL_FACING, faceL).setValue(BlockStateProperties.HALF, Half.BOTTOM));
        map.put(right, style.roofStair().pick(right).setValue(BlockStateProperties.HORIZONTAL_FACING, faceR).setValue(BlockStateProperties.HALF, Half.BOTTOM));
    }

    /**
     * Cage Lamps (Create Deco) posées à l'INTÉRIEUR, une fenêtre sur deux, sur toutes les bandes de
     * fenêtres SAUF la dernière (le dernier étage, sous la corniche, reste sans lampe murale). Thème
     * brique → {@code yellow_copper_lamp} ; thème pierre → {@code yellow_andesite_lamp}. Sans Create
     * Deco : no-op.
     */
    private static void interiorCageLamps(Map<BlockPos, BlockState> map, BlockPos min, BlockPos max, Theme theme) {
        if (!CreateCompat.isDecoLoaded()) return;
        int minX = min.getX(), maxX = max.getX(), minZ = min.getZ(), maxZ = max.getZ();
        int minY = min.getY(), maxY = max.getY();

        // Nombre de bandes de fenêtres (étages) ; la dernière est exclue (pas de lampe sous la corniche).
        int lastBand = -1;
        for (int band = 0; ; band++) {
            int base = minY + 2 + band * WINDOW_V_SPACING;
            if (base + WINDOW_HEIGHT - 1 > maxY - 2) break;
            lastBand = band;
        }
        if (lastBand <= 0) return; // aucune bande, ou une seule (= dernier étage) → rien à éclairer

        for (int band = 0; band < lastBand; band++) {
            // Hauteur d'accroche : juste AU-DESSUS de la fenêtre (pas collée à la vitre du bas).
            int y = minY + 2 + band * WINDOW_V_SPACING + WINDOW_HEIGHT;
            int idx = 0;
            for (Direction out : Direction.Plane.HORIZONTAL) {
                if (out.getAxis() == Direction.Axis.X) {
                    int x = (out == Direction.WEST) ? minX : maxX;
                    for (int z = minZ + 1; z < maxZ; z++) {
                        if (!isWindowColumn(x, z, min, max)) continue;
                        if ((idx++ & 1) != 0) continue; // une fenêtre sur deux
                        placeInteriorLamp(map, x, y, z, out, theme);
                    }
                } else {
                    int z = (out == Direction.NORTH) ? minZ : maxZ;
                    for (int x = minX + 1; x < maxX; x++) {
                        if (!isWindowColumn(x, z, min, max)) continue;
                        if ((idx++ & 1) != 0) continue;
                        placeInteriorLamp(map, x, y, z, out, theme);
                    }
                }
            }
        }
    }

    /** Pose une cage lamp sur la face intérieure du mur, juste à côté d'une fenêtre, tournée vers la pièce. */
    private static void placeInteriorLamp(Map<BlockPos, BlockState> map, int x, int y, int z, Direction out, Theme theme) {
        Direction inward = out.getOpposite();
        BlockPos cell = new BlockPos(x, y, z).relative(inward);
        BlockState lamp = (theme == Theme.BRICK)
                ? CreateCompat.cageLampCopper(inward)
                : CreateCompat.cageLampAndesite(inward);
        if (!lamp.isAir()) map.put(cell, lamp);
    }

    /** Oriente une poutre selon un axe si le bloc le supporte (rondins/girders) ; sinon l'état tel quel. */
    private static BlockState withAxis(BlockState beam, Direction.Axis axis) {
        return beam.hasProperty(BlockStateProperties.AXIS)
                ? beam.setValue(BlockStateProperties.AXIS, axis) : beam;
    }

    public static List<Placement> order(Map<BlockPos, BlockState> map) {
        List<Placement> list = new ArrayList<>(map.size());
        // Les vitres (glass panes) sont posées EN DERNIER : ainsi tous les murs existent déjà quand on
        // les place, et chaque vitre se raccorde correctement dès la pose (au lieu de « flotter » puis
        // de se corriger en fin de chantier). Sinon, ordre habituel bas → haut, X, Z.
        map.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<BlockPos, BlockState> e) -> e.getValue().getBlock() instanceof IronBarsBlock)
                        .thenComparingInt(e -> e.getKey().getY())
                        .thenComparingInt(e -> e.getKey().getX())
                        .thenComparingInt(e -> e.getKey().getZ()))
                .forEach(e -> list.add(new Placement(e.getKey(), e.getValue())));
        return list;
    }

    /**
     * Bloc d'une cellule selon sa bande verticale et son rôle horizontal.
     * Renvoie {@code null} pour les cellules INTÉRIEURES (sol intérieur + volume) : on ne bâtit que la
     * coque, l'intérieur est préservé (le mod sert à recouvrir une construction existante).
     */
    @Nullable
    private static BlockState classify(BlockPos pos, BlockPos min, BlockPos max,
                                       Direction facing, BuildStyle style, RoofType roofType, BlockState air) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        int minX = min.getX(), minY = min.getY(), minZ = min.getZ();
        int maxX = max.getX(), maxY = max.getY(), maxZ = max.getZ();

        boolean xEdge = (x == minX || x == maxX);
        boolean zEdge = (z == minZ || z == maxZ);
        boolean perimeter = xEdge || zEdge;

        if (y == minY) return (perimeter ? style.foundation() : style.floor()).pick(pos); // sol PLEIN (coque fermée)
        // Toit PLAT : dalle pleine à maxY. Toit PENTU : pas de plafond plat — on laisse le rang maxY
        // se comporter comme une dernière assise de mur (périmètre), l'intérieur reste ouvert.
        if (y == maxY && roofType == RoofType.FLAT) return style.roof().pick(pos);
        if (!perimeter) return null;                     // intérieur préservé (volume) → non touché
        if (xEdge && zEdge) return style.pillar().pick(pos);

        // Mur sur une seule face : features puis bande
        if (isDoor(x, y, z, min, max, facing)) return doorBlock(x, y, z, min, max, facing, air);
        if (isWindowColumn(x, z, min, max) && isWindowRow(y, min, max)) return style.window().pick(pos);

        return style.wall().pick(pos);                            // corps + corniche (palette mur)
    }

    /** Porte : ouverture 3×4 entièrement dégagée (plus de trappes décoratives), sur toute sa zone. */
    private static BlockState doorBlock(int x, int y, int z, BlockPos min, BlockPos max, Direction facing, BlockState air) {
        return air;
    }

    /**
     * Ouverture de porte, reproduite sur les DEUX murs opposés le long de l'axe de {@code facing}
     * (entrée avant ET arrière — même sans Create), toujours centrée sur la façade.
     */
    private static boolean isDoor(int x, int y, int z, BlockPos min, BlockPos max, Direction facing) {
        if (y < min.getY() + 1 || y > min.getY() + DOOR_HEIGHT) return false;
        int cx = (min.getX() + max.getX()) / 2;
        int cz = (min.getZ() + max.getZ()) / 2;
        boolean depthIsZ = facing.getAxis() == Direction.Axis.Z;
        return depthIsZ
                ? (z == min.getZ() || z == max.getZ()) && Math.abs(x - cx) <= DOOR_HALF_WIDTH
                : (x == min.getX() || x == max.getX()) && Math.abs(z - cz) <= DOOR_HALF_WIDTH;
    }

    /**
     * Une colonne de mur (hors angle) porte une fenêtre si elle tombe sur le motif CENTRÉ de la façade.
     * Centrer sur le milieu (et non sur un modulo brut) garantit une répartition symétrique.
     */
    private static boolean isWindowColumn(int x, int z, BlockPos min, BlockPos max) {
        boolean zEdge = (z == min.getZ() || z == max.getZ());
        int index, span;
        if (zEdge) {              // mur le long de l'axe X
            index = x - min.getX();
            span = max.getX() - min.getX();
        } else {                  // mur le long de l'axe Z
            index = z - min.getZ();
            span = max.getZ() - min.getZ();
        }
        if (index <= 0 || index >= span) return false; // exclut les angles
        int center = span / 2;
        return Math.floorMod(index - center, WINDOW_H_SPACING) == 0;
    }

    /** Rangées de fenêtres, pavées verticalement dans le corps (hors soubassement et corniche). */
    private static boolean isWindowRow(int y, BlockPos min, BlockPos max) {
        int minY = min.getY(), maxY = max.getY();
        if (y < minY + 2 || y > maxY - 2) return false;
        int off = y - (minY + 2);
        return off % WINDOW_V_SPACING < WINDOW_HEIGHT;
    }

}
