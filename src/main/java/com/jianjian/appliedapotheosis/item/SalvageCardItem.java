package com.jianjian.appliedapotheosis.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import appeng.items.materials.UpgradeCardItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * AE2 upgrade card for the ME Salvager. Installing at least one card is what makes the machine
 * actually salvage Apotheosis equipment.
 */
public class SalvageCardItem extends UpgradeCardItem {
    public SalvageCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, lines, flag);
        lines.add(Component.translatable("tooltip.applied_apotheosis.salvage_card").withStyle(ChatFormatting.GRAY));
    }
}
