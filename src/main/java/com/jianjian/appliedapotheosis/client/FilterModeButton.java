package com.jianjian.appliedapotheosis.client;

import java.util.List;

import com.jianjian.appliedapotheosis.filter.FilterMode;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;
import net.minecraft.network.chat.Component;

/**
 * The 16x16 button that cycles the blacklist/whitelist mode. AE2 only ships two-state toggle
 * buttons, so the icon is chosen from the current mode instead.
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
        return switch (this.mode) {
            case DISABLED -> Icon.TYPE_FILTER_ALL;
            case WHITELIST -> Icon.WHITELIST;
            case BLACKLIST -> Icon.BLACKLIST;
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
