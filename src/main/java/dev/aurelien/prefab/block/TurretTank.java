package dev.aurelien.prefab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * Réservoir de lave du <em>socle</em> de tourelle, composé par les deux variantes de socle
 * ({@link TurretBaseBlockEntity} et l'implémentation Create de compat/create) exactement comme
 * {@link TurretCombat} l'est — pour la même raison : les deux BlockEntity n'ont pas de classe mère
 * commune, donc composition plutôt qu'héritage.
 * <p>
 * <strong>Il vit sur le socle, pas sur l'arme</strong>, alors que seul le lance-flammes
 * ({@link TurretFlamethrowerBlock}) s'en sert. Deux raisons : le socle est au sol, c'est contre lui
 * qu'on branche un tuyau et c'est lui qu'on vise avec un seau ; et un réservoir qui appartiendrait à
 * l'arme perdrait son contenu à chaque échange d'arme. Avec une mitrailleuse montée il reste
 * simplement inutilisé.
 * <p>
 * Deux entrées, sans une ligne de code spécifique à Create :
 * <ul>
 *   <li>au seau (ou n'importe quel conteneur de fluide), cf. {@link #interactWithHeldContainer} ;</li>
 *   <li>par tuyau, via {@code Capabilities.FluidHandler.BLOCK} enregistrée sur les deux
 *       {@code BlockEntityType} (cf. {@code PrefabMod#registerCapabilities}) — c'est la capability
 *       NeoForge nue, que les tuyaux de Create parlent comme tout le monde.</li>
 * </ul>
 * <p>
 * Persistance : {@link #save}/{@link #load} sont appelées depuis {@code saveAdditional} (socle
 * charbon) et {@code write} (socle Create). Le contenu voyage donc jusqu'au client tout seul, par
 * {@code getUpdateTag} / {@code clientPacket=true} — <strong>aucun payload à écrire</strong> pour
 * afficher la jauge.
 */
public class TurretTank {
    /** Capacité, en seaux. Huit, comme demandé : de quoi tenir un siège sans devenir une réserve. */
    public static final int BUCKETS = 8;
    public static final int CAPACITY = BUCKETS * FluidType.BUCKET_VOLUME;

    /**
     * Le contenu change à chaque poussée d'un tuyau, soit potentiellement à chaque tick : envoyer un
     * {@code sendBlockUpdated} à chacune saturerait le réseau pour une jauge qui bouge de trois
     * pixels. On marque « sale » et on n'émet qu'au plus une fois par demi-seconde (cf.
     * {@link #serverTick}). La toute première modification après une accalmie, elle, part
     * immédiatement — c'est celle que le joueur regarde quand il vide son seau.
     */
    private static final int SYNC_INTERVAL = 10;

    private final FluidTank tank;
    private final Runnable syncToClient;
    private boolean dirty = false;
    private int syncCooldown = 0;

    public TurretTank(Runnable syncToClient) {
        this.syncToClient = syncToClient;
        // Validateur = lave uniquement : un tuyau qui pousse de l'eau se voit refuser le transfert
        // par la capability elle-même, plutôt que d'accepter puis de bloquer la tourelle.
        this.tank = new FluidTank(CAPACITY, stack -> stack.getFluid() == Fluids.LAVA) {
            @Override
            protected void onContentsChanged() {
                dirty = true;
            }
        };
    }

    /** Ce qu'on expose à la capability : le handler brut, remplissable et vidangeable. */
    public IFluidHandler handler() {
        return tank;
    }

    public int amount() {
        return tank.getFluidAmount();
    }

    public float fraction() {
        return (float) tank.getFluidAmount() / CAPACITY;
    }

    public boolean has(int mB) {
        return tank.getFluidAmount() >= mB;
    }

    /** Prélève {@code mB} d'un seul bloc, ou rien du tout : jamais de tir à moitié payé. */
    public boolean tryDrain(int mB) {
        if (!has(mB)) return false;
        tank.drain(mB, IFluidHandler.FluidAction.EXECUTE);
        return true;
    }

    /** À appeler depuis le tick serveur du socle — cf. {@link #SYNC_INTERVAL}. */
    public void serverTick() {
        if (syncCooldown > 0) syncCooldown--;
        if (!dirty || syncCooldown > 0) return;
        dirty = false;
        syncCooldown = SYNC_INTERVAL;
        syncToClient.run();
    }

    // ----- Interaction au seau -----

    /**
     * Remplit (ou vide) le réservoir avec le conteneur de fluide tenu en main. Renvoie {@code false}
     * quand il n'y a rien à faire, auquel cas l'appelant doit laisser filer l'interaction normale —
     * c'est ce qui garde le clic droit sur l'ouverture de l'interface.
     * <p>
     * « Rien à faire » couvre deux cas, et c'est le second qui compte :
     * <ul>
     *   <li>l'item tenu n'est pas un conteneur de fluide ;</li>
     *   <li>c'en est un, mais aucun transfert n'est possible — seau plein contre réservoir plein,
     *       seau vide contre réservoir vide. {@code FluidUtil.interactWithFluidHandler} renvoie
     *       {@code false} dans ces cas-là (vérifié dans les sources NeoForge), et s'arrêter à
     *       « c'est un conteneur » avalerait le clic sans rien afficher : un joueur avec un seau de
     *       lave en main devant un réservoir plein n'arriverait tout simplement plus à ouvrir son
     *       interface.</li>
     * </ul>
     * <p>
     * Seul le serveur mute quoi que ce soit — le client ne doit pas se fabriquer un seau vide qu'un
     * paquet viendrait reprendre. Côté client on répond donc « traité » dès qu'un conteneur est en
     * main, sans savoir si le transfert aboutira : c'est sans conséquence, parce que l'ouverture de
     * l'interface est entièrement pilotée par le serveur ({@code openMenu}) et que la branche
     * cliente de {@code useWithoutItem} ne fait rien. Au pire, une animation de bras en trop.
     */
    public static boolean interactWithHeldContainer(ItemStack held, Level level, BlockPos pos,
                                                    Player player, InteractionHand hand, @Nullable Direction side) {
        if (held.getCapability(Capabilities.FluidHandler.ITEM) == null) {
            return false;
        }
        return level.isClientSide || FluidUtil.interactWithFluidHandler(player, hand, level, pos, side);
    }

    // ----- Persistance -----

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put("lava", tank.writeToNBT(registries, new CompoundTag()));
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("lava")) {
            tank.readFromNBT(registries, tag.getCompound("lava"));
        }
    }
}
