package dev.aurelien.prefab.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Pont optionnel vers Create. Zéro dépendance compile : les blocs sont résolus au runtime via le
 * registre Minecraft (après chargement de Create). Toutes les méthodes sont sûres à appeler même
 * si Create n'est pas chargé — elles retournent {@link Blocks#AIR} comme état de repli.
 */
public final class CreateCompat {
    private CreateCompat() {}

    private static volatile boolean ready = false;
    private static Block metalGirder;
    private static Block encasedFan;
    private static Block windowPane;
    private static Block fluidTank;       // create
    private static Block fluidPipe;       // create
    private static Block factoryGauge;    // create
    private static Block valveHandle;     // create
    private static Block catwalkRailing;  // createdeco
    private static Block cageLampCopper;  // createdeco (thème brique : plafond + murs intérieurs)
    private static Block cageLampAndesite; // createdeco (thème pierre : plafond + murs intérieurs)

    /** Create est requis pour les blocs « create:* ». */
    public static boolean isLoaded() {
        return ModList.get().isLoaded("create");
    }

    /** Create Deco est requis pour les blocs décoratifs « createdeco:* » (catwalks, cage lamps…). */
    public static boolean isDecoLoaded() {
        return ModList.get().isLoaded("createdeco");
    }

    private static synchronized void ensureReady() {
        if (ready) return;
        ready = true;
        metalGirder = encasedFan = windowPane = Blocks.AIR;
        fluidTank = fluidPipe = factoryGauge = valveHandle = catwalkRailing = Blocks.AIR;
        cageLampCopper = cageLampAndesite = Blocks.AIR;
        if (isLoaded()) {
            metalGirder   = resolve("create:metal_girder");
            encasedFan    = resolve("create:encased_fan");
            windowPane    = resolve("create:weathered_iron_window_pane");
            fluidTank     = resolve("create:fluid_tank");
            fluidPipe     = resolve("create:fluid_pipe");
            factoryGauge  = resolve("create:factory_gauge");
            valveHandle   = resolve("create:copper_valve_handle");
        }
        if (isDecoLoaded()) {
            catwalkRailing   = resolve("createdeco:andesite_catwalk_railing");
            cageLampCopper   = resolve("createdeco:yellow_copper_lamp");
            cageLampAndesite = resolve("createdeco:yellow_andesite_lamp");
        }
    }

    private static Block resolve(String id) {
        return BuiltInRegistries.BLOCK
                .getOptional(ResourceLocation.parse(id))
                .orElse(Blocks.AIR);
    }

    /**
     * Metal Girder orienté en poutre continue selon {@code axis}. Le bloc ne s'auto-connecte PAS
     * tout seul (placement en monde) : il faut forcer la propriété de l'axe ({@code x} ou {@code z})
     * pour obtenir une poutre liée plutôt qu'une série de poteaux isolés.
     */
    public static BlockState metalGirder(Direction.Axis axis) {
        ensureReady();
        BlockState s = metalGirder.defaultBlockState();
        return (axis == Direction.Axis.X) ? withBool(s, "x", true)
             : (axis == Direction.Axis.Z) ? withBool(s, "z", true)
             : s;
    }

    /** Encased Fan (ventilateur encaissé) orienté vers {@code facing} — sert de climatiseur. */
    public static BlockState encasedFan(Direction facing) {
        ensureReady();
        return withFacing(encasedFan.defaultBlockState(), facing);
    }

    /**
     * Weathered Iron Window Pane (remplace le glass pane vanille pour un look industriel).
     * Pas de propriété à forcer : connectivité gérée par le moteur comme les glass panes.
     */
    public static BlockState windowPane() {
        ensureReady();
        return windowPane.defaultBlockState();
    }

    /** Fluid Tank Create (par défaut) : la block-entity formera d'elle-même le multiblock 1×N (look silo vitré). */
    public static BlockState fluidTank() {
        ensureReady();
        return fluidTank.defaultBlockState();
    }

    /** Fluid Pipe Create (par défaut) : se raccorde tout seul aux cuves/voisins fluidiques. */
    public static BlockState fluidPipe() {
        ensureReady();
        return fluidPipe.defaultBlockState();
    }

