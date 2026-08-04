package dev.aurelien.prefab.build;

import dev.aurelien.prefab.compat.CreateCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.Map;
import java.util.Set;

/**
 * Passe de décoration EXTÉRIEURE, en blocs Minecraft de base (+ un peu de cuivre). Tout est placé
 * dans la marge autour du bâtiment, de façon déterministe (hash de position) pour rester stable.
 *
 * <p>Répartition :</p>
 * <ul>
 *   <li>murs latéraux (ni façade/porte, ni arrière/contrôleur) : caisses éparses, faux réservoirs
 *       cuivre/granite, végétation grimpante — un seul élément par emplacement, jamais empilés/mélangés ;</li>
 *   <li>toit : bordure (parapet) + aérations en smooth stone uniquement ;</li>
 *   <li>sous le toit : lanternes suspendues à des chaînes.</li>
 * </ul>
 *
 * <p>Marge réservée : {@value MARGIN} blocs horizontalement, {@value MARGIN_UP} vers le haut.</p>
 */
public final class ExteriorDecorator {
    private ExteriorDecorator() {}

    public static final int MARGIN = 2;
    public static final int MARGIN_UP = 4;
    private static final int DOOR_CLEAR = 1; // demi-largeur dégagée devant la porte (pas de colonne)
    private static final int COLUMN_MIN_SPAN = 10; // en deçà (≈ ≤ 9 blocs), pas de colonne hors angles : place à la déco
    private static final int ROOF_SPACING = 5;     // pas de la grille (centrée) des décos de toit

    /**
     * Décore le bâtiment. {@code free} reçoit toutes les positions purement esthétiques (colonnes,
     * avant-toit, parapet/passerelle, aérations/lucarnes de toit) : ces éléments n'ajoutent rien à la
     * solidité de la coque, donc {@link ControllerBlockEntity} les exempte de coût en matériaux (sinon
     * un mur décoré de colonnes pleine hauteur peut, à lui seul, doubler la facture d'un petit bâtiment).
     */
    public static void decorate(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax,
                                Direction facing, RoofType roofType, BuildStyle style, BlockPos controller, Theme theme,
                                Set<BlockPos> free) {
        columns(map, bMin, bMax, facing, roofType, style, controller, free); // 1) colonnes (cassent le rectangle)
        entranceStairs(map, bMin, bMax, facing, style, free); // 1bis) marches devant les deux portes
        sideWallDecor(map, bMin, bMax, facing);               // 2) déco murale au sol : caisses + végétation
        wallSilos(map, bMin, bMax, facing);                   // 3) cuves/silos Create (colonnes), dans les gaps
        wallGreebles(map, bMin, bMax, facing);                // 4) greebles Create : jauges + volants
        wallAcUnits(map, bMin, bMax, facing);                 // 5) climatiseurs muraux au-dessus des fenêtres
        wallVines(map, bMin, bMax, facing);                   // 6) lianes grimpantes sur toutes les façades
        ceilingLanterns(map, bMin, bMax, theme);
        // Déco de toit PLAT uniquement : avant-toit, parapet et aérations n'ont pas de sens sur des pentes.
        if (roofType == RoofType.FLAT) {
            roofOverhang(map, bMin, bMax, style, free);       // avant-toit : déborde d'1 bloc, posé sur les colonnes
            if (CreateCompat.isDecoLoaded()) {
                roofCatwalk(map, bMin, bMax, free);           // passerelle : anneau de garde-corps Create Deco
            } else {
                roofParapet(map, bMin, bMax, style, free);    // sinon muret de parapet vanilla
            }
            roofClutter(map, bMin, bMax, free);               // déco de toit centrée : aérations 2×2
        }
    }

    // ---------------------------------------------------------------- Colonnes (pilastres) autour du bâtiment

