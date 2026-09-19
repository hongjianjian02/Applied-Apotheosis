package com.jianjian.appliedapotheosis.block;

import org.jetbrains.annotations.Nullable;

import com.jianjian.appliedapotheosis.blockentity.MeSalvagerBlockEntity;
import com.jianjian.appliedapotheosis.registry.ModMenus;

import appeng.block.AEBaseEntityBlock;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.InteractionUtil;
import dev.shadowsoffire.apotheosis.adventure.affix.AffixHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * ME Salvager: accepts Apotheosis affix equipment, salvages it with Apotheosis' own salvaging
 * rules and pushes the resulting materials into the connected ME network.
 */
public class MeSalvagerBlock extends AEBaseEntityBlock<MeSalvagerBlockEntity> {
    /** Whether the machine is currently salvaging, used to switch the model texture. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MeSalvagerBlock() {
        super(metalProps().strength(3.5F));
        this.registerDefaultState(this.defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    protected BlockState updateBlockStateFromBlockEntity(BlockState currentState, MeSalvagerBlockEntity be) {
        return currentState.setValue(ACTIVE, be.isActive());
    }

    @Override
    public InteractionResult onActivated(Level level, BlockPos pos, Player player, InteractionHand hand,
            @Nullable ItemStack heldItem, BlockHitResult hit) {
        // In wrench/alternate use mode AE2 tools (wrench, upgrade cards, memory card) handle the
        // interaction themselves.
        if (InteractionUtil.isInAlternateUseMode(player)) {
            return InteractionResult.PASS;
        }

        var blockEntity = this.getBlockEntity(level, pos);
        if (blockEntity == null) {
            return InteractionResult.PASS;
        }

        // Right-clicking with Apotheosis equipment feeds it straight into the machine.
        if (heldItem != null && !heldItem.isEmpty() && AffixHelper.hasAffixes(heldItem)) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            var leftover = blockEntity.insertForSalvaging(heldItem);
            if (leftover.getCount() == heldItem.getCount()) {
                // Nothing was accepted (buffer full or filtered out) - fall through to the GUI.
                openMenu(level, player, blockEntity);
                return InteractionResult.sidedSuccess(false);
            }

            player.setItemInHand(hand, leftover);
            return InteractionResult.sidedSuccess(false);
        }

        openMenu(level, player, blockEntity);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static void openMenu(Level level, Player player, MeSalvagerBlockEntity blockEntity) {
        if (!level.isClientSide()) {
            MenuOpener.open(ModMenus.ME_SALVAGER.get(), player, MenuLocators.forBlockEntity(blockEntity));
        }
    }
}
