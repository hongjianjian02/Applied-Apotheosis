package com.jianjian.appliedapotheosis.registry;

import java.util.concurrent.atomic.AtomicReference;

import com.jianjian.appliedapotheosis.AppliedApotheosis;
import com.jianjian.appliedapotheosis.block.MeSalvagerBlock;
import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import appeng.blockentity.AEBaseBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
            .create(Registries.BLOCK_ENTITY_TYPE, AppliedApotheosis.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeSalvagerBlockEntity>> ME_SALVAGER = BLOCK_ENTITY_TYPES
            .register("me_salvager", ModBlockEntities::createMeSalvagerType);

    private ModBlockEntities() {
    }

    private static BlockEntityType<MeSalvagerBlockEntity> createMeSalvagerType() {
        // The block entity needs its own type during construction, so hand it over through a holder.
        var typeHolder = new AtomicReference<BlockEntityType<MeSalvagerBlockEntity>>();

        var type = BlockEntityType.Builder.of(
                (pos, state) -> new MeSalvagerBlockEntity(typeHolder.get(), pos, state),
                ModBlocks.ME_SALVAGER.get()).build(null);
        typeHolder.set(type);

        // Lets AE2 (and this mod) resolve the block entity back to the item that represents it.
        AEBaseBlockEntity.registerBlockEntityItem(type, ModItems.ME_SALVAGER.get());

        // AE2 machines wire themselves to their block manually; a null ticker is correct here because
        // the ME Salvager is driven by the grid tick manager instead of a vanilla block ticker.
        var block = (MeSalvagerBlock) ModBlocks.ME_SALVAGER.get();
        block.setBlockEntity(MeSalvagerBlockEntity.class, type, null, null);

        return type;
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITY_TYPES.register(modBus);
    }
}
