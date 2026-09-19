package com.jianjian.appliedapotheosis.registry;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.block.MeSalvagerBlock;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            AppliedApotheosis.MODID);

    public static final RegistryObject<Block> ME_SALVAGER = BLOCKS.register("me_salvager", MeSalvagerBlock::new);

    /** Maximum number of each supported card a single ME Salvager accepts. */
    public static final int MAX_SALVAGE_CARDS = 3;
    public static final int MAX_SPEED_CARDS = 3;

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }

    /**
     * Tells AE2 which upgrade cards the ME Salvager accepts. Without this, AE2 rejects every card
     * (including ours) when a player tries to install one.
     */
    public static void registerUpgrades() {
        Upgrades.add(ModItems.SALVAGE_CARD.get(), ModItems.ME_SALVAGER.get(), MAX_SALVAGE_CARDS);
        Upgrades.add(AEItems.SPEED_CARD.asItem(), ModItems.ME_SALVAGER.get(), MAX_SPEED_CARDS);
    }
}
