package com.jianjian.appliedapotheosis.menu;

import java.util.List;

import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.filter.FilterMode;
import com.jianjian.appliedapotheosis.registry.ModMenus;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * GUI of the ME Salvager: the input buffer, the blacklist/whitelist entries and the upgrade cards.
 * <p>
 * The filter mode is cycled by a GUI button. The click travels to the server as a client action, and
 * the resulting value comes back through the {@link GuiSync} field.
 */
public class MeSalvagerMenu extends AEBaseMenu {
    private static final String ACTION_CYCLE_FILTER = "cycleFilterMode";

    private final MeSalvagerBlockEntity host;

    /** Mirrors {@link MeSalvagerBlockEntity#getFilterMode()} on the client. */
    @GuiSync(10)
    public FilterMode filterMode = FilterMode.DISABLED;

    public MeSalvagerMenu(int id, Inventory playerInventory, MeSalvagerBlockEntity host) {
        super(ModMenus.ME_SALVAGER.get(), id, playerInventory, host);
        this.host = host;

        var input = host.getInternalInventory();
        for (int i = 0; i < MeSalvagerBlockEntity.INPUT_SLOTS; i++) {
            var slot = new AppEngSlot(input, i);
            slot.setEmptyTooltip(MeSalvagerMenu::acceptedItemsTooltip);
            addSlot(slot, SlotSemantics.MACHINE_INPUT);
        }

        var filter = host.getFilterInventory();
        for (int i = 0; i < MeSalvagerBlockEntity.FILTER_SLOTS; i++) {
            var slot = new AppEngSlot(filter, i);
            slot.setEmptyTooltip(MeSalvagerMenu::acceptedItemsTooltip);
            addSlot(slot, SlotSemantics.CONFIG);
        }

        var upgrades = host.getUpgrades();
        for (int i = 0; i < upgrades.size(); i++) {
            addSlot(new AppEngSlot(upgrades, i), SlotSemantics.UPGRADE);
        }

        createPlayerInventorySlots(playerInventory);

        registerClientAction(ACTION_CYCLE_FILTER, this::cycleFilterMode);
    }

    public MeSalvagerBlockEntity getHost() {
        return this.host;
    }

    /** Tooltip shown on the empty input/filter slots, explaining what the machine accepts. */
    private static List<Component> acceptedItemsTooltip() {
        return List.of(Component.translatable("gui.applied_apotheosis.affix_only"));
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

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            this.filterMode = this.host.getFilterMode();
        }
        super.broadcastChanges();
    }
}
