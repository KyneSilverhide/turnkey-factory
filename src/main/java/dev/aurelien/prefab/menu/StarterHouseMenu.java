package dev.aurelien.prefab.menu;

import dev.aurelien.prefab.block.StarterHouseBlockEntity;
import dev.aurelien.prefab.reg.ModBlocks;
import dev.aurelien.prefab.reg.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Aucun slot machine : le kit ne consomme rien d'autre que lui-même (cf. l'allumeur de réverbères
 * pour le même patron à deux rangées d'inventaire joueur et rien d'autre).
 */
public class StarterHouseMenu extends AbstractContainerMenu {
    private static final int INV_START = 0;
    private static final int INV_END = INV_START + 27;   // 3x9 inventaire principal
    private static final int HOTBAR_END = INV_END + 9;    // + hotbar

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    public StarterHouseMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public StarterHouseMenu(int id, Inventory inv, StarterHouseBlockEntity be) {
        this(id, inv, be.getBlockPos());
    }

    private StarterHouseMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.STARTER_HOUSE.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        // Mêmes coordonnées que les autres machines, dupliquées pour la raison expliquée dans
        // LamplighterMenu : un menu se construit aussi sur serveur dédié, où MachineScreen n'existe pas.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 69 + col * 18, 154 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 69 + col * 18, 212));
        }
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();

        if (index < INV_END) {
            if (!moveItemStackTo(stackInSlot, INV_END, HOTBAR_END, false)) return ItemStack.EMPTY;
        } else if (index < HOTBAR_END) {
            if (!moveItemStackTo(stackInSlot, INV_START, INV_END, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.STARTER_HOUSE.get());
    }
}
