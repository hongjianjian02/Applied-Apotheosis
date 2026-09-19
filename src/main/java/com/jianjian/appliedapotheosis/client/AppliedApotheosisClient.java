package com.jianjian.appliedapotheosis.client;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.registry.ModMenus;

import appeng.init.client.InitScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AppliedApotheosis.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AppliedApotheosisClient {
    private AppliedApotheosisClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // AE2 loads screen styles from the ae2 namespace, so our style sheet lives in
        // assets/ae2/screens/me_salvager.json of this jar.
        event.enqueueWork(() -> InitScreens.register(ModMenus.ME_SALVAGER.get(), MeSalvagerScreen::new,
                "/screens/me_salvager.json"));

        if (com.jianjian.appliedapotheosis.dev.ClientSelfTest.isEnabled()) {
            AppliedApotheosis.LOGGER.warn("Applied Apotheosis client self-test enabled");
            com.jianjian.appliedapotheosis.dev.ClientSelfTest.register();
        }
    }
}
