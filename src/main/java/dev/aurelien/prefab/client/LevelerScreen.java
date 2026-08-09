package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.LevelerBlockEntity;
import dev.aurelien.prefab.menu.LevelerMenu;
import dev.aurelien.prefab.network.LevelerActionPayload;
import dev.aurelien.prefab.network.SetCenterPayload;
import dev.aurelien.prefab.network.SetLevelerRangePayload;
import dev.aurelien.prefab.network.SetLevelerTargetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class LevelerScreen extends AbstractContainerScreen<LevelerMenu> {
    // Disposition sur DEUX colonnes (plus large que haut → tient à l'écran même en GUI scale auto,
    // cf. ControllerScreen ; l'ancienne version tout-empilé-verticalement à 278 de haut débordait
    // de l'écran dès que la fenêtre était plus petite que le plancher garanti de 240 par Minecraft).
    private static final int Y_RANGE = 8;
    private static final int Y_TARGET = 30;
    private static final int Y_FILL_DEPTH = 52;
    private static final int Y_FILL_INFO = 74;
    private static final int Y_TOOLS_LABEL = 40;
    private static final int Y_ACTION_ROW = 98;
    /** Départ de la checklist (montre toutes les conditions à la fois, cf. TurretScreen) ; le texte de
     *  statut en prose est dessiné juste en dessous, à la position renvoyée par {@link #drawChecklist}. */
    private static final int Y_STATUS = 122;
    private static final int LINE_H = 10;
    private static final int CHECKLIST_GAP = 6;
    private static final int COLOR_OK = 0x4FA83D;
    private static final int COLOR_MISSING = 0xC24B4B;

    private static final int LABEL_X = 12;
    private static final int MINUS_X = 72;
    private static final int VALUE_X = 98;
    private static final int PLUS_X = 120;
    private static final int MAX_X = 146;
    /** Position X des slots pelle/pioche, cohérente avec {@link LevelerMenu}. */
    private static final int TOOLS_X = 180;

    private int range, targetY, fillDepth;

    private Button toggleButton;

    public LevelerScreen(LevelerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 230;
        this.imageHeight = 234;
    }

    private LevelerBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof LevelerBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        LevelerBlockEntity be = be();
        if (be != null) {
            range = be.range();
            targetY = be.targetOffsetY();
            fillDepth = be.fillDepth();
        } else {
            range = LevelerBlockEntity.DEFAULT_RANGE;
            targetY = 0;
            fillDepth = LevelerBlockEntity.DEFAULT_FILL_DEPTH;
        }

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            range = Mth.clamp(range - 1, LevelerBlockEntity.MIN_RANGE, LevelerBlockEntity.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + MINUS_X, topPos + Y_RANGE, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            range = Mth.clamp(range + 1, LevelerBlockEntity.MIN_RANGE, LevelerBlockEntity.MAX_RANGE);
            sendRange();
        }).bounds(leftPos + PLUS_X, topPos + Y_RANGE, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.leveler.max"), b -> {
            range = LevelerBlockEntity.MAX_RANGE;
            sendRange();
        }).bounds(leftPos + MAX_X, topPos + Y_RANGE, 32, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            targetY = Mth.clamp(targetY - 1, -LevelerBlockEntity.TARGET_MAX, LevelerBlockEntity.TARGET_MAX);
            sendTarget();
        }).bounds(leftPos + MINUS_X, topPos + Y_TARGET, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            targetY = Mth.clamp(targetY + 1, -LevelerBlockEntity.TARGET_MAX, LevelerBlockEntity.TARGET_MAX);
            sendTarget();
        }).bounds(leftPos + PLUS_X, topPos + Y_TARGET, 20, 20).build());

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            fillDepth = Mth.clamp(fillDepth - 1, LevelerBlockEntity.MIN_FILL_DEPTH, LevelerBlockEntity.MAX_FILL_DEPTH);
            sendTarget();
        }).bounds(leftPos + MINUS_X, topPos + Y_FILL_DEPTH, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            fillDepth = Mth.clamp(fillDepth + 1, LevelerBlockEntity.MIN_FILL_DEPTH, LevelerBlockEntity.MAX_FILL_DEPTH);
            sendTarget();
        }).bounds(leftPos + PLUS_X, topPos + Y_FILL_DEPTH, 20, 20).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            LevelerBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new LevelerActionPayload(menu.pos(), next));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, 130, 20).build());

        // À droite du bouton Démarrer/Arrêter, sur la même rangée : la colonne pelle/pioche (TOOLS_X)
        // n'y descend qu'à partir de Y=52/74, bien au-dessus — pas de conflit à Y_ACTION_ROW.
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.machine.set_center"), b -> {
            PacketDistributor.sendToServer(new SetCenterPayload(menu.pos()));
        }).bounds(leftPos + LABEL_X + 136, topPos + Y_ACTION_ROW, 70, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.machine.set_center.tooltip")))
                .build());
    }

    private void sendRange() {
        PacketDistributor.sendToServer(new SetLevelerRangePayload(menu.pos(), range));
    }

    private void sendTarget() {
        PacketDistributor.sendToServer(new SetLevelerTargetPayload(menu.pos(), targetY, fillDepth));
    }

    private Component toggleLabel() {
        LevelerBlockEntity be = be();
        boolean active = be != null && be.active();
        return Component.translatable(active ? "gui.turnkey_factory.leveler.stop" : "gui.turnkey_factory.leveler.start");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xD0101010);
        for (Slot slot : menu.slots) {
            slotBg(g, leftPos + slot.x - 1, topPos + slot.y - 1, slot.index < 2);
        }
    }

    /** {@code highlight} distingue les slots pelle/pioche (bordure violette) des slots d'inventaire (gris). */
    private void slotBg(GuiGraphics g, int x, int y, boolean highlight) {
        g.fill(x, y, x + 18, y + 18, highlight ? 0xFFB080FF : 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;
        int vx = leftPos + VALUE_X;

        label(g, lx, vx, Y_RANGE, Component.translatable("gui.turnkey_factory.leveler.range"), range);
        label(g, lx, vx, Y_TARGET, Component.translatable("gui.turnkey_factory.leveler.target_height"), targetY);
        label(g, lx, vx, Y_FILL_DEPTH, Component.translatable("gui.turnkey_factory.leveler.fill_depth"), fillDepth);

        Component toolsLabel = Component.translatable("gui.turnkey_factory.leveler.tools");
        g.drawString(font, toolsLabel, leftPos + TOOLS_X - font.width(toolsLabel) / 2 + 9, topPos + Y_TOOLS_LABEL, 0xC0C0FF, false);

        LevelerBlockEntity be = be();

        if (be != null && be.fillNeeded() > 0) {
            int missing = Math.max(0, be.fillNeeded() - be.fillSupplied());
            Component fillInfo = missing > 0
                    ? Component.translatable("gui.turnkey_factory.leveler.fill_info.missing", be.fillNeeded(), be.fillSupplied(), missing)
                    : Component.translatable("gui.turnkey_factory.leveler.fill_info.covered", be.fillNeeded());
            // Largeur limitée à la colonne gauche : cette ligne partage sa bande verticale avec les
            // slots pelle/pioche à droite (TOOLS_X), il ne faut pas dessiner par-dessus.
            drawWrapped(g, fillInfo, lx, topPos + Y_FILL_INFO, TOOLS_X - LABEL_X - 8, missing > 0 ? 0xFFC040 : 0x80FF80);
        }
        int maxTextWidth = imageWidth - LABEL_X - 8;
        int statusY = topPos + Y_STATUS;
        if (be != null) {
            statusY = drawChecklist(g, lx, topPos + Y_STATUS, maxTextWidth,
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.leveler.checklist.link"), be.hasLink()),
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.leveler.checklist.shovel"), be.hasShovel()),
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.leveler.checklist.pickaxe"), be.hasPickaxe()),
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.leveler.checklist.fill"), be.hasFill()));
        }

        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = 0xB0B0B0;
        } else {
            switch (be.status()) {
                case LevelerBlockEntity.STATUS_WORKING -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.working", be.queueSize());
                    statusColor = 0x80C0FF;
                }
                case LevelerBlockEntity.STATUS_MISSING_FILL -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.missing_fill");
                    statusColor = 0xFFC040;
                }
                case LevelerBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.done");
                    statusColor = 0x80FF80;
                }
                case LevelerBlockEntity.STATUS_NO_SHOVEL -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_shovel");
                    statusColor = 0xFF6060;
                }
                case LevelerBlockEntity.STATUS_NO_PICKAXE -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_pickaxe");
                    statusColor = 0xFF6060;
                }
                case LevelerBlockEntity.STATUS_NO_LINK -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.no_link");
                    statusColor = 0xFF6060;
                }
                default -> {
                    status = Component.translatable("gui.turnkey_factory.leveler.status.inactive");
                    statusColor = 0xB0B0B0;
                }
            }
        }
        drawWrapped(g, status, lx, statusY, maxTextWidth, statusColor);

        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }

        renderTooltip(g, mouseX, mouseY);
    }

    private void label(GuiGraphics g, int labelX, int valueX, int rowY, Component name, int v) {
        int textY = topPos + rowY + 6;
        g.drawString(font, name, labelX, textY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(v), valueX, textY, 0xFFE070, false);
    }

    private void drawWrapped(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        int lineY = y;
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            g.drawString(font, line, x, lineY, color, false);
            lineY += LINE_H;
        }
    }

    private record ChecklistItem(Component label, boolean ok) {}

    /**
     * Enchaîne les items horizontalement (façon TurretScreen#drawChecklist : toutes les conditions
     * visibles à la fois plutôt qu'un seul statut "gagnant"), passe à la ligne si la largeur disponible
     * est dépassée. Renvoie le Y juste sous la dernière ligne, pour enchaîner le texte de statut en
     * prose sans chevaucher la checklist.
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
