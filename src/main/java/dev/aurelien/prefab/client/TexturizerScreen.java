package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.TexturizerBlockEntity;
import dev.aurelien.prefab.menu.TexturizerMenu;
import dev.aurelien.prefab.network.SetTexturizerCoarseDirtPayload;
import dev.aurelien.prefab.network.SetTexturizerRadiusPayload;
import dev.aurelien.prefab.network.TexturizerActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

public class TexturizerScreen extends AbstractContainerScreen<TexturizerMenu> {
    private static final int Y_HEADER = 6;
    private static final int Y_RADIUS = 20;
    private static final int Y_COARSE = 46;
    private static final int Y_INFO = 72;
    private static final int Y_TOOLS_LABEL = 96;
    private static final int Y_ACTION_ROW = 106;
    private static final int Y_STATUS = 132;
    private static final int LINE_H = 10; // hauteur de ligne pour le texte multi-lignes (info/statut)

    private static final int LABEL_X = 12;
    private static final int MINUS_X = 72;
    private static final int VALUE_X = 98;
    private static final int PLUS_X = 120;
    /** Position X du slot pioche / libellé "Outils", cohérente avec {@link TexturizerMenu}. */
    static final int PICKAXE_X = 180;

    private int radius;
    private boolean coarseDirt;

    private Button toggleButton;
    private Button coarseDirtButton;

    public TexturizerScreen(TexturizerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 210;
        this.imageHeight = 244;
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

        coarseDirtButton = addRenderableWidget(Button.builder(coarseDirtLabel(), b -> {
            coarseDirt = !coarseDirt;
            PacketDistributor.sendToServer(new SetTexturizerCoarseDirtPayload(menu.pos(), coarseDirt));
            coarseDirtButton.setMessage(coarseDirtLabel());
        }).bounds(leftPos + LABEL_X, topPos + Y_COARSE, 156, 20).build());

        toggleButton = addRenderableWidget(Button.builder(toggleLabel(), b -> {
            TexturizerBlockEntity current = be();
            boolean next = current == null || !current.active();
            PacketDistributor.sendToServer(new TexturizerActionPayload(menu.pos(), next));
        }).bounds(leftPos + LABEL_X, topPos + Y_ACTION_ROW, 130, 20).build());
    }

    private void sendRadius() {
        PacketDistributor.sendToServer(new SetTexturizerRadiusPayload(menu.pos(), radius));
    }

    private Component toggleLabel() {
        TexturizerBlockEntity be = be();
        boolean active = be != null && be.active();
        return Component.translatable(active ? "gui.turnkey_factory.texturizer.stop" : "gui.turnkey_factory.texturizer.start");
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

        g.drawString(font, Component.translatable("gui.turnkey_factory.texturizer.zone"), lx, topPos + Y_HEADER, 0xC0C0FF, false);
        int textY = topPos + Y_RADIUS + 6;
        g.drawString(font, Component.translatable("gui.turnkey_factory.texturizer.radius"), lx, textY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(radius), vx, textY, 0xFFE070, false);

        int maxTextWidth = imageWidth - LABEL_X - 8;

        TexturizerBlockEntity be = be();
        if (be != null) {
            // Toujours affiché (même à 0 cellule restante) : sans ça, le compte de cobblestone disparaissait
            // dès que le texturiseur avait fini, alors que c'est justement là qu'on veut le consulter.
            Component info = Component.translatable("gui.turnkey_factory.texturizer.info", be.totalCells(), be.available());
            drawWrapped(g, info, lx, topPos + Y_INFO, maxTextWidth, be.available() > 0 ? 0x80FF80 : 0xFFC040);
        }

        Component toolsLabel = Component.translatable("gui.turnkey_factory.texturizer.tools");
        g.drawString(font, toolsLabel, leftPos + PICKAXE_X - font.width(toolsLabel) / 2 + 9, topPos + Y_TOOLS_LABEL, 0xC0C0FF, false);

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
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.missing_material");
                    statusColor = 0xFFC040;
                }
                case TexturizerBlockEntity.STATUS_DONE -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.done");
                    statusColor = 0x80FF80;
                }
                case TexturizerBlockEntity.STATUS_NO_PICKAXE -> {
                    status = Component.translatable("gui.turnkey_factory.texturizer.status.no_pickaxe");
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
        drawWrapped(g, status, lx, topPos + Y_STATUS, maxTextWidth, statusColor);

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

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // fenêtre épurée : pas de titre vanilla ni libellé d'inventaire
    }
}
