package com.jianjian.appliedapotheosis.menu;

import java.util.List;

import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.filter.FilterMode;
import com.jianjian.appliedapotheosis.filter.RarityFilter;
import com.jianjian.appliedapotheosis.registry.ModMenus;

import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.interfaces.IProgressProvider;
import appeng.menu.slot.AppEngSlot;
import appeng.menu.slot.FakeSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * GUI of the ME Salvager: the input buffer, the blacklist/whitelist entries and the upgrade cards.
 * <p>
 * The filter mode is cycled by a GUI button, the rarity tiers are ticked in the chip row next to it.
 * Both travel to the server as client actions, and the resulting values come back through the
 * {@link GuiSync} fields.
 */
public class MeSalvagerMenu extends AEBaseMenu implements IProgressProvider {
    private static final String ACTION_CYCLE_FILTER = "cycleFilterMode";
    private static final String ACTION_TOGGLE_RARITY = "toggleRarityFilter";

    private final MeSalvagerBlockEntity host;

    /** Mirrors {@link MeSalvagerBlockEntity#getFilterMode()} on the client. */
    @GuiSync(10)
    public FilterMode filterMode = FilterMode.DISABLED;

    /** Mirrors {@link MeSalvagerBlockEntity#getRarityFilter()} on the client. */
    @GuiSync(11)
    public int rarityFilter = RarityFilter.NONE;

    /** Whether the machine is salvaging right now, shown by the progress bar. */
    @GuiSync(12)
    public boolean working = false;

    /** Whether a salvage card is installed - without one nothing happens at all. */
    @GuiSync(13)
    public boolean hasCard = false;

    /** Ticks per cycle with the installed speed cards. */
    @GuiSync(14)
    public int cycleTicks = 1;

    public MeSalvagerMenu(int id, Inventory playerInventory, MeSalvagerBlockEntity host) {
        super(ModMenus.ME_SALVAGER.get(), id, playerInventory, host);
        this.host = host;

        var input = host.getInternalInventory();
        for (int i = 0; i < MeSalvagerBlockEntity.INPUT_SLOTS; i++) {
            var slot = new AppEngSlot(input, i);
            slot.setEmptyTooltip(MeSalvagerMenu::inputTooltip);
            addSlot(slot, SlotSemantics.MACHINE_INPUT);
        }

        var filter = host.getFilterInventory();
        for (int i = 0; i < MeSalvagerBlockEntity.FILTER_SLOTS; i++) {
            // A fake ("ghost") slot: clicking with an item, or dragging one in from JEI, only marks
            // the entry - the item itself is never taken. AE2's JEI plugin handles the drag part and
            // calls canSetFilterTo, which we narrow down to Apotheosis loot.
            var slot = new SalvageableFakeSlot(filter, i);
            slot.setEmptyTooltip(MeSalvagerMenu::filterTooltip);
            addSlot(slot, SlotSemantics.CONFIG);
        }

        var upgrades = host.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            addSlot(new AppEngSlot(upgrades, i), SlotSemantics.UPGRADE);
        }

        createPlayerInventorySlots(playerInventory);

        registerClientAction(ACTION_CYCLE_FILTER, this::cycleFilterMode);
        registerClientAction(ACTION_TOGGLE_RARITY, Integer.class, this::toggleRarityFilter);
    }

    public MeSalvagerBlockEntity getHost() {
        return this.host;
    }

    /**
     * Tooltip of an empty "to salvage" slot: what may be dropped in, including the rarity the
     * machine is currently configured for.
     */
    private static List<Component> inputTooltip() {
        return List.of(
                Component.translatable("gui.applied_apotheosis.input_hint",
                        MeSalvagerBlockEntity.minimumRarity().toString()),
                Component.translatable("gui.applied_apotheosis.affix_only")
                        .withStyle(ChatFormatting.GRAY));
    }

    /** Tooltip of an empty filter slot: the entries are markers, not real items. */
    private static List<Component> filterTooltip() {
        return List.of(Component.translatable("gui.applied_apotheosis.filter_hint")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * Clicks into an input slot that the machine refuses are explained, instead of being dropped
     * silently by vanilla - otherwise "why can't I put this in?" has no answer in game.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (clickType == ClickType.PICKUP && slotId >= 0 && slotId < this.slots.size()
                && !getCarried().isEmpty() && !this.slots.get(slotId).mayPlace(getCarried())
                && getSlotSemantic(this.slots.get(slotId)) == SlotSemantics.MACHINE_INPUT) {
            var stack = getCarried();
            var reason = !MeSalvagerBlockEntity.hasRequiredRarity(stack)
                    ? Component.translatable("gui.applied_apotheosis.refused_rarity",
                            MeSalvagerBlockEntity.minimumRarity().toString())
                    : Component.translatable("gui.applied_apotheosis.refused_filter");
            player.displayClientMessage(reason, true);
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Presses the filter button: on the client this asks the server to change the mode, on the
     * server it actually performs the change.
     */
    public void cycleFilterMode() {
        if (isClientSide()) {
            sendClientAction(ACTION_CYCLE_FILTER);
            return;
        }

        var next = this.host.getFilterMode().next();
        this.host.setFilterMode(next);
        this.filterMode = next;
    }

    /**
     * Ticks/unticked one rarity tier of the filter: on the client this asks the server to do it, on
     * the server it actually happens.
     */
    public void toggleRarityFilter(int index) {
        if (isClientSide()) {
            sendClientAction(ACTION_TOGGLE_RARITY, index);
            return;
        }

        this.host.toggleRarityFilter(index);
        this.rarityFilter = this.host.getRarityFilter();
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.filterMode = this.host.getFilterMode();
            this.rarityFilter = this.host.getRarityFilter();
            this.working = this.host.isActive();
            this.hasCard = this.host.isSalvageCardInstalled();
            this.cycleTicks = this.host.getCycleTicks();
        }
        super.broadcastChanges();
    }

    // ------------------------------------------------------------------
    // Progress bar (AE2's ProgressBar widget reads these)
    // ------------------------------------------------------------------

    /**
     * How far the current cycle has run. The machine works instantly once per cycle, so the bar is
     * driven by the world clock: it pulses with the machine's rhythm instead of pretending to be a
     * long-running job.
     */
    @Override
    public int getCurrentProgress() {
        var level = this.host.getLevel();
        if (level == null || !this.working) {
            return 0;
        }
        return (int) (level.getGameTime() % getMaxProgress());
    }

    @Override
    public int getMaxProgress() {
        return Math.max(1, this.cycleTicks);
    }

    /**
     * Filter entries are markers, not real items: the slot refuses normal placement and only accepts
     * Apotheosis loot as a marker, so marking something never costs the player the item.
     * <p>
     * Public so the developer self-test can exercise the marker rules without a player.
     */
    public static class SalvageableFakeSlot extends FakeSlot {
        public SalvageableFakeSlot(InternalInventory inventory, int slot) {
            super(inventory, slot);
        }

        @Override
        public boolean canSetFilterTo(ItemStack stack) {
            // Clearing an entry has to stay possible. AE2 validates the value it is about to store
            // through this method, and clearing stores an empty stack - refusing empty stacks here is
            // what made marked entries impossible to remove.
            return stack.isEmpty()
                    || (MeSalvagerBlockEntity.hasRequiredRarity(stack) && super.canSetFilterTo(stack));
        }
    }
}
