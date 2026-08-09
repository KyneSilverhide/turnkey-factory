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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Disposition sur DEUX colonnes, gabarit commun à {@link MachineScreen} : réglages à gauche, colonne
 * outil à droite, puis info / action / statut sur toute la largeur, sous le bas de la colonne droite
 * (c'est cette règle qui empêche la ligne d'info, qui se replie sur deux lignes, de passer sous le
 * bouton Démarrer comme c'était le cas avant).
 */
public class TexturizerScreen extends MachineScreen<TexturizerMenu> {
    private static final int Y_RADIUS = Y_ROW0;
    /** Motif et parcelles gratuites partagent une seule rangée (deux demi-boutons). */
    private static final int Y_TOGGLES = Y_ROW0 + ROW_STEP;
    private static final int TOGGLE_W = 76;
    private static final int TOGGLE_GAP = 4;
    /**
     * Remonté par rapport au {@link MachineScreen#Y_INFO} partagé : ici la colonne outil s'arrête à 40
     * et la rangée de motifs à 50, donc l'info peut démarrer plus haut. Ça lui laisse quatre lignes
     * avant le bouton Démarrer au lieu de deux — c'est la chaîne la plus longue du mod, et son premier
     * mot (« Cobble/gravier/andésite/pierre ») est insécable, donc le repli est difficile à prévoir.
     */
    private static final int Y_INFO_TOP = Y_ROW0 + 2 * ROW_STEP + 4;

    private int radius;
    private boolean coarseDirt;

    private Button toggleButton;
    private Button paletteButton;
    private Button coarseDirtButton;

    public TexturizerScreen(TexturizerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected int accentColor() {
        return 0x968A7A; // tuile de gravier de la mosaïque
    }

    private TexturizerBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof TexturizerBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected boolean isAccentSlot(Slot slot) {
        return slot.index == 0; // slot outil
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
        }).bounds(leftPos + MINUS_X, topPos + Y_RADIUS, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            radius = Mth.clamp(radius + 1, TexturizerBlockEntity.MIN_RADIUS, TexturizerBlockEntity.MAX_RADIUS);
            sendRadius();
        }).bounds(leftPos + PLUS_X, topPos + Y_RADIUS, SMALL_BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.texturizer.max"), b -> {
            radius = TexturizerBlockEntity.MAX_RADIUS;
            sendRadius();
        }).bounds(leftPos + MAX_X, topPos + Y_RADIUS, MAX_BTN_W, BTN_H).build());

        paletteButton = addRenderableWidget(Button.builder(paletteLabel(), b -> {
            TexturizerBlockEntity.Palette current = currentPalette();
            TexturizerBlockEntity.Palette next = current == TexturizerBlockEntity.Palette.STONE
                    ? TexturizerBlockEntity.Palette.DIRT
                    : TexturizerBlockEntity.Palette.STONE;
            PacketDistributor.sendToServer(new SetTexturizerPalettePayload(menu.pos(), next.ordinal()));
        }).bounds(leftPos + LABEL_X, topPos + Y_TOGGLES, TOGGLE_W, BTN_H)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.texturizer.palette.tooltip")))
                .build());

        // Disponible dans les deux motifs : la terre grossière n'apparaît nulle part ailleurs.
        coarseDirtButton = addRenderableWidget(Button.builder(coarseDirtLabel(), b -> {
            coarseDirt = !coarseDirt;
            PacketDistributor.sendToServer(new SetTexturizerCoarseDirtPayload(menu.pos(), coarseDirt));
            coarseDirtButton.setMessage(coarseDirtLabel());
        }).bounds(leftPos + LABEL_X + TOGGLE_W + TOGGLE_GAP, topPos + Y_TOGGLES, TOGGLE_W, BTN_H)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.texturizer.coarse_dirt.tooltip")))
                .build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            TexturizerBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new TexturizerActionPayload(menu.pos(), next));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, START_BTN_W, BTN_H).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.machine.set_center"), b -> {
            PacketDistributor.sendToServer(new SetCenterPayload(menu.pos()));
        }).bounds(leftPos + CENTER_BTN_X, topPos + Y_ACTION_ROW, CENTER_BTN_W, BTN_H)
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;

        label(g, Y_RADIUS, Component.translatable("gui.turnkey_factory.texturizer.radius"), radius);

        rightHeader(g, Component.translatable(currentPalette() == TexturizerBlockEntity.Palette.STONE
                ? "gui.turnkey_factory.texturizer.tool.pickaxe"
                : "gui.turnkey_factory.texturizer.tool.shovel"), Y_ROW0);

        int maxTextWidth = textWidth();
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
            drawWrapped(g, info, lx, topPos + Y_INFO_TOP, maxTextWidth, be.available() > 0 ? COLOR_GOOD : COLOR_WARN);
        }

        int statusY = topPos + Y_STATUS;
        if (be != null) {
            statusY = drawChecklist(g, lx, topPos + Y_STATUS, maxTextWidth,
                    check("gui.turnkey_factory.texturizer.checklist.link", be.hasLink()),
                    check("gui.turnkey_factory.texturizer.checklist.tool", be.hasTool()),
                    check("gui.turnkey_factory.texturizer.checklist.material", be.hasMaterial()));
        }

        Component status;
        int statusColor;
        if (be == null) {
            status = Component.empty();
            statusColor = COLOR_IDLE;
        } else {
            switch (be.status()) {
                case TexturizerBlockEntity.STATUS_WORKING -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.working", be.queueSize());
                    statusColor = COLOR_WORKING;
                }
                case TexturizerBlockEntity.STATUS_MISSING_MATERIAL -> {
                    String key = be.palette() == TexturizerBlockEntity.Palette.STONE
                            ? "gui.turnkey_factory.texturizer.status.missing_material.stone"
                            : "gui.turnkey_factory.texturizer.status.missing_material.dirt";
                    status = Component.translatable(key);
                    statusColor = COLOR_WARN;
                }
                case TexturizerBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.done");
                    statusColor = COLOR_GOOD;
                }
                case TexturizerBlockEntity.STATUS_NO_TOOL -> {
                    String key = be.palette() == TexturizerBlockEntity.Palette.STONE
                            ? "gui.turnkey_factory.texturizer.status.no_pickaxe"
                            : "gui.turnkey_factory.texturizer.status.no_shovel";
                    status = Component.translatable(key);
                    statusColor = COLOR_ERROR;
                }
                case TexturizerBlockEntity.STATUS_NO_LINK -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.no_link");
                    statusColor = COLOR_ERROR;
                }
                default -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.inactive");
                    statusColor = COLOR_IDLE;
                }
            }
        }
        drawWrapped(g, status, lx, statusY, maxTextWidth, statusColor);

        if (toggleButton != null) {
            toggleButton.setMessage(toggleLabel());
        }

        renderTooltip(g, mouseX, mouseY);
    }
}
