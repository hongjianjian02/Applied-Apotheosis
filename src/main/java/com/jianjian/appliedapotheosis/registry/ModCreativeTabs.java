package com.jianjian.appliedapotheosis.registry;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, AppliedApotheosis.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.applied_apotheosis"))
                    .icon(() -> new ItemStack(ModItems.SALVAGE_CARD.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.ME_SALVAGER.get());
                        output.accept(ModItems.SALVAGE_CARD.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        CREATIVE_TABS.register(modBus);
    }
}
