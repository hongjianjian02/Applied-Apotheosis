package com.jianjian.appliedapotheosis.dev;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.filter.FilterMode;
import com.jianjian.appliedapotheosis.filter.RarityFilter;
import com.jianjian.appliedapotheosis.registry.ModBlocks;
import com.jianjian.appliedapotheosis.registry.ModItems;
import com.jianjian.appliedapotheosis.registry.ModMenus;

import appeng.blockentity.storage.ChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import dev.shadowsoffire.apotheosis.Apotheosis;
import dev.shadowsoffire.apotheosis.adventure.loot.LootController;
import dev.shadowsoffire.apotheosis.adventure.loot.RarityRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

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
        MinecraftForge.EVENT_BUS.addListener(ClientSelfTest::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
            return;
        }

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
                level.setBlockAndUpdate(machinePos.south(), AEBlocks.CHEST.block().defaultBlockState());

                var machine = (MeSalvagerBlockEntity) level.getBlockEntity(machinePos);
                var chest = (ChestBlockEntity) level.getBlockEntity(machinePos.south());
                chest.getInternalInventory().addItems(AEItems.ITEM_CELL_1K.stack());
                machine.getUpgrades().addItems(new ItemStack(ModItems.SALVAGE_CARD.get()));

                // Fill the filter list (blacklist entries) and the input buffer so the GUI has content.
                // Anything the machine accepts may go in either place; these are rolled at mythic.
                var mythic = RarityRegistry.INSTANCE.holder(Apotheosis.loc("mythic")).get();
                var sword = LootController.createLootItem(new ItemStack(Items.DIAMOND_SWORD), mythic,
                        level.getRandom());
                var chestplate = LootController.createLootItem(new ItemStack(Items.DIAMOND_CHESTPLATE),
                        mythic, level.getRandom());
                var helmet = LootController.createLootItem(new ItemStack(Items.GOLDEN_HELMET), mythic,
                        level.getRandom());
                var bow = LootController.createLootItem(new ItemStack(Items.BOW), mythic,
                        level.getRandom());

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

                log("world prepared at {}, opening GUI", machinePos);
                log("menu type = {} | block entity = {}",
                        net.minecraftforge.registries.ForgeRegistries.MENU_TYPES
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

    private static void log(String message, Object... args) {
        AppliedApotheosis.LOGGER.info("[clientselftest] " + message, args);
    }
}
