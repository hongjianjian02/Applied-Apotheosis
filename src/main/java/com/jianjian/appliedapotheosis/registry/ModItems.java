package com.jianjian.appliedapotheosis.registry;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.item.SalvageCardItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM,
            AppliedApotheosis.MODID);

    /** The upgrade card that enables the ME Salvager. */
    public static final DeferredHolder<Item, Item> SALVAGE_CARD = ITEMS.register("salvage_card",
            () -> new SalvageCardItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ME_SALVAGER = ITEMS.register("me_salvager",
            () -> new BlockItem(ModBlocks.ME_SALVAGER.get(), new Item.Properties()));

    private ModItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
