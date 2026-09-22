package com.jianjian.appliedapotheosis.dev;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.block.MeSalvagerBlock;
import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.filter.FilterMode;
import com.jianjian.appliedapotheosis.filter.RarityFilter;
import com.jianjian.appliedapotheosis.menu.MeSalvagerMenu;
import com.jianjian.appliedapotheosis.registry.ModBlockEntities;
import com.jianjian.appliedapotheosis.registry.ModBlocks;
import com.jianjian.appliedapotheosis.registry.ModItems;

import appeng.api.config.Actionable;
import appeng.api.upgrades.Upgrades;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.MachineSource;
import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import dev.shadowsoffire.apotheosis.adventure.affix.salvaging.SalvagingMenu;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Developer self-test. Enabled only with {@code -Dapplied_apotheosis.selftest=true}: it builds a small
 * ME network in a real server world, feeds the ME Salvager genuine Apotheosis loot (an affix item and
 * a gem) and verifies that the salvaging results - the rarity material and the gem dust - end up in ME
 * network storage. The server then shuts down.
 */
public final class SelfTest {
    public static final String ENABLED_PROPERTY = "applied_apotheosis.selftest";

    private static final int MAX_TICKS = 600;
    private static final int LOG_EVERY = 40;

    private static ServerLevel level;
    private static MeSalvagerBlockEntity salvager;
    private static ChestBlockEntity chest;
    private static ItemStack rolledGear = ItemStack.EMPTY;
    private static ItemStack testGem = ItemStack.EMPTY;
    private static int ticks;
    private static int consumedAt = -1;
    private static boolean finished;
    private static int checks;
    private static int failed;