    /** Pilastres thématisés (palette {@code pillar}), aux angles (décalés en diagonale) et le long des murs. */
    private static void columns(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax,
                                Direction facing, RoofType roofType, BuildStyle style, BlockPos controller, Set<BlockPos> free) {
        int y0 = bMin.getY(), yTop = bMax.getY() - 1; // hauteur de mur (sous le toit)
        boolean pitched = roofType == RoofType.PITCHED;
        // Angles : un cran en diagonale vers l'extérieur, pour casser la silhouette rectangulaire.
        placeColumn(map, bMin.getX() - 1, bMin.getZ() - 1, y0, yTop, controller, bMin, bMax, pitched, style, free);
        placeColumn(map, bMin.getX() - 1, bMax.getZ() + 1, y0, yTop, controller, bMin, bMax, pitched, style, free);
        placeColumn(map, bMax.getX() + 1, bMin.getZ() - 1, y0, yTop, controller, bMin, bMax, pitched, style, free);
        placeColumn(map, bMax.getX() + 1, bMax.getZ() + 1, y0, yTop, controller, bMin, bMax, pitched, style, free);

        // Petits bâtiments : sur un mur court, les colonnes le long du mur ne laissent AUCUNE place à la
        // déco (caisses/clim/…). On les omet alors → seuls les 4 angles, et tout le mur reste décorable.
        int spanX = bMax.getX() - bMin.getX(), spanZ = bMax.getZ() - bMin.getZ();
        // Période 4 déphasée de 2 → les colonnes tombent ENTRE les fenêtres (qui sont au phase 0),
        // jamais devant (notamment la fenêtre centrale de la façade arrière).
        int cx = (bMin.getX() + bMax.getX()) / 2, cz = (bMin.getZ() + bMax.getZ()) / 2;
        if (spanX >= COLUMN_MIN_SPAN) {
            for (int x = bMin.getX() + 1; x < bMax.getX(); x++) {
                if (Math.floorMod(x - cx, 4) != 2) continue;
                if (!frontOfDoor(x, bMin.getZ() - 1, bMin, bMax, facing)) placeColumn(map, x, bMin.getZ() - 1, y0, yTop, controller, bMin, bMax, pitched, style, free);
                if (!frontOfDoor(x, bMax.getZ() + 1, bMin, bMax, facing)) placeColumn(map, x, bMax.getZ() + 1, y0, yTop, controller, bMin, bMax, pitched, style, free);
            }
        }
        if (spanZ >= COLUMN_MIN_SPAN) {
            for (int z = bMin.getZ() + 1; z < bMax.getZ(); z++) {
                if (Math.floorMod(z - cz, 4) != 2) continue;
                if (!frontOfDoor(bMin.getX() - 1, z, bMin, bMax, facing)) placeColumn(map, bMin.getX() - 1, z, y0, yTop, controller, bMin, bMax, pitched, style, free);
                if (!frontOfDoor(bMax.getX() + 1, z, bMin, bMax, facing)) placeColumn(map, bMax.getX() + 1, z, y0, yTop, controller, bMin, bMax, pitched, style, free);
            }
        }
    }

