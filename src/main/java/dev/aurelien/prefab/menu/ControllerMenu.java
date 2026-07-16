package dev.aurelien.prefab.menu;

import dev.aurelien.prefab.block.ControllerBlockEntity;
import dev.aurelien.prefab.reg.ModBlocks;
import dev.aurelien.prefab.reg.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class ControllerMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    /** Côté client : on ne transmet que la position ; l'écran lit le BlockEntity client (synchronisé). */
    public ControllerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(ModMenus.CONTROLLER.get(), id);
        this.pos = buf.readBlockPos();
        this.access = ContainerLevelAccess.create(inv.player.level(), this.pos);
    }

    /** Côté serveur. */
    public ControllerMenu(int id, Inventory inv, ControllerBlockEntity be) {
        super(ModMenus.CONTROLLER.get(), id);
        this.pos = be.getBlockPos();
        this.access = ContainerLevelAccess.create(be.getLevel(), this.pos);
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CONTROLLER.get());
    }
}
