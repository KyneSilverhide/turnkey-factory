package dev.aurelien.prefab.reg;

import dev.aurelien.prefab.PrefabMod;
import dev.aurelien.prefab.item.IncendiaryChargeItem;
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

    public static final DeferredItem<BlockItem> TURRET_BASE =
            ITEMS.registerSimpleBlockItem("turret_base", ModBlocks.TURRET_BASE);

    public static final DeferredItem<BlockItem> TURRET_MACHINEGUN =
            ITEMS.registerSimpleBlockItem("turret_machinegun", ModBlocks.TURRET_MACHINEGUN);

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

    // Munitions manufacturées (cf. le tableau des paliers dans TurretCombat).
    /**
     * Obus perforant : corps en fer, noyau de silex, ceinture de pépite — dégâts doublés par rapport
     * à une pépite de fer. Le silex tient la place qu'occuperait la poudre dans un vrai obus : Create
     * n'a aucune recette qui produise de la poudre à canon, alors que le silex se fabrique à l'infini
     * depuis un simple générateur de cobble (cobble → broyage → gravier → broyage → silex, 100 % à
     * chaque étape). La tourelle est de toute façon électrique, elle lance le projectile au lieu de
     * le propulser par combustion — rien à justifier côté fiction.
     */
    public static final DeferredItem<Item> AMMO_SLUG = ITEMS.registerSimpleItem("ammo_slug");

    /** Obus incendiaire : mêmes dégâts que l'obus perforant, mais enflamme la cible (cf. TurretCombat). */
    public static final DeferredItem<Item> AMMO_INCENDIARY = ITEMS.registerSimpleItem("ammo_incendiary");

    /**
     * Amorce incendiaire, 8 charges (cf. {@link IncendiaryChargeItem} pour le mécanisme d'usure).
     * {@code durability} force au passage une taille de pile de 1, ce qui est le bon comportement
     * pour un item destiné à être tenu par un déployeur.
     */
    public static final DeferredItem<Item> INCENDIARY_CHARGE =
            ITEMS.registerItem("incendiary_charge", IncendiaryChargeItem::new, new Item.Properties().durability(8));

    /**
     * Item transitoire de l'assemblage séquencé de l'obus perforant : c'est lui qui circule sur le
     * tapis entre la presse et les déployeurs. Il n'a d'usage qu'avec Create, mais reste enregistré
     * inconditionnellement, pour la même raison que {@link #COPPER_NUGGET} — un item retiré du
     * registre vide silencieusement les piles correspondantes au chargement du monde. Sans Create il
     * est simplement introuvable : aucune recette ne le produit et il n'apparaît pas dans l'onglet
     * créatif.
     */
    public static final DeferredItem<Item> INCOMPLETE_AMMO_SLUG = ITEMS.registerSimpleItem("incomplete_ammo_slug");
}
