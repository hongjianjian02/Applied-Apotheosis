package com.jianjian.appliedapotheosis;

import com.jianjian.appliedapotheosis.registry.ModBlockEntities;
import com.jianjian.appliedapotheosis.registry.ModBlocks;
import com.jianjian.appliedapotheosis.registry.ModCreativeTabs;
import com.jianjian.appliedapotheosis.registry.ModItems;
import com.jianjian.appliedapotheosis.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Applied Apotheosis - an Applied Energistics 2 addon that salvages Apotheosis (affix) equipment into
 * materials and feeds the results straight back into the ME network.
 */
@Mod(AppliedApotheosis.MODID)
public class AppliedApotheosis {
    public static final String MODID = "applied_apotheosis";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AppliedApotheosis(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();

        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);

        modBus.addListener(this::commonSetup);

        if (com.jianjian.appliedapotheosis.dev.SelfTest.isEnabled()) {
            LOGGER.warn("Applied Apotheosis self-test enabled - the server will shut down after verification");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS
                    .addListener(com.jianjian.appliedapotheosis.dev.SelfTest::onServerStarted);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // AE2 collects upgrade card associations lazily, but they must be in place before any
        // machine tries to validate an installed card.
        event.enqueueWork(ModBlocks::registerUpgrades);
        LOGGER.info("Applied Apotheosis loaded");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
