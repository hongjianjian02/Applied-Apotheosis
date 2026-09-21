package com.jianjian.appliedapotheosis.client;

import com.jianjian.appliedapotheosis.menu.MeSalvagerMenu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the ME Salvager. Slot positions come from
 * {@code assets/ae2/screens/me_salvager.json}.
 * <p>
 * The screen leans on AE2's own furniture: the filter button lives in the standard left toolbar and
 * the upgrade cards sit in AE2's upgrade panel on the right edge.
 */
public class MeSalvagerScreen extends AEBaseScreen<MeSalvagerMenu> {
    private final FilterModeButton filterModeButton;
    private final RarityFilterWidget rarityFilter;

    public MeSalvagerScreen(MeSalvagerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        // AE2's vertical button bar down the left edge of the screen.
        this.filterModeButton = addToLeftToolbar(new FilterModeButton(menu::cycleFilterMode));

        // AE2's upgrade panel, drawn around our upgrade slots on the right edge.
        this.widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE)));

        this.rarityFilter = new RarityFilterWidget(menu);
        this.widgets.add("rarityFilter", this.rarityFilter);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.filterModeButton.setMode(this.menu.filterMode);
        this.rarityFilter.setMask(this.menu.rarityFilter);
        setTextContent("status", statusText());
    }

    /** The one-line status AE2 machines are known for, shown between the input and the inventory. */
    private Component statusText() {
        if (!this.menu.hasCard) {
            return Component.translatable("gui.applied_apotheosis.status.no_card");
        }
        return Component.translatable(this.menu.getHost().isActive()
                ? "gui.applied_apotheosis.status.working"
                : "gui.applied_apotheosis.status.idle");
    }
}