    /**
     * Une colonne. Corps en palette {@code pillar}. En toit PENTU, on la coiffe d'un cran plus haut
     * ({@code yTop+1}) : un escalier d'accent tourné vers le bâtiment (corbeau soutenant l'avant-toit),
     * ou une dalle aux angles. On ne coiffe PAS si une tuile d'avant-toit occupe déjà la case.
     */
    private static void placeColumn(Map<BlockPos, BlockState> map, int x, int z, int y0, int yTop,
                                    BlockPos controller, BlockPos bMin, BlockPos bMax, boolean pitched, BuildStyle style,
                                    Set<BlockPos> free) {
        if (controller != null && controller.getX() == x && controller.getZ() == z) return; // n'encastre pas le contrôleur
        for (int y = y0; y <= yTop; y++) {
            BlockPos p = new BlockPos(x, y, z);
            // Pied de colonne (y0) : même matériau que le soubassement → ancre la colonne visuellement.
            map.put(p, (y == y0) ? style.foundation().pick(p) : style.pillar().pick(p));
            free.add(p);
        }
        if (!pitched) return;
        BlockPos cap = new BlockPos(x, yTop + 1, z);
        if (map.containsKey(cap)) return; // déjà coiffée par une tuile d'avant-toit : on laisse

        boolean xOut = (x < bMin.getX() || x > bMax.getX());
        boolean zOut = (z < bMin.getZ() || z > bMax.getZ());
        if (xOut && zOut) {                // angle : direction « vers le bâtiment » ambiguë → dalle
            // Dalle BASSE : elle repose sur le sommet du corps de pilier (sinon, en TOP, elle flotte).
            map.put(cap, style.trimSlab().pick(cap).setValue(BlockStateProperties.SLAB_TYPE, SlabType.BOTTOM));
            free.add(cap);
            return;
        }
        Direction inward = (x < bMin.getX()) ? Direction.EAST
                : (x > bMax.getX()) ? Direction.WEST
                : (z < bMin.getZ()) ? Direction.SOUTH : Direction.NORTH;
        map.put(cap, style.trimStair().pick(cap)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, inward)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM));
        free.add(cap);
    }

    /**
     * Marche d'entrée : un rang d'escaliers (palette {@code trimStair}, large comme la porte) juste
     * devant chacune des DEUX portes, pour franchir le bloc de dénivelé entre le sol extérieur et le
     * sol intérieur (surélevé d'un cran par défaut, cf. {@code ControllerBlockEntity#buildingMinMax}).
     * {@code frontOfDoor}/{@code DOOR_CLEAR} tiennent déjà les colonnes à l'écart de ces cellules.
     */
    private static void entranceStairs(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax,
                                       Direction facing, BuildStyle style, Set<BlockPos> free) {
        int y = bMin.getY();
        int cx = (bMin.getX() + bMax.getX()) / 2, cz = (bMin.getZ() + bMax.getZ()) / 2;
        boolean depthIsZ = facing.getAxis() == Direction.Axis.Z;
        Direction[] doorFaces = depthIsZ
                ? new Direction[]{Direction.NORTH, Direction.SOUTH}
                : new Direction[]{Direction.WEST, Direction.EAST};
        for (Direction out : doorFaces) {
            int wallCoord = switch (out) {
                case NORTH -> bMin.getZ();
                case SOUTH -> bMax.getZ();
                case WEST -> bMin.getX();
                default -> bMax.getX(); // EAST
            };
            int stepCoord = wallCoord + out.getStepX() + out.getStepZ(); // 1 cran hors du mur
            Direction ascend = out.getOpposite(); // on monte en marchant VERS le bâtiment
            for (int c = -BuildPlanner.DOOR_HALF_WIDTH; c <= BuildPlanner.DOOR_HALF_WIDTH; c++) {
                BlockPos p = depthIsZ ? new BlockPos(cx + c, y, stepCoord) : new BlockPos(stepCoord, y, cz + c);
                map.put(p, style.trimStair().pick(p)
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, ascend)
                        .setValue(BlockStateProperties.HALF, Half.BOTTOM));
                free.add(p);
            }
        }
    }

    /**
     * La cellule de marge est-elle juste devant une des DEUX ouvertures de porte (avant ET arrière,
     * les deux murs le long de l'axe de {@code facing}) ? Sert à ne pas obstruer l'entrée d'une colonne.
     */
    private static boolean frontOfDoor(int x, int z, BlockPos bMin, BlockPos bMax, Direction facing) {
        int cx = (bMin.getX() + bMax.getX()) / 2, cz = (bMin.getZ() + bMax.getZ()) / 2;
        boolean depthIsZ = facing.getAxis() == Direction.Axis.Z;
        return depthIsZ
                ? (z == bMin.getZ() - 1 || z == bMax.getZ() + 1) && Math.abs(x - cx) <= DOOR_CLEAR
                : (x == bMin.getX() - 1 || x == bMax.getX() + 1) && Math.abs(z - cz) <= DOOR_CLEAR;
    }

    /**
     * Les DEUX façades porte (avant ET arrière — les deux murs le long de l'axe de {@code facing})
     * restent dégagées de déco lourde (caisses, silos). Les deux murs latéraux sont décorés normalement.
     */
    private static boolean reservedFace(Direction out, Direction facing) {
        return out.getAxis() == facing.getAxis();
    }

    // ---------------------------------------------------------------- Déco des murs latéraux

    /**
     * Décore les emplacements espacés des 3 murs décorés (tous sauf la façade porte) en faisant TOURNER
     * les 4 types — amas de caisses, réservoir, climatiseur, végétation — sur un cycle continu mur après
     * mur. Conséquences : variété garantie, jamais deux fois le même type côte à côte, au moins un de
     * chaque dès qu'il y a ≥ 4 emplacements, et chaque emplacement est effectivement décoré.
     */
    private static void sideWallDecor(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Direction facing) {
        // Phase de départ déterministe : le cycle ne commence pas toujours par le même type.
        int phase = Math.floorMod(hash(bMin.getX(), bMin.getY(), bMin.getZ(), 17), 4);
        int slot = 0;
        slot = walkWall(map, bMin, bMax, Direction.NORTH, facing, slot, phase);
        slot = walkWall(map, bMin, bMax, Direction.SOUTH, facing, slot, phase);
        slot = walkWall(map, bMin, bMax, Direction.WEST, facing, slot, phase);
        walkWall(map, bMin, bMax, Direction.EAST, facing, slot, phase);
    }

    /**
     * Parcourt un mur le long de son axe (hors angles) et décore un emplacement tous les 4 blocs. Le
     * compteur {@code slot} est continu d'un mur à l'autre → les emplacements voisins (consécutifs sur
     * un même mur) reçoivent des types consécutifs du cycle, donc différents. Renvoie le slot suivant.
     */
    private static int walkWall(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax,
                                Direction out, Direction facing, int slot, int phase) {
        if (reservedFace(out, facing)) return slot;                  // façade porte : laissée nue
        if (out.getAxis() == Direction.Axis.X) {                     // mur le long de Z (x fixe)
            int x = (out == Direction.WEST) ? bMin.getX() : bMax.getX();
            for (int z = bMin.getZ() + 1; z < bMax.getZ(); z++) {
                if ((z - bMin.getZ()) % 4 != 1) continue;
                placeFeature(map, x, z, bMin, bMax, out, Math.floorMod(slot + phase, 4));
                slot++;
            }
        } else {                                                     // mur le long de X (z fixe)
            int z = (out == Direction.NORTH) ? bMin.getZ() : bMax.getZ();
            for (int x = bMin.getX() + 1; x < bMax.getX(); x++) {
                if ((x - bMin.getX()) % 4 != 1) continue;
                placeFeature(map, x, z, bMin, bMax, out, Math.floorMod(slot + phase, 4));
                slot++;
            }
        }
        return slot;
    }

    /** Pose le type de déco {@code type} (0/2 caisses, 1/3 végétation) à l'emplacement au sol. */
    private static void placeFeature(Map<BlockPos, BlockState> map, int x, int z,
                                     BlockPos bMin, BlockPos bMax, Direction out, int type) {
        BlockPos foot = new BlockPos(x, bMin.getY(), z).relative(out); // 1er anneau de marge, au sol
        switch (type) {
            case 0 -> crateCluster(map, foot, out, hash(x, 5, z, 41));
            case 1 -> vegetationFeature(map, x, z, bMin, bMax, out);
            case 2 -> crateCluster(map, foot, out, hash(x, 7, z, 53));
            default -> vegetationFeature(map, x, z, bMin, bMax, out);
        }
    }

    /** Végétation à un emplacement : vigne grimpante + petit amas de feuilles au pied. */
    private static void vegetationFeature(Map<BlockPos, BlockState> map, int x, int z,
                                          BlockPos bMin, BlockPos bMax, Direction out) {
        climbingVines(map, x, z, bMin, bMax, out);
        leafBush(map, new BlockPos(x, bMin.getY(), z).relative(out), out, hash(x, 7, z, 67));
    }

    /**
     * Amas de caisses inspiré d'un coin de dépôt : cluster compact (2 de large × 2 de profond) de
     * tonneaux/coffres aux hauteurs variées (1 à 3) → silhouette en gradins. Tonneaux tantôt debout
     * (cercle visible dessus), tantôt couchés vers l'extérieur ; feu de camp éteint rare, en sommet.
     */
    private static void crateCluster(Map<BlockPos, BlockState> map, BlockPos foot, Direction out, int seed) {
        Direction along = out.getClockWise();
        int n = seed;                            // compteur continu → le type CYCLE sur tout le tas (un peu de chaque)
        // Vrai TAS (et non colonnes) : 3 le long du mur × 2 de profond (limité par la marge), hauteurs en
        // pyramide (plus haut au centre et contre le mur) → silhouette de tas.
        for (int a = -1; a <= 1; a++) {
            for (int depth = 0; depth < 2; depth++) {
                BlockPos col = foot.relative(along, a).relative(out, depth);
                int height = Math.max(1, 3 - Math.abs(a) - depth);
                for (int i = 0; i < height; i++) {
                    map.putIfAbsent(col.above(i), crateBlock(n++, out)); // putIfAbsent : respecte colonnes/voisins
                }
                if (a == 0 && depth == 0 && seed % 2 == 0) { // feu de camp éteint ~1 tas sur 2, au sommet
                    map.putIfAbsent(col.above(height), Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
                }
            }
        }
    }

    /**
     * Une caisse, choisie par CYCLE (et non par hash) pour répartir équitablement : tonneau debout,
     * tonneau couché, coffre, pot décoré, planches. Appelée avec un compteur croissant → un amas montre
     * « un peu de chaque » au lieu de 8 tonneaux d'affilée.
     */
    private static BlockState crateBlock(int seq, Direction out) {
        return switch (Math.floorMod(seq, 5)) {
            case 0  -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP);
            case 1  -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, out);
            case 2  -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, out);
            case 3  -> Blocks.DECORATED_POT.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out);
            default -> Blocks.OAK_PLANKS.defaultBlockState();
        };
    }


    /**
     * Lianes grimpantes sur TOUTES les façades (y compris la porte), avec ~80 % de couverture.
     * En {@code putIfAbsent} : elles habillent les colonnes nues et s'intercalent entre les caisses
     * sans jamais les écraser.
     */
    private static void wallVines(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Direction facing) {
        for (int x = bMin.getX(); x <= bMax.getX(); x++) {
            for (int z = bMin.getZ(); z <= bMax.getZ(); z++) {
                boolean xEdge = (x == bMin.getX() || x == bMax.getX());
                boolean zEdge = (z == bMin.getZ() || z == bMax.getZ());
                if (!(xEdge || zEdge) || (xEdge && zEdge)) continue; // périmètre, hors angles
                Direction out = outward(x, z, bMin, bMax);
                if (hash(x, 9, z, 83) % 5 == 0) continue;            // ~1/5 de respirations aléatoires
                climbingVines(map, x, z, bMin, bMax, out);
            }
        }
    }

    /** Vigne grimpant le mur sur une hauteur variable. */
    private static void climbingVines(Map<BlockPos, BlockState> map, int x, int z, BlockPos bMin, BlockPos bMax, Direction out) {
        BlockPos cell = new BlockPos(x, bMin.getY(), z).relative(out);
        int span = Math.max(1, bMax.getY() - bMin.getY() - 2);
        int top = bMin.getY() + 1 + hash(x, 2, z, 23) % span;
        BlockState vine = Blocks.VINE.defaultBlockState().setValue(vineFace(out.getOpposite()), true);
        for (int y = bMin.getY() + 1; y <= top; y++) {
            map.putIfAbsent(new BlockPos(cell.getX(), y, cell.getZ()), vine);
        }
    }

    /** Petit amas de feuilles persistantes au pied du mur (1 à 3 blocs, façon buisson). */
    private static void leafBush(Map<BlockPos, BlockState> map, BlockPos foot, Direction out, int seed) {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        map.putIfAbsent(foot, leaves);
        if (seed % 2 == 0) map.putIfAbsent(foot.above(), leaves);                       // parfois plus touffu
        if (seed % 3 == 0) map.putIfAbsent(foot.relative(out.getClockWise()), leaves);  // parfois élargi le long du mur
        if (seed % 5 == 0) map.putIfAbsent(foot.relative(out), leaves);                 // parfois débordant vers l'extérieur
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty vineFace(Direction wallSide) {
        return switch (wallSide) {
            case NORTH -> VineBlock.NORTH;
            case SOUTH -> VineBlock.SOUTH;
            case EAST -> VineBlock.EAST;
            case WEST -> VineBlock.WEST;
            default -> VineBlock.UP;
        };
    }

    // ---------------------------------------------------------------- Climatiseurs muraux (au-dessus des fenêtres)

    /**
     * Pose des climatiseurs muraux (loom + comparateur + trappes) dans la marge, juste au-dessus de la
     * première bande de fenêtres, sur environ 1 fenêtre sur 2. Seulement si le bâtiment est assez haut
     * pour loger le loom (minY+4) et le comparateur (minY+5) sous la corniche (maxY-1).
     */
    /**
     * Climatiseurs muraux au-dessus de TOUTES les bandes de fenêtres (pas seulement la première),
     * sur les 4 façades (la façade porte est incluse : le check glass_pane filtre naturellement
     * le dessus de l'ouverture). Densité ~14 % (hash % 7), variable par bande (acY dans le hash).
     */
    private static void wallAcUnits(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Direction facing) {
        final int WIN_V_SPACING = 4; // même valeur que BuildPlanner.WINDOW_V_SPACING
        final int WIN_HEIGHT    = 2; // même valeur que BuildPlanner.WINDOW_HEIGHT

        for (int band = 0; ; band++) {
            int windowBase = bMin.getY() + 2 + band * WIN_V_SPACING; // rangée basse de la bande
            int acY = windowBase + WIN_HEIGHT; // pied fence gate : juste au-dessus du haut de la fenêtre
            if (acY + 2 >= bMax.getY()) break; // fence gate + loom + comparateur ne tiendraient plus

            for (Direction out : Direction.Plane.HORIZONTAL) {
                // Toutes façades incluses ; la façade porte n'a pas de vitre au-dessus de la porte
                // → le test glass_pane ci-dessous l'élimine sans code supplémentaire.
                if (out.getAxis() == Direction.Axis.X) {
                    int x = (out == Direction.WEST) ? bMin.getX() : bMax.getX();
                    for (int z = bMin.getZ() + 1; z < bMax.getZ(); z++) {
                        BlockState ws = map.get(new BlockPos(x, windowBase, z));
                        if (ws == null || ws.getBlock() != Blocks.GLASS_PANE) continue;
                        if (hash(x, acY, z, 61) % 7 != 0) continue; // ~14 %
                        placeWallAc(map, new BlockPos(x, acY, z).relative(out), out);
                    }
                } else {
                    int z = (out == Direction.NORTH) ? bMin.getZ() : bMax.getZ();
                    for (int x = bMin.getX() + 1; x < bMax.getX(); x++) {
                        BlockState ws = map.get(new BlockPos(x, windowBase, z));
                        if (ws == null || ws.getBlock() != Blocks.GLASS_PANE) continue;
                        if (hash(x, acY, z, 61) % 7 != 0) continue;
                        placeWallAc(map, new BlockPos(x, acY, z).relative(out), out);
                    }
                }
            }
        }
    }

    /**
     * Climatiseur mural. Si Create est chargé : colonne de 3 engrenages (petit/grand/petit cogwheel).
     * Sinon : fence gate + loom + comparateur + trappes latérales (version vanille).
     */
    private static void placeWallAc(Map<BlockPos, BlockState> map, BlockPos foot, Direction out) {
        if (map.containsKey(foot) || map.containsKey(foot.above()) || map.containsKey(foot.above(2))) return;
        if (CreateCompat.isLoaded()) {
            placeCreateAc(map, foot, out);
        } else {
            placeVanillaAc(map, foot, out);
        }
    }

    /**
     * Version Create : un Encased Fan (ventilateur) tourné vers l'extérieur = unité de climatisation,
     * posé au-dessus d'une spruce fence gate ouverte (grille/conduit). On évite les cogwheels empilés,
     * que Create détruit (engrenages qui « maillent » de façon invalide).
     */
    private static void placeCreateAc(Map<BlockPos, BlockState> map, BlockPos foot, Direction out) {
        map.put(foot, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()      // grille ouverte en dessous
                .setValue(BlockStateProperties.HORIZONTAL_FACING, out)
                .setValue(BlockStateProperties.OPEN, true));
        map.put(foot.above(), CreateCompat.encasedFan(out));           // ventilateur face à l'extérieur
    }

    /** Version vanille : fence gate ouverte au pied, loom au milieu, comparateur au sommet, trappes sur les côtés. */
    private static void placeVanillaAc(Map<BlockPos, BlockState> map, BlockPos foot, Direction out) {
        Direction along = out.getClockWise();
        map.put(foot, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, out)
                .setValue(BlockStateProperties.OPEN, true));
        map.put(foot.above(), Blocks.LOOM.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out));
        map.put(foot.above(2), Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, out));
        for (Direction d : new Direction[]{along, along.getOpposite()}) {
            map.putIfAbsent(foot.above().relative(d), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, d)
                    .setValue(BlockStateProperties.OPEN, true)
                    .setValue(BlockStateProperties.HALF, Half.BOTTOM));
        }
    }

    // ---------------------------------------------------------------- Cuves/silos & greebles (Create)

    /**
     * Cuves/silos : colonnes verticales de {@code create:fluid_tank} (2–3 de haut) dans la 1re marge,
     * contre les murs décorés, espacées (~tous les 6 blocs, déphasées des amas de caisses). La
     * block-entity Create forme ensuite le multiblock 1×N (rendu vitré façon silo). Sans Create : no-op.
     */
    private static void wallSilos(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Direction facing) {
        BlockState tank = CreateCompat.fluidTank();
        if (tank.isAir()) return;
        int h = Math.max(2, Math.min(3, bMax.getY() - bMin.getY() - 1));
        for (Direction out : Direction.Plane.HORIZONTAL) {
            if (reservedFace(out, facing)) continue;
            if (out.getAxis() == Direction.Axis.X) {
                int x = (out == Direction.WEST) ? bMin.getX() : bMax.getX();
                for (int z = bMin.getZ() + 2; z < bMax.getZ() - 1; z++) {
                    if (Math.floorMod(z - bMin.getZ(), 6) != 3) continue; // déphasé des caisses (slots %4==1)
                    trySilo(map, new BlockPos(x, bMin.getY(), z).relative(out), h, tank, out);
                }
            } else {
                int z = (out == Direction.NORTH) ? bMin.getZ() : bMax.getZ();
                for (int x = bMin.getX() + 2; x < bMax.getX() - 1; x++) {
                    if (Math.floorMod(x - bMin.getX(), 6) != 3) continue;
                    trySilo(map, new BlockPos(x, bMin.getY(), z).relative(out), h, tank, out);
                }
            }
        }
    }

    /**
     * Pose une colonne de cuve de hauteur {@code h} si l'espace est libre, puis la « branche » : des
     * tuyaux HORIZONTAUX courant le long du mur (dans la marge, à hauteur y0+1) des deux côtés vers les
     * piliers voisins — jamais vers le mur/l'usine (une cuve n'alimente pas le bâtiment, elle en reçoit).
     */
    private static void trySilo(Map<BlockPos, BlockState> map, BlockPos foot, int h, BlockState tank, Direction out) {
        for (int i = 0; i < h; i++) if (map.containsKey(foot.above(i))) return; // espace libre requis
        for (int i = 0; i < h; i++) map.put(foot.above(i), tank);

        BlockState pipe = CreateCompat.fluidPipe();
        if (pipe.isAir()) return;
        Direction along = out.getClockWise();
        final int REACH = 2; // longueur de tuyau de chaque côté, en direction des piliers voisins
        for (Direction dir : new Direction[]{along, along.getOpposite()}) {
            for (int i = 1; i <= REACH; i++) {
                BlockPos p = foot.above(1).relative(dir, i);
                if (map.containsKey(p)) break; // butée (pilier, caisse…) : on s'arrête là
                map.put(p, pipe);
            }
        }
    }

    /**
     * Greebles techniques (Create) : {@code factory_gauge} et {@code copper_valve_handle} fixés à plat
     * sur les murs décorés, à hauteur basse, de façon clairsemée (~1/9). En {@code putIfAbsent} : ne
     * recouvre jamais caisses, lianes ou cuves. Sans Create : no-op.
     */
    private static void wallGreebles(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Direction facing) {
        if (!CreateCompat.isLoaded()) return;
        int y = bMin.getY() + 2;
        if (y >= bMax.getY()) return;
        int cx = (bMin.getX() + bMax.getX()) / 2, cz = (bMin.getZ() + bMax.getZ()) / 2;
        // Toutes les façades — y compris les deux façades porte, avant ET arrière (sinon elles paraissent
        // nues) ; on dégage juste les colonnes des deux ouvertures de porte.
        for (Direction out : Direction.Plane.HORIZONTAL) {
            boolean doorFace = (out.getAxis() == facing.getAxis());
            if (out.getAxis() == Direction.Axis.X) {
                int x = (out == Direction.WEST) ? bMin.getX() : bMax.getX();
                for (int z = bMin.getZ() + 1; z < bMax.getZ(); z++) {
                    if (doorFace && Math.abs(z - cz) <= 2) continue; // dégage l'entrée
                    placeGreeble(map, new BlockPos(x, y, z).relative(out), out, hash(x, y, z, 71));
                }
            } else {
                int z = (out == Direction.NORTH) ? bMin.getZ() : bMax.getZ();
                for (int x = bMin.getX() + 1; x < bMax.getX(); x++) {
                    if (doorFace && Math.abs(x - cx) <= 2) continue; // dégage l'entrée
                    placeGreeble(map, new BlockPos(x, y, z).relative(out), out, hash(x, y, z, 71));
                }
            }
        }
    }

    private static void placeGreeble(Map<BlockPos, BlockState> map, BlockPos cell, Direction out, int seed) {
        if (Math.floorMod(seed, 9) != 0) return;          // clairsemé
        if (map.containsKey(cell)) return;
        BlockState g = (Math.floorMod(seed / 9, 2) == 0) ? CreateCompat.factoryGauge(out) : CreateCompat.valveHandle(out);
        if (!g.isAir()) map.put(cell, g);
    }

    // ---------------------------------------------------------------- Éclairage : lanternes suspendues

    private static void ceilingLanterns(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Theme theme) {
        int chainY = bMax.getY() - 1;     // juste sous le toit
        int lanternY = bMax.getY() - 2;   // lanterne suspendue
        if (lanternY <= bMin.getY()) return;

        BlockState chain = Blocks.CHAIN.defaultBlockState();
        // Avec Create Deco : cage lamp allumée suspendue (facing=DOWN), même variante que les lampes
        // murales intérieures selon le thème (harmonisation). Sans Create Deco : lanterne vanilla.
        BlockState lantern = CreateCompat.isDecoLoaded()
                ? (theme == Theme.BRICK ? CreateCompat.cageLampCopper(Direction.DOWN) : CreateCompat.cageLampAndesite(Direction.DOWN))
                : Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        // Grille CENTRÉE sur le milieu (sinon décalage d'un bloc, comme les fenêtres).
        int cx = (bMin.getX() + bMax.getX()) / 2;
        int cz = (bMin.getZ() + bMax.getZ()) / 2;
        for (int x = bMin.getX() + 1; x < bMax.getX(); x++) {
            if (Math.floorMod(x - cx, 4) != 0) continue;
            for (int z = bMin.getZ() + 1; z < bMax.getZ(); z++) {
                if (Math.floorMod(z - cz, 4) != 0) continue;
                map.put(new BlockPos(x, chainY, z), chain);
                map.put(new BlockPos(x, lanternY, z), lantern);
            }
        }
    }

    // ---------------------------------------------------------------- Toit : bordure + aérations

    /**
     * Avant-toit : le toit déborde d'un bloc tout autour (anneau au niveau du toit), reposant sur les
     * colonnes qui montent jusque-là. Donne un vrai débord soutenu plutôt qu'un mur nu et anguleux.
     */
    private static void roofOverhang(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, BuildStyle style, Set<BlockPos> free) {
        BlockState eave = style.roof().pick(bMin);
        int y = bMax.getY();
        for (int x = bMin.getX() - 1; x <= bMax.getX() + 1; x++) {
            for (int z = bMin.getZ() - 1; z <= bMax.getZ() + 1; z++) {
                boolean ring = (x < bMin.getX() || x > bMax.getX() || z < bMin.getZ() || z > bMax.getZ());
                if (ring) {
                    BlockPos p = new BlockPos(x, y, z);
                    map.put(p, eave); // anneau extérieur uniquement (le toit couvre déjà le reste)
                    free.add(p);
                }
            }
        }
    }

    /**
     * Passerelle de toit (Create Deco) : anneau de garde-corps {@code andesite_catwalk_railing} sur la
     * bordure de l'avant-toit. Chaque côté ({@code north/south/east/west}) du bloc représente un garde-
     * corps PHYSIQUE sur cette face — il doit donc être activé seulement quand cette face donne dans le
     * VIDE (hors du rectangle du toit), pas quand elle donne sur la case voisine de l'anneau (qui, elle,
     * est du plancher, pas un à-pic). Sur un côté droit, seule la face tournée vers l'extérieur du
     * bâtiment est donc vraie ; aux coins, les deux faces extérieures le sont. Le toit plat lui-même
     * sert de plancher.
     */
    private static void roofCatwalk(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Set<BlockPos> free) {
        int y = bMax.getY() + 1;
        int minX = bMin.getX() - 1, maxX = bMax.getX() + 1;
        int minZ = bMin.getZ() - 1, maxZ = bMax.getZ() + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!(x == minX || x == maxX || z == minZ || z == maxZ)) continue; // bordure seulement
                boolean north = (z - 1 < minZ); // la face nord donne dans le vide
                boolean south = (z + 1 > maxZ);
                boolean west  = (x - 1 < minX);
                boolean east  = (x + 1 > maxX);
                BlockPos p = new BlockPos(x, y, z);
                map.put(p, CreateCompat.catwalkRailing(north, south, east, west));
                free.add(p);
            }
        }
    }

    /** Parapet : muret en bordure de l'avant-toit (anneau extérieur, un cran plus loin que la coque). */
    private static void roofParapet(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, BuildStyle style, Set<BlockPos> free) {
        BlockState wall = style.parapetWall().pick(bMin);
        int y = bMax.getY() + 1;
        int minX = bMin.getX() - 1, maxX = bMax.getX() + 1;
        int minZ = bMin.getZ() - 1, maxZ = bMax.getZ() + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean edge = (x == minX || x == maxX || z == minZ || z == maxZ);
                if (edge) {
                    BlockPos p = new BlockPos(x, y, z);
                    map.put(p, wall);
                    free.add(p);
                }
            }
        }
    }

    /**
     * Décos de toit posées sur une grille CENTRÉE sur le milieu (comme les lanternes) → toujours au moins
     * une au centre, même sur un 7×7, et jamais décalées. Les types TOURNENT (aération, tas de caisses)
     * pour varier. Posées sur le toit (à {@code maxY + 1}), à l'intérieur du parapet.
     */
    private static void roofClutter(Map<BlockPos, BlockState> map, BlockPos bMin, BlockPos bMax, Set<BlockPos> free) {
        int cx = (bMin.getX() + bMax.getX()) / 2, cz = (bMin.getZ() + bMax.getZ()) / 2;
        int top = bMax.getY() + 1;
        for (int x = bMin.getX() + 1; x < bMax.getX(); x++) {
            if (Math.floorMod(x - cx, ROOF_SPACING) != 0) continue;
            for (int z = bMin.getZ() + 1; z < bMax.getZ(); z++) {
                if (Math.floorMod(z - cz, ROOF_SPACING) != 0) continue;
                // ~1/3 des positions → lucarne vitrée ; reste → sortie d'air 2×2
                if (hash(x, 0, z, 103) % 3 == 0) {
                    roofSkylight(map, new BlockPos(x, top, z), bMin, bMax, free);
                } else {
                    roofVent(map, new BlockPos(x, top, z), bMax, free);
                }
            }
        }
    }

    /**
     * Lucarne vitrée : une vitre centrale flanquée d'un bloc plein de chaque côté (curb), et une dalle
     * TOP en dessous du curb pour la transition entre la tuile de toit et la base du curb.
     *
     * <pre>
     *   maxY+1 : [curb][pane][curb]
     *   maxY   : [slab][tile][slab]   ← dalle TOP sous le curb, tuile centrale inchangée
     * </pre>
     */
    private static void roofSkylight(Map<BlockPos, BlockState> map, BlockPos base, BlockPos bMin, BlockPos bMax, Set<BlockPos> free) {
        BlockState pane = Blocks.IRON_BARS.defaultBlockState();
        BlockState curb = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState slab = Blocks.SMOOTH_STONE_SLAB.defaultBlockState()
                .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP);
        int x = base.getX(), y = base.getY(), z = base.getZ();
        map.put(base, pane);
        free.add(base);
        // Orientation : perpendiculaire à l'axe long du bâtiment (hash pour varier)
        boolean alongX = hash(x, 0, z, 107) % 2 == 0;
        if (alongX) {
            if (x - 1 > bMin.getX()) { put(map, free, new BlockPos(x - 1, y, z), curb); put(map, free, new BlockPos(x - 1, y - 1, z), slab); }
            if (x + 1 < bMax.getX()) { put(map, free, new BlockPos(x + 1, y, z), curb); put(map, free, new BlockPos(x + 1, y - 1, z), slab); }
        } else {
            if (z - 1 > bMin.getZ()) { put(map, free, new BlockPos(x, y, z - 1), curb); put(map, free, new BlockPos(x, y - 1, z - 1), slab); }
            if (z + 1 < bMax.getZ()) { put(map, free, new BlockPos(x, y, z + 1), curb); put(map, free, new BlockPos(x, y - 1, z + 1), slab); }
        }
    }

    private static void put(Map<BlockPos, BlockState> map, Set<BlockPos> free, BlockPos p, BlockState state) {
        map.put(p, state);
        free.add(p);
    }

    /** Aération : plaque 2×2 de smooth stone coiffée de rails (grille), repliée vers l'intérieur si au bord. */
    private static void roofVent(Map<BlockPos, BlockState> map, BlockPos base, BlockPos bMax, Set<BlockPos> free) {
        BlockState plate = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState rail = Blocks.RAIL.defaultBlockState();
        int sx = (base.getX() < bMax.getX() - 1) ? 1 : -1; // déborde vers l'intérieur, jamais sur le parapet
        int sz = (base.getZ() < bMax.getZ() - 1) ? 1 : -1;
        for (int dx : new int[]{0, sx}) {
            for (int dz : new int[]{0, sz}) {
                BlockPos p = new BlockPos(base.getX() + dx, base.getY(), base.getZ() + dz);
                map.put(p, plate);
                map.put(p.above(), rail);
                free.add(p);
                free.add(p.above());
            }
        }
    }

    // ---------------------------------------------------------------- Utilitaires

    private static Direction outward(int x, int z, BlockPos bMin, BlockPos bMax) {
        if (x == bMin.getX()) return Direction.WEST;
        if (x == bMax.getX()) return Direction.EAST;
        if (z == bMin.getZ()) return Direction.NORTH;
        return Direction.SOUTH;
    }

    private static int hash(int x, int y, int z, int salt) {
        long h = x * 73856093L ^ y * 19349663L ^ z * 83492791L ^ salt * 2654435761L;
        h ^= h >>> 13;
        h *= 0x5BD1E995L;
        h ^= h >>> 15;
        return (int) (h & 0x7FFFFFFF);
    }
}
