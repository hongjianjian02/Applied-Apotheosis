package com.jianjian.appliedapotheosis;

import com.jianjian.appliedapotheosis.registry.ModBlockEntities;
import com.jianjian.appliedapotheosis.registry.ModBlocks;
import com.jianjian.appliedapotheosis.registry.ModCreativeTabs;
import com.jianjian.appliedapotheosis.registry.ModItems;
import com.jianjian.appliedapotheosis.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Applied Apotheosis - an Applied Energistics 2 addon that salvages Apotheosis (affix) equipment into
 * materials and feeds the results straight back into the ME network.
 */
@Mod(AppliedApotheosis.MODID)
public class AppliedApotheosis {
    public static final String MODID = "applied_apotheosis";
    public static final Logger LOGGER = LogUtils.getLogger();

    // NeoForge injects the mod event bus and this mod's container into the constructor.
    public AppliedApotheosis(IEventBus modBus, ModContainer container) {
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        ModBlockEntities.register(modBus);
        ModMenus.register(modBus);
        ModCreativeTabs.register(modBus);

        // Tunables live in config/applied_apotheosis-common.toml
        container.registerConfig(ModConfig.Type.COMMON, AppliedApotheosisConfig.SPEC);

        modBus.addListener(this::commonSetup);

        if (com.jianjian.appliedapotheosis.dev.SelfTest.isEnabled()) {
            LOGGER.warn("Applied Apotheosis self-test enabled - the server will shut down after verification");
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS
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
        // the (namespace, path) constructor is deprecated in this Forge version
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
