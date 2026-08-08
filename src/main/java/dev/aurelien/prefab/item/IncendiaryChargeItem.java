package dev.aurelien.prefab.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Amorce incendiaire : un item-outil de 8 charges, appliqué sur un obus perforant pour en faire un
 * obus incendiaire. Le mélange poudre à canon + poudre de blaze reste cher, mais il rend 8 obus, ce
 * qui garde la poudre dans un rôle de bonus occasionnel plutôt que de verrou — Create n'a aucune
 * source de poudre à canon, une munition qui en exigerait immobiliserait la tourelle derrière une
 * ferme à creepers.
 * <p>
 * Avec Create, l'usure est gérée par le déployeur lui-même : sa recette {@code create:deploying}
 * porte {@code keep_held_item}, et {@code BeltDeployerCallbacks} appelle alors
 * {@code hurtAndBreak(1)} au lieu de {@code shrink(1)} — exactement le comportement du papier de
 * verre. Cette classe ne sert donc qu'à obtenir la <strong>même</strong> usure à la table
 * d'artisanat, où rien de tel n'existe : on passe par le reliquat d'artisanat, que NeoForge rend
 * sensible à la pile (cf. {@code IItemExtension#getCraftingRemainingItem(ItemStack)}, appelé par
 * {@code Recipe#getRemainingItems} via {@code ItemStack}). L'amorce est donc rendue au joueur,
 * abîmée d'un point, jusqu'à disparaître au huitième usage.
 */
public class IncendiaryChargeItem extends Item {
    public IncendiaryChargeItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        // La pile d'entrée fait forcément 1 (item à durabilité), mais on repart d'une copie à 1
        // plutôt que de la pile telle quelle : le reliquat ne doit jamais multiplier l'amorce.
        ItemStack remainder = stack.copy();
        remainder.setCount(1);
        int damage = remainder.getDamageValue() + 1;
        // Dernière charge consommée : rien à rendre, l'amorce est épuisée.
        if (damage >= remainder.getMaxDamage()) return ItemStack.EMPTY;
        remainder.setDamageValue(damage);
        return remainder;
    }
}
