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
import net.unfamily.reprsbridge.item.ModItems;
import net.unfamily.reprsbridge.RepRSBridge;
import com.buuz135.replication.block.MatterPipeBlock;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(RepRSBridge.MOD_ID);

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
}
