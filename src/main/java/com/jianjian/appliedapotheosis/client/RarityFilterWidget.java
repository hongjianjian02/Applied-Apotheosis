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
    /** Cell size and pitch: 18 matches the slot grid, so the row lines up with five slot columns. */
    private static final int CHIP = 18;
    /** Cell pitch. Kept as a field so the grid stays aligned if a gap is ever wanted. */
    private static final int GAP = 0;
    /** Slot-like recess, matching the machine's own slots (#8B8B8B inside a #373737 border). */
    private static final int SLOT_FILL = 0xFF8B8B8B;
    private static final int SLOT_BORDER = 0xFF373737;
    /** Dimming overlay drawn over the icon of an unticked chip. */
    private static final int DIMMED = 0x8C000000;
    /** How much of the rarity colour is mixed into the slot fill of a ticked chip. */
    private static final float TINT = 0.5f;

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
            boolean hovered = isHovered() && mouseX >= x && mouseX < x + CHIP && mouseY >= y && mouseY < y + CHIP;
            int color = this.colors[i];

            // A recess like the machine's own slots: neutral fill, tinted by the rarity when ticked.
            g.fill(x, y, x + CHIP, y + CHIP, ticked ? blend(SLOT_FILL, color, TINT) : SLOT_FILL);

            var icon = this.icons[i];
            if (!icon.isEmpty()) {
                g.renderItem(icon, x + 1, y + 1);
                if (!ticked) {
                    g.fill(x + 1, y + 1, x + CHIP - 1, y + CHIP - 1, DIMMED);
                }
            } else if (ticked) {
                g.fill(x + 4, y + 4, x + CHIP - 4, y + CHIP - 4, withAlpha(darken(color), 0xFF));
            }

            // Hairline border, with the rarity shown along the bottom edge and hover along the top.
            g.fill(x, y, x + CHIP, y + 1, hovered ? 0xFFFFFFFF : SLOT_BORDER);
            g.fill(x, y, x + 1, y + CHIP, SLOT_BORDER);
            g.fill(x + CHIP - 1, y, x + CHIP, y + CHIP, SLOT_BORDER);
            g.fill(x, y + CHIP - 1, x + CHIP, y + CHIP, ticked ? brighten(color) : SLOT_BORDER);
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

    /** Mixes {@code t} of {@code b} into {@code a}, per channel. */
    private static int blend(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) (((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) ((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Pushes a colour towards the light end, for the indicator line of a ticked chip. */
    private static int brighten(int rgb) {
        return blend(rgb, 0xFFFFFF, 0.35f);
    }

    /** Darkens a colour, used when a chip has no icon yet. */
    private static int darken(int rgb) {
        return blend(rgb, 0x000000, 0.25f);
    }
}
