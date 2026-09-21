package com.jianjian.appliedapotheosis.blockentity;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.jianjian.appliedapotheosis.filter.FilterMode;
import com.jianjian.appliedapotheosis.filter.RarityFilter;
import com.jianjian.appliedapotheosis.registry.ModItems;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.ISegmentedInventory;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkInvBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.FilteredInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;

import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemItem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The ME Salvager block entity.
 * <p>
 * Items pushed into the input buffer (by pipes, an ME interface, or by right-clicking the block with
 * an Apotheosis affix item or a gem) are salvaged using Apotheosis' own salvaging recipes: affix
 * equipment turns into the material of its rarity, gems turn into gem dust. The results are buffered
 * internally and then inserted into the ME network.
 * <p>
 * At least one {@code applied_apotheosis:salvage_card} must be installed for the machine to do anything;
 * every further card lets it process one more item per tick, while AE2 speed cards shorten the tick
 * interval itself.
 */
public class MeSalvagerBlockEntity extends AENetworkInvBlockEntity
        implements IGridTickable, IUpgradeableObject {

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 9;
    public static final int UPGRADE_SLOTS = 3;
    /** Slots holding the blacklist/whitelist entries. */
    public static final int FILTER_SLOTS = 9;

    /** AE consumed while the machine is connected to a network, but idle. */
    private static final double IDLE_POWER = 1.0;
    /** AE consumed per salvaged item. */
    private static final double POWER_PER_OPERATION = 20.0;
    /** How often the machine runs when idle-ish, before speed cards are taken into account. */
    private static final int BASE_TICK_RATE = 10;

    /**
     * Minimum Apotheosis rarity the machine works with. Items below it are rejected from both the
     * input buffer and the filter list. Set this to {@code apotheosis:mythic} (or any other rarity)
     * to restrict the machine to higher-tier equipment.
     */
    private static final ResourceLocation MINIMUM_RARITY = Apotheosis.loc("common");

    private final AppEngInternalInventory input;
    private final AppEngInternalInventory output;
    private final AppEngInternalInventory filter;
    private final InternalInventory exposedInput;
    private final IUpgradeInventory upgrades;
    private final IActionSource actionSource = new MachineSource(this);

    /** Blacklist / whitelist behaviour, configured through the GUI. */
    private FilterMode filterMode = FilterMode.DISABLED;

    /**
     * The rarity tiers the filter refers to, as a bit mask over {@link RarityFilter#TIERS}.
     * {@link RarityFilter#NONE} means the rarity selection is not a restriction at all.
     */
    private int rarityFilter = RarityFilter.NONE;

    /** Synced to the client for the "active" block state. */
    private boolean active;

    public MeSalvagerBlockEntity(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);

        this.input = new AppEngInternalInventory(this, INPUT_SLOTS, 64, new SalvageableItemFilter());
        this.output = new AppEngInternalInventory(this, OUTPUT_SLOTS);
        this.filter = new AppEngInternalInventory(this, FILTER_SLOTS, 1, new SalvageableOnlyFilter());
        this.exposedInput = new FilteredInternalInventory(this.input, new SalvageableItemFilter());

        this.getMainNode()
                .setIdlePowerUsage(IDLE_POWER)
                .setFlags()
                .addService(IGridTickable.class, this);

        this.upgrades = UpgradeInventories.forMachine(ModItems.ME_SALVAGER.get(), UPGRADE_SLOTS,
                this::saveChanges);
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.COVERED;
    }

    // ------------------------------------------------------------------
    // Inventories
    // ------------------------------------------------------------------

    @Override
    public InternalInventory getInternalInventory() {
        return this.input;
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction facing) {
        return this.exposedInput;
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return this.upgrades;
    }

    @Nullable
    @Override
    public InternalInventory getSubInventory(ResourceLocation id) {
        if (id.equals(ISegmentedInventory.STORAGE)) {
            return this.input;
        } else if (id.equals(ISegmentedInventory.UPGRADES)) {
            return this.upgrades;
        } else if (id.equals(ISegmentedInventory.CONFIG)) {
            return this.filter;
        }
        return super.getSubInventory(id);
    }

    @Override
    public void onChangeInventory(InternalInventory inv, int slot) {
        if (inv == this.upgrades && !isEnabled()) {
            setActive(false);
        }
        this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
    }

    /**
     * Tries to insert an Apotheosis affix item into the input buffer, e.g. when a player
     * right-clicks the machine while holding one.
     *
     * @return the part of the stack that could not be inserted
     */
    public ItemStack insertForSalvaging(ItemStack stack) {
        var leftover = this.input.addItems(stack.copy());
        if (leftover.getCount() != stack.getCount()) {
            this.saveChanges();
            this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }
        return leftover;
    }

    // ------------------------------------------------------------------
    // Blacklist / whitelist
    // ------------------------------------------------------------------

    /** The inventory holding the filter entries (shown as slots in the GUI). */
    public InternalInventory getFilterInventory() {
        return this.filter;
    }

    public FilterMode getFilterMode() {
        return this.filterMode;
    }

    /**
     * Changes the filter mode. Items that are already buffered are re-evaluated against the new
     * mode, so switching to a blacklist stops the machine from salvaging matching items.
     */
    public void setFilterMode(FilterMode mode) {
        if (this.filterMode != mode) {
            this.filterMode = mode;
            this.saveChanges();
            this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }
    }

    /** The rarity tiers the filter refers to, as a bit mask (see {@link RarityFilter}). */
    public int getRarityFilter() {
        return this.rarityFilter;
    }

    /** Replaces the rarity selection wholesale; mainly useful for tests and commands. */
    public void setRarityFilter(int mask) {
        int clamped = RarityFilter.clamp(mask);
        if (this.rarityFilter != clamped) {
            this.rarityFilter = clamped;
            this.saveChanges();
            this.getMainNode().ifPresent((grid, node) -> grid.getTickManager().wakeDevice(node));
        }
    }

    /**
     * Ticks or unticks a single rarity tier of the filter. Items already buffered are re-evaluated
     * automatically, because the filter runs again on every salvage attempt.
     */
    public void toggleRarityFilter(int index) {
        setRarityFilter(RarityFilter.toggle(this.rarityFilter, index));
    }

    /**
     * Whether the configured blacklist/whitelist allows the given item to be processed.
     * <p>
     * The filter has two parts: the rarity tiers ticked in the GUI, and the item types in the filter
     * slots. Either part being empty means it is not a restriction, so a machine that only ever used
     * the item list keeps working as before. In whitelist mode an item has to pass both parts, in
     * blacklist mode either part is enough to block it.
     */
    public boolean isAllowedByFilter(ItemStack stack) {
        boolean rarityTicked = matchesRarityFilter(stack);
        boolean itemListed = matchesItemFilter(stack);

        return switch (this.filterMode) {
            case DISABLED -> true;
            case WHITELIST -> (this.rarityFilter == RarityFilter.NONE || rarityTicked)
                    && (isItemFilterEmpty() || itemListed);
            case BLACKLIST -> !rarityTicked && !itemListed;
        };
    }

    /** Whether the stack's rarity is one of the tiers ticked next to the filter label. */
    public boolean matchesRarityFilter(ItemStack stack) {
        if (this.rarityFilter == RarityFilter.NONE) {
            return false;
        }

        var rarity = AffixHelper.getRarity(stack);
        if (!rarity.isBound()) {
            return false;
        }

        return RarityFilter.isTicked(this.rarityFilter, RarityFilter.indexOf(rarity.getId()));
    }

    /** Whether any slot of the filter list holds an entry. */
    public boolean isItemFilterEmpty() {
        for (int slot = 0; slot < this.filter.size(); slot++) {
            if (!this.filter.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesItemFilter(ItemStack stack) {
        for (int slot = 0; slot < this.filter.size(); slot++) {
            var entry = this.filter.getStackInSlot(slot);
            if (!entry.isEmpty() && ItemStack.isSameItem(entry, stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the item is Apotheosis loot of at least {@link #MINIMUM_RARITY} rarity - either affix
     * equipment or a gem. Everything else is rejected by the input buffer and by the filter list alike.
     */
    public static boolean hasRequiredRarity(ItemStack stack) {
        if (!isApotheosisLoot(stack)) {
            return false;
        }

        var rarity = AffixHelper.getRarity(stack);
        if (!rarity.isBound()) {
            return false;
        }

        var minimum = RarityRegistry.INSTANCE.holder(MINIMUM_RARITY);
        return minimum.isBound() && rarity.get().isAtLeast(minimum.get());
    }

    /**
     * Whether the stack is something Apotheosis' salvaging recipes handle at all.
     * <p>
     * Affix equipment carries an affix list; gems carry no affix list but do store a rarity in the
     * same {@code affix_data.rarity} tag (this is exactly what Apotheosis' own {@code GemIngredient}
     * matches on), and salvaging them yields gem dust. Everything else - plain items, and loot whose
     * affix list was emptied - is refused.
     */
    public static boolean isApotheosisLoot(ItemStack stack) {
        return AffixHelper.hasAffixes(stack) || GemItem.getGem(stack).isBound();
    }

    /** Everything the machine accepts: Apotheosis loot of the required rarity that passes the filter mode. */
    public boolean isAccepted(ItemStack stack) {
        return hasRequiredRarity(stack) && isAllowedByFilter(stack);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data); // saves the input inventory
        this.upgrades.writeToNBT(data, "upgrades");
        data.putString("filterMode", this.filterMode.name());
        data.putInt("rarityFilter", this.rarityFilter);

        var outputTag = new CompoundTag();
        for (int i = 0; i < this.output.size(); i++) {
            var stack = this.output.getStackInSlot(i);
            if (!stack.isEmpty()) {
                outputTag.put("item" + i, stack.save(new CompoundTag()));
            }
        }
        data.put("output", outputTag);

        var filterTag = new CompoundTag();
        for (int i = 0; i < this.filter.size(); i++) {
            var stack = this.filter.getStackInSlot(i);
            if (!stack.isEmpty()) {
                filterTag.put("item" + i, stack.save(new CompoundTag()));
            }
        }
        data.put("filter", filterTag);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data); // loads the input inventory
        this.upgrades.readFromNBT(data, "upgrades");
        this.filterMode = FilterMode.byName(data.getString("filterMode"));
        this.rarityFilter = RarityFilter.clamp(data.getInt("rarityFilter"));

        var outputTag = data.getCompound("output");
        for (int i = 0; i < this.output.size(); i++) {
            this.output.setItemDirect(i, ItemStack.of(outputTag.getCompound("item" + i)));
        }

        var filterTag = data.getCompound("filter");
        for (int i = 0; i < this.filter.size(); i++) {
            this.filter.setItemDirect(i, ItemStack.of(filterTag.getCompound("item" + i)));
        }
    }

    @Override
    public void addAdditionalDrops(Level level, net.minecraft.core.BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : this.upgrades) {
            drops.add(upgrade);
        }
        for (var stack : this.output) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
        for (var stack : this.filter) {
            if (!stack.isEmpty()) {
                drops.add(stack);
            }
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        this.upgrades.clear();
        this.output.clear();
        this.filter.clear();
    }

    // ------------------------------------------------------------------
    // Client sync / state
    // ------------------------------------------------------------------

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean wasActive = this.active;
        this.active = data.readBoolean();
        return wasActive != this.active || changed;
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(this.active);
    }

    public boolean isActive() {
        return this.active;
    }

    private void setActive(boolean value) {
        if (this.active != value) {
            this.active = value;
            this.markForUpdate();
        }
    }

    // ------------------------------------------------------------------
    // Ticking
    // ------------------------------------------------------------------

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        int minTickRate = Math.max(1, BASE_TICK_RATE - 3 * this.speedCards());
        return new TickingRequest(minTickRate, minTickRate + 20, !this.hasWork(), false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (this.level == null || this.level.isClientSide()) {
            return TickRateModulation.SLEEP;
        }

        if (!isEnabled()) {
            setActive(false);
            return TickRateModulation.SLEEP;
        }

        var grid = node.getGrid();

        // Whatever is buffered goes into the network first, so the buffer never becomes a bottleneck.
        if (!this.output.isEmpty()) {
            this.pushOutputToNetwork(grid);
            if (!this.output.isEmpty()) {
                // The network cannot accept the results yet - stop instead of voiding items.
                setActive(false);
                return TickRateModulation.IDLE;
            }
        }

        int operations = getOperationsPerCycle();
        boolean didWork = false;
        for (int i = 0; i < operations; i++) {
            if (!this.salvageOne(grid)) {
                break;
            }
            didWork = true;
        }

        setActive(didWork);
        return didWork ? TickRateModulation.FASTER : TickRateModulation.IDLE;
    }

    /**
     * How many items the machine salvages per grid tick. The first salvage card is required for the
     * machine to run at all; every further card lets it process one more item per tick.
     */
    public int getOperationsPerCycle() {
        return Math.max(1, this.salvageCards());
    }

    private boolean hasWork() {
        return isEnabled() && (!this.output.isEmpty() || this.findSalvageableSlot() >= 0);
    }

    private boolean isEnabled() {
        return this.upgrades.isInstalled(ModItems.SALVAGE_CARD.get());
    }

    private int speedCards() {
        return this.upgrades.getInstalledUpgrades(AEItems.SPEED_CARD.asItem());
    }

    private int salvageCards() {
        return this.upgrades.getInstalledUpgrades(ModItems.SALVAGE_CARD.get());
    }

    /**
     * First input slot holding something the machine can actually salvage: accepted by the rarity
     * gate and the filter mode, and matched by one of Apotheosis' salvaging recipes. Loot that passes
     * the gate but has no recipe (ancient gear, for instance - Apotheosis ships no
     * {@code ancient_material} recipe) is skipped rather than stalling everything behind it.
     */
    private int findSalvageableSlot() {
        if (this.level == null) {
            return -1;
        }
        for (int slot = 0; slot < this.input.size(); slot++) {
            var stack = this.input.getStackInSlot(slot);
            if (!isAccepted(stack)) {
                continue;
            }
            if (SalvagingMenu.findMatch(this.level, stack) == null) {
                continue;
            }
            return slot;
        }
        return -1;
    }

    /**
     * Salvages a single item from the input buffer, if there is one and everything fits.
     *
     * @return true if an item was actually salvaged
     */
    private boolean salvageOne(IGrid grid) {
        int slot = this.findSalvageableSlot();
        if (slot < 0 || this.level == null) {
            return false;
        }

        var single = this.input.getStackInSlot(slot).copyWithCount(1);
        var results = SalvagingMenu.salvageItem(this.level, single);
        if (results.isEmpty()) {
            // Only reachable if the recipe manager was reloaded between the probe and here.
            return false;
        }

        // Dry run: never consume the input unless every result fits into the buffer.
        for (var result : results) {
            if (result.isEmpty()) {
                continue;
            }
            if (!this.output.simulateAdd(result.copy()).isEmpty()) {
                return false;
            }
        }

        var energy = grid.getEnergyService();
        if (energy.extractAEPower(POWER_PER_OPERATION, Actionable.SIMULATE,
                PowerMultiplier.CONFIG) < POWER_PER_OPERATION) {
            return false;
        }
        energy.extractAEPower(POWER_PER_OPERATION, Actionable.MODULATE, PowerMultiplier.CONFIG);

        for (var result : results) {
            if (result.isEmpty()) {
                continue;
            }
            var leftover = this.output.addItems(result.copy());
            if (!leftover.isEmpty()) {
                // Should be unreachable thanks to the dry run; never void items if it happens.
                Containers.dropItemStack(this.level, this.worldPosition.getX() + 0.5,
                        this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5, leftover);
            }
        }

        this.input.extractItem(slot, 1, false);
        this.saveChanges();
        return true;
    }

    /** Inserts the contents of the internal output buffer into the ME network. */
    private void pushOutputToNetwork(IGrid grid) {
        var storage = grid.getStorageService().getInventory();

        for (int slot = 0; slot < this.output.size(); slot++) {
            var stack = this.output.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            var key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }

            long inserted = storage.insert(key, stack.getCount(), Actionable.MODULATE, this.actionSource);
            if (inserted <= 0) {
                continue;
            }

            int remaining = (int) (stack.getCount() - inserted);
            this.output.setItemDirect(slot, remaining <= 0 ? ItemStack.EMPTY : stack.copyWithCount(remaining));
            this.saveChanges();
        }
    }

    /**
     * Only Apotheosis loot (affix equipment and gems) that the blacklist/whitelist allows may enter
     * the machine.
     */
    private class SalvageableItemFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return isAccepted(stack);
        }
    }

    /**
     * The filter list only accepts Apotheosis loot, so entries always describe something the machine
     * is actually able to salvage.
     */
    private static class SalvageableOnlyFilter implements IAEItemFilter {
        @Override
        public boolean allowExtract(InternalInventory inv, int slot, int amount) {
            return true;
        }

        @Override
        public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
            return hasRequiredRarity(stack);
        }
    }
}
