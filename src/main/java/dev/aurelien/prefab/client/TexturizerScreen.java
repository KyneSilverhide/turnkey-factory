package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.TexturizerBlockEntity;
import dev.aurelien.prefab.menu.TexturizerMenu;
import dev.aurelien.prefab.network.SetCenterPayload;
import dev.aurelien.prefab.network.SetTexturizerCoarseDirtPayload;
import dev.aurelien.prefab.network.SetTexturizerPalettePayload;
import dev.aurelien.prefab.network.SetTexturizerRadiusPayload;
import dev.aurelien.prefab.network.TexturizerActionPayload;
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

/**
 * Disposition harmonisée avec Leveler/Lamplighter/Turret (cf. leurs javadocs de classe) : un en-tête de
 * section générique ("Zone de travail"), une colonne outil séparée à droite (au lieu d'empiler tout en
 * une seule colonne étroite), largeur 280 pour rester sous le plancher de 240 de haut garanti par
 * Minecraft en échelle auto (cf. {@link TexturizerMenu} pour les positions de slots correspondantes).
 */
public class TexturizerScreen extends AbstractContainerScreen<TexturizerMenu> {
    private static final int Y_HEADER = 6;
    private static final int Y_RADIUS = 20;
    /** Motif et parcelles gratuites partagent une seule rangée (deux demi-boutons) pour ne pas agrandir la fenêtre. */
    private static final int Y_TOGGLES = 46;
    private static final int Y_INFO = 70;
    private static final int Y_ACTION_ROW = 84;
    /** Départ de la checklist (montre toutes les conditions à la fois, cf. TurretScreen) ; le texte de
     *  statut en prose est dessiné juste en dessous, à la position renvoyée par {@link #drawChecklist}. */
    private static final int Y_STATUS = 108;
    private static final int LINE_H = 10; // hauteur de ligne pour le texte multi-lignes (info/statut)
    private static final int CHECKLIST_GAP = 6;
    private static final int COLOR_OK = 0x4FA83D;
    private static final int COLOR_MISSING = 0xC24B4B;

    private static final int LABEL_X = 12;
    private static final int MINUS_X = 72;
    private static final int VALUE_X = 98;
    private static final int PLUS_X = 120;
    private static final int MAX_X = 146;
    private static final int TOGGLE_W = 76; // largeur d'un demi-bouton sur la rangée Y_TOGGLES
    private static final int TOGGLE_GAP = 4;
    private static final int TOGGLE_BTN_W = 130;
    private static final int CENTER_BTN_W = 70;
    private static final int CENTER_BTN_GAP = 8;
    /** Position X du slot outil / libellé de son nom, cohérente avec {@link TexturizerMenu}. */
    static final int TOOL_X = 240;

    private int radius;
    private boolean coarseDirt;

    private Button toggleButton;
    private Button paletteButton;
    private Button coarseDirtButton;

    public TexturizerScreen(TexturizerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 280;
        this.imageHeight = 232;
    }

    private TexturizerBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof TexturizerBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();

