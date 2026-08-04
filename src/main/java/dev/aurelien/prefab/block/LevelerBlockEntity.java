package dev.aurelien.prefab.block;

import dev.aurelien.prefab.build.InventoryNetwork;
import dev.aurelien.prefab.build.NaturalTerrain;
import dev.aurelien.prefab.build.ToolDurability;
import dev.aurelien.prefab.menu.LevelerMenu;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Aplanit automatiquement une zone rectangulaire de terrain à une hauteur cible : retire ce qui
 * dépasse, comble les creux. Alimentée par une pelle (sol meuble : terre, sable, gravier…) et une
 * pioche (roche, minerais), chacune dans son propre slot, durabilité consommée par bloc traité ;
 * puise le matériau de remblai dans les inventaires liés (flood-fill, comme le bloc de contrôle).
 * Ne touche jamais un bloc qui n'est pas du terrain naturel ({@link NaturalTerrain#isNaturalGround})
 * ni un arbre (troncs et feuilles) : les constructions du joueur et la végétation ligneuse sont
 * protégées.
 */
public class LevelerBlockEntity extends BlockEntity implements MenuProvider, Container {
    public static final int MIN_SIZE = 3;
    public static final int MAX_SIZE = 31;
    public static final int SIZE_STEP = 2;   // largeur/longueur : impaires uniquement (centrage exact)
    public static final int OFFSET_MAX = 15;
    public static final int TARGET_MAX = 20;
    public static final int MIN_FILL_DEPTH = 1;
    public static final int MAX_FILL_DEPTH = 24;
    public static final int DEFAULT_FILL_DEPTH = 4;

    private static final int SCAN_INTERVAL = 20;   // ticks entre deux scans d'inventaires liés (1 s)
    private static final int SCAN_UP = 24;          // hauteur explorée au-dessus de la cible (retrait)
    private static final int LEVEL_PER_TICK = 4;    // opérations (retrait/remblai) par tick
    private static final int MAX_PREVIEW = 128;     // cellules de retrait renvoyées au client (fantôme)

    public static final int SLOT_SHOVEL = 0;
    public static final int SLOT_PICKAXE = 1;

    public static final int STATUS_NO_SHOVEL = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_MISSING_FILL = 3;
    public static final int STATUS_INACTIVE = 4;
    public static final int STATUS_NO_LINK = 5;
    public static final int STATUS_NO_PICKAXE = 6;

    private int width = 7, length = 7;
    private int offX = 0, offZ = 0;
    private int targetOffsetY = 0;
    private int fillDepth = DEFAULT_FILL_DEPTH;
    private int scanCooldown = 0;
    private boolean active = false;
    private Direction facing = Direction.NORTH;
    /** Vrai dès qu'un plan a été calculé au moins une fois (pose ou chargement) : évite un aperçu vide au premier tick. */
    private boolean planComputed = false;

    private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private final List<BlockPos> linked = new ArrayList<>();

    private record LevelOp(BlockPos pos, boolean fill) {}
    private final ArrayDeque<LevelOp> queue = new ArrayDeque<>();

    /** Cellules de retrait à venir, plafonnées, synchronisées au client pour le fantôme rouge. */
    private final List<BlockPos> removalPreview = new ArrayList<>();

    private int status = STATUS_NO_SHOVEL;
    private int queueSizeClient = 0;

    /** Estimation calculée à chaque {@link #computePlan}, synchronisée au client pour l'affichage. */
    private int fillNeeded = 0;
    private int fillSupplied = 0;