    /** Factory Gauge mural orienté vers {@code out} (greeble technique). */
    public static BlockState factoryGauge(Direction out) {
        ensureReady();
        BlockState s = factoryGauge.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.ATTACH_FACE)) s = s.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.WALL);
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, out);
        return s;
    }

    /** Valve Handle (volant) orienté vers {@code facing} (greeble technique). */
    public static BlockState valveHandle(Direction facing) {
        ensureReady();
        return withFacing(valveHandle.defaultBlockState(), facing);
    }

    /**
     * Cage Lamp Create Deco allumée en permanence, orientée vers {@code facing} (DOWN = suspendue).
     * La luminosité suit {@code shouldBeLit = INVERTED xor hasSignal} : sans redstone, il faut
     * {@code inverted=true} pour que la lampe reste allumée (sinon elle s'éteint à la pose). Même
     * variante au plafond et sur les murs intérieurs (harmonisée par thème).
     */
    public static BlockState cageLampCopper(Direction facing) {
        ensureReady();
        return litLamp(cageLampCopper, facing);
    }

    /** Cage Lamp andésite (thème pierre) — mêmes règles d'allumage que {@link #cageLampCopper}. */
    public static BlockState cageLampAndesite(Direction facing) {
        ensureReady();
        return litLamp(cageLampAndesite, facing);
    }

    private static BlockState litLamp(Block lamp, Direction facing) {
        if (lamp == Blocks.AIR) return Blocks.AIR.defaultBlockState();
        BlockState s = withFacing(lamp.defaultBlockState(), facing);
        if (s.hasProperty(BlockStateProperties.INVERTED)) s = s.setValue(BlockStateProperties.INVERTED, true);
        if (s.hasProperty(BlockStateProperties.LIT)) s = s.setValue(BlockStateProperties.LIT, true);
        return s;
    }

    /**
     * Andesite Catwalk Railing Create Deco avec connexions explicites (booléens {@code north/south/
     * east/west}). On NE s'appuie PAS sur l'auto-connexion : le railing calcule ses liaisons dans
     * {@code neighborChanged} (et non {@code updateShape}), donc {@code updateFromNeighbourShapes} ne
     * fait rien. On force donc les côtés directement selon la géométrie de l'anneau de toit.
     */
    public static BlockState catwalkRailing(boolean north, boolean south, boolean east, boolean west) {
        ensureReady();
        BlockState s = catwalkRailing.defaultBlockState();
        s = withBool(s, "north", north);
        s = withBool(s, "south", south);
        s = withBool(s, "east", east);
        s = withBool(s, "west", west);
        return s;
    }


    private static BlockState withFacing(BlockState s, Direction facing) {
        return s.hasProperty(BlockStateProperties.FACING) ? s.setValue(BlockStateProperties.FACING, facing) : s;
    }

    /** Force une propriété booléenne nommée (résolue sur la StateDefinition du bloc, sans dépendre de Create). */
    private static BlockState withBool(BlockState s, String name, boolean val) {
        Property<?> p = s.getBlock().getStateDefinition().getProperty(name);
        return (p instanceof BooleanProperty bp) ? s.setValue(bp, val) : s;
    }

    // ----- Clipboard (liste de matériaux) -----

    /** Une ligne à écrire sur le clipboard : icône, texte et quantité affichée à côté. */
    public record ClipboardLine(ItemStack icon, Component text, int amount) {}

    private static final int CLIPBOARD_LINES_PER_PAGE = 8;

    private static volatile boolean clipboardReady = false;
    private static Item clipboardItem;
    private static DataComponentType clipboardContentType; // DataComponentType<ClipboardContent>, brut (pas de dépendance compile)
    private static Constructor<?> clipboardEntryCtor;       // ClipboardEntry(boolean, MutableComponent)
    private static Method clipboardEntryDisplayItem;         // ClipboardEntry#displayItem(ItemStack, int)
    private static Constructor<?> clipboardContentCtor;      // ClipboardContent(ClipboardType, List<List<ClipboardEntry>>, boolean)
    private static Object clipboardTypeWritten;              // ClipboardOverrides.ClipboardType.WRITTEN

    private static synchronized void ensureClipboardReady() {
        if (clipboardReady) return;
        clipboardReady = true;
        if (!isLoaded()) return;
        try {
            clipboardItem = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse("create:clipboard")).orElse(null);

            Class<?> allDataComponents = Class.forName("com.simibubi.create.AllDataComponents");
            clipboardContentType = (DataComponentType) allDataComponents.getField("CLIPBOARD_CONTENT").get(null);

            Class<?> entryClass = Class.forName("com.simibubi.create.content.equipment.clipboard.ClipboardEntry");
            clipboardEntryCtor = entryClass.getConstructor(boolean.class, net.minecraft.network.chat.MutableComponent.class);
            clipboardEntryDisplayItem = entryClass.getMethod("displayItem", ItemStack.class, int.class);

            Class<?> contentClass = Class.forName("com.simibubi.create.content.equipment.clipboard.ClipboardContent");
            Class<?> typeClass = Class.forName("com.simibubi.create.content.equipment.clipboard.ClipboardOverrides$ClipboardType");
            clipboardContentCtor = contentClass.getConstructor(typeClass, List.class, boolean.class);
            clipboardTypeWritten = typeClass.getField("WRITTEN").get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            // API Create modifiée ou absente : le clipboard reste indisponible (isClipboard renverra false).
            clipboardItem = null;
        }
    }

    /** Vrai si {@code stack} est un clipboard Create (et que Create est chargé). */
    public static boolean isClipboard(ItemStack stack) {
        ensureClipboardReady();
        return clipboardItem != null && stack.is(clipboardItem);
    }

    /**
     * Écrit {@code lines} sur le clipboard (une page = {@link #CLIPBOARD_LINES_PER_PAGE} lignes, une
     * entrée cochable par ligne avec son icône). Passe par réflexion sur les vraies classes Create
     * (constructeurs/accesseurs réels, pas un format NBT deviné) — zéro dépendance compile. Renvoie
     * false sans rien modifier si Create/le clipboard n'est pas disponible ou si l'API a changé.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean writeMaterialsClipboard(ItemStack clipboardStack, List<ClipboardLine> lines) {
        ensureClipboardReady();
        if (clipboardContentType == null || clipboardEntryCtor == null || clipboardContentCtor == null) return false;
        try {
            List<List<Object>> pages = new ArrayList<>();
            List<Object> page = new ArrayList<>();
            for (ClipboardLine line : lines) {
                if (page.size() >= CLIPBOARD_LINES_PER_PAGE) {
                    pages.add(page);
                    page = new ArrayList<>();
                }
                Object entry = clipboardEntryCtor.newInstance(false, line.text().copy());
                clipboardEntryDisplayItem.invoke(entry, line.icon(), line.amount());
                page.add(entry);
            }
            pages.add(page);
            Object content = clipboardContentCtor.newInstance(clipboardTypeWritten, pages, false);
            clipboardStack.set(clipboardContentType, content);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