        TexturizerBlockEntity be = be();
        if (be != null) {
            radius = be.radius();
            coarseDirt = be.coarseDirtPatches();
        } else {
            radius = TexturizerBlockEntity.DEFAULT_RADIUS;
            coarseDirt = false;
        }

        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            radius = Mth.clamp(radius - 1, TexturizerBlockEntity.MIN_RADIUS, TexturizerBlockEntity.MAX_RADIUS);
            sendRadius();
        }).bounds(leftPos + MINUS_X, topPos + Y_RADIUS, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            radius = Mth.clamp(radius + 1, TexturizerBlockEntity.MIN_RADIUS, TexturizerBlockEntity.MAX_RADIUS);
            sendRadius();
        }).bounds(leftPos + PLUS_X, topPos + Y_RADIUS, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.texturizer.max"), b -> {
            radius = TexturizerBlockEntity.MAX_RADIUS;
            sendRadius();
        }).bounds(leftPos + MAX_X, topPos + Y_RADIUS, 32, 20).build());

        paletteButton = addRenderableWidget(Button.builder(paletteLabel(), b -> {
            TexturizerBlockEntity.Palette current = currentPalette();
            TexturizerBlockEntity.Palette next = current == TexturizerBlockEntity.Palette.STONE
                    ? TexturizerBlockEntity.Palette.DIRT
                    : TexturizerBlockEntity.Palette.STONE;
            PacketDistributor.sendToServer(new SetTexturizerPalettePayload(menu.pos(), next.ordinal()));
        }).bounds(leftPos + LABEL_X, topPos + Y_TOGGLES, TOGGLE_W, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.texturizer.palette.tooltip")))
                .build());

        // Disponible dans les deux motifs : la terre grossière n'apparaît nulle part ailleurs.
        coarseDirtButton = addRenderableWidget(Button.builder(coarseDirtLabel(), b -> {
            coarseDirt = !coarseDirt;
            PacketDistributor.sendToServer(new SetTexturizerCoarseDirtPayload(menu.pos(), coarseDirt));
            coarseDirtButton.setMessage(coarseDirtLabel());
        }).bounds(leftPos + LABEL_X + TOGGLE_W + TOGGLE_GAP, topPos + Y_TOGGLES, TOGGLE_W, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.texturizer.coarse_dirt.tooltip")))
                .build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            TexturizerBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new TexturizerActionPayload(menu.pos(), next));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, TOGGLE_BTN_W, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.machine.set_center"), b -> {
            PacketDistributor.sendToServer(new SetCenterPayload(menu.pos()));
        }).bounds(leftPos + LABEL_X + TOGGLE_BTN_W + CENTER_BTN_GAP, topPos + Y_ACTION_ROW, CENTER_BTN_W, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.machine.set_center.tooltip")))
                .build());
    }

    private void sendRadius() {
        PacketDistributor.sendToServer(new SetTexturizerRadiusPayload(menu.pos(), radius));
    }

    private Component toggleLabel() {
        TexturizerBlockEntity be = be();
        boolean active = be != null && be.active();
        return Component.translatable(active ? "gui.turnkey_factory.texturizer.stop" : "gui.turnkey_factory.texturizer.start");
    }

    /**
     * Toujours lu depuis le bloc entité live (jamais mis en cache localement) : {@code mayPlace} du slot
     * outil ({@link TexturizerMenu}) fait de même, donc le bouton ne peut jamais afficher un motif
     * différent de celui qui gouverne réellement quel outil est accepté.
     */
    private TexturizerBlockEntity.Palette currentPalette() {
        TexturizerBlockEntity be = be();
        return be != null ? be.palette() : TexturizerBlockEntity.Palette.STONE;
    }

    private Component paletteLabel() {
        return Component.translatable(currentPalette() == TexturizerBlockEntity.Palette.STONE
                ? "gui.turnkey_factory.texturizer.palette.stone"
                : "gui.turnkey_factory.texturizer.palette.dirt");
    }

    private Component coarseDirtLabel() {
        return Component.translatable(coarseDirt
                ? "gui.turnkey_factory.texturizer.coarse_dirt.on"
                : "gui.turnkey_factory.texturizer.coarse_dirt.off");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xD0101010);
        for (Slot slot : menu.slots) {
            slotBg(g, leftPos + slot.x - 1, topPos + slot.y - 1, slot.index == 0);
        }
    }

    /** {@code highlight} distingue le slot pioche (bordure violette) des slots d'inventaire (gris). */
    private void slotBg(GuiGraphics g, int x, int y, boolean highlight) {
        g.fill(x, y, x + 18, y + 18, highlight ? 0xFFB080FF : 0xFF8B8B8B);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF373737);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;
        int vx = leftPos + VALUE_X;

        g.drawString(font, Component.translatable("gui.turnkey_factory.machine.work_area"), lx, topPos + Y_HEADER, 0xC0C0FF, false);
        int textY = topPos + Y_RADIUS + 6;
        g.drawString(font, Component.translatable("gui.turnkey_factory.texturizer.radius"), lx, textY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(radius), vx, textY, 0xFFE070, false);

        // Pleine largeur : le slot outil (TOOL_X) ne descend que jusqu'à Y=40 (cf. TexturizerMenu), donc
        // rien n'empiète sur l'info/la checklist/le statut qui commencent bien plus bas.
        int maxTextWidth = imageWidth - LABEL_X - 8;

        TexturizerBlockEntity be = be();
        if (be != null) {
            if (paletteButton != null) {
                paletteButton.setMessage(paletteLabel());
            }
            // Toujours affiché (même à 0 cellule restante) : sans ça, le compte de matériau disparaissait
            // dès que le texturiseur avait fini, alors que c'est justement là qu'on veut le consulter.
            String infoKey = be.palette() == TexturizerBlockEntity.Palette.STONE
                    ? "gui.turnkey_factory.texturizer.info.stone"
                    : "gui.turnkey_factory.texturizer.info.dirt";
            Component info = Component.translatable(infoKey, be.totalCells(), be.available());
            drawWrapped(g, info, lx, topPos + Y_INFO, maxTextWidth, be.available() > 0 ? 0x80FF80 : 0xFFC040);
        }

        Component toolsLabel = Component.translatable(currentPalette() == TexturizerBlockEntity.Palette.STONE
                ? "gui.turnkey_factory.texturizer.tool.pickaxe"
                : "gui.turnkey_factory.texturizer.tool.shovel");
        g.drawString(font, toolsLabel, leftPos + TOOL_X - font.width(toolsLabel) / 2 + 9, topPos + Y_HEADER, 0xC0C0FF, false);

        int statusY = topPos + Y_STATUS;
        if (be != null) {
            statusY = drawChecklist(g, lx, topPos + Y_STATUS, maxTextWidth,
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.texturizer.checklist.link"), be.hasLink()),
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.texturizer.checklist.tool"), be.hasTool()),
                    new ChecklistItem(Component.translatable("gui.turnkey_factory.texturizer.checklist.material"), be.hasMaterial()));
        }

        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = 0xB0B0B0;
        } else {
            switch (be.status()) {
                case TexturizerBlockEntity.STATUS_WORKING -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.working", be.queueSize());
                    statusColor = 0x80C0FF;
                }
                case TexturizerBlockEntity.STATUS_MISSING_MATERIAL -> {
                    String key = be.palette() == TexturizerBlockEntity.Palette.STONE
                            ? "gui.turnkey_factory.texturizer.status.missing_material.stone"
                            : "gui.turnkey_factory.texturizer.status.missing_material.dirt";
                    status = Component.translatable(key);
                    statusColor = 0xFFC040;
                }
                case TexturizerBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.done");
                    statusColor = 0x80FF80;
                }
                case TexturizerBlockEntity.STATUS_NO_TOOL -> {
                    String key = be.palette() == TexturizerBlockEntity.Palette.STONE
                            ? "gui.turnkey_factory.texturizer.status.no_pickaxe"
                            : "gui.turnkey_factory.texturizer.status.no_shovel";
                    status = Component.translatable(key);
                    statusColor = 0xFF6060;
                }
                case TexturizerBlockEntity.STATUS_NO_LINK -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.no_link");
                    statusColor = 0xFF6060;
                }
                default -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.inactive");
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

    /** Découpe {@code text} sur plusieurs lignes plutôt que de le laisser déborder du panneau. */
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
