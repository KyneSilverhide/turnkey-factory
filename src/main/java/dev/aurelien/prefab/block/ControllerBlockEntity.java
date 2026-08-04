package dev.aurelien.prefab.block;

import dev.aurelien.prefab.build.BuildPlanner;
import dev.aurelien.prefab.build.BuildStyles;
import dev.aurelien.prefab.build.CostModel;
import dev.aurelien.prefab.build.ExteriorDecorator;
import dev.aurelien.prefab.build.InventoryNetwork;
import dev.aurelien.prefab.build.NaturalTerrain;
import dev.aurelien.prefab.build.RoofType;
import dev.aurelien.prefab.build.Theme;
import dev.aurelien.prefab.compat.CreateCompat;
import dev.aurelien.prefab.menu.ControllerMenu;
import dev.aurelien.prefab.reg.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class ControllerBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MIN_SIZE = 7;          // minimum commun aux 3 dimensions
    public static final int MAX_HORIZONTAL = 63;   // largeur/longueur : impair, donc max 63 (borne demandée : 64)
    public static final int MAX_HEIGHT = 64;       // hauteur : pas par 1
    public static final int HORIZONTAL_STEP = 2;   // largeur/longueur : seulement des valeurs impaires
    public static final int OFFSET_MAX = 15;

    private static final int SCAN_INTERVAL = 20;      // ticks entre deux scans (1 s)
    private static final int MAX_COLLISIONS = 64;     // nombre de blocs en collision renvoyés au client
    private static final int MAX_FLOOR_PREVIEW = 4096; // couvre l'empreinte max (63×63 = 3969)
    private static final int BUILD_PER_TICK = 32;     // blocs posés par tick pendant la construction

    private int width = 7, length = 7, height = 7;
    private int offX = 0, offY = 0, offZ = 0;
    private Direction facing = Direction.NORTH;
    private RoofType roofType = RoofType.FLAT;
    private Theme theme = Theme.STONE;
    private int scanCooldown = 0;

    /** Inventaires connectés (détectés via capability IItemHandler). */
    private final List<BlockPos> linked = new ArrayList<>();

    /** Données calculées côté serveur, synchronisées au client pour le rendu du fantôme. */
    private boolean obstructed = false;
    private final List<BlockPos> collisions = new ArrayList<>();
    /**
     * Aperçu de la couche de sol : toutes les cellules du sol qui seront réellement remplacées (hors
     * cellules déjà conformes), séparées en « sûres » (terrain naturel, cf. {@link NaturalTerrain}) et
     * « à risque » (tout le reste — probablement posé par un joueur). Sert au fantôme : le sol de l'usine
     * remplace désormais la couche de terrain existante (et non plus une couche vide au-dessus), donc on
     * doit clairement montrer ce qui va disparaître, en rouge si ce n'est pas un bloc de terrain classique.
     */
    private final List<BlockPos> floorSafe = new ArrayList<>();
    private final List<BlockPos> floorUnsafe = new ArrayList<>();
    /** Vrai après une construction : on gèle la détection pour ne pas flaguer le bâtiment qu'on vient de poser. */
    private boolean ghostSuppressed = false;

    /** Construction en cours. La file est transitoire : au chargement on recalcule le plan (cf. pendingResume). */
    private final ArrayDeque<BuildPlanner.Placement> buildQueue = new ArrayDeque<>();
    private int buildTotal = 0;
    private int buildRemainingClient = 0;     // miroir côté client (synchronisé)
    private ItemStack waitingFor = ItemStack.EMPTY; // matériau manquant, si en pause
    private boolean creativeBuild = false;    // construction lancée par un joueur créatif : matériaux ignorés
    private boolean ignoreObstacles = false;  // mode « Ignorer » : cellules obstruées retirées du plan
    private boolean pendingResume = false;    // une construction était en cours au chargement : à reprendre
    private List<MaterialLine> clientMaterialLines = List.of(); // miroir client (reçu via getUpdateTag)

    /** Plan mis en cache (pos -> état attendu), invalidé à tout changement de config. */
    private Map<BlockPos, BlockState> cachedPlan = null;
    /** Positions décoratives du plan mis en cache (gratuites), calculées avec {@link #cachedPlan}. */
    private Set<BlockPos> cachedFree = null;
    /** Liste de matériaux mise en cache (ressource -> quantité), invalidée avec le plan. */
    private Map<Item, Integer> cachedBom = null;

    public ControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONTROLLER.get(), pos, state);
    }

    // ----- Dimensions -----

    public int width()  { return width; }
    public int length() { return length; }
    public int height() { return height; }

    public void setDims(int w, int l, int h) {
        if (!buildQueue.isEmpty()) return; // pas de modification pendant une construction
        this.width  = clampHorizontal(w);
        this.length = clampHorizontal(l);
        this.height = clampHeight(h);
        onConfigChanged();
    }

    /** Largeur/longueur : bornées [7, 63] et forcées impaires. */
    public static int clampHorizontal(int v) {
        int c = Math.max(MIN_SIZE, Math.min(MAX_HORIZONTAL, v));
        if (c % 2 == 0) c--;   // ramène à la valeur impaire inférieure (reste >= 7)
        return c;
    }

    /** Hauteur : bornée [7, 64], pas de 1. */
    public static int clampHeight(int v) {
        return Math.max(MIN_SIZE, Math.min(MAX_HEIGHT, v));
    }

    // ----- Décalage du fantôme -----

    public int offsetX() { return offX; }
    public int offsetY() { return offY; }
    public int offsetZ() { return offZ; }

    public void setOffset(int x, int y, int z) {
        if (!buildQueue.isEmpty()) return; // pas de modification pendant une construction
        this.offX = clampOffset(x);
        this.offY = clampOffset(y);
        this.offZ = clampOffset(z);
        onConfigChanged();
    }

    private static int clampOffset(int v) {
        return Math.max(-OFFSET_MAX, Math.min(OFFSET_MAX, v));
    }

    public Direction facing() { return facing; }

    public void setFacing(Direction f) {
        this.facing = f;
        onConfigChanged();
    }

    // ----- Style (thème + toit) -----

    public RoofType roofType() { return roofType; }
    public Theme theme() { return theme; }

    public void setStyle(Theme theme, RoofType roofType) {
        if (!buildQueue.isEmpty()) return; // pas de modification pendant une construction
        this.theme = theme;
        this.roofType = roofType;
        onConfigChanged();
    }

    /** Recalcule collisions immédiatement (côté serveur) puis pousse l'état au client. */
    private void onConfigChanged() {
        ghostSuppressed = false; // l'utilisateur a modifié la config : on réactive le fantôme
        cachedPlan = null;       // la config a changé : le plan doit être recalculé
        cachedBom = null;        // … et la liste de matériaux qui en découle
        if (level instanceof ServerLevel server) {
            recomputeCollisions(server);
        }
        syncToClient();
    }

    /** Plan attendu (mis en cache), recalculé après tout changement de config. */
    private Map<BlockPos, BlockState> currentPlan() {
        if (cachedPlan == null) {
            BlockPos[] mm = buildingMinMax();
            cachedFree = new HashSet<>();
            cachedPlan = BuildPlanner.planMap(mm[0], mm[1], facing, BuildStyles.of(theme), roofType, getBlockPos(), theme, cachedFree);
        }
        return cachedPlan;
    }

    /** Positions purement décoratives (colonnes, avant-toit, parapet, aérations de toit) : gratuites, cf. {@link #billOfMaterials()}. */
    private Set<BlockPos> currentFree() {
        currentPlan(); // assure le calcul conjoint du plan et de son ensemble de positions gratuites
        return cachedFree;
    }

    /**
     * Liste de matériaux (BOM) : agrège le coût en ressources de base ({@link CostModel}) sur tout le
     * plan courant, à l'exception des positions purement décoratives ({@link #currentFree()} — colonnes,
     * avant-toit, parapet, aérations de toit — qui n'ajoutent rien à la solidité de la coque et restent
     * gratuites). Donc fonction directe de la taille, du thème, du toit (plat/pentu) et des décos, et
     * recalculée à chaque changement de config (cache invalidé en même temps que le plan).
     */
    public Map<Item, Integer> billOfMaterials() {
        if (cachedBom == null) {
            Map<Item, Integer> bom = new LinkedHashMap<>();
            Set<BlockPos> free = currentFree();
            for (Map.Entry<BlockPos, BlockState> entry : currentPlan().entrySet()) {
                if (free.contains(entry.getKey())) continue;
                for (Map.Entry<Item, Integer> e : CostModel.costOf(entry.getValue()).entrySet()) {
                    bom.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
            cachedBom = bom;
        }
        return cachedBom;
    }

    /** Une ligne de la liste de matériaux : item requis, quantité requise et disponible dans les inventaires liés. */
    public record MaterialLine(Item item, int required, int available) {
        public int missing() {
            return Math.max(0, required - available);
        }
    }

    /**
     * Liste de matériaux avec disponibilité, dans l'ordre de {@link #billOfMaterials()}. Base commune du
     * livre écrit, du panneau GUI et de l'export clipboard Create.
     */
    public List<MaterialLine> materialLines() {
        Map<Item, Integer> need = currentNeed();
        ServerLevel server = (level instanceof ServerLevel s) ? s : null;
        List<MaterialLine> lines = new ArrayList<>(need.size());
        for (Map.Entry<Item, Integer> e : need.entrySet()) {
            int avail = server != null ? countAvailable(server, e.getKey()) : 0;
            lines.add(new MaterialLine(e.getKey(), e.getValue(), avail));
        }
        return lines;
    }

    /**
     * Besoin en matériaux à l'instant présent : le plan complet ({@link #billOfMaterials()}) tant que
     * rien n'est en cours, ou seulement ce qui RESTE à poser une fois la construction démarrée. Sans
     * ça, le total figé du plan entier réapparaît comme « manquant » dès que le matériau déjà fourni a
     * été consommé par la construction, alors qu'il a déjà servi — donnant l'impression fausse qu'il en
     * faut encore autant (cf. retour de test : mêmes quantités redemandées en boucle).
     */
    private Map<Item, Integer> currentNeed() {
        if (buildQueue.isEmpty() || !(level instanceof ServerLevel server)) return billOfMaterials();
        if (creativeBuild) return Map.of(); // construction créative en cours : rien n'est requis
        Set<BlockPos> free = currentFree();
        Map<Item, Integer> bom = new LinkedHashMap<>();
        for (BuildPlanner.Placement p : buildQueue) {
            if (free.contains(p.pos())) continue;                       // purement décoratif : gratuit
            if (server.getBlockState(p.pos()) == p.state()) continue;    // déjà posé (reprise après rechargement)
            for (Map.Entry<Item, Integer> e : CostModel.costOf(p.state()).entrySet()) {
                bom.merge(e.getKey(), e.getValue(), Integer::sum);
            }
        }
        return bom;
    }

    /**
     * Transforme un livre et plume en livre écrit listant les matériaux requis et, pour chacun, ce qui
     * est disponible dans les inventaires liés et ce qu'il manque (en rouge). Renvoie le livre écrit.
     */
    public ItemStack writeMaterialsBook() {
        List<MaterialLine> need = materialLines();

        List<Filterable<Component>> pages = new ArrayList<>();
        MutableComponent page = Component.empty();
        page.append(Component.translatable("gui.turnkey_factory.book.title_line").withStyle(ChatFormatting.BOLD));
        page.append(Component.literal("\n"));
        page.append(Component.translatable("gui.turnkey_factory.book.dimensions", width, length, height));
        page.append(Component.literal("\n"));
        page.append(Component.translatable("gui.turnkey_factory.controller.theme", theme.label()));
        page.append(Component.literal("  "));
        page.append(Component.translatable("gui.turnkey_factory.controller.roof", roofType.label()));
        page.append(Component.literal("\n\n"));

        int line = 4; // lignes déjà posées (titre + dimensions + thème + vide)
        boolean allOk = true;
        for (MaterialLine ml : need) {
            if (line >= 13) { // page pleine : on passe à la suivante
                pages.add(Filterable.passThrough(page));
                page = Component.empty();
                line = 0;
            }
            MutableComponent l = Component.empty()
                    .append(Component.literal(ml.required() + "× ").withStyle(ChatFormatting.BLACK))
                    .append(new ItemStack(ml.item()).getHoverName());
            if (ml.missing() > 0) {
                allOk = false;
                l.append(Component.translatable("gui.turnkey_factory.book.missing", ml.missing()).withStyle(ChatFormatting.RED));
            } else {
                l.append(Component.literal("  ✓").withStyle(ChatFormatting.DARK_GREEN));
            }
            page.append(l).append(Component.literal("\n"));
            line++;
        }
        page.append(Component.literal("\n"));
        page.append(Component.translatable(allOk ? "gui.turnkey_factory.book.all_available" : "gui.turnkey_factory.book.missing_resources")
                .withStyle(allOk ? ChatFormatting.DARK_GREEN : ChatFormatting.RED));
        pages.add(Filterable.passThrough(page));

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        // Titre/auteur du livre écrit : métadonnées String simples (pas des Component), donc figées à la
        // création — pas de localisation par joueur possible ici, contrairement aux pages ci-dessus.
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Matériaux"), // titre court (limite de longueur des livres écrits)
                "Bloc de contrôle", 0, pages, false));
        return book;
    }

    /**
     * Écrit la même liste de matériaux sur un clipboard Create (une entrée cochable par ligne, avec
     * icône). Renvoie false si Create ou le clipboard n'est pas disponible (aucune modification faite).
     */
    public boolean writeMaterialsClipboard(ItemStack clipboardStack) {
        List<CreateCompat.ClipboardLine> lines = new ArrayList<>();
        for (MaterialLine ml : materialLines()) {
            MutableComponent text = new ItemStack(ml.item()).getHoverName().copy();
            if (ml.missing() > 0) {
                text.append(Component.translatable("gui.turnkey_factory.book.missing", ml.missing()).withStyle(ChatFormatting.RED));
            }
            lines.add(new CreateCompat.ClipboardLine(new ItemStack(ml.item()), text, ml.required()));
        }
        return CreateCompat.writeMaterialsClipboard(clipboardStack, lines);
    }

    /** Côté client : dernière liste de matériaux reçue du serveur (cf. {@link #getUpdateTag}), pour le panneau GUI. */
    public List<MaterialLine> clientMaterialLines() {
        return clientMaterialLines;
    }

    // ----- Inventaires liés -----

    public int linkedCount() { return linked.size(); }

    public List<BlockPos> linkedPositions() { return linked; }

    // ----- Fantôme : géométrie et collision -----

    /** Coins (min, max) inclusifs en cellules-blocs de la future construction. */
    public BlockPos[] buildingMinMax() {
        Direction lateral = facing.getClockWise();
        BlockPos controller = getBlockPos();
        // Le bâtiment commence à MARGIN+1 blocs devant le contrôleur : ainsi la marge réservée (qui
        // s'étend de MARGIN vers le contrôleur) s'arrête juste DEVANT lui → le bloc factory et les coffres
        // qu'on lui accole restent hors zone bleue, sans collision ni nettoyage intempestif.
        BlockPos s = controller
                .relative(facing, 1 + ExteriorDecorator.MARGIN)   // devant, au-delà de la marge réservée
                .relative(lateral.getOpposite(), (width - 1) / 2) // centré latéralement
                // Sol au niveau du contrôleur (donc 1 bloc plus haut que le sol extérieur) : la marche
                // franchie par les escaliers d'entrée (cf. ExteriorDecorator#entranceStairs) plutôt qu'un
                // sol flush qui empiétait sur la couche de terrain sous le contrôleur lui-même.
                .offset(offX, offY, offZ);                        // décalage joueur
        BlockPos e = s
                .relative(facing, length - 1)
                .relative(lateral, width - 1)
                .above(height - 1);
        return new BlockPos[]{
                new BlockPos(Math.min(s.getX(), e.getX()), Math.min(s.getY(), e.getY()), Math.min(s.getZ(), e.getZ())),
                new BlockPos(Math.max(s.getX(), e.getX()), Math.max(s.getY(), e.getY()), Math.max(s.getZ(), e.getZ()))
        };
    }

    /** Volume RÉSERVÉ = bâtiment + marge (pour la déco extérieure). Sert au fantôme, à la collision et au nettoyage. */
    public BlockPos[] reservedMinMax() {
        BlockPos[] mm = buildingMinMax();
        // Vers le haut : au moins la marge déco, mais assez pour englober l'apex d'un toit pentu.
        int up = Math.max(ExteriorDecorator.MARGIN_UP, BuildPlanner.roofTopExtension(mm[0], mm[1], roofType));
        return new BlockPos[]{
                mm[0].offset(-ExteriorDecorator.MARGIN, 0, -ExteriorDecorator.MARGIN),
                mm[1].offset(ExteriorDecorator.MARGIN, up, ExteriorDecorator.MARGIN)
        };
    }

    /** Boîte du bâtiment (coque). */
    public AABB innerBox() {
        return boxOf(buildingMinMax());
    }

    /** Boîte de la zone de sécurité réservée (bâtiment + marge déco). */
    public AABB reservedBox() {
        return boxOf(reservedMinMax());
    }

    private static AABB boxOf(BlockPos[] mm) {
        return new AABB(mm[0].getX(), mm[0].getY(), mm[0].getZ(),
                mm[1].getX() + 1, mm[1].getY() + 1, mm[1].getZ() + 1);
    }

    public boolean isObstructed() { return obstructed; }

    public List<BlockPos> collisions() { return collisions; }

    /** Cellules du sol qui seront remplacées et sont du terrain naturel (aperçu fantôme, vert). */
    public List<BlockPos> floorSafe() { return floorSafe; }

    /** Cellules du sol qui seront remplacées mais ne sont PAS du terrain naturel (aperçu fantôme, rouge). */
    public List<BlockPos> floorUnsafe() { return floorUnsafe; }

    /**
     * Un bloc « obstrue » s'il n'est PAS naturel. Minecraft ne mémorise pas qui a posé un bloc :
     * on ignore donc le terrain et la végétation générés par le monde (on peut tout raser), et on
     * considère tout le reste comme une construction du joueur (à protéger). Heuristique, donc faillible.
     */
    private static boolean isObstructing(BlockState s) {
        if (s.isAir() || s.canBeReplaced()) return false;
        if (!s.getFluidState().isEmpty()) return false;
        return !NaturalTerrain.isNaturalGround(s);
    }

    /** Recalcule l'obstruction et la liste plafonnée des blocs en collision. Renvoie true si ça a changé. */
    private boolean recomputeCollisions(ServerLevel server) {
        // Pas de détection pendant (ou juste après) une construction : on ne veut pas flaguer nos propres blocs.
        if (!buildQueue.isEmpty() || ghostSuppressed) {
            return false;
        }
        boolean wasObstructed = obstructed;
        List<BlockPos> previous = new ArrayList<>(collisions);

        collisions.clear();
        floorSafe.clear();
        floorUnsafe.clear();
        boolean obs = false;
        Map<BlockPos, BlockState> plan = currentPlan();
        BlockPos[] shell = buildingMinMax();
        int floorY = shell[0].getY();
        BlockPos[] reserved = reservedMinMax();
        for (BlockPos p : BlockPos.betweenClosed(reserved[0], reserved[1])) {
            if (p.equals(getBlockPos())) continue;
            if (!server.isLoaded(p)) continue;
            BlockState current = server.getBlockState(p);
            BlockPos key = p.immutable();
            BlockState expected = plan.get(key);

            // On ne réclame QUE les cellules où l'on va RÉELLEMENT poser un bloc de coque. Tout le reste
            // est ignoré : intérieur préservé, marge libre, déco — et surtout les mécanismes du joueur
            // (qu'on vient envelopper) ou les coffres posés autour du contrôleur. Le but du mod est de
            // recouvrir l'existant, pas d'exiger un volume vide.
            if (expected == null || expected.isAir()) continue; // aucune pose prévue ici
            if (current == expected) continue;                  // déjà conforme (construction idempotente)
            if (!inShell(key, shell)) continue;                 // déco de marge : « à nous », tolérée
            // Cellule de coque où l'on doit poser un bloc, mais une construction étrangère l'occupe.
            boolean unsafe = isObstructing(current);
            if (unsafe) {
                obs = true;
                if (collisions.size() < MAX_COLLISIONS) collisions.add(key);
            }
            // Sol de l'usine : remplace maintenant la couche de terrain existante (cf. buildingMinMax) —
            // on montre TOUT ce qui va disparaître là, pas seulement les cellules « à risque ».
            if (key.getY() == floorY) {
                List<BlockPos> bucket = unsafe ? floorUnsafe : floorSafe;
                if (bucket.size() < MAX_FLOOR_PREVIEW) bucket.add(key);
            }
        }
        obstructed = obs;
        return obstructed != wasObstructed || !collisions.equals(previous);
    }

    private static boolean inShell(BlockPos p, BlockPos[] shell) {
        return p.getX() >= shell[0].getX() && p.getX() <= shell[1].getX()
                && p.getY() >= shell[0].getY() && p.getY() <= shell[1].getY()
                && p.getZ() >= shell[0].getZ() && p.getZ() <= shell[1].getZ();
    }

    // ----- Tick serveur : scan inventaires + collisions -----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;

        if (pendingResume) {
            pendingResume = false;
            resumeBuild();
        }

        boolean building = !buildQueue.isEmpty();
        if (building) {
            tickBuild(server);
        }

        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            rescan(server);
            recomputeCollisions(server);
            // Toujours resynchroniser (~1/s) : la disponibilité des matériaux dans les inventaires liés
            // peut changer sans que la structure du bâtiment/des liens ne bouge (panneau ressources GUI).
            syncToClient();
        }
    }

    // ----- Construction -----

    public boolean isBuilding() {
        return buildRemaining() > 0;
    }

    public int buildTotal() {
        return buildTotal;
    }

    public int buildRemaining() {
        return buildQueue.isEmpty() ? buildRemainingClient : buildQueue.size();
    }

    public ItemStack waitingFor() {
        return waitingFor;
    }

    /** Comment gérer un site obstrué au démarrage : refuser, écraser, ou construire autour. */
    public enum BuildStartMode { NORMAL, FORCE, IGNORE }

    /**
     * Démarre la construction. Refuse si déjà en cours, ou si obstrué et {@code mode} vaut
     * {@link BuildStartMode#NORMAL}. Renvoie true si lancée.
     * @param creative si vrai (joueur en mode créatif), la construction ignore les inventaires liés et
     *                 ne consomme/attend aucun matériau (cf. {@link #tickBuild}).
     * @param mode {@link BuildStartMode#FORCE} démarre MÊME si le site est obstrué et écrase les blocs
     *             signalés en rouge (la détection « terrain naturel » est une heuristique faillible, cf.
     *             {@link NaturalTerrain} : certains blocs modded ne sont couverts par aucun tag connu et
     *             sont donc protégés par erreur). {@link BuildStartMode#IGNORE} démarre aussi malgré
     *             l'obstruction, mais RETIRE ces cellules du plan au lieu de les écraser : l'usine
     *             construit autour, ce bloc reste tel quel, sans matériau supplémentaire à prévoir (la
     *             liste de matériaux affichée avant de démarrer reste inchangée — au pire légèrement
     *             surestimée pour ces quelques cellules, tant pis). Dans les deux cas, le joueur a déjà vu
     *             en rouge, dans le fantôme, exactement quelles cellules sont concernées avant de choisir.
     */
    public boolean startBuild(boolean creative, BuildStartMode mode) {
        if (!(level instanceof ServerLevel server)) return false;
        if (!buildQueue.isEmpty()) return false;
        // Aucune source de matériau en survie : refuser le démarrage plutôt que de laisser la file se
        // remplir et poser quand même les éléments purement décoratifs (colonnes…), gratuits par nature
        // (cf. currentFree()) — sans ça, Forcer/Ignorer construisait une coquille partielle que rien ne
        // pouvait jamais compléter, sans qu'aucun matériau ne soit prélevé nulle part (retour utilisateur).
        if (!creative && linked.isEmpty()) return false;

        ghostSuppressed = false;       // force une vraie vérification du site
        recomputeCollisions(server);
        if (obstructed && mode == BuildStartMode.NORMAL) {
            syncToClient();
            return false;
        }

        creativeBuild = creative;
        ignoreObstacles = obstructed && mode == BuildStartMode.IGNORE;
        List<BuildPlanner.Placement> order = BuildPlanner.order(currentPlan());
        if (ignoreObstacles) {
            BlockPos[] shell = buildingMinMax();
            order.removeIf(p -> inShell(p.pos(), shell) && isObstructing(server.getBlockState(p.pos())));
        }
        buildQueue.addAll(order);
        buildTotal = buildQueue.size();
        waitingFor = ItemStack.EMPTY;
        syncToClient();
        return true;
    }

    /** Reprend une construction interrompue par un rechargement : on recalcule le plan ; tickBuild saute le déjà-posé. */
    private void resumeBuild() {
        if (!buildQueue.isEmpty() || !(level instanceof ServerLevel server)) return;
        // Même garde qu'au démarrage (cf. startBuild) : si les coffres liés ont disparu entre-temps (ou
        // n'ont jamais existé pour cette construction), ne pas relancer la file — sinon le balayage
        // reprendrait quand même les éléments gratuits sans jamais pouvoir poser le reste.
        if (!creativeBuild && linked.isEmpty()) {
            buildRemainingClient = 0; // évite un statut « en construction » figé indéfiniment côté client
            syncToClient();
            return;
        }
        List<BuildPlanner.Placement> order = BuildPlanner.order(currentPlan());
        if (ignoreObstacles) { // même filtre qu'au démarrage (cf. startBuild), réappliqué sur l'état actuel du monde
            BlockPos[] shell = buildingMinMax();
            order.removeIf(p -> inShell(p.pos(), shell) && isObstructing(server.getBlockState(p.pos())));
        }
        buildQueue.addAll(order);
        buildTotal = buildQueue.size();
        waitingFor = ItemStack.EMPTY;
        syncToClient();
    }

    public void cancelBuild() {
        buildQueue.clear();
        buildTotal = 0;
        buildRemainingClient = 0;
        waitingFor = ItemStack.EMPTY;
        ignoreObstacles = false;
        syncToClient();
    }

    /**
     * Construit jusqu'à {@link #BUILD_PER_TICK} blocs. Un item manquant NE bloque PLUS toute la file :
     * on continue de parcourir les placements suivants (queue = {@link BuildPlanner#order}, murs puis
     * vitres en dernier — cf. {@link BuildPlanner#order}) et on ne saute que ceux qui dépendent
     * spécifiquement de l'item manquant. Sans ça, une simple pénurie de cobblestone au milieu des murs
     * empêchait TOUTES les vitres (placées après, en toute fin de file) d'être posées, alors que le
     * verre était disponible — cf. retour de test.
     */
    private void tickBuild(ServerLevel server) {
        BlockPos[] shell = buildingMinMax();
        int placed = 0;
        // Disponibilité par item, mise en cache pour ce tick : évite de rescanner les inventaires liés à
        // chaque placement bloqué par le même item manquant (potentiellement des milliers sur un grand
        // bâtiment), tout en restant exacte au fur et à mesure que les placements réussis consomment.
        Map<Item, Integer> available = new HashMap<>();
        Item firstMissing = null;

        Iterator<BuildPlanner.Placement> it = buildQueue.iterator();
        while (placed < BUILD_PER_TICK && it.hasNext()) {
            BuildPlanner.Placement p = it.next();

            if (p.pos().equals(getBlockPos())) { // ne JAMAIS écraser le contrôleur lui-même
                it.remove();
                continue;
            }

            BlockState target = p.state();
            BlockState current = server.getBlockState(p.pos());

            if (current == target) {           // déjà bon (souvent air == air) : on saute
                it.remove();
                continue;
            }

            // Décoration purement esthétique de la marge (colonne, silo, aération de toit…), hors coque :
            // recomputeCollisions() tolère volontairement un bloc étranger ici (coffres liés du joueur,
            // notamment) et ne l'a donc jamais signalé en rouge dans le fantôme. Si on l'écrasait quand
            // même ici, on détruirait sans prévenir un bloc que le joueur n'a jamais eu l'occasion de
            // protéger via Forcer/Ignorer — contrairement à la coque, l'élément décoratif n'en vaut pas
            // la peine : on l'abandonne simplement.
            if (!inShell(p.pos(), shell) && isObstructing(current)) {
                it.remove();
                continue;
            }

            if (target.isAir()) {              // dégagement : aucun matériau requis
                server.setBlock(p.pos(), target, Block.UPDATE_ALL);
                it.remove();
                placed++;
                continue;
            }

            Map<Item, Integer> cost = (creativeBuild || currentFree().contains(p.pos())) ? Map.of() : CostModel.costOf(target);
            if (cost.isEmpty()) {              // aucun coût (créatif, libre, ou purement décoratif) : posé librement
                server.setBlock(p.pos(), target, Block.UPDATE_ALL);
                it.remove();
                placed++;
                continue;
            }

            Item missing = tryConsume(server, cost, available);
            if (missing == null) {
                server.setBlock(p.pos(), target, Block.UPDATE_ALL);
                it.remove();
                placed++;
            } else if (firstMissing == null) {
                firstMissing = missing; // on continue : cet item manquant ne doit pas bloquer le reste de la file
            }
        }

        if (firstMissing != null) {
            if (waitingFor.isEmpty() || !waitingFor.is(firstMissing)) {
                waitingFor = new ItemStack(firstMissing);
                syncToClient();
            }
        } else if (!waitingFor.isEmpty()) {
            waitingFor = ItemStack.EMPTY;
            syncToClient();
        }

        if (buildQueue.isEmpty()) {
            fixupConnections(server); // raccorde vitres/murs maintenant que tous les voisins existent
            waitingFor = ItemStack.EMPTY;
            buildTotal = 0;
            ghostSuppressed = true;   // construction finie : on n'affiche plus d'obstruction sur nos propres blocs
            obstructed = false;
            collisions.clear();
            floorSafe.clear();
            floorUnsafe.clear();
            syncToClient();
        }
    }

    /**
     * Après la pose : recalcule les états de connexion des blocs « raccordables » (vitres/barreaux,
     * murs). Comme la coque est bâtie bloc par bloc, chacun a été posé avant ses voisins et n'a donc
     * pas pu se raccorder ; on refait la passe une fois le bâtiment complet.
     */
    private void fixupConnections(ServerLevel server) {
        for (BlockPos pos : currentPlan().keySet()) {
            if (pos.equals(getBlockPos()) || !server.isLoaded(pos)) continue;
            BlockState state = server.getBlockState(pos);
            // GlassPaneBlock hérite d'IronBarsBlock → couvre vitres et barreaux ; WallBlock → parapet.
            // (Les catwalk railings Create Deco fixent leurs côtés à la pose : pas de fixup ici.)
            if (!(state.getBlock() instanceof IronBarsBlock || state.getBlock() instanceof WallBlock)) continue;
            BlockState updated = Block.updateFromNeighbourShapes(state, server, pos);
            if (updated != state) {
                server.setBlock(pos, updated, Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Consomme un coût (plusieurs items possibles) dans les inventaires liés, en tout-ou-rien, via un
     * cache de disponibilité {@code available} partagé sur tout un {@link #tickBuild}. La disponibilité
     * réelle n'est interrogée qu'une fois par item (première rencontre) puis tenue à jour localement au
     * fil des prélèvements — sans ça, reparcourir les inventaires liés pour CHAQUE placement bloqué par
     * le même item manquant serait bien trop coûteux sur un grand bâtiment (potentiellement des milliers
     * de placements en attente du même matériau). Renvoie null si tout a été prélevé, sinon le premier
     * item manquant.
     */
    private Item tryConsume(ServerLevel server, Map<Item, Integer> cost, Map<Item, Integer> available) {
        // Phase 1 : vérifier la disponibilité de chaque composant (via le cache).
        for (Map.Entry<Item, Integer> e : cost.entrySet()) {
            int avail = available.computeIfAbsent(e.getKey(), item -> countAvailable(server, item));
            if (avail < e.getValue()) {
                return e.getKey();
            }
        }
        // Phase 2 : prélever (la disponibilité est garantie) et mettre le cache à jour.
        for (Map.Entry<Item, Integer> e : cost.entrySet()) {
            extract(server, e.getKey(), e.getValue());
            available.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
        return null;
    }

    private int countAvailable(ServerLevel server, Item item) {
        return InventoryNetwork.countEligible(server, linked, eligibility(item));
    }

    private void extract(ServerLevel server, Item item, int amount) {
        InventoryNetwork.extractEligible(server, linked, eligibility(item), amount);
    }

    /**
     * Éligibilité pour un coût : {@link Items#OAK_PLANKS}/{@link Items#OAK_LOG} sont des représentants
     * d'une famille (n'importe quelle essence — chêne, épicéa, bouleau… — convient) ; tout autre item
     * exige une correspondance exacte.
     */
    private static Predicate<Item> eligibility(Item representative) {
        if (representative == Items.OAK_PLANKS) return i -> new ItemStack(i).is(ItemTags.PLANKS);
        if (representative == Items.OAK_LOG) return i -> new ItemStack(i).is(ItemTags.LOGS);
        return i -> i == representative;
    }

    /**
     * Flood-fill BFS 6-directions depuis le contrôleur (cf. {@link InventoryNetwork#rescan}).
     * Renvoie true si l'ensemble lié a changé.
     */
    private boolean rescan(ServerLevel server) {
        return InventoryNetwork.rescan(server, getBlockPos(), linked);
    }

    // ----- Synchronisation client -----

    private void syncToClient() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        // données transitoires (non persistées sur disque mais utiles au client)
        tag.putBoolean("obstructed", obstructed);
        tag.putLongArray("collisions", collisions.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("floorSafe", floorSafe.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("floorUnsafe", floorUnsafe.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("buildTotal", buildTotal);
        tag.putInt("buildRemaining", buildQueue.size());
        tag.putString("waiting", waitingFor.isEmpty()
                ? "" : BuiltInRegistries.ITEM.getKey(waitingFor.getItem()).toString());
        ListTag materials = new ListTag();
        for (MaterialLine ml : materialLines()) {
            CompoundTag m = new CompoundTag();
            m.putString("id", BuiltInRegistries.ITEM.getKey(ml.item()).toString());
            m.putInt("req", ml.required());
            m.putInt("avail", ml.available());
            materials.add(m);
        }
        tag.put("materials", materials);
        return tag;
    }

    // ----- Persistance -----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("w", width);
        tag.putInt("l", length);
        tag.putInt("h", height);
        tag.putInt("ox", offX);
        tag.putInt("oy", offY);
        tag.putInt("oz", offZ);
        tag.putInt("facing", facing.get2DDataValue());
        tag.putInt("roof", roofType.ordinal());
        tag.putInt("theme", theme.ordinal());
        tag.putLongArray("linked", linked.stream().mapToLong(BlockPos::asLong).toArray());
        // On ne persiste pas la file (le plan est recalculable) mais seulement le fait qu'on construisait.
        tag.putBoolean("building", !buildQueue.isEmpty());
        tag.putBoolean("creativeBuild", creativeBuild);
        tag.putBoolean("ignoreObstacles", ignoreObstacles);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("w")) width  = clampHorizontal(tag.getInt("w"));
        if (tag.contains("l")) length = clampHorizontal(tag.getInt("l"));
        if (tag.contains("h")) height = clampHeight(tag.getInt("h"));
        if (tag.contains("ox")) offX = tag.getInt("ox");
        if (tag.contains("oy")) offY = tag.getInt("oy");
        if (tag.contains("oz")) offZ = tag.getInt("oz");
        if (tag.contains("facing")) facing = Direction.from2DDataValue(tag.getInt("facing"));
        if (tag.contains("roof")) roofType = RoofType.byOrdinal(tag.getInt("roof"));
        if (tag.contains("theme")) theme = Theme.byOrdinal(tag.getInt("theme"));

        linked.clear();
        for (long packed : tag.getLongArray("linked")) {
            linked.add(BlockPos.of(packed));
        }
        cachedPlan = null; // config potentiellement modifiée : on recalculera le plan à la demande
        if (tag.getBoolean("building")) {
            creativeBuild = tag.getBoolean("creativeBuild");
            ignoreObstacles = tag.getBoolean("ignoreObstacles");
            pendingResume = true; // une construction était en cours : reprise au prochain tick serveur
        }
        // transitoire (présent uniquement dans les paquets réseau)
        if (tag.contains("obstructed")) obstructed = tag.getBoolean("obstructed");
        if (tag.contains("collisions")) {
            collisions.clear();
            for (long packed : tag.getLongArray("collisions")) {
                collisions.add(BlockPos.of(packed));
            }
        }
        if (tag.contains("floorSafe")) {
            floorSafe.clear();
            for (long packed : tag.getLongArray("floorSafe")) {
                floorSafe.add(BlockPos.of(packed));
            }
        }
        if (tag.contains("floorUnsafe")) {
            floorUnsafe.clear();
            for (long packed : tag.getLongArray("floorUnsafe")) {
                floorUnsafe.add(BlockPos.of(packed));
            }
        }
        if (tag.contains("buildTotal")) buildTotal = tag.getInt("buildTotal");
        if (tag.contains("buildRemaining")) buildRemainingClient = tag.getInt("buildRemaining");
        if (tag.contains("waiting")) {
            String id = tag.getString("waiting");
            if (id.isEmpty()) {
                waitingFor = ItemStack.EMPTY;
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                Item it = rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
                waitingFor = it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
            }
        }
        if (tag.contains("materials")) {
            List<MaterialLine> parsed = new ArrayList<>();
            for (net.minecraft.nbt.Tag t : tag.getList("materials", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) t;
                ResourceLocation rl = ResourceLocation.tryParse(m.getString("id"));
                Item it = rl != null ? BuiltInRegistries.ITEM.get(rl) : Items.AIR;
                if (it != Items.AIR) {
                    parsed.add(new MaterialLine(it, m.getInt("req"), m.getInt("avail")));
                }
            }
            clientMaterialLines = parsed;
        }
    }

    // ----- Menu -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.turnkey_factory.controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ControllerMenu(id, inv, this);
    }
}
