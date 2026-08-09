package dev.aurelien.prefab.menu;

import dev.aurelien.prefab.block.TexturizerBlockEntity;
import dev.aurelien.prefab.reg.ModBlocks;
import dev.aurelien.prefab.reg.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class TexturizerMenu extends AbstractContainerMenu {
    private static final int TOOL_SLOT = 0;
    private static final int INV_START = 1;
    private static final int INV_END = INV_START + 27;   // 3x9 inventaire principal
    private static final int HOTBAR_END = INV_END + 9;    // + hotbar

    private final ContainerLevelAccess access;
    private final BlockPos pos;

    /** Côté client : le slot outil est un conteneur factice, synchronisé automatiquement par le protocole vanilla. */
    public TexturizerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, buf.readBlockPos(), new SimpleContainer(1));
    }

    /** Côté serveur : le BlockEntity EST le conteneur (1 slot outil persistant). */
    public TexturizerMenu(int id, Inventory inv, TexturizerBlockEntity be) {
        this(id, inv, be.getBlockPos(), be);
    }

    private TexturizerMenu(int id, Inventory inv, BlockPos pos, Container container) {
        super(ModMenus.TEXTURIZER.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);

        // Colonne outil à droite (TOOL_X, cf. TexturizerScreen), au-dessus de l'inventaire joueur.
        addSlot(new Slot(container, TOOL_SLOT, 240, 22) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(requiredToolTag(inv.player.level()));
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inv, col + row * 9 + 9, 9 + col * 18, 146 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inv, col, 9 + col * 18, 204));
        }
    }

    /** Outil requis par le motif actuellement sélectionné (pioche/pelle) : pioche par défaut si le bloc entité n'est pas (encore) chargé. */
    private TagKey<Item> requiredToolTag(Level level) {
        return level.getBlockEntity(pos) instanceof TexturizerBlockEntity be ? be.palette().toolTag : ItemTags.PICKAXES;
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

        if (index == TOOL_SLOT) {
            if (!moveItemStackTo(stackInSlot, INV_START, HOTBAR_END, true)) return ItemStack.EMPTY;
        } else if (stackInSlot.is(requiredToolTag(player.level()))) {
            if (!moveItemStackTo(stackInSlot, TOOL_SLOT, TOOL_SLOT + 1, false)) return ItemStack.EMPTY;
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
        return stillValid(access, player, ModBlocks.TEXTURIZER.get());
    }
}
