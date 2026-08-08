package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.LamplighterBlockEntity;
import dev.aurelien.prefab.menu.LamplighterMenu;
import dev.aurelien.prefab.network.LamplighterActionPayload;
import dev.aurelien.prefab.network.SetLamplighterRangePayload;
import dev.aurelien.prefab.network.SetLamplighterSpacingPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

public class LamplighterScreen extends AbstractContainerScreen<LamplighterMenu> {
    // Disposition sur DEUX colonnes (plus large que haut → tient à l'écran même en GUI scale auto,
    // cf. ControllerScreen ; l'ancienne version tout-empilé-verticalement à 286 de haut débordait
    // de l'écran dès que la fenêtre était plus petite que le plancher garanti de 240 par Minecraft).
    private static final int Y_RANGE = 8;
    private static final int Y_SPACING = 32;
    private static final int Y_MATERIALS_HEADER = 56;
    private static final int Y_ACTION_ROW = 70;
    /** Départ de la checklist (montre toutes les conditions à la fois, cf. TurretScreen) ; le texte de
     *  statut en prose est dessiné juste en dessous, à la position renvoyée par {@link #drawChecklist}. */
    private static final int Y_STATUS = 94;
    private static final int LINE_H = 10;
    private static final int CHECKLIST_GAP = 6;
    private static final int COLOR_OK = 0x4FA83D;
    private static final int COLOR_MISSING = 0xC24B4B;

    private static final int LABEL_X = 12;
    private static final int MINUS_X = 72;
    private static final int VALUE_X = 98;
    private static final int PLUS_X = 120;
    private static final int MAX_X = 146;

    // Colonne matériaux (à droite du bouton Démarrer / statut).
    private static final int MATERIALS_X = 150;
    private static final int Y_MATERIALS_ROW1 = 70;
    private static final int MATERIAL_ROW_H = 16;

    private int range;
    private int spacing;

    private Button toggleButton;

    public LamplighterScreen(LamplighterMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 280;
        this.imageHeight = 230;
    }