    public LevelerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEVELER.get(), pos, state);
    }

    // ----- Configuration -----

    public int width() { return width; }
    public int length() { return length; }
    public int offsetX() { return offX; }
    public int offsetZ() { return offZ; }
    public int targetOffsetY() { return targetOffsetY; }
    public int status() { return status; }
    public int queueSize() { return queueSizeClient; }
    /** Nombre total de blocs de remblai requis par le plan courant. */
    public int fillNeeded() { return fillNeeded; }
    /** Parmi {@link #fillNeeded()}, combien seront couverts par les débris du dégagement au-dessus de la grille. */
    public int fillSupplied() { return fillSupplied; }
    public List<BlockPos> removalPreview() { return removalPreview; }
    public boolean active() { return active; }

    /**
     * Démarre/arrête le travail. Refuse le démarrage si aucun inventaire n'est lié (rien pour puiser le
     * remblai manquant, ni pour récupérer le débris) — le statut « À l'arrêt » l'indique déjà (cf.
     * serverTick). L'arrêt ne vide plus la file/l'aperçu : ils restent visibles (fantôme + estimation de
     * remblai) et le travail reprend exactement où il en était à la relance.
     */
    public void setActive(boolean value) {
        if (value && linked.isEmpty()) {
            return;
        }
        this.active = value;
        syncToClient();
    }

    /** Hauteur cible en Y monde (bloc lui-même + décalage réglable). */
    public int targetY() {
        return getBlockPos().getY() + targetOffsetY;
    }

    public Direction facing() { return facing; }

    public void setFacing(Direction f) {
        this.facing = f;
        onConfigChanged();
    }

    /**
     * Bornes de l'empreinte (en coordonnées monde), pour le calcul du plan et le fantôme (grille plate).
     * La zone démarre juste DEVANT le bloc (dans le sens {@link #facing}), jamais sous/sur lui : comme
     * le bloc de contrôle, la niveleuse reste hors de sa propre zone de travail — sinon changer la
     * hauteur cible finit par la recouvrir ou l'enterrer elle-même.
     */
    private BlockPos[] footprintMinMaxXZ() {
        Direction lateral = facing.getClockWise();
        BlockPos s = getBlockPos()
                .relative(facing, 1)                              // juste devant le bloc
                .relative(lateral.getOpposite(), (width - 1) / 2)  // centré latéralement
                .offset(offX, 0, offZ);                            // décalage joueur
        BlockPos e = s.relative(facing, length - 1).relative(lateral, width - 1);
        return new BlockPos[]{
                new BlockPos(Math.min(s.getX(), e.getX()), 0, Math.min(s.getZ(), e.getZ())),
                new BlockPos(Math.max(s.getX(), e.getX()), 0, Math.max(s.getZ(), e.getZ()))
        };
    }

    public int footprintMinX() { return footprintMinMaxXZ()[0].getX(); }
    public int footprintMaxX() { return footprintMinMaxXZ()[1].getX(); }
    public int footprintMinZ() { return footprintMinMaxXZ()[0].getZ(); }
    public int footprintMaxZ() { return footprintMinMaxXZ()[1].getZ(); }

    public int fillDepth() { return fillDepth; }

    public void setDims(int w, int l) {
        this.width = clampSize(w);
        this.length = clampSize(l);
        onConfigChanged();
    }

    public void setTarget(int ox, int oz, int oy, int depth) {
        this.offX = clampOffset(ox);
        this.offZ = clampOffset(oz);
        this.targetOffsetY = clampTarget(oy);
        this.fillDepth = clampFillDepth(depth);
        onConfigChanged();
    }

    public static int clampSize(int v) {
        int c = Math.max(MIN_SIZE, Math.min(MAX_SIZE, v));
        if (c % 2 == 0) c--;
        return c;
    }

    public static int clampOffset(int v) {
        return Math.max(-OFFSET_MAX, Math.min(OFFSET_MAX, v));
    }

    public static int clampFillDepth(int v) {
        return Math.max(MIN_FILL_DEPTH, Math.min(MAX_FILL_DEPTH, v));
    }

    public static int clampTarget(int v) {
        return Math.max(-TARGET_MAX, Math.min(TARGET_MAX, v));
    }

    /**
     * La config a changé : la file en cours ne correspond plus à la zone/cible → on la jette et on
     * recalcule le plan (retrait + remblai + estimation) TOUT DE SUITE, avant même de démarrer, pour que
     * le joueur voie le coût réel en remblai dès qu'il ajuste taille/décalage/cible/profondeur.
     */
    private void onConfigChanged() {
        queue.clear();
        removalPreview.clear();
        fillNeeded = 0;
        fillSupplied = 0;
        if (level instanceof ServerLevel server) {
            computePlan(server);
            planComputed = true;
        }
        syncToClient();
    }

    // ----- Calcul du plan (dépend du terrain RÉEL, contrairement au plan statique du contrôleur) -----

    /**
     * Parcourt chaque colonne de l'empreinte : la cible ELLE-MÊME et tout ce qui est au-dessus (terrain
     * naturel ou végétation) est mis en file de retrait (haut → bas, sûr pour le sable/gravier restant
     * en place) — la grille du fantôme est le niveau du sol FINI, donc la couche qui repose dessus doit
     * disparaître aussi. En dessous de la cible, tout creux (air) jusqu'au premier bloc plein est mis en
     * file de remblai — enfilé du FOND vers la cible pour que le remblai se pose toujours sur un support
     * déjà en place.
     * <p>
     * Le RETRAIT complet passe TOUJOURS avant le REMBLAI dans la file (deux listes séparées, concaténées
     * à la fin) : chaque bloc retiré rejoint les inventaires liés (cf. serverTick), donc en vidant
     * d'abord toute la zone, on maximise la quantité de « débris » disponible avant le premier besoin de
     * remblai — si le terrain excavé suffit, le joueur n'a rien à fournir lui-même.
     */
    private void computePlan(ServerLevel server) {
        queue.clear();
        removalPreview.clear();

        BlockPos origin = getBlockPos();
        int target = targetY();
        int minX = footprintMinX();
        int maxX = footprintMaxX();
        int minZ = footprintMinZ();
        int maxZ = footprintMaxZ();

        List<LevelOp> removeOps = new ArrayList<>();
        List<LevelOp> fillOps = new ArrayList<>();
        int debrisCount = 0; // blocs retirés qui rendront un vrai item réutilisable comme remblai

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        List<BlockPos> holeCells = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Ne descend que dans la masse solide continue au-dessus de la cible : dès qu'on retombe
                // dans une couche d'air (ou de fluide) après avoir déjà traversé du solide, on s'arrête —
                // pas la peine de creuser plus bas dans une éventuelle grotte/poche déconnectée. Troncs ET
                // feuilles sont totalement transparents au balayage : jamais retirés, et ils ne comptent
                // ni comme du solide ni comme une couche d'air (un arbre reste intégralement en place).
                boolean foundSolid = false;
                for (int y = target + SCAN_UP; y >= target; y--) {
                    p.set(x, y, z);
                    if (p.equals(origin)) continue; // ne jamais se retirer soi-même
                    if (!server.isLoaded(p)) continue;
                    BlockState state = server.getBlockState(p);
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) continue;

                    boolean open = state.isAir() || !state.getFluidState().isEmpty();
                    if (open) {
                        if (foundSolid) break; // couche d'air sous la masse retirée : on s'arrête là
                        continue;              // encore au-dessus de la surface : on continue de descendre
                    }
                    foundSolid = true;

                    // Terrain naturel OU végétation/déco « remplaçable » (herbe, fougère, buisson mort…) :
                    // ces plantes ne sont pas taguées « sol » mais doivent quand même être rasées, sinon
                    // elles restent perchées sur le trou laissé par le bloc du dessous et créent un creux —
                    // mais rasées à la pelle, elles ne rendent RIEN (comme en survie, seules des cisailles
                    // le permettraient) : on ne les compte donc pas comme débris récupérable ci-dessous.
                    if (NaturalTerrain.isNaturalGround(state) || state.canBeReplaced()) {
                        BlockPos immutable = p.immutable();
                        removeOps.add(new LevelOp(immutable, false));
                        if (removalPreview.size() < MAX_PREVIEW) removalPreview.add(immutable);
                        if (yieldsDrops(server, immutable, state, toolFor(state))) debrisCount++;
                    }
                }

                holeCells.clear();
                boolean floorFound = false;
                for (int y = target - 1; y >= target - fillDepth; y--) {
                    p.set(x, y, z);
                    if (!server.isLoaded(p)) break;
                    BlockState state = server.getBlockState(p);
                    // Une déco sans collision tombée au fond du trou (fleur, herbe haute, champignon,
                    // pousse…) ne doit pas faire croire qu'on a atteint le fond : contrairement à
                    // canBeReplaced() — qui ne couvre PAS les fleurs — la forme de collision identifie
                    // correctement tout ce qui est traversable, donc pas un vrai obstacle. Ça sera de toute
                    // façon écrasé par le remblai posé par-dessus, donc on continue de creuser en dessous.
                    if (!state.isAir() && !state.getCollisionShape(server, p).isEmpty()) {
                        floorFound = true; // vrai fond atteint : tout ce qui est au-dessus peut être comblé en sécurité
                        break;
                    }
                    holeCells.add(p.immutable());
                }
                // Si aucun fond solide n'a été trouvé dans la profondeur autorisée (trou/ravin plus profond
                // que fillDepth), on NE comble PAS : un bloc soumis à la gravité (sable, gravier…) posé sans
                // appui confirmé tomberait aussitôt dans le vide restant en dessous, viderait la case qu'on
                // vient de remplir et gâcherait le matériau prélevé. Mieux vaut laisser le trou tel quel —
                // le joueur peut augmenter la profondeur de remblai s'il veut le combler entièrement.
                if (floorFound) {
                    for (int i = holeCells.size() - 1; i >= 0; i--) {
                        fillOps.add(new LevelOp(holeCells.get(i), true));
                    }
                }
            }
        }

        queue.addAll(removeOps);
        queue.addAll(fillOps);

        fillNeeded = fillOps.size();
        // Estimation : le dégagement passe intégralement avant le remblai (cf. plus haut), donc tous les
        // débris récupérables sont disponibles avant le premier bloc à combler — plus ce qui est déjà
        // stocké dans les inventaires liés (le joueur a pu remplir un coffre entre-temps).
        int stocked = InventoryNetwork.countEligible(server, linked, LevelerBlockEntity::isEligibleFill);
        fillSupplied = Math.min(fillNeeded, debrisCount + stocked);
    }

    /**
     * Item éligible comme remblai : n'importe quel bloc présent dans les inventaires liés convient
     * (pas de filtre — demande explicite de l'utilisateur), du moment qu'il correspond à un vrai bloc.
     */
    private static boolean isEligibleFill(Item item) {
        return Block.byItem(item) != Blocks.AIR;
    }

    // ----- Tick serveur -----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;

        // Premier tick après pose/chargement : calcule tout de suite l'aperçu (fantôme + estimation de
        // remblai), sans attendre un changement de config ou un démarrage.
        if (!planComputed) {
            computePlan(server);
            planComputed = true;
        }

        ItemStack shovel = items.get(SLOT_SHOVEL);
        boolean working = false;

        if (!active) {
            // La file/l'aperçu ne sont PLUS vidés ici : ils restent visibles à l'arrêt (estimation avant
            // démarrage). Le statut distingue « aucun inventaire lié » (démarrage impossible, cf.
            // setActive) de la simple pause.
            setStatus(linked.isEmpty() ? STATUS_NO_LINK : STATUS_INACTIVE);
        } else if (shovel.isEmpty()) {
            setStatus(STATUS_NO_SHOVEL);
        } else {
            if (queue.isEmpty()) {
                computePlan(server);
            }
            if (queue.isEmpty()) {
                setStatus(STATUS_DONE);
            } else {
                working = true;
                int done = 0;
                while (done < LEVEL_PER_TICK && !queue.isEmpty()) {
                    LevelOp op = queue.peek();
                    boolean usedPickaxe = false;
                    if (op.fill()) {
                        // La cellule a pu être occupée entre-temps (le joueur y a construit, ou du gravier
                        // d'une colonne voisine y est tombé) : on ne l'écrase jamais, on abandonne juste cet
                        // ordre de remblai sans consommer de matériau.
                        if (!server.getBlockState(op.pos()).isAir()) {
                            queue.poll();
                            done++;
                            continue;
                        }
                        Item material = InventoryNetwork.pickWeightedRandom(
                                server, linked, LevelerBlockEntity::isEligibleFill, server.getRandom());
                        if (material == null) {
                            setStatus(STATUS_MISSING_FILL);
                            working = false;
                            break;
                        }
                        InventoryNetwork.extract(server, linked, material, 1);
                        server.setBlock(op.pos(), Block.byItem(material).defaultBlockState(), Block.UPDATE_ALL);
                    } else {
                        BlockState removed = server.getBlockState(op.pos());
                        // Déjà retiré entre-temps (miné manuellement par le joueur, etc.) : rien à faire, et
                        // surtout aucune durabilité à consommer pour une case déjà vide.
                        if (removed.isAir()) {
                            queue.poll();
                            removalPreview.remove(op.pos());
                            done++;
                            continue;
                        }
                        // Revalidation à l'exécution : le plan a été calculé potentiellement bien avant (file
                        // longue sur une grande zone). Si le joueur a construit ici depuis — un bloc qui
                        // n'est ni terrain naturel ni « remplaçable » (fleur, herbe…) occupe maintenant cette
                        // case — on protège ce bloc et on abandonne cette cellule, exactement comme si elle
                        // n'avait jamais fait partie du plan.
                        boolean stillEligible = !removed.is(BlockTags.LEAVES) && !removed.is(BlockTags.LOGS)
                                && (NaturalTerrain.isNaturalGround(removed) || removed.canBeReplaced());
                        if (!stillEligible) {
                            queue.poll();
                            removalPreview.remove(op.pos());
                            done++;
                            continue;
                        }
                        usedPickaxe = removed.is(BlockTags.MINEABLE_WITH_PICKAXE);
                        ItemStack tool = usedPickaxe ? items.get(SLOT_PICKAXE) : shovel;
                        if (usedPickaxe && tool.isEmpty()) {
                            // Roche/minerai : la pelle ne suffit pas, on attend qu'une pioche soit fournie
                            // plutôt que de laisser le bloc en place indéfiniment sans rien tenter.
                            setStatus(STATUS_NO_PICKAXE);
                            working = false;
                            break;
                        }
                        // Table de butin RÉELLE du bloc (avec le bon outil) AVANT de le retirer : gère Silk
                        // Touch / Fortune et l'item exact (pierre→cobble, herbe→terre…) sans heuristique
                        // câblée en dur — et rend RIEN pour l'herbe/fougère/etc. sans cisailles, comme en survie.
                        List<ItemStack> drops = Block.getDrops(removed, server, op.pos(), null, null, tool);
                        server.setBlock(op.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        // Le débris rejoint les inventaires liés : il redevient disponible comme remblai
                        // (répartition pondérée, cf. pickWeightedRandom) au lieu d'être simplement perdu.
                        for (ItemStack debris : drops) {
                            if (debris.isEmpty()) continue;
                            ItemStack leftover = InventoryNetwork.insert(server, linked, debris);
                            if (!leftover.isEmpty()) {
                                Block.popResource(server, op.pos(), leftover);
                            }
                        }
                    }
                    boolean broken = damageTool(server, usedPickaxe ? items.get(SLOT_PICKAXE) : shovel);
                    queue.poll();
                    removalPreview.remove(op.pos());
                    done++;
                    if (broken) {
                        working = false;
                        setStatus(usedPickaxe ? STATUS_NO_PICKAXE : STATUS_NO_SHOVEL);
                        break;
                    }
                }
                if (working) {
                    setStatus(queue.isEmpty() ? STATUS_DONE : STATUS_WORKING);
                }
            }
        }

        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            boolean dirty = InventoryNetwork.rescan(server, getBlockPos(), linked);
            if (working || queueSizeClient != queue.size()) dirty = true; // rafraîchit la progression ~1/s
            if (!active) {
                // À l'arrêt : on rafraîchit l'aperçu/l'estimation régulièrement (~1/s), pas seulement au
                // changement de config — le joueur peut remplir un coffre lié entre-temps et voir le
                // besoin de remblai diminuer sans rien avoir à toucher dans l'interface.
                computePlan(server);
                dirty = true;
            }
            if (dirty) syncToClient();
        }
    }

    /** Consomme 1 point de durabilité sur l'outil donné (pelle ou pioche). Renvoie true s'il vient de casser. */
    private boolean damageTool(ServerLevel server, ItemStack tool) {
        return ToolDurability.damage(server, tool);
    }

    /** Vrai si retirer {@code state} à cette position avec {@code tool} rendrait au moins un item (table de butin réelle). */
    private static boolean yieldsDrops(ServerLevel server, BlockPos pos, BlockState state, ItemStack tool) {
        return !Block.getDrops(state, server, pos, null, null, tool).isEmpty();
    }

    /** Outil requis pour miner {@code state} à l'estimation : la pioche pour la roche/minerai, sinon la pelle. */
    private ItemStack toolFor(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE) ? items.get(SLOT_PICKAXE) : items.get(SLOT_SHOVEL);
    }

    private void setStatus(int s) {
        if (status != s) {
            status = s;
            syncToClient();
        }
    }

    // ----- Container (slots pelle + pioche) -----

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT_SHOVEL).isEmpty() && items.get(SLOT_PICKAXE).isEmpty();
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
        tag.putLongArray("preview", removalPreview.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("fillNeeded", fillNeeded);
        tag.putInt("fillSupplied", fillSupplied);
        return tag;
    }

    // ----- Persistance -----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("w", width);
        tag.putInt("l", length);
        tag.putInt("ox", offX);
        tag.putInt("oz", offZ);
        tag.putInt("targetY", targetOffsetY);
        tag.putInt("fillDepth", fillDepth);
        tag.putBoolean("active", active);
        tag.putInt("facing", facing.get2DDataValue());
        tag.putLongArray("linked", linked.stream().mapToLong(BlockPos::asLong).toArray());
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("w")) width = clampSize(tag.getInt("w"));
        if (tag.contains("l")) length = clampSize(tag.getInt("l"));
        if (tag.contains("ox")) offX = tag.getInt("ox");
        if (tag.contains("oz")) offZ = tag.getInt("oz");
        if (tag.contains("targetY")) targetOffsetY = tag.getInt("targetY");
        if (tag.contains("fillDepth")) fillDepth = clampFillDepth(tag.getInt("fillDepth"));
        if (tag.contains("active")) active = tag.getBoolean("active");
        if (tag.contains("facing")) facing = Direction.from2DDataValue(tag.getInt("facing"));

        linked.clear();
        for (long packed : tag.getLongArray("linked")) {
            linked.add(BlockPos.of(packed));
        }
        ContainerHelper.loadAllItems(tag, items, registries);

        // transitoire (présent uniquement dans les paquets réseau)
        if (tag.contains("status")) status = tag.getInt("status");
        if (tag.contains("queueSize")) queueSizeClient = tag.getInt("queueSize");
        if (tag.contains("preview")) {
            removalPreview.clear();
            for (long packed : tag.getLongArray("preview")) {
                removalPreview.add(BlockPos.of(packed));
            }
        }
        if (tag.contains("fillNeeded")) fillNeeded = tag.getInt("fillNeeded");
        if (tag.contains("fillSupplied")) fillSupplied = tag.getInt("fillSupplied");
    }

    // ----- Menu -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.turnkey_factory.leveler");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new LevelerMenu(id, inv, this);
    }
}