    private SelfTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        try {
            setup();
            MinecraftForge.EVENT_BUS.addListener(SelfTest::onServerTick);
        } catch (Throwable t) {
            AppliedApotheosis.LOGGER.error("[selftest] setup FAILED", t);
            event.getServer().halt(false);
        }
    }

    private static void setup() {
        log("registered item applied_apotheosis:me_salvager = {}",
                ForgeRegistries.ITEMS.getValue(AppliedApotheosis.id("me_salvager")));
        log("registered item applied_apotheosis:salvage_card = {}",
                ForgeRegistries.ITEMS.getValue(AppliedApotheosis.id("salvage_card")));
        log("registered block entity type = {}", ModBlockEntities.ME_SALVAGER.get());
        log("card slots on the machine = {} salvage / {} speed | upgrade slots = {}",
                Upgrades.getMaxInstallable(ModItems.SALVAGE_CARD.get(), ModItems.ME_SALVAGER.get()),
                Upgrades.getMaxInstallable(AEItems.SPEED_CARD, ModItems.ME_SALVAGER.get()),
                MeSalvagerBlockEntity.UPGRADE_SLOTS);

        // --- the mechanic itself, straight through the Apotheosis API ---
        LootRarity mythic = RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get();
        rolledGear = LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD), mythic,
                level.getRandom());
        log("rolled affix gear = {} | hasAffixes = {} | rarity = {}",
                rolledGear, AffixHelper.hasAffixes(rolledGear), AffixHelper.getRarity(rolledGear).getId());
        log("Apotheosis salvaging produced: {}", SalvagingMenu.salvageItem(level, rolledGear));
        log("expected result item = {}", mythic.getMaterial());

        // --- build a tiny ME network: machine + creative energy cell + ME chest with a 1k cell ---
        BlockPos machinePos = level.getSharedSpawnPos().offset(0, 6, 0);
        level.setChunkForced(machinePos.getX() >> 4, machinePos.getZ() >> 4, true);
        level.getChunkAt(machinePos);
        log("test area at {} | chunk loaded = {} | ticking = {}", machinePos,
                level.isLoaded(machinePos), level.shouldTickBlocksAt(machinePos));
        level.setBlockAndUpdate(machinePos, ModBlocks.ME_SALVAGER.get().defaultBlockState());

        BlockPos powerPos = machinePos.east();
        level.setBlockAndUpdate(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());

        BlockPos chestPos = machinePos.south();
        level.setBlockAndUpdate(chestPos, AEBlocks.CHEST.block().defaultBlockState());

        if (!(level.getBlockEntity(machinePos) instanceof MeSalvagerBlockEntity machine)) {
            throw new IllegalStateException("ME Salvager block entity was not created");
        }
        salvager = machine;
        // The test world is reused between runs, so start from a clean machine and empty storage.
        salvager.getUpgrades().clear();
        salvager.getInternalInventory().clear();
        salvager.getFilterInventory().clear();
        salvager.setFilterMode(FilterMode.DISABLED);

        chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
        if (chest == null) {
            throw new IllegalStateException("ME Chest block entity was not created");
        }
        var cellLeftover = chest.getInternalInventory().addItems(AEItems.ITEM_CELL_1K.stack());
        log("inserted 1k storage cell into ME chest, leftover = {}", cellLeftover.getCount());

        // install one salvage card, then feed the machine the affix gear
        var cardLeftover = salvager.getUpgrades().addItems(new ItemStack(ModItems.SALVAGE_CARD.get()));
        log("installed salvage card (leftover {}) -> installed = {}", cardLeftover.getCount(),
                salvager.getUpgrades().getInstalledUpgrades(ModItems.SALVAGE_CARD.get()));
        log("parallelism with 1 salvage card = {} item(s) per tick (expect 1)",
                salvager.getOperationsPerCycle());

        salvager.getUpgrades().addItems(new ItemStack(ModItems.SALVAGE_CARD.get(), 2));
        log("parallelism with 3 salvage cards = {} item(s) per tick (expect 3)",
                salvager.getOperationsPerCycle());

        // the intended maximum build has to fit: 3 salvage + 3 speed in the six upgrade slots
        var speedLeftover = salvager.getUpgrades().addItems(new ItemStack(AEItems.SPEED_CARD, 3));
        var timing = salvager.getTickingRequest(null);
        log("max build: {} salvage + {} speed in {} slot(s), leftover {} -> {} item(s) per {} tick(s) = {} per second",
                salvager.getUpgrades().getInstalledUpgrades(ModItems.SALVAGE_CARD.get()),
                salvager.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD.asItem()),
                salvager.getUpgrades().size(), speedLeftover.getCount(),
                salvager.getOperationsPerCycle(), timing.minTickRate(),
                salvager.getOperationsPerCycle() * 20 / Math.max(1, timing.minTickRate()));

        var junkLeftover = salvager.insertForSalvaging(new ItemStack(Items.DIAMOND, 3));
        log("plain diamonds rejected? leftover = {} (3 = yes)", junkLeftover.getCount());

        // --- rarity gate: every Apotheosis affix item is accepted, from common upwards ---
        var commonGear = LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD),
                RarityRegistry.INSTANCE.holder(Apotheosis.loc("common")).get(), level.getRandom());
        log("lowest-rarity gear = {} | rarity = {} | hasAffixes = {}",
                commonGear, AffixHelper.getRarity(commonGear).getId(), AffixHelper.hasAffixes(commonGear));
        log("common gear into input -> leftover = {} (0 = correctly accepted)",
                salvager.insertForSalvaging(commonGear.copy()).getCount());
        log("common gear into filter list -> valid = {} (true = correctly accepted)",
                salvager.getFilterInventory().isItemValid(0, commonGear.copy()));

        var epicGear = LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD),
                RarityRegistry.INSTANCE.holder(Apotheosis.loc("epic")).get(), level.getRandom());
        log("epic gear = {} | rarity = {} | hasAffixes = {}",
                epicGear, AffixHelper.getRarity(epicGear).getId(), AffixHelper.hasAffixes(epicGear));
        log("epic gear into input -> leftover = {} (0 = correctly accepted)",
                salvager.insertForSalvaging(epicGear.copy()).getCount());

        log("mythic gear into filter list -> valid = {} (true = correctly accepted)",
                salvager.getFilterInventory().isItemValid(0, rolledGear.copy()));
        log("mythic gear into input -> leftover = {} (0 = correctly accepted)",
                salvager.insertForSalvaging(rolledGear.copy()).getCount());
        salvager.getInternalInventory().clear();

        // --- gems count as loot too: no affix list, but a rarity in the same affix_data tag, and
        //     Apotheosis salvages them into gem dust instead of a material ---
        var gemType = GemRegistry.INSTANCE.getValues().stream().findFirst().orElse(null);
        if (gemType == null) {
            log("no gems registered - skipping the gem check");
        } else {
            testGem = GemRegistry.createGemStack(gemType, mythic);
            log("mythic gem = {} | rarity = {} | hasAffixes = {} | isApotheosisLoot = {} | accepted = {}",
                    testGem, AffixHelper.getRarity(testGem).getId(), AffixHelper.hasAffixes(testGem),
                    MeSalvagerBlockEntity.isApotheosisLoot(testGem),
                    MeSalvagerBlockEntity.hasRequiredRarity(testGem));
            log("mythic gem into input -> leftover = {} (0 = correctly accepted)",
                    salvager.insertForSalvaging(testGem.copy()).getCount());
            log("mythic gem into filter list -> valid = {} (true = correctly accepted)",
                    salvager.getFilterInventory().isItemValid(0, testGem.copy()));
            salvager.getInternalInventory().clear();
            log("Apotheosis salvaging produced from the gem: {} (expect gem dust)",
                    SalvagingMenu.salvageItem(level, testGem.copy()));
        }

        // A mythic item whose affix list was emptied must not sneak in either.
        var stripped = rolledGear.copy();
        if (stripped.hasTag()) {
            stripped.getTag().getCompound(AffixHelper.AFFIX_DATA).remove(AffixHelper.AFFIXES);
        }
        log("mythic rarity but empty affix list -> hasAffixes = {} | accepted = {}",
                AffixHelper.hasAffixes(stripped), MeSalvagerBlockEntity.hasRequiredRarity(stripped));

        // --- blacklist / whitelist behaviour ---
        var otherGear = LootController.createLootItem(new ItemStack(Items.DIAMOND_CHESTPLATE),
                RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get(), level.getRandom());
        log("second affix item = {} | hasAffixes = {}", otherGear, AffixHelper.hasAffixes(otherGear));

        salvager.getFilterInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_SWORD));

        salvager.setFilterMode(FilterMode.BLACKLIST);
        log("BLACKLIST + listed sword -> sword leftover = {} (1 = correctly blocked)",
                salvager.insertForSalvaging(rolledGear.copy()).getCount());
        log("BLACKLIST + unlisted chestplate -> leftover = {} (0 = correctly accepted)",
                salvager.insertForSalvaging(otherGear.copy()).getCount());

        salvager.getInternalInventory().clear();

        salvager.setFilterMode(FilterMode.WHITELIST);
        log("WHITELIST + listed sword -> leftover = {} (0 = correctly accepted)",
                salvager.insertForSalvaging(rolledGear.copy()).getCount());
        log("WHITELIST + unlisted chestplate -> leftover = {} (1 = correctly blocked)",
                salvager.insertForSalvaging(otherGear.copy()).getCount());

        salvager.getInternalInventory().clear();
        salvager.setFilterMode(FilterMode.DISABLED);

        // --- rarity filter row: 普通 / 罕见 / 稀有 / 史诗 / 神话 ---
        salvager.getFilterInventory().clear();
        int mythicBit = 1 << RarityFilter.indexOf(Apotheosis.loc("mythic"));

        salvager.setFilterMode(FilterMode.WHITELIST);
        salvager.setRarityFilter(mythicBit);
        log("WHITELIST + only mythic ticked -> mythic gear leftover = {} (0 = correctly accepted)",
                salvager.insertForSalvaging(rolledGear.copy()).getCount());
        log("WHITELIST + only mythic ticked -> epic gear leftover = {} (1 = correctly blocked)",
                salvager.insertForSalvaging(epicGear.copy()).getCount());
        log("WHITELIST + only mythic ticked -> common gear leftover = {} (1 = correctly blocked)",
                salvager.insertForSalvaging(commonGear.copy()).getCount());
        salvager.getInternalInventory().clear();

        salvager.setFilterMode(FilterMode.BLACKLIST);
        log("BLACKLIST + mythic ticked -> mythic gear leftover = {} (1 = correctly blocked)",
                salvager.insertForSalvaging(rolledGear.copy()).getCount());
        log("BLACKLIST + mythic ticked -> common gear leftover = {} (0 = correctly accepted)",
                salvager.insertForSalvaging(commonGear.copy()).getCount());
        salvager.getInternalInventory().clear();

        // Ticking nothing is not a restriction, so old machines keep behaving the same.
        salvager.setRarityFilter(RarityFilter.NONE);
        log("WHITELIST + nothing ticked + empty list -> common gear leftover = {} (0 = no restriction)",
                salvager.insertForSalvaging(commonGear.copy()).getCount());
        salvager.getInternalInventory().clear();

        // --- filter entries are ghost markers: marking with an item never takes the item ---
        var markerSlot = new MeSalvagerMenu.SalvageableFakeSlot(salvager.getFilterInventory(), 0);
        log("filter slot = {} | normal placement allowed = {} (false = can never consume an item)",
                markerSlot.getClass().getSimpleName(), markerSlot.mayPlace(rolledGear.copy()));
        log("marker rules: affix gear = {} (true) | plain diamond = {} (false)",
                markerSlot.canSetFilterTo(rolledGear.copy()),
                markerSlot.canSetFilterTo(new ItemStack(Items.DIAMOND)));

        var carried = rolledGear.copy();
        markerSlot.set(carried);
        log("after marking: filter holds {} (1 x marker) | carried stack is still {} (not consumed)",
                salvager.getFilterInventory().getStackInSlot(0), carried);
        salvager.getFilterInventory().clear();

        log("right-click feeding: affix gear = {} | gem = {} | plain diamond = {} (expect true / true / false)",
                MeSalvagerBlock.feedsOnUse(rolledGear.copy()),
                MeSalvagerBlock.feedsOnUse(testGem.copy()),
                MeSalvagerBlock.feedsOnUse(new ItemStack(Items.DIAMOND)));

        // ------------------------------------------------------------------
        // Assertions. The narrative logs above stay readable for humans; these
        // are the machine-checkable version, so CI fails when a rule breaks.
        // ------------------------------------------------------------------
        expect("upgrade slots fit 3 salvage + 3 speed", MeSalvagerBlockEntity.UPGRADE_SLOTS, 6);
        expect("block has a horizontal facing property",
                ModBlocks.ME_SALVAGER.get().defaultBlockState()
                        .hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING),
                true);
        expect("facing can be changed with a wrench",
                appeng.api.orientation.IOrientationStrategy
                        .get(ModBlocks.ME_SALVAGER.get().defaultBlockState()).allowsPlayerRotation(),
                true);
        expect("salvage card limit", Upgrades.getMaxInstallable(ModItems.SALVAGE_CARD.get(),
                ModItems.ME_SALVAGER.get()), 3);
        expect("speed card limit", Upgrades.getMaxInstallable(AEItems.SPEED_CARD,
                ModItems.ME_SALVAGER.get()), 3);
        expect("parallelism with 3 salvage cards", salvager.getOperationsPerCycle(), 3);
        expect("cycle length with 3 speed cards", salvager.getCycleTicks(), 1);
        expect("three salvage plus three speed all fit", speedLeftover.getCount(), 0);

        expect("affix gear counts as loot", MeSalvagerBlockEntity.isApotheosisLoot(rolledGear.copy()), true);
        expect("affix gear passes the rarity gate", MeSalvagerBlockEntity.hasRequiredRarity(rolledGear.copy()), true);
        expect("common gear passes the gate", MeSalvagerBlockEntity.hasRequiredRarity(commonGear.copy()), true);
        expect("plain diamond is not accepted", MeSalvagerBlockEntity.hasRequiredRarity(new ItemStack(Items.DIAMOND)), false);
        expect("emptied affix list is rejected", MeSalvagerBlockEntity.hasRequiredRarity(stripped.copy()), false);
        expect("affix gear salvages into its rarity material",
                SalvagingMenu.salvageItem(level, rolledGear.copy()).stream().anyMatch(s -> s.is(mythic.getMaterial())),
                true);
        if (!testGem.isEmpty()) {
            expect("gem counts as loot", MeSalvagerBlockEntity.isApotheosisLoot(testGem.copy()), true);
            expect("gem passes the rarity gate", MeSalvagerBlockEntity.hasRequiredRarity(testGem.copy()), true);
            expect("gem salvages into gem dust",
                    SalvagingMenu.salvageItem(level, testGem.copy()).stream()
                            .anyMatch(s -> "apotheosis:gem_dust"
                                    .equals(String.valueOf(ForgeRegistries.ITEMS.getKey(s.getItem())))),
                    true);
        }

        // blacklist / whitelist, re-checked through the machine's own acceptance path
        salvager.getFilterInventory().clear();
        salvager.getInternalInventory().clear();
        salvager.setFilterMode(FilterMode.BLACKLIST);
        salvager.getFilterInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_SWORD));
        expect("blacklist blocks the listed type", salvager.insertForSalvaging(rolledGear.copy()).getCount(), 1);
        expect("blacklist lets another type through", salvager.insertForSalvaging(otherGear.copy()).getCount(), 0);
        salvager.getInternalInventory().clear();

        salvager.setFilterMode(FilterMode.WHITELIST);
        expect("whitelist lets the listed type through", salvager.insertForSalvaging(rolledGear.copy()).getCount(), 0);
        expect("whitelist blocks another type", salvager.insertForSalvaging(otherGear.copy()).getCount(), 1);
        salvager.getInternalInventory().clear();
        salvager.getFilterInventory().clear();

        salvager.setRarityFilter(mythicBit);
        expect("whitelist + only mythic ticked accepts mythic",
                salvager.insertForSalvaging(rolledGear.copy()).getCount(), 0);
        expect("whitelist + only mythic ticked blocks epic",
                salvager.insertForSalvaging(epicGear.copy()).getCount(), 1);
        salvager.getInternalInventory().clear();

        salvager.setFilterMode(FilterMode.BLACKLIST);
        expect("blacklist + mythic ticked blocks mythic",
                salvager.insertForSalvaging(rolledGear.copy()).getCount(), 1);
        expect("blacklist + mythic ticked still accepts common",
                salvager.insertForSalvaging(commonGear.copy()).getCount(), 0);
        salvager.getInternalInventory().clear();

        salvager.setRarityFilter(RarityFilter.NONE);
        salvager.setFilterMode(FilterMode.WHITELIST);
        expect("whitelist with nothing ticked and an empty list is no restriction",
                salvager.insertForSalvaging(commonGear.copy()).getCount(), 0);
        salvager.getInternalInventory().clear();

        expect("filter slot refuses normal placement", markerSlot.mayPlace(rolledGear.copy()), false);
        expect("filter slot accepts loot markers", markerSlot.canSetFilterTo(rolledGear.copy()), true);
        expect("filter slot rejects junk markers", markerSlot.canSetFilterTo(new ItemStack(Items.DIAMOND)), false);
        expect("right-click feeds affix gear", MeSalvagerBlock.feedsOnUse(rolledGear.copy()), true);
        if (!testGem.isEmpty()) {
            expect("right-click feeds gems", MeSalvagerBlock.feedsOnUse(testGem.copy()), true);
        }
        expect("right-click does not feed junk", MeSalvagerBlock.feedsOnUse(new ItemStack(Items.DIAMOND)), false);

        salvager.setFilterMode(FilterMode.DISABLED);
    }

    /** Feeds the queued loot - one affix item and one gem - and reports what the buffer took. */
    private static void feedLoot() {
        var gearLeftover = salvager.insertForSalvaging(rolledGear.copy());
        log("affix gear accepted? leftover = {} (0 = yes)", gearLeftover.getCount());

        if (!testGem.isEmpty()) {
            var gemLeftover = salvager.insertForSalvaging(testGem.copy());
            log("gem accepted? leftover = {} (0 = yes)", gemLeftover.getCount());
        }
        log("input buffer now holds {} item(s) (expect 2: one affix item + one gem)", inputCount());
    }

    /** Number of non-empty slots in the machine's input buffer. */
    private static int inputCount() {
        int count = 0;
        for (int slot = 0; slot < salvager.getInternalInventory().size(); slot++) {
            if (!salvager.getInternalInventory().getStackInSlot(slot).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        ticks++;

        if (ticks == 1) {
            // The storage cell only comes online once the grid has formed, so wipe it on the first
            // tick and feed the loot right after: the totals below are then exactly this run's.
            clearNetworkStorage();
            feedLoot();
        }

        if (inputCount() == 0 && consumedAt < 0) {
            consumedAt = ticks;
            log("everything consumed at tick {} (machine salvaged the queued loot)", ticks);
        }

        if (ticks % LOG_EVERY == 0) {
            log("tick {}: node ready={} active={} powered={} | input={} item(s) | network=[{}]",
                    ticks, salvager.getMainNode().isReady(), salvager.getMainNode().isActive(),
                    salvager.getMainNode().isPowered(), inputCount(),
                    networkContents());
        }

        // Give the machine a few more grid ticks to flush its output buffer into the network.
        boolean settled = consumedAt > 0 && ticks >= consumedAt + 80;
        if (!settled && ticks < MAX_TICKS) {
            return;
        }
        finished = true;

        try {
            log("finished after {} ticks (input emptied at tick {})", ticks, consumedAt);
            log("machine node: ready = {} active = {} powered = {}",
                    salvager.getMainNode().isReady(), salvager.getMainNode().isActive(),
                    salvager.getMainNode().isPowered());

            log("machine input slots now hold {} item(s) (0 means everything was consumed)", inputCount());
            var contents = networkContents();
            log("ME network storage now holds = [{}]", contents);

            var mythic = RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get();
            log("expected material from the affix item = {}", new ItemStack(mythic.getMaterial()));

            expect("input buffer is empty again", inputCount(), 0);
            expect("network holds the salvaged material", contents.contains("mythic_material"), true);
            if (!testGem.isEmpty()) {
                expect("network holds the gem dust", contents.contains("gem_dust"), true);
            }

            log("assertions: {} checked, {} failed", checks, failed);
            if (failed > 0) {
                AppliedApotheosis.LOGGER.error("[selftest] {} of {} assertions FAILED", failed, checks);
                level.getServer().halt(false);
                // Leave with a non-zero status so CI notices a broken rule.
                Runtime.getRuntime().halt(1);
                return;
            }
            log("all assertions passed");
        } catch (Throwable t) {
            AppliedApotheosis.LOGGER.error("[selftest] verification FAILED", t);
        } finally {
            level.getServer().halt(false);
        }
    }

    /** Empties the storage cell, so every run reports exactly the loot it fed in. */
    private static void clearNetworkStorage() {
        var storage = chest.getCellInventory(0);
        if (storage == null) {
            return;
        }
        var source = new MachineSource(chest);
        for (var entry : storage.getAvailableStacks()) {
            storage.extract(entry.getKey(), entry.getLongValue(), Actionable.MODULATE, source);
        }
    }

    /** Renders the contents of the storage cell mounted in the ME chest. */
    private static String networkContents() {
        var storage = chest.getCellInventory(0);
        if (storage == null) {
            return "no cell";
        }
        var contents = new StringBuilder();
        for (var entry : storage.getAvailableStacks()) {
            if (contents.length() > 0) {
                contents.append(", ");
            }
            contents.append(entry.getLongValue()).append(" x ").append(entry.getKey());
        }
        return contents.toString();
    }

    private static void log(String message, Object... args) {
        AppliedApotheosis.LOGGER.info("[selftest] " + message, args);
    }

    /** Records an assertion. The run exits non-zero when any of them does not hold. */
    private static void expect(String what, Object actual, Object expected) {
        checks++;
        boolean ok = java.util.Objects.equals(String.valueOf(actual), String.valueOf(expected));
        if (!ok) {
            failed++;
        }
        log("{} = {} (expected {}){}", what, actual, expected, ok ? "" : "   <-- FAILED");
    }
}
