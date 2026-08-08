package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PrefabMod.MODID);

    public static final DeferredItem<BlockItem> CONTROLLER =
            ITEMS.registerSimpleBlockItem("controller", ModBlocks.CONTROLLER);

    public static final DeferredItem<BlockItem> LEVELER =
            ITEMS.registerSimpleBlockItem("leveler", ModBlocks.LEVELER);

    public static final DeferredItem<BlockItem> TEXTURIZER =
            ITEMS.registerSimpleBlockItem("texturizer", ModBlocks.TEXTURIZER);

    public static final DeferredItem<BlockItem> LAMPLIGHTER =
            ITEMS.registerSimpleBlockItem("lamplighter", ModBlocks.LAMPLIGHTER);

    public static final DeferredItem<BlockItem> TURRET =
            ITEMS.registerSimpleBlockItem("turret", ModBlocks.TURRET);

    // Composants intermédiaires de la recette du bloc de contrôle.
    /** Plan d'architecte (papier + lapis) : la « mémoire de plan » du bloc de contrôle. */
    public static final DeferredItem<Item> ARCHITECT_BLUEPRINT = ITEMS.registerSimpleItem("architect_blueprint");
    /** Cœur de contrôle (quartz + redstone + or) : la logique de calcul/scan du bloc de contrôle. */
    public static final DeferredItem<Item> CONTROL_CORE = ITEMS.registerSimpleItem("control_core");

    /**
     * Munition tourelle "moitié dégâts" (cf. {@code TurretCombat}) : Minecraft 1.21.1 n'a pas de
     * pépite de cuivre vanilla, mais plusieurs mods en ajoutent une — Create la première. La nôtre
     * est donc déclarée dans le tag conventionnel {@code c:nuggets/copper} (cf.
     * {@code data/c/tags/item/nuggets/copper.json}, qui fusionne avec celui des autres mods au lieu
     * de le remplacer) : la tourelle accepte n'importe laquelle, et les machines des autres mods
     * acceptent la nôtre.
     * <p>
     * Pour éviter deux "Pépite de cuivre" côte à côte, sa recette et son entrée dans l'onglet créatif
     * sont désactivées quand Create est chargé. L'item reste malgré tout <strong>toujours</strong>
     * enregistré : conditionner l'enregistrement lui-même ferait disparaître du registre un item que
     * des joueurs ont peut-être en coffre, et Minecraft supprime silencieusement au chargement les
     * piles dont l'item n'existe plus. Une recette absente ne coûte rien ; un item absent coûte
     * l'inventaire du joueur.
     */
    public static final DeferredItem<Item> COPPER_NUGGET = ITEMS.registerSimpleItem("copper_nugget");
}
