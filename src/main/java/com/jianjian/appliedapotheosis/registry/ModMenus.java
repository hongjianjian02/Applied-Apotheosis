package com.jianjian.appliedapotheosis.registry;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.menu.MeSalvagerMenu;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            AppliedApotheosis.MODID);

    public static final RegistryObject<MenuType<MeSalvagerMenu>> ME_SALVAGER = MENUS.register("me_salvager",
            ModMenus::createMeSalvagerMenu);

    private ModMenus() {
    }

    private static MenuType<MeSalvagerMenu> createMeSalvagerMenu() {
        var type = IForgeMenuType.create(ModMenus::createMenuFromNetwork);
        // Lets AE2 route MenuOpener.open(...) to our own open method.
        MenuOpener.addOpener(type, ModMenus::openMenu);
        return type;
    }

    /** Called on the client when the server opens the menu there. */
    private static MeSalvagerMenu createMenuFromNetwork(int containerId, Inventory playerInventory,
            net.minecraft.network.FriendlyByteBuf buffer) {
        var locator = MenuLocators.readFromPacket(buffer);
        var host = locator.locate(playerInventory.player, MeSalvagerBlockEntity.class);
        if (host == null) {
            throw new IllegalStateException("Couldn't find the ME Salvager for an open menu request");
        }
        return new MeSalvagerMenu(containerId, playerInventory, host);
    }

    /** Called on the server to open the menu for a player. */
    private static boolean openMenu(Player player, MenuLocator locator, boolean fromSubMenu) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        var host = locator.locate(player, MeSalvagerBlockEntity.class);
        if (host == null) {
            return false;
        }

        MenuProvider menu = new SimpleMenuProvider(
                (containerId, inventory, p) -> new MeSalvagerMenu(containerId, inventory, host),
                Component.translatable("container.applied_apotheosis.me_salvager"));

        NetworkHooks.openScreen(serverPlayer, menu, buffer -> MenuLocators.writeToPacket(buffer, locator));
        return true;
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
