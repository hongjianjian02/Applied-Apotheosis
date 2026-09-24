package com.jianjian.appliedapotheosis.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.items.materials.UpgradeCardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * AE2 upgrade card for the ME Salvager. Installing at least one card is what makes the machine
 * actually salvage Apotheosis equipment.
 */
public class SalvageCardItem extends UpgradeCardItem {
    public SalvageCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.applied_apotheosis.salvage_card").withStyle(ChatFormatting.GRAY));
    }
}
