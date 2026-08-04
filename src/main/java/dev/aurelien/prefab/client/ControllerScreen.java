package dev.aurelien.prefab.client;

import dev.aurelien.prefab.block.ControllerBlockEntity;
import dev.aurelien.prefab.build.RoofType;
import dev.aurelien.prefab.build.Theme;
import dev.aurelien.prefab.menu.ControllerMenu;
import dev.aurelien.prefab.network.BuildActionPayload;
import dev.aurelien.prefab.network.SetDimsPayload;
import dev.aurelien.prefab.network.SetOffsetPayload;
import dev.aurelien.prefab.network.SetStylePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    // Disposition sur DEUX colonnes (plus large que haut → tient à l'écran même en GUI scale auto).
    private static final int COL2_DX = 168; // décalage horizontal de la 2e colonne (Décalage du fantôme)

    // Disposition verticale (relative à topPos)
    private static final int Y_HEADER = 8;
    private static final int Y_ROW1 = 22, Y_ROW2 = 46, Y_ROW3 = 70;
    private static final int Y_STYLE = 96;   // rangée style : Thème (gauche) + Toit (droite)
    private static final int Y_INFO = 122;
    private static final int Y_BUTTONS = 138;
    private static final int Y_STATUS = 162;

    // Colonnes des contrôles (relatives à leftPos, dans une colonne de panneau)
    private static final int LABEL_X = 12;
    private static final int MINUS_X = 96;
    private static final int VALUE_X = 122;
    private static final int PLUS_X = 144;

    // Panneau ressources (3e colonne, à droite) : icône + quantité par matériau requis.
    private static final int PANEL3_X = 348;
    private static final int Y_MATERIALS_ROW1 = 24;
    private static final int MATERIAL_ROW_H = 16;

    private int w, l, h;
    private int ox, oy, oz;
    private Theme theme = Theme.STONE;
    private RoofType roof = RoofType.FLAT;

    private Button buildButton;
    private Button cancelButton;
    private Button forceButton;
    private Button ignoreButton;
    private Button themeButton;
    private Button roofButton;
    private final List<Button> configButtons = new ArrayList<>();

    public ControllerScreen(ControllerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 500;
        this.imageHeight = 184;
    }

    private ControllerBlockEntity be() {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof ControllerBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    protected void init() {
        super.init();
        configButtons.clear();

        ControllerBlockEntity be = be();
        if (be != null) {
            w = be.width();  l = be.length(); h = be.height();
            ox = be.offsetX(); oy = be.offsetY(); oz = be.offsetZ();
            theme = be.theme(); roof = be.roofType();
        } else {
            w = l = 7; h = 7; ox = oy = oz = 0;
            theme = Theme.STONE; roof = RoofType.FLAT;
        }

        // Colonne gauche : dimensions (largeur/longueur impaires [7..63], hauteur pas de 1 [7..64])
        horizontalRow(0, Y_ROW1, () -> w, v -> w = v);
        horizontalRow(0, Y_ROW2, () -> l, v -> l = v);
        heightRow(0, Y_ROW3, () -> h, v -> h = v);
        // Colonne droite : décalage du fantôme (-15..+15)
        offRow(COL2_DX, Y_ROW1, () -> ox, v -> ox = v);
        offRow(COL2_DX, Y_ROW2, () -> oy, v -> oy = v);
        offRow(COL2_DX, Y_ROW3, () -> oz, v -> oz = v);

        // Rangée style : Thème (gauche) + Toit (droite), boutons cycliques.
        themeButton = addRenderableWidget(Button.builder(themeLabel(), b -> {
            theme = theme.nextAvailable();
            sendStyle();
        }).bounds(leftPos + LABEL_X, topPos + Y_STYLE, 152, 20).build());
        themeButton.active = Theme.AVAILABLE.length > 1; // un seul thème pour l'instant → bouton inerte (étape 2)
        roofButton = addRenderableWidget(Button.builder(roofLabel(), b -> {
            roof = roof.next();
            sendStyle();
        }).bounds(leftPos + LABEL_X + 160, topPos + Y_STYLE, 152, 20).build());

        buildButton = addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.controller.build"), b ->
                PacketDistributor.sendToServer(new BuildActionPayload(menu.pos(), BuildActionPayload.START))
        ).bounds(leftPos + LABEL_X, topPos + Y_BUTTONS, 152, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.controller.cancel"), b ->
                PacketDistributor.sendToServer(new BuildActionPayload(menu.pos(), BuildActionPayload.CANCEL))
        ).bounds(leftPos + LABEL_X + 160, topPos + Y_BUTTONS, 152, 20).build());
        // Même emplacement qu'Annuler, partagé en deux moitiés : mutuellement exclusifs avec Annuler
        // (l'un pendant la construction, les deux autres seulement si le site est obstrué et qu'on n'a
        // pas encore démarré). Forcer écrase les cellules signalées ; Ignorer les exclut du plan.
        forceButton = addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.controller.force"), b ->
                PacketDistributor.sendToServer(new BuildActionPayload(menu.pos(), BuildActionPayload.START_FORCE))
        ).bounds(leftPos + LABEL_X + 160, topPos + Y_BUTTONS, 74, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.controller.force.tooltip")))
                .build());
        ignoreButton = addRenderableWidget(Button.builder(Component.translatable("gui.turnkey_factory.controller.ignore"), b ->
                PacketDistributor.sendToServer(new BuildActionPayload(menu.pos(), BuildActionPayload.START_IGNORE))
        ).bounds(leftPos + LABEL_X + 160 + 78, topPos + Y_BUTTONS, 74, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.turnkey_factory.controller.ignore.tooltip")))
                .build());
    }

    private Component themeLabel() {
        return Component.translatable("gui.turnkey_factory.controller.theme", theme.label());
    }

    private Component roofLabel() {
        return Component.translatable("gui.turnkey_factory.controller.roof", roof.label());
    }

    private void sendStyle() {
        PacketDistributor.sendToServer(new SetStylePayload(menu.pos(), theme.ordinal(), roof.ordinal()));
    }

    private void horizontalRow(int colDx, int y, IntSupplier get, IntConsumer set) {
        row(colDx, y, get, set, ControllerBlockEntity.MIN_SIZE, ControllerBlockEntity.MAX_HORIZONTAL,
                ControllerBlockEntity.HORIZONTAL_STEP, this::sendDims);
    }

    private void heightRow(int colDx, int y, IntSupplier get, IntConsumer set) {
        row(colDx, y, get, set, ControllerBlockEntity.MIN_SIZE, ControllerBlockEntity.MAX_HEIGHT, 1, this::sendDims);
    }

    private void offRow(int colDx, int y, IntSupplier get, IntConsumer set) {
        row(colDx, y, get, set, -ControllerBlockEntity.OFFSET_MAX, ControllerBlockEntity.OFFSET_MAX, 1, this::sendOffset);
    }

    private void row(int colDx, int y, IntSupplier get, IntConsumer set, int min, int max, int step, Runnable onChange) {
        configButtons.add(addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            set.accept(Mth.clamp(get.getAsInt() - step, min, max));
            onChange.run();
        }).bounds(leftPos + MINUS_X + colDx, topPos + y, 20, 20).build()));
        configButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            set.accept(Mth.clamp(get.getAsInt() + step, min, max));
            onChange.run();
        }).bounds(leftPos + PLUS_X + colDx, topPos + y, 20, 20).build()));
    }

    private void sendDims() {
        PacketDistributor.sendToServer(new SetDimsPayload(menu.pos(), w, l, h));
    }

    private void sendOffset() {
        PacketDistributor.sendToServer(new SetOffsetPayload(menu.pos(), ox, oy, oz));
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        int lx = leftPos + LABEL_X;
        int vx = leftPos + VALUE_X;
        int lx2 = lx + COL2_DX;
        int vx2 = vx + COL2_DX;

        // Colonne gauche : dimensions
        g.drawString(font, Component.translatable("gui.turnkey_factory.controller.dimensions"), lx, topPos + Y_HEADER, 0xC0C0FF, false);
        label(g, lx, vx, Y_ROW1, Component.translatable("gui.turnkey_factory.width"), w);
        label(g, lx, vx, Y_ROW2, Component.translatable("gui.turnkey_factory.length"), l);
        label(g, lx, vx, Y_ROW3, Component.translatable("gui.turnkey_factory.controller.height"), h);

        // Colonne droite : décalage du fantôme
        g.drawString(font, Component.translatable("gui.turnkey_factory.controller.ghost_offset"), lx2, topPos + Y_HEADER, 0xC0C0FF, false);
        label(g, lx2, vx2, Y_ROW1, Component.translatable("gui.turnkey_factory.axis_x"), ox);
        label(g, lx2, vx2, Y_ROW2, Component.translatable("gui.turnkey_factory.axis_y"), oy);
        label(g, lx2, vx2, Y_ROW3, Component.translatable("gui.turnkey_factory.axis_z"), oz);

        ControllerBlockEntity be = be();
        int linked = be != null ? be.linkedCount() : 0;
        boolean obstructed = be != null && be.isObstructed();
        boolean building = be != null && be.isBuilding();
        // Créatif : aucun matériau requis, donc pas besoin d'inventaire lié. Survie sans rien de lié :
        // rien à construire, cf. ControllerBlockEntity#startBuild — les boutons de démarrage restent
        // visibles (le joueur doit pouvoir le CONSTATER) mais inertes plutôt que de sembler fonctionner.
        boolean hasSource = linked > 0 || (minecraft != null && minecraft.player != null && minecraft.player.isCreative());

        // Colonne 3 : ressources requises (icône + quantité, manquant en surbrillance), tooltip au survol.
        g.drawString(font, Component.translatable("gui.turnkey_factory.controller.materials"), leftPos + PANEL3_X, topPos + Y_HEADER, 0xC0C0FF, false);
        List<ControllerBlockEntity.MaterialLine> materials = be != null ? be.clientMaterialLines() : List.of();
        int maxRows = (imageHeight - Y_MATERIALS_ROW1 - 8) / MATERIAL_ROW_H;
        ItemStack hoveredIcon = ItemStack.EMPTY;
        for (int i = 0; i < materials.size() && i < maxRows; i++) {
            ControllerBlockEntity.MaterialLine ml = materials.get(i);
            int iconX = leftPos + PANEL3_X;
            int rowY = topPos + Y_MATERIALS_ROW1 + i * MATERIAL_ROW_H;
            ItemStack icon = new ItemStack(ml.item());
            g.renderItem(icon, iconX, rowY);
            if (mouseX >= iconX && mouseX < iconX + 16 && mouseY >= rowY && mouseY < rowY + 16) {
                hoveredIcon = icon;
            }
            int missing = ml.missing();
            Component qty = missing > 0
                    ? Component.literal("×" + ml.required() + " ").append(Component.translatable("gui.turnkey_factory.book.missing", missing))
                    : Component.literal("×" + ml.required());
            g.drawString(font, qty, iconX + 18, rowY + 4, missing > 0 ? 0xFFC040 : 0x80FF80, false);
        }

        // Ligne d'info (pleine largeur, sous les deux colonnes)
        g.drawString(font, Component.translatable("gui.turnkey_factory.controller.linked", linked), lx, topPos + Y_INFO, 0xA0E0A0, false);
        if (!building) {
            g.drawString(font, Component.translatable(obstructed ? "gui.turnkey_factory.controller.obstructed" : "gui.turnkey_factory.controller.free"),
                    lx2, topPos + Y_INFO, obstructed ? 0xFF6060 : 0x80FF80, false);
        }

        // Statut de construction
        Component status;
        int statusColor;
        if (building) {
            ItemStack waiting = be.waitingFor();
            if (!waiting.isEmpty()) {
                status = Component.translatable("gui.turnkey_factory.controller.status.waiting", waiting.getHoverName());
                statusColor = 0xFFC040;
            } else {
                status = Component.translatable("gui.turnkey_factory.controller.status.building",
                        be.buildTotal() - be.buildRemaining(), be.buildTotal());
                statusColor = 0x80C0FF;
            }
        } else {
            status = Component.translatable(obstructed ? "gui.turnkey_factory.controller.status.site_obstructed" : "gui.turnkey_factory.controller.status.ready");
            statusColor = obstructed ? 0xFF6060 : 0xB0B0B0;
        }
        g.drawString(font, status, lx, topPos + Y_STATUS, statusColor, false);

        if (buildButton != null) buildButton.active = !building && !obstructed && hasSource;
        if (cancelButton != null) {
            cancelButton.visible = building;
            cancelButton.active = building;
        }
        if (forceButton != null) {
            forceButton.visible = !building && obstructed;
            forceButton.active = !building && obstructed && hasSource;
        }
        if (ignoreButton != null) {
            ignoreButton.visible = !building && obstructed;
            ignoreButton.active = !building && obstructed && hasSource;
        }
        if (themeButton != null) {
            themeButton.setMessage(themeLabel());
            themeButton.active = !building && Theme.AVAILABLE.length > 1;
        }
        if (roofButton != null) {
            roofButton.setMessage(roofLabel());
            roofButton.active = !building;
        }
        for (Button b : configButtons) b.active = !building; // pas de modif de dimensions/décalage en cours de build

        if (!hoveredIcon.isEmpty()) {
            g.renderTooltip(font, hoveredIcon, mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    /** Dessine le libellé à gauche et la valeur centrée entre les boutons (-/+), alignés verticalement. */
    private void label(GuiGraphics g, int labelX, int valueX, int rowY, Component name, int v) {
        int textY = topPos + rowY + 6;
        g.drawString(font, name, labelX, textY, 0xFFFFFF, false);
        g.drawString(font, String.valueOf(v), valueX, textY, 0xFFE070, false);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // fenêtre épurée : pas de titre vanilla ni libellé d'inventaire
    }
}
