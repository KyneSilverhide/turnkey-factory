package dev.aurelien.prefab.menu;

import dev.aurelien.prefab.block.LevelerBlockEntity;
import dev.aurelien.prefab.reg.ModBlocks;
import dev.aurelien.prefab.reg.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class LevelerMenu extends AbstractContainerMenu {
    private static final int SHOVEL_SLOT = 0;
    private static final int PICKAXE_SLOT = 1;
    private static final int INV_START = 2;
    private static final int INV_END = INV_START + 27;   // 3x9 inventaire principal
    private static final int HOTBAR_END = INV_END + 9;    // + hotbar

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    /** Côté client : les slots pelle/pioche sont un conteneur factice, synchronisé automatiquement par le protocole vanilla. */
    public LevelerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new SimpleContainer(2));
    }

    /** Côté serveur : le BlockEntity EST le conteneur (2 slots pelle/pioche persistants). */
    public LevelerMenu(int id, Inventory inv, LevelerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be);
    }

    private LevelerMenu(int id, Inventory inv, BlockPos pos, Container container) {
        super(ModMenus.LEVELER.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        addSlot(new Slot(container, SHOVEL_SLOT, 221, 110) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ItemTags.SHOVELS);
            }
        });
        addSlot(new Slot(container, PICKAXE_SLOT, 245, 110) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ItemTags.PICKAXES);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 74 + col * 18, 152 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 74 + col * 18, 210));
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

        if (index == SHOVEL_SLOT || index == PICKAXE_SLOT) {
            if (!moveItemStackTo(stackInSlot, INV_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (stackInSlot.is(ItemTags.SHOVELS)) {
            if (!moveItemStackTo(stackInSlot, SHOVEL_SLOT, SHOVEL_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (stackInSlot.is(ItemTags.PICKAXES)) {
            if (!moveItemStackTo(stackInSlot, PICKAXE_SLOT, PICKAXE_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (index < INV_END) {
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
        return stillValid(access, player, ModBlocks.LEVELER.get());
    }
}
