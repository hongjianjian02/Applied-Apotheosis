package com.jianjian.appliedapotheosis.client;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.registry.ModMenus;

import appeng.init.client.InitScreens;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = AppliedApotheosis.MODID, value = Dist.CLIENT)
public final class AppliedApotheosisClient {
    private AppliedApotheosisClient() {
    }

    /**
     * AE2 19 registers screens from NeoForge's menu screen event, and loads screen styles from the ae2
     * namespace - so our style sheet lives in assets/ae2/screens/me_salvager.json of this jar.
     */
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        InitScreens.register(event, ModMenus.ME_SALVAGER.get(), MeSalvagerScreen::new,
                "/screens/me_salvager.json");
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (com.jianjian.appliedapotheosis.dev.ClientSelfTest.isEnabled()) {
            AppliedApotheosis.LOGGER.warn("Applied Apotheosis client self-test enabled");
            com.jianjian.appliedapotheosis.dev.ClientSelfTest.register();
        }
    }
}
