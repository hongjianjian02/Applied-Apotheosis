package com.jianjian.appliedapotheosis.client;

import com.jianjian.appliedapotheosis.menu.MeSalvagerMenu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Screen of the ME Salvager. Slot positions come from
 * {@code assets/ae2/screens/me_salvager.json}.
 */
public class MeSalvagerScreen extends AEBaseScreen<MeSalvagerMenu> {
    private final FilterModeButton filterModeButton;
    private final RarityFilterWidget rarityFilter;

    public MeSalvagerScreen(MeSalvagerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        this.filterModeButton = new FilterModeButton(menu::cycleFilterMode);
        this.widgets.add("filterMode", this.filterModeButton);

        this.rarityFilter = new RarityFilterWidget(menu);
        this.widgets.add("rarityFilter", this.rarityFilter);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.filterModeButton.setMode(this.menu.filterMode);
        this.rarityFilter.setMask(this.menu.rarityFilter);
    }
}
