package com.jianjian.appliedapotheosis.client;

import com.jianjian.appliedapotheosis.menu.MeSalvagerMenu;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.ProgressBar;
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
    private final ProgressBar progressBar;

    public MeSalvagerScreen(MeSalvagerMenu menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        // The blacklist/whitelist button sits inside the panel, right next to the filter label and
        // the rarity chips, so the whole filter UI is in one place. (AE2 does the same with buttons
        // like openPriority; a toolbar button outside the panel was too easy to miss.)
        this.filterModeButton = new FilterModeButton(menu::cycleFilterMode);
        this.widgets.add("filterMode", this.filterModeButton);

        // AE2's upgrade panel, drawn around our upgrade slots on the right edge.
        this.widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE)));

        this.rarityFilter = new RarityFilterWidget(menu);
        this.widgets.add("rarityFilter", this.rarityFilter);

        // AE2's progress bar: it pulses with the machine's cycle and its tooltip says why it is idle.
        this.progressBar = new ProgressBar(menu, style.getImage("progressBar"), ProgressBar.Direction.VERTICAL);
        this.widgets.add("progressBar", this.progressBar);
    }

    /**
     * Dev self-test hook: lets the layout check report where the filter mode button ended up, so a
     * missing icon can be told apart from a missing button.
     */
    public FilterModeButton filterModeButton() {
        return this.filterModeButton;
    }

    /** Dev self-test hook: the rarity chips widget, to check its position against the style sheet. */
    public RarityFilterWidget rarityFilterWidget() {
        return this.rarityFilter;
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        this.filterModeButton.setMode(this.menu.filterMode);
        this.rarityFilter.setMask(this.menu.rarityFilter);
        this.progressBar.setFullMsg(statusText());
    }

    /** Shown in the progress bar's tooltip: why the machine is or is not running. */
    private Component statusText() {
        if (!this.menu.hasCard) {
            return Component.translatable("gui.applied_apotheosis.status.no_card");
        }
        if (!this.menu.working) {
            return Component.translatable("gui.applied_apotheosis.status.idle");
        }
        return Component.translatable("gui.applied_apotheosis.status.working", this.menu.getMaxProgress());
    }
}
