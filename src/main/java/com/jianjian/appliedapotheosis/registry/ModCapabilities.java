package com.jianjian.appliedapotheosis.registry;

import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;

import appeng.api.AECapabilities;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * NeoForge capabilities for the ME Salvager.
 * <p>
 * AE2 19 turned grid membership and inventory access into block capabilities and registers them only
 * for its own block entity types, so an addon has to register its own machine - without the grid
 * host capability the machine never connects to neighbouring cables or machines, which leaves it in
 * a grid of its own with no power.
 */
public final class ModCapabilities {
    private ModCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        var type = ModBlockEntities.ME_SALVAGER.get();

        // Lets neighbouring AE2 blocks find this machine's grid node.
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, type, (be, side) -> be);

        // Lets pipes, hoppers and AE2 buses push items into the input buffer.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type,
                (MeSalvagerBlockEntity be, net.minecraft.core.Direction side) -> {
                    var inventory = be.getExposedInventoryForSide(side);
                    return inventory == null ? null : inventory.toItemHandler();
                });
    }
}
