package com.jianjian.appliedapotheosis.dev;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.filter.FilterMode;
import com.jianjian.appliedapotheosis.filter.RarityFilter;
import com.jianjian.appliedapotheosis.menu.MeSalvagerMenu;
import com.jianjian.appliedapotheosis.registry.ModBlocks;
import com.jianjian.appliedapotheosis.registry.ModItems;
import com.jianjian.appliedapotheosis.registry.ModMenus;

import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.SlotSemantics;
import appeng.menu.locator.MenuLocators;
import dev.shadowsoffire.apotheosis.tiers.GenContext;
import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.loot.LootController;
import dev.shadowsoffire.apotheosis.loot.RarityRegistry;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Client-side developer self-test, enabled with {@code -Dapplied_apotheosis.clientselftest=true}.
 * <p>
 * It builds a small ME network in the (quick-play) single player world, opens the ME Salvager GUI so
 * that the screen style sheet and widgets are really instantiated, and then takes a screenshot of it.
 */
public final class ClientSelfTest {
    public static final String ENABLED_PROPERTY = "applied_apotheosis.clientselftest";

    private static int ticks;
    private static int stage;
    private static boolean done;

    private ClientSelfTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ClientSelfTest::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (done) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
            return;
        }

        // The dev window is usually unfocused, and pausing on lost focus closes the screen we are
        // trying to screenshot.
        minecraft.options.pauseOnLostFocus = false;

        ticks++;
        switch (stage) {
            case 0 -> {
                if (ticks > 40) {
                    stage = 1;
                    ticks = 0;
                    prepareAndOpenGui(minecraft);
                }
            }
            case 1 -> {
                if (ticks == 20) {
                    logGuiLayout(minecraft);
                }
                if (ticks == 30) {
                    minecraft.getSingleplayerServer().execute(ClientSelfTest::simulateSlotClicks);
                }
                if (ticks % 20 == 0) {
                    log("tick {}: current screen = {}", ticks, minecraft.screen);
                }
                if (ticks > 80) {
                    stage = 2;
                    ticks = 0;
                    log("open screen is {}", minecraft.screen);
                    Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(),
                            message -> log("screenshot: {}", message.getString()));
                }
            }
            default -> {
                if (ticks > 20) {
                    done = true;
                    log("finished, screen still open = {}", minecraft.screen);
                }
            }
        }
    }

    private static void prepareAndOpenGui(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        server.execute(() -> {
            try {
                var player = server.getPlayerList().getPlayers().get(0);
                var level = player.serverLevel();

                // Same layout as the dedicated server self-test: machine + power + ME chest with a cell
                BlockPos machinePos = level.getSharedSpawnPos().offset(0, 6, 0);
                level.setChunkForced(machinePos.getX() >> 4, machinePos.getZ() >> 4, true);
                level.setBlockAndUpdate(machinePos, ModBlocks.ME_SALVAGER.get().defaultBlockState());
                level.setBlockAndUpdate(machinePos.east(),
                        AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                level.setBlockAndUpdate(machinePos.south(), AEBlocks.ME_CHEST.block().defaultBlockState());

                var machine = (MeSalvagerBlockEntity) level.getBlockEntity(machinePos);
                var chest = (MEChestBlockEntity) level.getBlockEntity(machinePos.south());
                chest.getInternalInventory().addItems(AEItems.ITEM_CELL_1K.stack());
                machine.getUpgrades().addItems(new ItemStack(ModItems.SALVAGE_CARD.get()));

                // Fill the filter list (blacklist entries) and the input buffer so the GUI has content.
                // Anything the machine accepts may go in either place; these are rolled at mythic.
                var mythic = RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get();
                var sword = LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD), mythic,
                        GenContext.dummy(level.getRandom()));
                var chestplate = LootController.createLootItem(new ItemStack(Items.DIAMOND_CHESTPLATE),
                        mythic, GenContext.dummy(level.getRandom()));
                var helmet = LootController.createLootItem(new ItemStack(Items.GOLDEN_HELMET), mythic,
                        GenContext.dummy(level.getRandom()));
                var bow = LootController.createLootItem(new ItemStack(Items.BOW), mythic,
                        GenContext.dummy(level.getRandom()));

                machine.setFilterMode(FilterMode.BLACKLIST);
                machine.getFilterInventory().setItemDirect(0, sword.copyWithCount(1));
                machine.getFilterInventory().setItemDirect(1, helmet.copyWithCount(1));
                machine.getFilterInventory().setItemDirect(2, bow.copyWithCount(1));
                machine.insertForSalvaging(chestplate);
                // Tick a few rarity chips so the screenshot shows both states of the row.
                machine.setRarityFilter(1 << RarityFilter.indexOf(Apotheosis.loc("rare"))
                        | 1 << RarityFilter.indexOf(Apotheosis.loc("epic"))
                        | 1 << RarityFilter.indexOf(Apotheosis.loc("mythic")));
                log("filter list holds {} / {} / {}",
                        machine.getFilterInventory().getStackInSlot(0),
                        machine.getFilterInventory().getStackInSlot(1),
                        machine.getFilterInventory().getStackInSlot(2));
                log("rarity filter mask = 0b{}", Integer.toBinaryString(machine.getRarityFilter()));

                // Put the machine in the hotbar so the screenshot also shows the block's item form.
                player.getInventory().setItem(0, new ItemStack(ModItems.ME_SALVAGER.get()));

                log("world prepared at {}, opening GUI", machinePos);
                log("menu type = {} | block entity = {}",
                        net.minecraft.core.registries.BuiltInRegistries.MENU
                                .getKey(ModMenus.ME_SALVAGER.get()),
                        machine);
                boolean opened = MenuOpener.open(ModMenus.ME_SALVAGER.get(), player,
                        MenuLocators.forBlockEntity(machine));
                log("MenuOpener.open returned {}", opened);
            } catch (Throwable t) {
                AppliedApotheosis.LOGGER.error("[clientselftest] preparing the world failed", t);
            }
        });
    }

    /**
     * Server side: drive Minecraft's own click path into the "to salvage" slots, the same way a
     * player dropping items in would. This is what tells us whether the buffer really refuses items.
     */
    private static void simulateSlotClicks() {
        try {
            var server = Minecraft.getInstance().getSingleplayerServer();
            var player = server.getPlayerList().getPlayers().get(0);
            if (!(player.containerMenu instanceof MeSalvagerMenu menu)) {
                log("click test skipped, menu is {}", player.containerMenu);
                return;
            }

            var host = (MeSalvagerBlockEntity) menu.getHost();
            var buffer = host.getInternalInventory();
            var slot = menu.getSlots(SlotSemantics.MACHINE_INPUT).get(0);

            // The GUI was prepared with a blacklist holding a diamond sword; clear it so the plain
            // case (no filtering) is what gets tested first. Also empty the buffer so the clicks
            // below are not swaps.
            host.getFilterInventory().clear();
            host.getInternalInventory().clear();
            host.setFilterMode(FilterMode.DISABLED);
            host.setRarityFilter(RarityFilter.NONE);

            var mythic = RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get();
            var loot = LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD), mythic,
                    GenContext.dummy(server.overworld().getRandom()));

            menu.setCarried(loot.copy());
            menu.clicked(slot.index, 0, ClickType.PICKUP, player);
            log("click with affix gear, no filter: buffer slot 0 = {} | carried left = {} (want the sword in the buffer)",
                    buffer.getStackInSlot(0), menu.getCarried());

            menu.setCarried(new ItemStack(Items.DIAMOND, 4));
            menu.clicked(slot.index + 1, 0, ClickType.PICKUP, player);
            log("click with plain diamonds: buffer slot 1 = {} (must stay empty) | carried left = {} (must stay 4)",
                    buffer.getStackInSlot(1), menu.getCarried());

            // now list that sword in a blacklist and click again: the machine must refuse it
            host.getInternalInventory().clear();
            host.getFilterInventory().setItemDirect(0, new ItemStack(Items.DIAMOND_SWORD));
            host.setFilterMode(FilterMode.BLACKLIST);
            menu.setCarried(loot.copy());
            menu.clicked(slot.index, 0, ClickType.PICKUP, player);
            log("click with a blacklisted sword: buffer slot 0 = {} (must stay empty) | carried left = {}",
                    buffer.getStackInSlot(0), menu.getCarried());

            host.getInternalInventory().clear();
            host.getFilterInventory().clear();
            host.setFilterMode(FilterMode.DISABLED);
            menu.setCarried(ItemStack.EMPTY);
            log("input click test finished");
        } catch (Throwable t) {
            AppliedApotheosis.LOGGER.error("[clientselftest] slot click test failed", t);
        }
    }

    private static void log(String message, Object... args) {
        AppliedApotheosis.LOGGER.info("[clientselftest] " + message, args);
    }

    /** Reports where AE2 ended up placing the slots and widgets, to check the layout from the log. */
    private static void logGuiLayout(Minecraft minecraft) {
        if (!(minecraft.player.containerMenu instanceof MeSalvagerMenu menu)) {
            // A screen style that fails to load leaves the client without our screen - make that loud.
            AppliedApotheosis.LOGGER.error("[clientselftest] GUI did NOT open, menu is {} and screen is {}",
                    minecraft.player.containerMenu, minecraft.screen);
            return;
        }

        var upgrades = menu.getSlots(SlotSemantics.UPGRADE);
        log("GUI layout: {} upgrade slot(s), first at {} / {} | screen = {} x {}",
                upgrades.size(), upgrades.get(0).x, upgrades.get(0).y,
                minecraft.screen.width, minecraft.screen.height);
        log("GUI layout: filter slots at {} / {}",
                menu.getSlots(SlotSemantics.CONFIG).get(0).x,
                menu.getSlots(SlotSemantics.CONFIG).get(0).y);
    }
}
