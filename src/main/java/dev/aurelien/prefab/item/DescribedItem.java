package dev.aurelien.prefab.item;

import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Item dont le seul comportement propre est une tooltip d'une ligne, dérivée de son
 * {@code descriptionId} (cf. {@link TooltipHelper#simple}) — évite une sous-classe dédiée par
 * composant intermédiaire (munitions, plan d'architecte, cœur de contrôle...).
 */
public class DescribedItem extends Item {
    public DescribedItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TooltipHelper.simple(tooltip, getDescriptionId());
    }
}
