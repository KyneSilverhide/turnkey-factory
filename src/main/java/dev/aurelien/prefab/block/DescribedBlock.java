package dev.aurelien.prefab.block;

import com.mojang.serialization.MapCodec;
import dev.aurelien.prefab.util.TooltipHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Bloc dont le seul comportement propre est une tooltip d'une ligne, dérivée de son
 * {@code descriptionId} (cf. {@link TooltipHelper#simple}) — pour les blocs purement décoratifs/
 * composants de recette (ossature en bois...) qui n'ont sinon besoin d'aucune sous-classe.
 */
public class DescribedBlock extends Block {
    public static final MapCodec<DescribedBlock> CODEC = simpleCodec(DescribedBlock::new);

    public DescribedBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        TooltipHelper.simple(tooltip, getDescriptionId());
    }
}
