package net.unfamily.reprsbridge.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.unfamily.reprsbridge.block.custom.*;
import net.unfamily.reprsbridge.block.entity.*;
import net.unfamily.reprsbridge.item.ModItems;
import net.unfamily.reprsbridge.RepRSBridge;
import com.buuz135.replication.block.MatterPipeBlock;

import java.util.function.Supplier;

import com.refinedmods.refinedstorage.common.content.BlockConstants;
import com.refinedmods.refinedstorage.common.support.AbstractBaseBlock;
import com.refinedmods.refinedstorage.common.support.AbstractBlockEntityTicker;
import com.refinedmods.refinedstorage.common.support.NetworkNodeBlockItem;
import com.refinedmods.refinedstorage.common.support.network.NetworkNodeBlockEntityTicker;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.unfamily.reprsbridge.block.entity.ModBlockEntities;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntityF;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;

import static com.refinedmods.refinedstorage.common.util.IdentifierUtil.createTranslation;

public class ModBlocks extends AbstractBaseBlock implements EntityBlock {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RepRSBridge.MOD_ID);

    private static final Component HELP = createTranslation("item", "bridge.help");
    private static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final AbstractBlockEntityTicker<RepRSBridgeBlockEntityF> TICKER = new NetworkNodeBlockEntityTicker<>(
        () -> ModBlockEntities.REPRSBRIDGE_F_BE.get(),
             ACTIVE
    );
        

    // Registriamo il blocco RepRSBridgeBl per la connessione con Replication
    public static final DeferredBlock<Block> REPRSBRIDGE = registerBlock("rep_rs_bridge",
            () -> new RepRSBridgeBl(BlockBehaviour.Properties.of()
                .strength(0.3F, 0.3F)  // Very easy to break
                .sound(SoundType.COPPER)  // Copper sound
                .noOcclusion()));      // Maintains noOcclusion property
                

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(String name, DeferredBlock<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        
        // Add our block to the list of blocks the pipe can connect to
        // This is done after registration so the block is available
        registerConnectableBlocks();
    }
    
    /**
     * Register our block in the list of blocks to which Matter Network pipes can connect
     */
    private static void registerConnectableBlocks() {
        // Add our namespace to the list of allowed namespaces
        MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block -> 
            block instanceof RepRSBridgeBl ||
            (block.getClass().getName().contains("reprsbridge"))
        );
    }

    @Override
    protected BlockState getDefaultState() {
        return super.getDefaultState().setValue(ACTIVE, false);
    }

    @Override
    protected void createBlockStateDefinition(final StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(final BlockPos pos, final BlockState state) {
        return new RepRSBridgeBlockEntityF(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(final Level level,
                                                                  final BlockState blockState,
                                                                  final BlockEntityType<T> type) {
        return TICKER.get(level, type);
    }

    public ModBlocks() {
        super(BlockConstants.PROPERTIES);
    }



    public BlockItem createBlockItem() {
        return new NetworkNodeBlockItem(this, HELP);
    }
}
