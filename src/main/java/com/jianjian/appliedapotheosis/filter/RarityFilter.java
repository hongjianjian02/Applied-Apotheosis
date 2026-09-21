package com.jianjian.appliedapotheosis.filter;

import java.util.List;

import dev.shadowsoffire.apotheosis.Apotheosis;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The five Apotheosis rarity tiers the blacklist/whitelist can be narrowed down to, in ascending
 * order (普通 → 神话).
 * <p>
 * The machine stores the ticked tiers as a bit mask. An empty mask means "no rarity restriction" -
 * that keeps machines which only ever used the item filter behaving exactly as before, and it makes
 * the whitelist usable with rarities alone.
 */
public final class RarityFilter {
    /** Rarity ids, ascending; index {@code i} corresponds to bit {@code i} of the mask. */
    public static final List<ResourceLocation> TIERS = List.of(
            Apotheosis.loc("common"),
            Apotheosis.loc("uncommon"),
            Apotheosis.loc("rare"),
            Apotheosis.loc("epic"),
            Apotheosis.loc("mythic"));

    private static final String[] NAME_KEYS = {
            "gui.applied_apotheosis.rarity.common",
            "gui.applied_apotheosis.rarity.uncommon",
            "gui.applied_apotheosis.rarity.rare",
            "gui.applied_apotheosis.rarity.epic",
            "gui.applied_apotheosis.rarity.mythic"
    };

    /** Used when the client has no synced rarity data yet (the real colours come from Apotheosis). */
    private static final int[] FALLBACK_COLORS = {
            0x808080, 0x33FF33, 0x5555FF, 0xBB00BB, 0xED7014
    };

    /** Nothing ticked: no rarity restriction. */
    public static final int NONE = 0;
    /** Every tier ticked. */
    public static final int ALL = (1 << TIERS.size()) - 1;

    private RarityFilter() {
    }

    public static int size() {
        return TIERS.size();
    }

    public static boolean isTicked(int mask, int index) {
        return index >= 0 && index < TIERS.size() && (mask & (1 << index)) != 0;
    }

    /** Flips a single tier; out-of-range indices are ignored. */
    public static int toggle(int mask, int index) {
        if (index < 0 || index >= TIERS.size()) {
            return mask;
        }
        return mask ^ (1 << index);
    }

    /** Drops bits that do not belong to a tier. */
    public static int clamp(int mask) {
        return mask & ALL;
    }

    /** Index of a rarity id, or -1 for rarities outside these five tiers (e.g. {@code ancient}). */
    public static int indexOf(ResourceLocation id) {
        return TIERS.indexOf(id);
    }

    public static Component tierName(int index) {
        return Component.translatable(NAME_KEYS[index]);
    }

    public static int fallbackColor(int index) {
        return FALLBACK_COLORS[index];
    }
}
