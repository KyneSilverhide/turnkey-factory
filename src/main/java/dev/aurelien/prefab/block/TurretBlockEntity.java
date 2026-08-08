package dev.aurelien.prefab.block;

import dev.aurelien.prefab.build.InventoryNetwork;
import dev.aurelien.prefab.menu.TurretMenu;
import dev.aurelien.prefab.reg.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Tourelle défensive alimentée au charbon : tir en portée directe (hitscan, pas d'entité-projectile)
 * rendu visible par une traînée de particules + un son. Le ciblage/tir proprement dit vit dans
 * {@link TurretCombat} (partagé avec l'implémentation Create de compat/create, cf. sa javadoc) ;
 * cette classe n'apporte que la source d'énergie (charbon) et la persistance/sync vanilla.
 * <p>
 * Alimentation en charbon (famille de combustibles vanilla, pas seulement {@code Items.COAL}) tirée
 * des inventaires liés — le flood-fill {@link InventoryNetwork} et son résultat ({@code linked})
 * vivent dans {@link TurretCombat} (partagés avec les munitions, identiques pour les deux variantes
 * de tourelle), réutilisés ici via {@link TurretCombat#linkedInventories()} plutôt que refaits en
 * double. Pas de slot dédié dans le menu. {@link #charge} est une jauge à capacité fixe
 * ({@link #MAX_SHOTS} tirs) : chaque tir en décompte 1 (jamais le scan à vide), et le ravitaillement
 * (cf. {@link #tryRefuel}) tourne indépendamment du tir, au rythme de {@link #LINK_SCAN_INTERVAL} —
 * il puise un item de combustible dans les inventaires liés et l'ajoute à la jauge, mais seulement
 * s'il y rentre entièrement ({@link #shotsOf} ≤ place restante) : jamais de combustible à moitié
 * consommé/gâché. À {@link #TICKS_PER_SHOT} = 200 (le coût par item d'un four vanilla), un charbon
 * (1600 ticks) vaut 8 tirs — le même nombre d'objets qu'il peut cuire.
 */
public class TurretBlockEntity extends BlockEntity implements MenuProvider, ITurret {
    public static final int MIN_RANGE = TurretCombat.MIN_RANGE;
    public static final int MAX_RANGE = TurretCombat.MAX_RANGE;
    public static final int DEFAULT_RANGE = TurretCombat.DEFAULT_RANGE;

    private static final int LINK_SCAN_INTERVAL = 20;
    private static final int TICKS_PER_SHOT = 200;
    private static final int MAX_SHOTS = 512;

    /** Cadence fixe : le charbon fournit de l'énergie, pas de la vitesse — rien à faire varier ici,
     *  contrairement à la variante Create dont la cadence suit le régime du réseau cinétique. */
    private final TurretCombat combat = new TurretCombat(this, this::tryConsumeShot, this::syncToClient,
            () -> {}, () -> TurretCombat.DEFAULT_FIRE_INTERVAL);

    private int refuelCooldown = 0;
    /** Jauge en nombre de tirs, jamais en ticks de combustion — cf. javadoc de classe. */
    private int charge = 0;

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURRET.get(), pos, state);
    }

    // ----- ITurret -----

    @Override public int range() { return combat.range(); }
    @Override public boolean active() { return combat.active(); }
    @Override public boolean targetHostile() { return combat.targetHostile(); }
    @Override public boolean targetNeutral() { return combat.targetNeutral(); }
    @Override public boolean targetPlayer() { return combat.targetPlayer(); }
    @Override public int currentTargetId() { return combat.currentTargetId(); }
    @Override public void setActive(boolean value) { combat.setActive(value); }
    @Override public void setRange(int r) { combat.setRange(r); }
    @Override public void setTargets(boolean hostile, boolean neutral, boolean player) { combat.setTargets(hostile, neutral, player); }
    @Override public boolean hasPower() { return charge > 0; }
    @Override public boolean hasAmmo() { return combat.hasAmmo(); }

    /** Enregistré une seule fois par {@link dev.aurelien.prefab.block.TurretBlock#setPlacedBy}. */
    public void setOwner(java.util.UUID id) { combat.setOwner(id); }

    @Override
    public Component powerLabel() {
        // Nombre de tirs réellement restants À LA PORTÉE ACTUELLE (cf. chargeCostFor) : charge est une
        // réserve d'énergie brute depuis que le coût par tir varie avec la portée, plus un simple
        // compteur de tirs — l'afficher tel quel tromperait le joueur sur l'autonomie réelle.
        return Component.translatable("gui.turnkey_factory.turret.fuel", charge / chargeCostFor(combat.range()));
    }

    @Override
    public float powerFraction() {
        return (float) charge / MAX_SHOTS;
    }

    public static int clampRange(int v) {
        return TurretCombat.clampRange(v);
    }

    /**
     * Vrai jusqu'au premier tick serveur après (re)création de ce BlockEntity (pose OU chargement d'un
     * chunk) : {@code active} est persisté (cf. {@link TurretCombat#save}) mais doit rester le reflet du
     * signal redstone RÉEL, pas d'une valeur figée sur disque — {@code onPlace}/{@code neighborChanged}
     * (cf. {@link TurretBlock}) ne se redéclenchent pas à un simple rechargement de chunk sans
     * changement de voisin, donc sans ce resync l'état chargé peut rester en désaccord avec le signal
     * effectivement présent au bloc.
     */
    private boolean pendingRedstoneSync = true;

    // ----- Tick serveur -----

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;

        if (pendingRedstoneSync) {
            pendingRedstoneSync = false;
            ITurret.syncRedstoneState(level, getBlockPos());
        }

        combat.serverTick(server);

        if (--refuelCooldown <= 0) {
            refuelCooldown = LINK_SCAN_INTERVAL;
            if (tryRefuel(server)) syncToClient();
        }
    }

    /**
     * Ravitaillement indépendant du tir (contrairement à {@link #tryConsumeShot}, appelé à chaque
     * {@link #LINK_SCAN_INTERVAL} tant que la jauge n'est pas pleine) : un seul item de combustible
     * par appel, n'importe lequel accepté par un four vanilla ({@link AbstractFurnaceBlockEntity#isFuel}).
     * Ne consomme rien si l'item pioché ne rentre pas entièrement dans la place restante — évite de
     * gâcher un seau de lave pour 3 tirs de marge alors qu'un charbon suffirait, au prix de laisser
     * la toute fin de jauge non comblée si seul du combustible surdimensionné est disponible.
     */
    private boolean tryRefuel(ServerLevel server) {
        int headroom = MAX_SHOTS - charge;
        if (headroom <= 0) return false;

        List<BlockPos> linked = combat.linkedInventories();
        Item fuel = InventoryNetwork.pickWeightedRandom(server, linked,
                i -> AbstractFurnaceBlockEntity.isFuel(new ItemStack(i)), server.getRandom());
        if (fuel == null) return false;

        int shots = shotsOf(fuel);
        if (shots <= 0 || shots > headroom) return false;

        InventoryNetwork.extract(server, linked, fuel, 1);
        charge += shots;
        return true;
    }

    private static int shotsOf(Item fuel) {
        return AbstractFurnaceBlockEntity.getFuel().getOrDefault(fuel, 0) / TICKS_PER_SHOT;
    }

    /**
     * Décompte le coût d'1 tir de la jauge (cf. javadoc de classe) — jamais appelé en dehors d'un tir
     * effectif. Le coût croît avec la portée configurée ({@link #chargeCostFor}) : une tourelle réglée
     * au maximum (32) coûte 4× plus cher à faire tourner qu'au minimum (4), plutôt qu'un coût de tir
     * plat indépendant de la zone de couverture choisie.
     */
    private boolean tryConsumeShot() {
        int cost = chargeCostFor(combat.range());
        if (charge < cost) return false;
        charge -= cost;
        syncToClient();
        return true;
    }

    /** 1 charge au minimum de portée (4), jusqu'à 4 charges au maximum (32) — palier tous les 8 blocs. */
    private static int chargeCostFor(int range) {
        return Math.max(1, (range + 7) / 8);
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
        combat.saveTransient(tag);
        return tag;
    }

    // ----- Persistance -----

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        combat.save(tag);
        tag.putInt("charge", charge);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        combat.load(tag);
        if (tag.contains("charge")) charge = Math.min(MAX_SHOTS, tag.getInt("charge"));

        // transitoire (présent uniquement dans les paquets réseau)
        combat.loadTransient(tag);
    }

    // ----- Menu -----

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.turnkey_factory.turret");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new TurretMenu(id, inv, getBlockPos());
    }
}
