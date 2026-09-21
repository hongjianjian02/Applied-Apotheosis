package com.jianjian.appliedapotheosis.client;

import java.util.ArrayList;
import java.util.List;

import com.jianjian.appliedapotheosis.filter.RarityFilter;
import com.jianjian.appliedapotheosis.menu.MeSalvagerMenu;

import appeng.client.gui.widgets.ITooltip;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * The row of five rarity chips (普通 → 神话) that sits next to the filter label. Every chip shows the
 * material that rarity salvages into - 神秘废金属 / 陈旧布匹 / 发光水晶碎片 / 玄奥沙 / 神铸珍珠 - on a
 * backdrop in that rarity's colour: lit when the tier belongs to the filter, darkened when it does
 * not. What "belongs to the filter" means depends on the mode - a whitelist salvages exactly the
 * ticked tiers, a blacklist never salvages them.
 * <p>
 * Clicking a chip hands its index to the menu, which forwards it to the server; the resulting mask
 * comes back through the synced field.
 */
public class RarityFilterWidget extends AbstractWidget implements ITooltip {
    private static final int CHIP = 18;
    private static final int GAP = 1;
    /** Chip backdrop, used for the unticked state. */
    private static final int BACKDROP = 0xFF10151C;
    /** Dimming overlay drawn over the icon of an unticked chip. */
    private static final int DIMMED = 0xA0000000;

    private final MeSalvagerMenu menu;
    private final int[] colors = new int[RarityFilter.size()];
    private final ItemStack[] icons = new ItemStack[RarityFilter.size()];
    private int mask = RarityFilter.NONE;

    public RarityFilterWidget(MeSalvagerMenu menu) {
        super(0, 0, RarityFilter.size() * CHIP + (RarityFilter.size() - 1) * GAP, CHIP,
                Component.translatable("gui.applied_apotheosis.rarity_filter"));
        this.menu = menu;
        for (int i = 0; i < this.colors.length; i++) {
            this.colors[i] = resolveColor(i);
            this.icons[i] = resolveIcon(i);
        }
    }

    /** Rarity colours come from Apotheosis' synced rarity data, with a fallback for the loading screen. */
    private static int resolveColor(int index) {
        var holder = RarityRegistry.INSTANCE.holder(RarityFilter.TIERS.get(index));
        if (holder.isBound()) {
            var color = holder.get().getColor();
            if (color != null) {
                return color.getValue();
            }
        }
        return RarityFilter.fallbackColor(index);
    }

    /**
     * The item a rarity salvages into - the same material series the wiki chart shows. Read from the
     * rarity data, so a datapack that repoints a material is followed automatically.
     */
    private static ItemStack resolveIcon(int index) {
        var holder = RarityRegistry.INSTANCE.holder(RarityFilter.TIERS.get(index));
        if (holder.isBound()) {
            var material = holder.get().getMaterial();
            if (material != null) {
                return new ItemStack(material);
            }
        }
        return ItemStack.EMPTY;
    }

    public void setMask(int mask) {
        this.mask = RarityFilter.clamp(mask);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < RarityFilter.size(); i++) {
            int x = getX() + i * (CHIP + GAP);
            int y = getY();
            boolean ticked = RarityFilter.isTicked(this.mask, i);
            int color = this.colors[i];

            g.fill(x, y, x + CHIP, y + CHIP, BACKDROP);
            if (ticked) {
                g.fill(x + 1, y + 1, x + CHIP - 1, y + CHIP - 1, withAlpha(color, 0x66));
            }

            var icon = this.icons[i];
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + 1, y + 1);
                if (!ticked) {
                    g.fill(x + 1, y + 1, x + CHIP - 1, y + CHIP - 1, DIMMED);
                }
            } else if (ticked) {
                // Rarity data not loaded yet: fall back to a plain swatch.
                g.fill(x + 3, y + 3, x + CHIP - 3, y + CHIP - 3, withAlpha(color, 0xFF));
            }

            g.renderOutline(x, y, CHIP, CHIP, withAlpha(color, ticked ? 0xFF : 0x55));

            if (isHovered() && mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + CHIP) {
                g.renderOutline(x - 1, y - 1, CHIP + 2, CHIP + 2, 0xFFFFFFFF);
            }
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int step = CHIP + GAP;
        int index = (int) ((mouseX - getX()) / step);
        // Ignore the gaps between the chips.
        if (index >= 0 && index < RarityFilter.size() && mouseX < getX() + index * step + CHIP) {
            this.menu.toggleRarityFilter(index);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }

    @Override
    public List<Component> getTooltipMessage() {
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable("gui.applied_apotheosis.rarity_filter"));
        for (int i = 0; i < RarityFilter.size(); i++) {
            boolean ticked = RarityFilter.isTicked(this.mask, i);
            lines.add(Component.literal(ticked ? "[x] " : "[ ] ")
                    .append(RarityFilter.tierName(i))
                    .withStyle(ticked ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("gui.applied_apotheosis.rarity_filter.hint")
                .withStyle(ChatFormatting.GRAY));
        return lines;
    }

    @Override
    public Rect2i getTooltipArea() {
        return new Rect2i(getX(), getY(), getWidth(), getHeight());
    }

    @Override
    public boolean isTooltipAreaVisible() {
        return this.visible;
    }

    private static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
