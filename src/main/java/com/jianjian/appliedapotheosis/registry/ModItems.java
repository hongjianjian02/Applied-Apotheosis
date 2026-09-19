package com.jianjian.appliedapotheosis.registry;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.item.SalvageCardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            AppliedApotheosis.MODID);

    /** The upgrade card that enables the ME Salvager. */
    public static final RegistryObject<Item> SALVAGE_CARD = ITEMS.register("salvage_card",
            () -> new SalvageCardItem(new Item.Properties()));

    public static final RegistryObject<Item> ME_SALVAGER = ITEMS.register("me_salvager",
            () -> new BlockItem(ModBlocks.ME_SALVAGER.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
