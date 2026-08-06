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
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Retexture la surface naturelle autour du bloc en un motif FIXE : cobblestone/gravier/andésite/pierre
 * à parts égales (25% chacun, {@link #MOSAIC}). Ne consomme que de la cobblestone dans les inventaires
 * liés (1 par cellule, quel que soit le bloc tiré du motif) — c'est elle qui « paie » le mélange entier.
 * Les blocs de sol retirés ne sont ni récupérés ni redéposés : ils sont directement remplacés (aucun
 * appel à {@code Block.getDrops}). Option « parcelles d'herbe » : une part des cellules devient une
 * parcelle de terre grossière + pousse au lieu du motif — gratuite, sans coût de cobblestone.
 * Se propage en cercles concentriques depuis la colonne juste sous le bloc, en épousant les petites
 * variations de hauteur du terrain (mais jamais les falaises/grottes : cf. {@link #findSurfaceY}), et
 * s'arrête net sur tout ce qui n'est pas du sol naturel — ne comble donc jamais un trou. Consomme une
 * pioche (durabilité par bloc) et exige un inventaire lié pour démarrer, exactement comme la niveleuse.
 */
public class TexturizerBlockEntity extends BlockEntity implements MenuProvider, Container {
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

    /** Motif fixe, parts égales (25% chacun) : cobblestone / gravier / andésite / pierre. */
    private static final BlockState[] MOSAIC = {
            Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.GRAVEL.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(),
            Blocks.STONE.defaultBlockState(),
    };

    private static final float COARSE_DIRT_CHANCE = 0.05f; // part des cellules transformées en parcelle gratuite
    private static final float PLANT_CHANCE = 0.6f;         // chance qu'une parcelle reçoive une pousse
    private static final float FERN_CHANCE = 0.2f;          // parmi les pousses, part de fougère (sinon herbe)

    public static final int SLOT_PICKAXE = 0;

    public static final int STATUS_NO_PICKAXE = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_MISSING_MATERIAL = 3;
    public static final int STATUS_INACTIVE = 4;
    public static final int STATUS_NO_LINK = 5;

    private int radius = DEFAULT_RADIUS;
    private boolean coarseDirtPatches = false;
    private int scanCooldown = 0;
    private int toolCharge = 0;
    private boolean active = false;
    /** Vrai dès qu'un plan a été calculé au moins une fois (pose ou chargement) : évite un aperçu vide au premier tick. */
    private boolean planComputed = false;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final List<BlockPos> linked = new ArrayList<>();

    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    /** Cellules à venir, plafonnées, synchronisées au client pour le fantôme. */
    private final List<BlockPos> preview = new ArrayList<>();

    private int status = STATUS_NO_PICKAXE;
    private int queueSizeClient = 0;
    private int totalCells = 0;
    private int available = 0;

    public TexturizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TEXTURIZER.get(), pos, state);
    }

    // ----- Configuration -----

    public int radius() { return radius; }
    public boolean coarseDirtPatches() { return coarseDirtPatches; }
    public int status() { return status; }
    public int queueSize() { return queueSizeClient; }
    public int totalCells() { return totalCells; }
    public int available() { return available; }
    public List<BlockPos> preview() { return preview; }
    public boolean active() { return active; }

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

    public static int clampRadius(int v) {
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, v));
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

        BlockPos origin = getBlockPos();
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
            Integer surfaceY = findSurfaceY(server, c.x(), c.z(), c.refY(), p, origin);
            if (surfaceY == null) continue; // pas de sol naturel accessible ici : on ne propage pas plus loin

            BlockPos pos = new BlockPos(c.x(), surfaceY, c.z());
            // On propage TOUJOURS à travers une cellule déjà texturée (sinon un disque déjà fini bloque
            // toute extension de rayon), mais on ne la remet pas au travail : seules les cellules encore
            // naturelles-et-pas-finies sont mises en file.
            if (needsTexturing(server.getBlockState(pos))) {
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
        available = InventoryNetwork.countEligible(server, linked, item -> item == Items.COBBLESTONE);
    }

    /**
     * Cherche, autour de {@code refY} (± {@link #STEP_WINDOW}), le bloc de sol naturel OU déjà texturé
     * le plus haut dont le dessus est ouvert (air ou remplaçable) — la « surface » de cette colonne.
     * Renvoie {@code null} si rien de tel n'existe dans la fenêtre : la colonne est alors ignorée sans
     * propager, ce qui épouse les petites pentes tout en arrêtant la propagation sur une vraie falaise,
     * un trou ou un bloc posé par le joueur. {@code self} (la position du texturiseur) compte comme
     * « ouvert » : sans ça, la colonne de départ — juste sous la machine — échouait toujours, puisque
     * la case au-dessus de son propre sol est occupée par la machine elle-même. Accepter aussi les
     * cellules déjà texturées (cf. {@link #isFinishedTexture}) comme sol « marchable » est indispensable :
     * sinon, une fois le disque intérieur fini, il forme un mur infranchissable qui empêche toute
     * extension ultérieure du rayon d'atteindre les nouvelles cellules au-delà.
     */
    @Nullable
    private static Integer findSurfaceY(ServerLevel server, int x, int z, int refY, BlockPos.MutableBlockPos p, BlockPos self) {
        for (int y = refY + STEP_WINDOW; y >= refY - STEP_WINDOW; y--) {
            p.set(x, y, z);
            if (!server.isLoaded(p)) continue;
            BlockState state = server.getBlockState(p);
            if (!NaturalTerrain.isSurfaceGround(state) && !isFinishedTexture(state)) continue;
            p.set(x, y + 1, z);
            if (p.getX() == self.getX() && p.getY() == self.getY() && p.getZ() == self.getZ()) return y;
            if (!server.isLoaded(p)) continue;
            BlockState above = server.getBlockState(p);
            if (above.isAir() || above.canBeReplaced()) return y;
        }
        return null;
    }

    /** Un des blocs que le texturiseur pose lui-même (motif ou parcelle) : déjà fini, jamais remis au travail. */
    private static boolean isFinishedTexture(BlockState state) {
        return state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.STONE);
    }

    /** Cellule encore naturelle et pas déjà texturée par nous : c'est elle, et seulement elle, qu'on remet au travail. */
    private static boolean needsTexturing(BlockState state) {
        return NaturalTerrain.isSurfaceGround(state) && !isFinishedTexture(state);
    }

    // ----- Tick serveur -----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;

        if (!planComputed) {
            computePlan(server);
            planComputed = true;
        }

        ItemStack pickaxe = items.get(SLOT_PICKAXE);
        boolean working = false;

        if (!active) {
            setStatus(linked.isEmpty() ? STATUS_NO_LINK : STATUS_INACTIVE);
        } else if (pickaxe.isEmpty()) {
            setStatus(STATUS_NO_PICKAXE);
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
                    if (!needsTexturing(current)) {
                        queue.poll();
                        preview.remove(pos);
                        done++;
                        continue;
                    }
                    BlockPos abovePos = pos.above();
                    BlockState above = server.getBlockState(abovePos);
                    if (!abovePos.equals(worldPosition) && !(above.isAir() || above.canBeReplaced())) {
                        queue.poll();
                        preview.remove(pos);
                        done++;
                        continue;
                    }

                    // Parcelle gratuite : tirée avant tout coût de matériau, ne consomme pas de cobblestone.
                    boolean freePatch = coarseDirtPatches && server.getRandom().nextFloat() < COARSE_DIRT_CHANCE;
                    if (!freePatch) {
                        int cobble = InventoryNetwork.countEligible(server, linked, item -> item == Items.COBBLESTONE);
                        if (cobble <= 0) {
                            setStatus(STATUS_MISSING_MATERIAL);
                            working = false;
                            break;
                        }
                        InventoryNetwork.extract(server, linked, Items.COBBLESTONE, 1);
                    }
                    placePattern(server, pos, freePatch);

                    boolean broken = false;
                    if (++toolCharge >= TOOL_DAMAGE_INTERVAL) {
                        toolCharge = 0;
                        broken = damageTool(server, pickaxe);
                    }
                    queue.poll();
                    preview.remove(pos);
                    done++;
                    if (broken) {
                        working = false;
                        setStatus(STATUS_NO_PICKAXE);
                        break;
                    }
                }
                // Recalculé à chaque tick de travail : sans ça, le compte affiché resterait figé à sa
                // valeur du début de run tant que le plan n'est pas recalculé (fin de file, config...).
                available = InventoryNetwork.countEligible(server, linked, item -> item == Items.COBBLESTONE);
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
     * genre de butin qu'on ne veut pas ici. Si {@code freePatch}, pose une parcelle de terre grossière
     * (+ pousse éventuelle) à la place du motif payant — variation gratuite, ne consomme pas de cobblestone.
     */
    private void placePattern(ServerLevel server, BlockPos pos, boolean freePatch) {
        BlockPos above = pos.above();
        if (!server.getBlockState(above).isAir()) {
            server.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        BlockState placed = freePatch
                ? Blocks.COARSE_DIRT.defaultBlockState()
                : MOSAIC[server.getRandom().nextInt(MOSAIC.length)];
        server.setBlock(pos, placed, Block.UPDATE_ALL);

        if (freePatch && server.getRandom().nextFloat() < PLANT_CHANCE) {
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

    // ----- Container (slot pioche) -----

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_PICKAXE).isEmpty();
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
        tag.putBoolean("coarseDirtPatches", coarseDirtPatches);
        tag.putBoolean("active", active);
        tag.putLongArray("linked", linked.stream().mapToLong(BlockPos::asLong).toArray());
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("radius")) radius = clampRadius(tag.getInt("radius"));
        if (tag.contains("coarseDirtPatches")) coarseDirtPatches = tag.getBoolean("coarseDirtPatches");
        if (tag.contains("active")) active = tag.getBoolean("active");

        linked.clear();
        for (long packed : tag.getLongArray("linked")) {
            linked.add(BlockPos.of(packed));
        }
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
