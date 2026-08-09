package dev.aurelien.prefab.menu;

import dev.aurelien.prefab.block.ITurret;
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
 * Aucun slot machine : munitions puisées dans les inventaires liés (cf. {@link ITurret}), aucun
 * slot dédié dans ce menu. Ne dépend que d'un {@link BlockPos} — sert aussi bien la tourelle
 * charbon ({@code TurretBaseBlockEntity}) que l'implémentation Create (compat/create), d'où
 * {@link #stillValid} qui vérifie la présence d'un {@link ITurret} plutôt qu'un bloc précis.
 */
public class TurretMenu extends AbstractContainerMenu {
    private static final int INV_START = 0;
    private static final int INV_END = INV_START + 27;   // 3x9 inventaire principal
    private static final int HOTBAR_END = INV_END + 9;    // + hotbar

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    public TurretMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos());
    }

    public TurretMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenus.TURRET.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        // Inventaire centré dans le panneau de 300 de large : (300 - 9*18) / 2 = 69. Ces valeurs
        // dupliquent MachineScreen.INV_X/INV_Y/HOTBAR_Y, qui est côté client uniquement — un menu est
        // construit aussi sur le serveur dédié et ne peut pas charger une classe d'écran.
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
        return access.evaluate((level, p) -> level.getBlockEntity(p) instanceof ITurret
                && player.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) <= 64.0, true);
    }
}