    private LamplighterBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof LamplighterBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        LamplighterBlockEntity be = be();
        if (be != null) {
            range = be.range();
            spacing = be.spacing();
        } else {
            range = LamplighterBlockEntity.DEFAULT_RANGE;
            spacing = LamplighterBlockEntity.DEFAULT_SPACING;
        }

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            range = Mth.clamp(range - 1, LamplighterBlockEntity.MIN_RANGE, LamplighterBlockEntity.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + MINUS_X, topPos + Y_RANGE, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            range = Mth.clamp(range + 1, LamplighterBlockEntity.MIN_RANGE, LamplighterBlockEntity.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + PLUS_X, topPos + Y_RANGE, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.lamplighter.max"), b -> {
            range = LamplighterBlockEntity.MAX_RANGE;
            sendRange();
        }).bounds(leftPos + MAX_X, topPos + Y_RANGE, 32, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            spacing = Mth.clamp(spacing - 1, LamplighterBlockEntity.MIN_SPACING, LamplighterBlockEntity.MAX_SPACING);
            sendSpacing();
        }).bounds(leftPos + MINUS_X, topPos + Y_SPACING, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            spacing = Mth.clamp(spacing + 1, LamplighterBlockEntity.MIN_SPACING, LamplighterBlockEntity.MAX_SPACING);
            sendSpacing();
        }).bounds(leftPos + PLUS_X, topPos + Y_SPACING, 20, 20).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            LamplighterBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new LamplighterActionPayload(menu.pos(), next));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, 130, 20).build());
    }

    private void sendRange() {
        PacketDistributor.sendToServer(new SetLamplighterRangePayload(menu.pos(), range));
    }

    private void sendSpacing() {
        PacketDistributor.sendToServer(new SetLamplighterSpacingPayload(menu.pos(), spacing));
    }

    private Component toggleLabel() {
        LamplighterBlockEntity be = be();
        boolean active = be != null && be.active();
        return Component.translatable(active ? "gui.turnkey_factory.lamplighter.stop" : "gui.turnkey_factory.lamplighter.start");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xD0101010);
        for (Slot slot : menu.slots) {
            slotBg(g, leftPos + slot.x - 1, topPos + slot.y - 1);
        }
    }

    private void slotBg(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    private ItemStack hoveredIcon = ItemStack.EMPTY;
    private Component hoveredText = null;

    /** Icône réelle du bloc + quantité (stock/manquant), même format que la liste de matériaux du contrôleur. */
    private void materialRow(GuiGraphics g, int mouseX, int mouseY, int rowY, ItemStack icon, int required, int available, Component tooltipOverride) {
        int iconX = leftPos + MATERIALS_X;
        g.renderItem(icon, iconX, rowY);
        if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY && mouseY < rowY + 16) {
            if (tooltipOverride != null) hoveredText = tooltipOverride;
            else hoveredIcon = icon;
        }
        int missing = Math.max(0, required - available);
        Component qty = missing > 0
                ? Component.literal("×" + required + " ").append(Component.translatable("gui.turnkey_factory.book.missing", missing))
                : Component.literal("×" + required);
        g.drawString(font, qty, iconX + 20, rowY + 4, missing > 0 ? 0xFFC040 : 0x80FF80, false);
    }

    private static Item resolveLogItem(String registryId) {
        if (registryId.isEmpty()) return Items.OAK_LOG;
        ResourceLocation loc = ResourceLocation.tryParse(registryId);
        if (loc == null) return Items.OAK_LOG;
        return BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.OAK_LOG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hoveredIcon = ItemStack.EMPTY;
        hoveredText = null;
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;
        int vx = leftPos + VALUE_X;

        int rangeTextY = topPos + Y_RANGE + 6;
        g.drawString(font, Component.translatable("gui.turnkey_factory.lamplighter.range"), lx, rangeTextY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(range), vx, rangeTextY, 0xFFE070, false);

        int spacingTextY = topPos + Y_SPACING + 6;
        g.drawString(font, Component.translatable("gui.turnkey_factory.lamplighter.spacing"), lx, spacingTextY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(spacing), vx, spacingTextY, 0xFFE070, false);

        // Colonne gauche uniquement (bouton Démarrer / statut) : ne pas déborder sous la colonne matériaux.
        int maxTextWidth = MATERIALS_X - LABEL_X - 8;

        LamplighterBlockEntity be = be();
        if (be != null) {
            int required = be.totalLamps();
            g.drawString(font, Component.translatable("gui.turnkey_factory.lamplighter.materials", required), lx, topPos + Y_MATERIALS_HEADER, 0xC0C0FF, false);

            materialRow(g, mouseX, mouseY, topPos + Y_MATERIALS_ROW1, new ItemStack(Items.TORCH), required, be.availTorch(), null);
            materialRow(g, mouseX, mouseY, topPos + Y_MATERIALS_ROW1 + MATERIAL_ROW_H, new ItemStack(Items.IRON_INGOT), required, be.availIron(), null);

            boolean anySpecies = be.speciesLogId().isEmpty();
            Item logItem = resolveLogItem(be.speciesLogId());
            Component logTooltip = anySpecies ? Component.translatable("gui.turnkey_factory.lamplighter.any_log") : null;
            materialRow(g, mouseX, mouseY, topPos + Y_MATERIALS_ROW1 + 2 * MATERIAL_ROW_H, new ItemStack(logItem), required, be.availLog(), logTooltip);
        }

        int statusY = topPos + Y_STATUS;
        if (be != null) {
            statusY = drawChecklist(g, lx, topPos + Y_STATUS, maxTextWidth,
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.lamplighter.checklist.link"), be.hasLink()),
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.lamplighter.checklist.species"), be.hasSpecies()),
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.lamplighter.checklist.material"), be.hasMaterial()));
        }

        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = 0xB0B0B0;
        } else {
            switch (be.status()) {
                case LamplighterBlockEntity.STATUS_WORKING -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.working", be.queueSize());
                    statusColor = 0x80C0FF;
                }
                case LamplighterBlockEntity.STATUS_MISSING_MATERIAL -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.missing_material");
                    statusColor = 0xFFC040;
                }
                case LamplighterBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.done");
                    statusColor = 0x80FF80;
                }
                case LamplighterBlockEntity.STATUS_NO_SPECIES -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.no_species");
                    statusColor = 0xFF6060;
                }
                case LamplighterBlockEntity.STATUS_NO_LINK -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.no_link");
                    statusColor = 0xFF6060;
                }
                default -> {
                    status = Component.translatable("gui.turnkey_factory.lamplighter.status.inactive");
                    statusColor = 0xB0B0B0;
                }
            }
        }
        drawWrapped(g, status, lx, statusY, maxTextWidth, statusColor);

        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }

        if (hoveredText != null) {
            g.renderTooltip(font, hoveredText, mouseX, mouseY);
        } else if (!hoveredIcon.isEmpty()) {
            g.renderTooltip(font, hoveredIcon, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, lineY, color, false);
            lineY += LINE_H;
        }
        return lineY;
    }

    private record ChecklistItem(Component label, boolean ok) {}

    /**
     * Enchaîne les items horizontalement (façon TurretScreen#drawChecklist : toutes les conditions
     * visibles à la fois plutôt qu'un seul statut "gagnant"), passe à la ligne si la largeur disponible
     * est dépassée (colonne étroite ici, cf. maxTextWidth = MATERIALS_X - LABEL_X - 8). Renvoie le Y
     * juste sous la dernière ligne, pour enchaîner le texte de statut en prose sans chevaucher la
     * checklist.
     */
    private int drawChecklist(GuiGraphics g, int x, int y, int maxWidth, ChecklistItem... items) {
        int cx = x, cy = y;
        for (ChecklistItem item : items) {
            int w = font.width(item.label());
            if (cx != x && cx + w > x + maxWidth) {
                cx = x;
                cy += LINE_H;
            }
            g.drawString(font, item.label(), cx, cy, item.ok() ? COLOR_OK : COLOR_MISSING, false);
            cx += w + CHECKLIST_GAP;
        }
        return cy + LINE_H;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // fenêtre épurée : pas de titre vanilla ni libellé d'inventaire
    }
}
