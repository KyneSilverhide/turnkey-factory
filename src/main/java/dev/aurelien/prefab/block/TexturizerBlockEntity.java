package dev.aurelien.prefab.block;

import dev.aurelien.prefab.build.InventoryNetwork;
import dev.aurelien.prefab.build.NaturalTerrain;
import dev.aurelien.prefab.build.ToolDurability;
import dev.aurelien.prefab.menu.TexturizerMenu;
import dev.aurelien.prefab.reg.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Retexture la surface naturelle autour du bloc en un motif FIXE, au choix parmi deux {@link Palette} :
 * pierre (cobblestone/gravier/andésite/pierre, à la pioche) ou terre (terre/podzol/terre enracinée, à la
 * pelle), toujours à parts égales. Ne consomme que le matériau de coût de la palette active dans les
 * inventaires liés (1 par cellule, quel que soit le bloc tiré du motif) — c'est lui qui « paie » le
 * mélange entier. Les blocs de sol retirés ne sont ni récupérés ni redéposés : ils sont directement
 * remplacés (aucun appel à {@code Block.getDrops}). Option « parcelles d'herbe » (disponible dans les
 * deux motifs, cf. {@link #setCoarseDirtPatches}) : une part des cellules devient une parcelle de terre
 * grossière + pousse (toujours la paire, jamais l'une sans l'autre) au lieu du motif — même coût que
 * n'importe quelle autre cellule, ce n'est qu'une variante d'apparence ; c'est le seul endroit où de la
 * terre grossière apparaît.
 * Se propage en cercles concentriques depuis la colonne juste sous le bloc, en épousant les petites
 * variations de hauteur du terrain (mais jamais les falaises/grottes : cf. {@link #findSurfaceY}), et
 * s'arrête net sur tout ce qui n'est pas du sol naturel — ne comble donc jamais un trou. Consomme l'outil
 * exigé par la palette active (durabilité par bloc) et exige un inventaire lié pour démarrer, exactement
 * comme la niveleuse.
 */
public class TexturizerBlockEntity extends BlockEntity implements MenuProvider, Container, CenterableMachine {
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 8;

    private static final int STEP_WINDOW = 3;       // amplitude de suivi du terrain par saut de colonne
    private static final int SCAN_INTERVAL = 20;     // ticks entre deux scans d'inventaires liés (1 s)
    private static final int WORK_PER_TICK = 4;      // cellules traitées par tick
    private static final int MAX_PREVIEW = 128;      // cellules renvoyées au client (fantôme)
    // 1 point de durabilité tous les N cellules (pas une par cellule) : à WORK_PER_TICK=4, une pioche
    // en fer (250 pts) durait ~3s sans ce ralenti — bien trop vite pour une machine censée tourner en
    // tâche de fond. Avec ce ralenti, une pioche en fer couvre ~8000 cellules (un disque de rayon ~50).
    private static final int TOOL_DAMAGE_INTERVAL = 32;

    /** Motif « pierre », parts égales (25% chacun) : cobblestone / gravier / andésite / pierre. Payé en cobblestone. */
    private static final BlockState[] STONE_MOSAIC = {
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
    };

    /**
     * Motif « terre », parts égales (33% chacun) : terre / podzol / terre enracinée. Payé en terre. La
     * terre grossière n'apparaît JAMAIS dans ce mélange payant — elle n'existe que via l'option
     * « parcelles gratuites » (terre grossière + pousse, jamais l'une sans l'autre), disponible dans les
     * deux motifs.
     */
    private static final BlockState[] DIRT_MOSAIC = {
            Blocks.DIRT.defaultBlockState(),
            Blocks.PODZOL.defaultBlockState(),
            Blocks.ROOTED_DIRT.defaultBlockState(),
    };

    /** Les deux motifs proposés par le texturiseur, chacun avec son matériau de coût et son outil dédiés. */
    public enum Palette {
        STONE(STONE_MOSAIC, Items.COBBLESTONE, ItemTags.PICKAXES),
        DIRT(DIRT_MOSAIC, Items.DIRT, ItemTags.SHOVELS);

        public final BlockState[] mosaic;
        public final Item costItem;
        public final TagKey<Item> toolTag;

        Palette(BlockState[] mosaic, Item costItem, TagKey<Item> toolTag) {
            this.mosaic = mosaic;
            this.costItem = costItem;
            this.toolTag = toolTag;
        }
    }

    private static final float COARSE_DIRT_CHANCE = 0.05f; // part des cellules transformées en parcelle de terre grossière + pousse
    // Une parcelle gratuite pose TOUJOURS une pousse (jamais de terre grossière nue) : cf. placePattern.
    private static final float FERN_CHANCE = 0.2f;          // parmi les pousses, part de fougère (sinon herbe)

    public static final int SLOT_TOOL = 0;

    public static final int STATUS_NO_TOOL = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_MISSING_MATERIAL = 3;
    public static final int STATUS_INACTIVE = 4;
    public static final int STATUS_NO_LINK = 5;

    private int radius = DEFAULT_RADIUS;
    private Palette palette = Palette.STONE;
    private boolean coarseDirtPatches = false;
    private int scanCooldown = 0;
    private int toolCharge = 0;
    private boolean active = false;
    /** Vrai dès qu'un plan a été calculé au moins une fois (pose ou chargement) : évite un aperçu vide au premier tick. */
    private boolean planComputed = false;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<BlockPos> linked = new ArrayList<>();
    /** Cf. {@link CenterableMachine} : {@code null} = cette machine est sa propre référence géométrique. */
    @Nullable
    private BlockPos centerPos;
    /**
     * Positions déjà converties par le motif terre (persisté, jamais synchronisé au client — cf.
     * {@link #getUpdateTag}). Le motif terre inclut la terre nue elle-même parmi ses 4 variantes cibles,
     * donc contrairement au motif pierre, on ne peut pas déduire « déjà fait » de l'identité du bloc en
     * place (une case déjà en terre nue serait sinon prise pour un terrain naturel jamais traité, alors
     * que c'est justement le cas le plus courant à texturer). On retient donc nous-mêmes les cases déjà
     * traitées : une case n'est reconvertie qu'une fois, quel que soit le résultat du tirage.
     */
    private final Set<Long> dirtTexturedCells = new HashSet<>();

    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    /** Cellules à venir, plafonnées, synchronisées au client pour le fantôme. */
    private final List<BlockPos> preview = new ArrayList<>();

    private int status = STATUS_NO_TOOL;
    private int queueSizeClient = 0;
    private int totalCells = 0;
    private int available = 0;

    public TexturizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEXTURIZER.get(), pos, state);
    }

    // ----- Configuration -----

    public int radius() { return radius; }
    public Palette palette() { return palette; }
    public boolean coarseDirtPatches() { return coarseDirtPatches; }
    public int status() { return status; }
    public int queueSize() { return queueSizeClient; }
    public int totalCells() { return totalCells; }
    public int available() { return available; }
    public List<BlockPos> preview() { return preview; }
    public boolean active() { return active; }

    // ----- Checklist GUI (montre TOUTES les conditions à la fois, cf. TurretScreen#drawChecklist) -----

    public boolean hasLink() { return !linked.isEmpty(); }
    public boolean hasTool() { return !items.get(SLOT_TOOL).isEmpty() && items.get(SLOT_TOOL).is(palette.toolTag); }
    public boolean hasMaterial() { return available() > 0; }

    /** Démarre/arrête le travail. Refuse le démarrage si aucun inventaire n'est lié (rien à puiser comme pattern). */
    public void setActive(boolean value) {
        if (value && linked.isEmpty()) {
            return;
        }
        this.active = value;
        syncToClient();
    }

    public void setRadius(int r) {
        this.radius = clampRadius(r);
        onConfigChanged();
    }

    public void setCoarseDirtPatches(boolean value) {
        this.coarseDirtPatches = value;
        onConfigChanged();
    }

    /**
     * Bascule le motif (pierre/terre). Le motif « terre » réutilise déjà la terre grossière comme l'une
     * de ses 4 variantes payantes ; l'option « parcelles gratuites » (qui pose aussi de la terre
     * grossière, gratuitement) n'a donc de sens qu'en motif pierre — cf. {@link #serverTick()}.
     */
    public void setPalette(Palette value) {
        this.palette = value;
        onConfigChanged();
    }

    public static int clampRadius(int v) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, v));
    }

    // ----- CenterableMachine -----

    @Override
    @Nullable
    public BlockPos centerPos() { return centerPos; }

    @Override
    public void setCenterPos(@Nullable BlockPos pos) {
        this.centerPos = pos;
        onConfigChanged();
    }

    /** La config a changé : la file en cours ne correspond plus à la zone → on la jette et on recalcule tout de suite. */
    private void onConfigChanged() {
        queue.clear();
        preview.clear();
        totalCells = 0;
        if (level instanceof ServerLevel server) {
            computePlan(server);
            planComputed = true;
        }
        syncToClient();
    }

    // ----- Calcul du plan (propagation concentrique le long de la surface) -----

    private record Candidate(int x, int z, int refY, int distSq) {}

    /**
     * BFS pondéré par la distance XZ au carré (file de priorité, pas une simple FIFO) : la colonne la
     * plus proche du bloc est toujours résolue en premier, ce qui donne une propagation en cercles
     * concentriques plutôt qu'un losange. Chaque colonne résout sa propre hauteur de surface à partir
     * de la hauteur de sa voisine ({@link #findSurfaceY}, fenêtre {@link #STEP_WINDOW}) : une colonne
     * sans surface valide dans cette fenêtre (trou, falaise, terrain non naturel) n'est pas ajoutée et
     * ne propage pas plus loin dans cette direction — c'est ce qui garantit qu'on ne comble jamais un
     * trou et qu'on ne grimpe pas une paroi.
     */
    private void computePlan(ServerLevel server) {
        queue.clear();
        preview.clear();

        BlockPos origin = originPos();
        int ox = origin.getX();
        int oz = origin.getZ();
        int seedY = origin.getY() - 1; // « il commence sur le bloc en dessous »
        int r2 = radius * radius;
        int maxCells = (2 * radius + 1) * (2 * radius + 1);

        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::distSq));
        Set<Long> visited = new HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        visited.add(packXZ(ox, oz));
        frontier.add(new Candidate(ox, oz, seedY, 0));

        List<BlockPos> ordered = new ArrayList<>();
        while (!frontier.isEmpty() && ordered.size() < maxCells) {
            Candidate c = frontier.poll();
            Integer surfaceY = findSurfaceY(server, c.x(), c.z(), c.refY(), p, palette);
            if (surfaceY == null) continue; // pas de sol naturel accessible ici : on ne propage pas plus loin

            BlockPos pos = new BlockPos(c.x(), surfaceY, c.z());
            // On propage TOUJOURS à travers une cellule déjà texturée (sinon un disque déjà fini bloque
            // toute extension de rayon), mais on ne la remet pas au travail : seules les cellules encore
            // naturelles-et-pas-finies sont mises en file.
            if (needsTexturing(server.getBlockState(pos), pos, palette)) {
                ordered.add(pos);
                if (preview.size() < MAX_PREVIEW) preview.add(pos);
            }

            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = c.x() + d.getStepX();
                int nz = c.z() + d.getStepZ();
                int dx = nx - ox;
                int dz = nz - oz;
                int distSq = dx * dx + dz * dz;
                if (distSq > r2) continue;
                if (!visited.add(packXZ(nx, nz))) continue;
                frontier.add(new Candidate(nx, nz, surfaceY, distSq));
            }
        }

        queue.addAll(ordered);
        totalCells = ordered.size();
        available = InventoryNetwork.countEligible(server, linked, item -> item == palette.costItem);
    }

    /**
     * Cherche, autour de {@code refY} (± {@link #STEP_WINDOW}), le bloc de sol naturel OU déjà texturé
     * le plus haut dont le dessus est ouvert (air ou remplaçable) — la « surface » de cette colonne.
     * Renvoie {@code null} si rien de tel n'existe dans la fenêtre : la colonne est alors ignorée sans
     * propager, ce qui épouse les petites pentes tout en arrêtant la propagation sur une vraie falaise,
     * un trou ou un bloc posé par le joueur. Une {@link CenterableMachine} au-dessus (le texturiseur
     * lui-même, ou une machine voisine centrée sur lui — cf. {@link #originPos()}) compte comme
     * « ouvert » : sans ça, la colonne de départ — juste sous une machine — échouait toujours, puisque
     * la case au-dessus de son propre sol est occupée par un bloc qui n'est ni air ni remplaçable.
     * Accepter aussi les cellules qui SONT un bloc du motif (cf. {@link #isPaletteBlock}) comme sol
     * « marchable » est indispensable : sinon, une fois le disque intérieur fini, il forme un mur
     * infranchissable qui empêche toute extension ultérieure du rayon d'atteindre les nouvelles cellules
     * au-delà. Ce test reste volontairement basé sur l'état RÉEL du bloc (jamais sur
     * {@link #dirtTexturedCells}) : sinon un trou creusé après coup dans une case déjà convertie serait
     * pris pour du sol marchable et la propagation franchirait le trou au lieu de s'y arrêter.
     */
    @Nullable
    private Integer findSurfaceY(ServerLevel server, int x, int z, int refY, BlockPos.MutableBlockPos p, Palette palette) {
        for (int y = refY + STEP_WINDOW; y >= refY - STEP_WINDOW; y--) {
            p.set(x, y, z);
            if (!server.isLoaded(p)) continue;
            BlockState state = server.getBlockState(p);
            if (!NaturalTerrain.isSurfaceGround(state) && !isPaletteBlock(state, palette)) continue;
            p.set(x, y + 1, z);
            if (!server.isLoaded(p)) continue;
            BlockState above = server.getBlockState(p);
            if (isClearableAbove(above) || server.getBlockEntity(p) instanceof CenterableMachine) return y;
        }
        return null;
    }

    /** Vrai si {@code state} est, par son identité seule, un des blocs que ce motif pose (+ la parcelle terre grossière en motif pierre — en motif terre elle est déjà couverte par {@code isSurfaceGround}). */
    private static boolean isPaletteBlock(BlockState state, Palette palette) {
        if (palette == Palette.STONE && state.is(Blocks.COARSE_DIRT)) return true; // parcelle terre grossière + pousse
        for (BlockState mosaicState : palette.mosaic) {
            if (state.is(mosaicState.getBlock())) return true;
        }
        return false;
    }

    /**
     * Vrai si {@code state} peut être silencieusement effacé pour texturer la cellule juste en dessous :
     * air/remplaçable (herbe, fougère...) OU petite flore (fleur, jeune pousse, champignon, culture...).
     * {@link BushBlock} couvre la quasi-totalité de la petite flore vanilla/moddée sans jamais inclure un
     * tronc ou des feuilles (classes distinctes) — donc pas de risque de faire tomber un arbre pour poser
     * une dalle en dessous. Sans ce test, une case autrement parfaitement texturable (herbe surmontée
     * d'une fleur, par ex.) n'était jamais ne serait-ce que mise en file : {@link #placePattern} sait
     * déjà effacer ce qui se trouve au-dessus, encore fallait-il que la cellule soit retenue en premier lieu.
     */
    private static boolean isClearableAbove(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.getBlock() instanceof BushBlock;
    }

    /**
     * Une cellule déjà traitée par le motif ACTUELLEMENT sélectionné : jamais remise au travail. En motif
     * pierre, l'identité du bloc suffit ({@link #isPaletteBlock}) : aucune de ses 4 variantes cibles n'est
     * un terrain de départ courant. En motif terre, ce serait faux : la terre nue fait elle-même partie
     * des 4 variantes cibles, donc une case de terre nue jamais traitée serait sinon confondue avec une
     * case déjà convertie — on retient donc {@link #dirtTexturedCells} plutôt que de déduire « fini » du
     * bloc en place.
     */
    private boolean isFinishedTexture(BlockState state, BlockPos pos, Palette palette) {
        if (palette == Palette.DIRT) {
            return dirtTexturedCells.contains(pos.asLong());
        }
        return isPaletteBlock(state, palette);
    }

    /** Cellule encore naturelle et pas déjà texturée par nous : c'est elle, et seulement elle, qu'on remet au travail. */
    private boolean needsTexturing(BlockState state, BlockPos pos, Palette palette) {
        return NaturalTerrain.isSurfaceGround(state) && !isFinishedTexture(state, pos, palette);
    }

    // ----- Tick serveur -----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;

        // Auto-réparation du centre (cf. CenterableMachine#originPos) : le bloc désigné a disparu depuis
        // (cassé) → on redevient sa propre référence. Simple effacement de champ, sans recalcul immédiat
        // du plan : le prochain computePlan (ci-dessous, ou la repasse périodique plus bas) le fera avec
        // la bonne origine.
        if (centerPos != null && !(server.getBlockEntity(centerPos) instanceof CenterableMachine)) {
            centerPos = null;
        }

        if (!planComputed) {
            computePlan(server);
            planComputed = true;
        }

        ItemStack tool = items.get(SLOT_TOOL);
        boolean working = false;

        if (!active) {
            setStatus(linked.isEmpty() ? STATUS_NO_LINK : STATUS_INACTIVE);
        } else if (tool.isEmpty() || !tool.is(palette.toolTag)) {
            // Un outil du mauvais type (ex. une pioche restée en place après un passage en motif terre)
            // compte comme absent : il faut le remplacer par celui qu'exige le motif actif.
            setStatus(STATUS_NO_TOOL);
        } else {
            if (queue.isEmpty()) {
                computePlan(server);
            }
            if (queue.isEmpty()) {
                setStatus(STATUS_DONE);
            } else {
                working = true;
                int done = 0;
                while (done < WORK_PER_TICK && !queue.isEmpty()) {
                    BlockPos pos = queue.peek();
                    BlockState current = server.getBlockState(pos);

                    // Revalidation à l'exécution : le plan a pu être calculé bien avant (grande zone). Si le
                    // terrain a changé ici depuis (miné, construit dessus, déjà texturé), on abandonne la cellule.
                    if (!needsTexturing(current, pos, palette)) {
                        queue.poll();
                        preview.remove(pos);
                        done++;
                        continue;
                    }
                    BlockPos abovePos = pos.above();
                    BlockState above = server.getBlockState(abovePos);
                    if (!abovePos.equals(worldPosition) && !isClearableAbove(above)) {
                        queue.poll();
                        preview.remove(pos);
                        done++;
                        continue;
                    }

                    // Variante « terre grossière + pousse » du motif payant, pas un bonus gratuit : coûte
                    // le même matériau que n'importe quelle autre cellule. Disponible dans les deux motifs
                    // (la terre grossière n'apparaît nulle part ailleurs).
                    boolean coarseDirtPatch = coarseDirtPatches && server.getRandom().nextFloat() < COARSE_DIRT_CHANCE;
                    int stock = InventoryNetwork.countEligible(server, linked, item -> item == palette.costItem);
                    if (stock <= 0) {
                        setStatus(STATUS_MISSING_MATERIAL);
                        working = false;
                        break;
                    }
                    InventoryNetwork.extract(server, linked, palette.costItem, 1);
                    placePattern(server, pos, coarseDirtPatch);

                    boolean broken = false;
                    if (++toolCharge >= TOOL_DAMAGE_INTERVAL) {
                        toolCharge = 0;
                        broken = damageTool(server, tool);
                    }
                    queue.poll();
                    preview.remove(pos);
                    done++;
                    if (broken) {
                        working = false;
                        setStatus(STATUS_NO_TOOL);
                        break;
                    }
                }
                // Recalculé à chaque tick de travail : sans ça, le compte affiché resterait figé à sa
                // valeur du début de run tant que le plan n'est pas recalculé (fin de file, config...).
                available = InventoryNetwork.countEligible(server, linked, item -> item == palette.costItem);
                // Une seule fois par tick (pas par cellule placée) : suffisant pour que le chunk soit
                // sauvegardé avec un dirtTexturedCells à jour, sans marquer le chunk sale des milliers
                // de fois sur un grand disque.
                if (working && palette == Palette.DIRT) setChanged();
                if (working) {
                    setStatus(queue.isEmpty() ? STATUS_DONE : STATUS_WORKING);
                }
            }
        }

        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            boolean dirty = InventoryNetwork.rescan(server, getBlockPos(), linked);
            if (working || queueSizeClient != queue.size()) dirty = true;
            if (!active) {
                computePlan(server);
                dirty = true;
            }
            if (dirty) syncToClient();
        }
    }

    /**
     * Remplace directement le bloc de sol par le motif (aucun butin récupéré ni redéposé). Si de l'herbe,
     * une fougère ou toute autre pousse se trouvait au-dessus, elle est effacée en silence avant le
     * remplacement plutôt que laissée se casser toute seule (mise à jour de voisinage automatique du
     * jeu) : cassée « naturellement », elle aurait une chance de lâcher des graines — exactement le
     * genre de butin qu'on ne veut pas ici. Si {@code coarseDirtPatch}, pose une parcelle de terre
     * grossière TOUJOURS accompagnée d'une pousse à la place du motif payant — jamais de terre grossière
     * nue : c'est soit le motif normal, soit la paire complète. Coûte le même matériau qu'une cellule
     * normale (cf. {@link #serverTick()}) : ce n'est qu'une variante d'apparence, pas un bonus gratuit.
     * Pour la colonne d'origine (juste sous la machine), {@code above} est le bloc du texturiseur
     * lui-même, pas une pousse — {@link #serverTick()} l'autorise explicitement à traverser ce garde-fou
     * (cf. {@code abovePos.equals(worldPosition)}), donc il ne faut ni l'effacer ni y poser une pousse ici
     * (seule exception où une parcelle reste sans pousse : la machine occupe déjà la case).
     */
    private void placePattern(ServerLevel server, BlockPos pos, boolean coarseDirtPatch) {
        BlockPos above = pos.above();
        boolean aboveIsSelf = above.equals(worldPosition);
        if (!aboveIsSelf) {
            BlockState aboveState = server.getBlockState(above);
            if (!aboveState.isAir()) {
                server.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                // Moitié basse d'une plante à double hauteur (tournesol, lilas, herbe/fougère hautes...) :
                // n'effacer que celle-ci laisserait la moitié haute flotter, sans jamais se casser toute
                // seule (UPDATE_CLIENTS ne déclenche volontairement aucune mise à jour de voisinage, cf.
                // plus haut — donc pas de recalcul automatique du support de la moitié haute non plus).
                if (aboveState.getBlock() instanceof DoublePlantBlock
                        && aboveState.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
                    server.setBlock(above.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }

        BlockState placed = coarseDirtPatch
                ? Blocks.COARSE_DIRT.defaultBlockState()
                : palette.mosaic[server.getRandom().nextInt(palette.mosaic.length)];
        server.setBlock(pos, placed, Block.UPDATE_ALL);

        // Motif terre : seul moyen de savoir que cette case est « déjà faite » (cf. isFinishedTexture),
        // puisque l'identité du bloc posé ne le distingue pas d'une case jamais traitée. Persistance
        // (setChanged) déclenchée une seule fois par tick par l'appelant, pas ici cellule par cellule.
        if (palette == Palette.DIRT) {
            dirtTexturedCells.add(pos.asLong());
        }

        if (!aboveIsSelf && coarseDirtPatch) {
            BlockState plant = server.getRandom().nextFloat() < FERN_CHANCE
                    ? Blocks.FERN.defaultBlockState()
                    : Blocks.SHORT_GRASS.defaultBlockState();
            server.setBlock(above, plant, Block.UPDATE_ALL);
        }
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /** Consomme 1 point de durabilité sur la pioche. Renvoie true si elle vient de casser. */
    private boolean damageTool(ServerLevel server, ItemStack tool) {
        return ToolDurability.damage(server, tool);
    }

    private void setStatus(int s) {
        if (status != s) {
            status = s;
            syncToClient();
        }
    }

    // ----- Container (slot outil) -----

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_TOOL).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) return false;
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
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
        // Purement une mémoire serveur (jusqu'à ~16k positions à rayon max) : inutile au client et bien
        // trop lourd pour une sync réseau régulière — cf. la doc de dirtTexturedCells.
        tag.remove("dirtTexturedCells");
        tag.putInt("status", status);
        tag.putInt("queueSize", queue.size());
        tag.putLongArray("preview", preview.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("totalCells", totalCells);
        tag.putInt("available", available);
        return tag;
    }

    // ----- Persistance -----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("radius", radius);
        tag.putString("palette", palette.name());
        tag.putBoolean("coarseDirtPatches", coarseDirtPatches);
        tag.putBoolean("active", active);
        tag.putLongArray("linked", linked.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("dirtTexturedCells", dirtTexturedCells.stream().mapToLong(Long::longValue).toArray());
        if (centerPos != null) tag.putLong("centerPos", centerPos.asLong());
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("radius")) radius = clampRadius(tag.getInt("radius"));
        if (tag.contains("palette")) {
            try {
                palette = Palette.valueOf(tag.getString("palette"));
            } catch (IllegalArgumentException ignored) {
                palette = Palette.STONE;
            }
        }
        if (tag.contains("coarseDirtPatches")) coarseDirtPatches = tag.getBoolean("coarseDirtPatches");
        if (tag.contains("active")) active = tag.getBoolean("active");

        linked.clear();
        for (long packed : tag.getLongArray("linked")) {
            linked.add(BlockPos.of(packed));
        }
        dirtTexturedCells.clear();
        for (long packed : tag.getLongArray("dirtTexturedCells")) {
            dirtTexturedCells.add(packed);
        }
        centerPos = tag.contains("centerPos") ? BlockPos.of(tag.getLong("centerPos")) : null;
        ContainerHelper.loadAllItems(tag, items, registries);

        // transitoire (présent uniquement dans les paquets réseau)
        if (tag.contains("status")) status = tag.getInt("status");
        if (tag.contains("queueSize")) queueSizeClient = tag.getInt("queueSize");
        if (tag.contains("preview")) {
            preview.clear();
            for (long packed : tag.getLongArray("preview")) {
                preview.add(BlockPos.of(packed));
            }
        }
        if (tag.contains("totalCells")) totalCells = tag.getInt("totalCells");
        if (tag.contains("available")) available = tag.getInt("available");
    }

    // ----- Menu -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.turnkey_factory.texturizer");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TexturizerMenu(id, inv, this);
    }
}
