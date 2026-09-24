package com.jianjian.appliedapotheosis.client;

import java.util.List;

import com.jianjian.appliedapotheosis.filter.FilterMode;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.network.chat.Component;

/**
 * The 16x16 button that cycles the blacklist/whitelist mode. AE2 only ships two-state toggle
 * buttons, so the icon is chosen from the current mode instead.
 * <p>
 * On 1.21.1 the icon names are AE2 19's: its {@code WHITELIST} / {@code BLACKLIST} icons are stale
 * (no glyphs left in the icon sheet, and no AE2 screen uses them), so the storage filter icons AE2
 * 19 does use are the working equivalent.
 */
public class FilterModeButton extends IconButton {
    private FilterMode mode = FilterMode.DISABLED;

    public FilterModeButton(Runnable onPress) {
        super(button -> onPress.run());
        updateMessage();
    }

    public void setMode(FilterMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            updateMessage();
        }
    }

    private void updateMessage() {
        setMessage(Component.translatable("gui.applied_apotheosis.filter_mode", this.mode.getDisplayName()));
    }

    @Override
    protected Icon getIcon() {
        // AE2 19 still has Icon.WHITELIST / Icon.BLACKLIST, but their glyphs are gone from its icon
        // sheet and nothing in AE2 uses them any more - drawing them shows Minecraft's magenta
        // missing-texture colour. These two are what AE2 19's own SettingToggleButton uses, and they
        // carry the same meaning (only these pass / these never pass).
        return switch (this.mode) {
            case DISABLED -> Icon.TYPE_FILTER_ALL;
            case WHITELIST -> Icon.STORAGE_FILTER_EXTRACTABLE_ONLY;
            case BLACKLIST -> Icon.STORAGE_FILTER_EXTRACTABLE_NONE;
        };
    }

    @Override
    public List<Component> getTooltipMessage() {
        return List.of(
                Component.translatable("gui.applied_apotheosis.filter_mode", this.mode.getDisplayName()),
                Component.translatable(tooltipKey()).withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    private String tooltipKey() {
        return switch (this.mode) {
            case DISABLED -> "gui.applied_apotheosis.filter.disabled.tooltip";
            case WHITELIST -> "gui.applied_apotheosis.filter.whitelist.tooltip";
            case BLACKLIST -> "gui.applied_apotheosis.filter.blacklist.tooltip";
        };
    }
}
