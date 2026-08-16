package dev.aurelien.prefab.block;

import dev.aurelien.prefab.build.InventoryNetwork;
import dev.aurelien.prefab.build.NaturalTerrain;
import dev.aurelien.prefab.config.PrefabServerConfig;
import dev.aurelien.prefab.menu.LamplighterMenu;
import dev.aurelien.prefab.reg.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Plante des lampadaires (muret + 3 fences + slab en poteau, bras potence en trapdoor + chaîne +
 * lanterne) répartis sur une grille espacée autour du bloc, en restant strictement au sol. La
 * propagation qui trouve le sol de chaque colonne est celle du {@link TexturizerBlockEntity}
 * (BFS en cercles concentriques, fenêtre de suivi {@link #STEP_WINDOW}) : elle refuse déjà toute
 * colonne qui grimperait sur une paroi verticale (falaise, mur d'usine). Le seul angle mort de
 * cette technique — un toit plat en palette pierre passe le même test de tag que de la pierre
 * naturelle — est couvert par {@link #hasSolidGroundBelow} : un toit repose sur le vide intérieur
 * du bâtiment, un vrai sol non. Coût par lampadaire : 1 torche + 1 lingot de fer + 1 bûche (peu
 * importe l'essence, cf. {@link #pickSpecies}) puisés dans les inventaires liés — la essence
 * choisie détermine seulement quelle variante de fence/slab/trapdoor est posée, exactement comme
 * le texturiseur puise 1 cobblestone pour poser indifféremment gravier/andésite/pierre.
 */
public class LamplighterBlockEntity extends BlockEntity implements MenuProvider, CenterableMachine {
    /** Bornes réglables via {@link PrefabServerConfig#LAMPLIGHTER_MIN_RANGE}/{@code LAMPLIGHTER_MAX_RANGE}. */
    public static int minRange() { return PrefabServerConfig.LAMPLIGHTER_MIN_RANGE.get(); }
    public static int maxRange() { return PrefabServerConfig.LAMPLIGHTER_MAX_RANGE.get(); }
    public static final int DEFAULT_RANGE = 24;

    // Espacement par défaut : la lanterne éclaire en niveau 15, -1 par bloc parcouru. A mi-chemin
    // entre deux lampadaires espacés de 12, on est a distance 6 -> niveau ~9, confortablement
    // au-dessus du seuil historique (8) qui empêche les spawns hostiles sur un bloc opaque.
    /** Bornes réglables via {@link PrefabServerConfig#LAMPLIGHTER_MIN_SPACING}/{@code LAMPLIGHTER_MAX_SPACING}. */
    public static int minSpacing() { return PrefabServerConfig.LAMPLIGHTER_MIN_SPACING.get(); }
    public static int maxSpacing() { return PrefabServerConfig.LAMPLIGHTER_MAX_SPACING.get(); }
    public static final int DEFAULT_SPACING = 12;

    private static final int STEP_WINDOW = 3;
    private static final int SCAN_INTERVAL = 20;
    private static final int WORK_PER_TICK = 1;

    public static final int STATUS_NO_SPECIES = 0;
    public static final int STATUS_WORKING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_MISSING_MATERIAL = 3;
    public static final int STATUS_INACTIVE = 4;
    public static final int STATUS_NO_LINK = 5;

    private int range = clampRange(DEFAULT_RANGE);
    private int spacing = clampSpacing(DEFAULT_SPACING);
    private int scanCooldown = 0;
    private boolean active = false;
    private boolean planComputed = false;

    private final List<BlockPos> linked = new ArrayList<>();
    private final ArrayDeque<LampJob> queue = new ArrayDeque<>();
    /** Cf. {@link CenterableMachine} : {@code null} = cette machine est sa propre référence géométrique. */
    @Nullable
    private BlockPos centerPos;

    private int status = STATUS_NO_LINK;
    private int queueSizeClient = 0;
    private int totalLamps = 0;
    private int availTorch = 0;
    private int availIron = 0;
    private int availLog = 0;
    private String speciesLabel = "";
    /** Id d'enregistrement de la bûche résolue (ex. "minecraft:oak_log"), pour l'icône côté client. */
    private String speciesLogId = "";

    public LamplighterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAMPLIGHTER.get(), pos, state);
    }

    // ----- Configuration -----

    public int range() { return range; }
    public int spacing() { return spacing; }
    public int status() { return status; }
    public int queueSize() { return queueSizeClient; }
    public int totalLamps() { return totalLamps; }
    public int availTorch() { return availTorch; }
    public int availIron() { return availIron; }
    public int availLog() { return availLog; }
    public String speciesLabel() { return speciesLabel; }
    public String speciesLogId() { return speciesLogId; }
    public boolean active() { return active; }

    // ----- Checklist GUI (montre TOUTES les conditions à la fois, cf. TurretScreen#drawChecklist) -----

    public boolean hasLink() { return !linked.isEmpty(); }
    public boolean hasSpecies() { return !speciesLabel.isEmpty(); }
    public boolean hasMaterial() { return availTorch > 0 && availIron > 0 && availLog > 0; }

    public void setActive(boolean value) {
        if (value && linked.isEmpty()) {
            return;
        }
        this.active = value;
        syncToClient();
    }

    public void setRange(int r) {
        this.range = clampRange(r);
        onConfigChanged();
    }

    public void setSpacing(int s) {
        this.spacing = clampSpacing(s);
        onConfigChanged();
    }

    public static int clampRange(int v) {
        return Math.max(minRange(), Math.min(maxRange(), v));
    }

    public static int clampSpacing(int v) {
        return Math.max(minSpacing(), Math.min(maxSpacing(), v));
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

    private void onConfigChanged() {
        queue.clear();
        totalLamps = 0;
        if (level instanceof ServerLevel server) {
            computePlan(server);
            planComputed = true;
        }
        syncToClient();
    }

    // ----- Calcul du plan (géométrie uniquement, l'essence de bois se résout au moment de bâtir) -----

    private record Candidate(int x, int z, int refY, int distSq) {}
    private record LampJob(BlockPos pole, Direction dir) {}

    private void computePlan(ServerLevel server) {
        queue.clear();

        BlockPos origin = originPos();
        int ox = origin.getX();
        int oz = origin.getZ();
        int seedY = origin.getY() - 1;
        int r2 = range * range;
        int maxCells = (2 * range + 1) * (2 * range + 1);

        PriorityQueue<Candidate> frontier = new PriorityQueue<>(Comparator.comparingInt(Candidate::distSq));
        Set<Long> visited = new HashSet<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();

        visited.add(packXZ(ox, oz));
        frontier.add(new Candidate(ox, oz, seedY, 0));

        List<LampJob> jobs = new ArrayList<>();
        int visitedCount = 0;
        while (!frontier.isEmpty() && visitedCount < maxCells) {
            Candidate c = frontier.poll();
            visitedCount++;
            Integer surfaceY = findSurfaceY(server, c.x(), c.z(), c.refY(), p);
            if (surfaceY == null) continue;

            int dx = c.x() - ox;
            int dz = c.z() - oz;
            if ((dx != 0 || dz != 0) && Math.floorMod(dx, spacing) == 0 && Math.floorMod(dz, spacing) == 0) {
                BlockPos pole = new BlockPos(c.x(), surfaceY, c.z());
                Direction dir = pickClearDirection(server, pole, radialDirection(dx, dz));
                if (dir != null) {
                    jobs.add(new LampJob(pole, dir));
                }
            }

            for (Direction d : Direction.Plane.HORIZONTAL) {
                int nx = c.x() + d.getStepX();
                int nz = c.z() + d.getStepZ();
                int ndx = nx - ox;
                int ndz = nz - oz;
                int distSq = ndx * ndx + ndz * ndz;
                if (distSq > r2) continue;
                if (!visited.add(packXZ(nx, nz))) continue;
                frontier.add(new Candidate(nx, nz, surfaceY, distSq));
            }
        }

        queue.addAll(jobs);
        totalLamps = jobs.size();
    }

    private static Direction radialDirection(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Identique au texturiseur (voir sa javadoc pour le détail de la fenêtre de suivi) : seule
     * l'identité du bloc de sol compte, jamais ce qui repose dessus (torche, clôture, tuyau Create,
     * lampadaire déjà bâti, machine voisine...). Mélanger les deux coupait la propagation dès la
     * première colonne encombrée — exactement le piège que documente {@code isFinishedTexture} côté
     * texturiseur — alors qu'un lampadaire non plaçable ici ne doit empêcher ni les colonnes voisines
     * d'être explorées, ni le rayon de s'étendre au-delà. Le dégagement réel de la fixture (8 cellules)
     * est revérifié séparément par {@link #hasClearance} au moment de la pose, seul endroit où une
     * obstruction doit faire sauter CE lampadaire précis. {@link #hasSolidGroundBelow} rejette tout
     * support qui repose sur du vide (toit, plateforme).
     */
    @Nullable
    private static Integer findSurfaceY(ServerLevel server, int x, int z, int refY, BlockPos.MutableBlockPos p) {
        for (int y = refY + STEP_WINDOW; y >= refY - STEP_WINDOW; y--) {
            p.set(x, y, z);
            if (!server.isLoaded(p)) continue;
            BlockState state = server.getBlockState(p);
            if (!NaturalTerrain.isSurfaceGround(state)) continue;
            if (!hasSolidGroundBelow(server, x, y, z)) continue;
            return y;
        }
        return null;
    }

    /**
     * Un toit d'usine repose sur le vide intérieur du bâtiment (plusieurs blocs de hauteur sous
     * lui) ; un vrai sol a toujours quelque chose juste en dessous. Un seul bloc de profondeur
     * suffit à faire la distinction sans rejeter à tort de fines couches naturelles (neige sur
     * pierre, etc.) qu'une exigence sur 2 blocs pourrait invalider.
     */
    private static boolean hasSolidGroundBelow(ServerLevel server, int x, int y, int z) {
        BlockPos below = new BlockPos(x, y - 1, z);
        if (!server.isLoaded(below)) return false;
        BlockState state = server.getBlockState(below);
        return !state.isAir() && !state.canBeReplaced();
    }

    /**
     * Les 8 positions de la fixture, calculées une seule fois et partagées entre la vérification de
     * dégagement et la pose réelle — les deux étaient dupliquées avant et avaient fini par diverger
     * en hauteur (bras trop bas d'un bloc). Poteau : muret puis 3 fences puis slab en couronnement.
     * Bras (trapdoor/chaîne/lanterne) aligné sur le haut du poteau : la trapdoor est au niveau du
     * slab, la chaîne au niveau de la 3e fence, la lanterne au niveau de la 2e — la fence du bas
     * n'a que valeur de hauteur, sans bras dessus.
     */
    private record FixtureLayout(BlockPos wall, BlockPos fence1, BlockPos fence2, BlockPos fence3, BlockPos slab,
                                  BlockPos trapdoor, BlockPos chain, BlockPos lantern) {
        static FixtureLayout of(BlockPos pole, Direction dir) {
            BlockPos wall = pole.above();
            BlockPos fence1 = wall.above();
            BlockPos fence2 = fence1.above();
            BlockPos fence3 = fence2.above();
            BlockPos slab = fence3.above();
            BlockPos trapdoor = slab.relative(dir);
            BlockPos chain = fence3.relative(dir);
            BlockPos lantern = fence2.relative(dir);
            return new FixtureLayout(wall, fence1, fence2, fence3, slab, trapdoor, chain, lantern);
        }

        List<BlockPos> cells() {
            return List.of(wall, fence1, fence2, fence3, slab, trapdoor, chain, lantern);
        }
    }

    /**
     * Les 8 cellules de la fixture doivent être libres : jamais percer un bâtiment ou une frondaison
     * ({@code LeafBlock} n'est pas un {@link BushBlock}, donc les feuilles restent bloquantes). La
     * petite flore au sol ({@link BushBlock} : herbe, fougère, fleur, jeune pousse, culture, buisson
     * mort...) ne compte en revanche pas comme un obstacle — elle est écrasée à la pose, cf.
     * {@link #placeLamp}, dans le même esprit que le texturiseur (voir sa javadoc).
     */
    private static boolean hasClearance(ServerLevel server, BlockPos pole, Direction dir) {
        for (BlockPos cell : FixtureLayout.of(pole, dir).cells()) {
            if (!server.isLoaded(cell)) return false;
            BlockState state = server.getBlockState(cell);
            if (!(state.isAir() || state.canBeReplaced() || state.getBlock() instanceof BushBlock)) return false;
        }
        return true;
    }

    /**
     * Essaie la direction radiale (vers l'extérieur du bloc) en priorité, puis les 3 autres : sur un
     * terrain accidenté, la direction radiale seule échouait souvent (bosse/rocher juste dans le sens
     * du bras) et faisait sauter tout le lampadaire alors qu'une autre direction aurait suffi.
     */
    @Nullable
    private static Direction pickClearDirection(ServerLevel server, BlockPos pole, Direction radial) {
        if (hasClearance(server, pole, radial)) return radial;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (d != radial && hasClearance(server, pole, d)) return d;
        }
        return null;
    }

    private static long packXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
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

        boolean working = false;

        if (!active) {
            setStatus(linked.isEmpty() ? STATUS_NO_LINK : STATUS_INACTIVE);
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
                    LampJob job = queue.peek();

                    // Revalidation à l'exécution : le plan a pu être calculé bien avant.
                    if (!hasClearance(server, job.pole(), job.dir())) {
                        queue.poll();
                        done++;
                        continue;
                    }

                    Species species = pickSpecies(server);
                    if (species == null) {
                        setStatus(STATUS_NO_SPECIES);
                        working = false;
                        break;
                    }
                    if (InventoryNetwork.countEligible(server, linked, i -> i == Items.TORCH) < 1
                            || InventoryNetwork.countEligible(server, linked, i -> i == Items.IRON_INGOT) < 1
                            || InventoryNetwork.countEligible(server, linked, i -> i == species.logItem()) < 1) {
                        setStatus(STATUS_MISSING_MATERIAL);
                        working = false;
                        break;
                    }

                    InventoryNetwork.extract(server, linked, Items.TORCH, 1);
                    InventoryNetwork.extract(server, linked, Items.IRON_INGOT, 1);
                    InventoryNetwork.extract(server, linked, species.logItem(), 1);
                    placeLamp(server, job, species.parts());
                    applySpecies(species);

                    queue.poll();
                    done++;
                }
                refreshAvailability(server);
                if (working) {
                    setStatus(queue.isEmpty() ? STATUS_DONE : STATUS_WORKING);
                }
            }
        }

        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            boolean dirty = InventoryNetwork.rescan(server, getBlockPos(), linked);
            if (working || queueSizeClient != queue.size()) dirty = true;
            // Rafraîchi même à l'arrêt (pas seulement après une pose) : sans ça, le GUI affiche
            // encore "aucune essence" juste après avoir lié un coffre plein de bûches, tant qu'aucun
            // lampadaire n'a encore été construit pour déclencher la mise à jour.
            applySpecies(pickSpecies(server));
            refreshAvailability(server);
            dirty = true;
            if (!active) {
                computePlan(server);
            }
            if (dirty) syncToClient();
        }
    }

    /** Recalcule le stock par type de ressource (affiché dans le GUI en face du besoin total). */
    private void refreshAvailability(ServerLevel server) {
        Species species = pickSpecies(server);
        availTorch = InventoryNetwork.countEligible(server, linked, i -> i == Items.TORCH);
        availIron = InventoryNetwork.countEligible(server, linked, i -> i == Items.IRON_INGOT);
        availLog = species == null ? 0 : InventoryNetwork.countEligible(server, linked, i -> i == species.logItem());
    }

    private void placeLamp(ServerLevel server, LampJob job, WoodParts parts) {
        Direction dir = job.dir();
        FixtureLayout f = FixtureLayout.of(job.pole(), dir);

        // Écrase la petite flore tolérée par hasClearance (herbe, fougère, fleur...) avec un drop
        // normal, comme si un joueur l'avait fauchée, plutôt que de la faire disparaître en silence
        // sous le setBlock qui suit.
        for (BlockPos cell : f.cells()) {
            if (!server.getBlockState(cell).isAir()) {
                server.destroyBlock(cell, true);
            }
        }

        server.setBlock(f.wall(), Blocks.COBBLESTONE_WALL.defaultBlockState(), Block.UPDATE_ALL);
        server.setBlock(f.fence1(), parts.fence().defaultBlockState(), Block.UPDATE_ALL);
        server.setBlock(f.fence2(), parts.fence().defaultBlockState(), Block.UPDATE_ALL);
        server.setBlock(f.fence3(), parts.fence().defaultBlockState(), Block.UPDATE_ALL);
        server.setBlock(f.slab(), parts.slab().defaultBlockState(), Block.UPDATE_ALL);
        // FACING = dir (pas l'opposé) : le bras pointe vers l'extérieur. OPEN = false : la trapdoor
        // reste à plat (horizontale), en console, au lieu de se dresser à la verticale.
        server.setBlock(f.trapdoor(), parts.trapdoor().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, dir)
                .setValue(BlockStateProperties.HALF, Half.BOTTOM)
                .setValue(BlockStateProperties.OPEN, false), Block.UPDATE_ALL);
        server.setBlock(f.chain(), Blocks.CHAIN.defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
        server.setBlock(f.lantern(), Blocks.LANTERN.defaultBlockState()
                .setValue(BlockStateProperties.HANGING, true), Block.UPDATE_ALL);
    }

    // ----- Résolution de l'essence de bois -----

    private record Species(Item logItem, WoodParts parts, String label) {}
    private record WoodParts(Block fence, Block slab, Block trapdoor) {
        @Nullable
        static WoodParts resolve(Item logItem, String namespace, String speciesPath) {
            Block fence = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, speciesPath + "_fence")).orElse(null);
            Block slab = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, speciesPath + "_slab")).orElse(null);
            Block trapdoor = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(namespace, speciesPath + "_trapdoor")).orElse(null);
            if (fence == null || slab == null || trapdoor == null) return null;
            return new WoodParts(fence, slab, trapdoor);
        }
    }

    /** "stripped_dark_oak_log" -> "dark_oak" ; couvre logs, wood, stems et hyphae (troncs du Nether). */
    private static String speciesPath(ResourceLocation logId) {
        String path = logId.getPath();
        if (path.startsWith("stripped_")) path = path.substring("stripped_".length());
        for (String suffix : new String[]{"_log", "_wood", "_stem", "_hyphae"}) {
            if (path.endsWith(suffix)) {
                path = path.substring(0, path.length() - suffix.length());
                break;
            }
        }
        return path;
    }

    /**
     * Recalculé à chaque tentative de pose (pas mis en cache) : les stocks changent au fil de la
     * construction, exactement comme le texturiseur revérifie sa cobblestone à chaque cellule.
     * Choisit l'essence la plus abondante parmi les bûches présentes qui a bien une fence/slab/
     * trapdoor correspondante enregistrée (repli gracieux si un pack de bois moddé ne fournit pas
     * les trois, même esprit que {@code CreateCompat}).
     */
    private Species pickSpecies(ServerLevel server) {
        Map<Item, Integer> tally = new LinkedHashMap<>();
        for (BlockPos p : linked) {
            if (!server.isLoaded(p)) continue;
            IItemHandler handler = server.getCapability(Capabilities.ItemHandler.BLOCK, p, null);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                if (!(item instanceof BlockItem bi) || !bi.getBlock().defaultBlockState().is(BlockTags.LOGS)) continue;
                tally.merge(item, stack.getCount(), Integer::sum);
            }
        }
        return tally.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> {
                    if (!(e.getKey() instanceof BlockItem blockItem)) return null;
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
                    String path = speciesPath(id);
                    WoodParts parts = WoodParts.resolve(e.getKey(), id.getNamespace(), path);
                    if (parts == null) return null;
                    String label = path.substring(0, 1).toUpperCase() + path.substring(1).replace('_', ' ');
                    return new Species(e.getKey(), parts, label);
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Met à jour l'essence affichée/synchronisée (libellé + id d'item pour l'icône côté client). */
    private void applySpecies(@Nullable Species species) {
        if (species == null) {
            speciesLabel = "";
            speciesLogId = "";
        } else {
            speciesLabel = species.label();
            speciesLogId = BuiltInRegistries.ITEM.getKey(species.logItem()).toString();
        }
    }

    private void setStatus(int s) {
        if (status != s) {
            status = s;
            syncToClient();
        }
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
        tag.putInt("totalLamps", totalLamps);
        tag.putInt("availTorch", availTorch);
        tag.putInt("availIron", availIron);
        tag.putInt("availLog", availLog);
        tag.putString("species", speciesLabel);
        tag.putString("speciesLogId", speciesLogId);
        return tag;
    }

    // ----- Persistance -----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("range", range);
        tag.putInt("spacing", spacing);
        tag.putBoolean("active", active);
        tag.putLongArray("linked", linked.stream().mapToLong(BlockPos::asLong).toArray());
        if (centerPos != null) tag.putLong("centerPos", centerPos.asLong());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("range")) range = clampRange(tag.getInt("range"));
        if (tag.contains("spacing")) spacing = clampSpacing(tag.getInt("spacing"));
        if (tag.contains("active")) active = tag.getBoolean("active");

        linked.clear();
        for (long packed : tag.getLongArray("linked")) {
            linked.add(BlockPos.of(packed));
        }
        centerPos = tag.contains("centerPos") ? BlockPos.of(tag.getLong("centerPos")) : null;

        // transitoire (présent uniquement dans les paquets réseau)
        if (tag.contains("status")) status = tag.getInt("status");
        if (tag.contains("queueSize")) queueSizeClient = tag.getInt("queueSize");
        if (tag.contains("totalLamps")) totalLamps = tag.getInt("totalLamps");
        if (tag.contains("availTorch")) availTorch = tag.getInt("availTorch");
        if (tag.contains("availIron")) availIron = tag.getInt("availIron");
        if (tag.contains("availLog")) availLog = tag.getInt("availLog");
        if (tag.contains("species")) speciesLabel = tag.getString("species");
        if (tag.contains("speciesLogId")) speciesLogId = tag.getString("speciesLogId");
    }

    // ----- Menu -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.turnkey_factory.lamplighter");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new LamplighterMenu(id, inv, this);
    }
}
