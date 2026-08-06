package dev.aurelien.prefab.menu;

import dev.aurelien.prefab.block.LamplighterBlockEntity;
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

/** Aucun slot machine : les matériaux viennent exclusivement des inventaires liés (cf. le texturiseur pour le pattern). */
public class LamplighterMenu extends AbstractContainerMenu {
    private static final int INV_START = 0;
    private static final int INV_END = INV_START + 27;   // 3x9 inventaire principal
    private static final int HOTBAR_END = INV_END + 9;    // + hotbar

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    public LamplighterMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public LamplighterMenu(int id, Inventory inv, LamplighterBlockEntity be) {
        this(id, inv, be.getBlockPos());
    }

    private LamplighterMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.LAMPLIGHTER.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 9 + col * 18, 144 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 9 + col * 18, 202));
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
        return stillValid(access, player, ModBlocks.LAMPLIGHTER.get());
    }
}
