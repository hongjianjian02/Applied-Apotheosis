package com.jianjian.appliedapotheosis.filter;

import net.minecraft.network.chat.Component;

/**
 * How the ME Salvager decides which Apotheosis equipment it is allowed to salvage.
 */
public enum FilterMode {
    /** No filtering: every Apotheosis affix item is accepted. */
    DISABLED("gui.applied_apotheosis.filter.disabled"),
    /** Only items matching one of the filter slots are accepted. */
    WHITELIST("gui.applied_apotheosis.filter.whitelist"),
    /** Every item except the ones matching a filter slot is accepted. */
    BLACKLIST("gui.applied_apotheosis.filter.blacklist");

    private static final FilterMode[] VALUES = values();

    private final String translationKey;

    FilterMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(this.translationKey);
    }

    /** The mode the toggle button switches to next. */
    public FilterMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** Safe lookup for NBT data saved by name. */
    public static FilterMode byName(String name) {
        for (var mode : VALUES) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return DISABLED;
    }
}
