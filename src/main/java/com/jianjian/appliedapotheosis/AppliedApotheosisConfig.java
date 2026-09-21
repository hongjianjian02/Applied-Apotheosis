package com.jianjian.appliedapotheosis;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Tunables of the ME Salvager, written to {@code config/applied_apotheosis-common.toml}.
 * <p>
 * Read lazily wherever they are used, so a config reload takes effect on the next cycle without
 * restarting the game.
 */
public final class AppliedApotheosisConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue IDLE_POWER;
    public static final ForgeConfigSpec.DoubleValue POWER_PER_OPERATION;
    public static final ForgeConfigSpec.IntValue BASE_TICK_RATE;
    public static final ForgeConfigSpec.IntValue TICKS_PER_SPEED_CARD;
    public static final ForgeConfigSpec.ConfigValue<String> MINIMUM_RARITY;

    static {
        var builder = new ForgeConfigSpec.Builder();

        builder.comment("ME Salvager").push("machine");

        IDLE_POWER = builder
                .comment("AE drawn per tick while the machine is connected to a network, even when idle")
                .defineInRange("idlePower", 1.0, 0.0, 1000.0);

        POWER_PER_OPERATION = builder
                .comment("AE drawn per salvaged item")
                .defineInRange("powerPerOperation", 20.0, 0.0, 100000.0);

        BASE_TICK_RATE = builder
                .comment("Ticks per cycle with no speed cards installed")
                .defineInRange("baseTickRate", 10, 1, 200);

        TICKS_PER_SPEED_CARD = builder
                .comment("Ticks shaved off the cycle by each AE2 speed card")
                .defineInRange("ticksPerSpeedCard", 3, 0, 200);

        MINIMUM_RARITY = builder
                .comment("Lowest Apotheosis rarity the machine works with, e.g. apotheosis:common or apotheosis:mythic")
                .define("minimumRarity", "apotheosis:common");

        builder.pop();
        SPEC = builder.build();
    }

    private AppliedApotheosisConfig() {
    }
}
