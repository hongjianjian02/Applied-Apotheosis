package com.jianjian.appliedapotheosis.dev;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.filter.FilterMode;
import com.jianjian.appliedapotheosis.registry.ModBlockEntities;
import com.jianjian.appliedapotheosis.registry.ModBlocks;
import com.jianjian.appliedapotheosis.registry.ModItems;

import appeng.api.upgrades.Upgrades;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
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
 * ME network in a real server world, feeds the ME Salvager a genuine Apotheosis affix item and
 * verifies that the salvaging results end up in ME network storage. The server then shuts down.
 */
public final class SelfTest {
    public static final String ENABLED_PROPERTY = "applied_apotheosis.selftest";

    private static final int MAX_TICKS = 600;
    private static final int LOG_EVERY = 40;

    private static ServerLevel level;
    private static MeSalvagerBlockEntity salvager;
    private static ChestBlockEntity chest;
    private static ItemStack rolledGear = ItemStack.EMPTY;
    private static int ticks;
    private static int consumedAt = -1;
    private static boolean finished;

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
        log("card slots on the machine = {} salvage / {} speed",
                Upgrades.getMaxInstallable(ModItems.SALVAGE_CARD.get(), ModItems.ME_SALVAGER.get()),
                Upgrades.getMaxInstallable(AEItems.SPEED_CARD, ModItems.ME_SALVAGER.get()));

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

        // --- what exactly counts as mythic equipment? gems have a rarity but no affix list ---
        var gemType = GemRegistry.INSTANCE.getValues().stream().findFirst().orElse(null);
        if (gemType == null) {
            log("no gems registered - skipping the gem check");
        } else {
            var mythicGem = GemRegistry.createGemStack(gemType,
                    RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get());
            log("mythic gem = {} | rarity = {} | hasAffixes = {} | accepted by the machine = {}",
                    mythicGem, AffixHelper.getRarity(mythicGem).getId(), AffixHelper.hasAffixes(mythicGem),
                    MeSalvagerBlockEntity.hasRequiredRarity(mythicGem));
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

        var gearLeftover = salvager.insertForSalvaging(rolledGear.copy());
        log("affix gear accepted? leftover = {} (0 = yes)", gearLeftover.getCount());
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        ticks++;

        if (salvager.getInternalInventory().getStackInSlot(0).isEmpty() && consumedAt < 0) {
            consumedAt = ticks;
            log("gear consumed at tick {} (machine salvaged it)", ticks);
        }

        if (ticks % LOG_EVERY == 0) {
            log("tick {}: node ready={} active={} powered={} | input={} | network=[{}]",
                    ticks, salvager.getMainNode().isReady(), salvager.getMainNode().isActive(),
                    salvager.getMainNode().isPowered(),
                    salvager.getInternalInventory().getStackInSlot(0),
                    networkContents());
        }

        // Give the machine a few more grid ticks to flush its output buffer into the network.
        boolean settled = consumedAt > 0 && ticks >= consumedAt + 80;
        if (!settled && ticks < MAX_TICKS) {
            return;
        }
        finished = true;

        try {
            log("finished after {} ticks (gear consumed at tick {})", ticks, consumedAt);
            log("machine node: ready = {} active = {} powered = {}",
                    salvager.getMainNode().isReady(), salvager.getMainNode().isActive(),
                    salvager.getMainNode().isPowered());

            var input = salvager.getInternalInventory().getStackInSlot(0);
            log("machine input slot now = {} (empty means the gear was consumed)", input);
            log("ME network storage now holds = [{}]", networkContents());

            var mythic = RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get();
            log("expected material = {}", new ItemStack(mythic.getMaterial()));
        } catch (Throwable t) {
            AppliedApotheosis.LOGGER.error("[selftest] verification FAILED", t);
        } finally {
            level.getServer().halt(false);
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
}
