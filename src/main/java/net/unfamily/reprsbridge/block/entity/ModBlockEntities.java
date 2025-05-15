package net.unfamily.reprsbridge.block.entity;

import net.unfamily.reprsbridge.RepRSBridge;
import net.unfamily.reprsbridge.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RepRSBridge.MOD_ID);

    public static final Supplier<BlockEntityType<RepRSBridgeBlockEntity>> REPRSBRIDGE_BE =
            BLOCK_ENTITIES.register("bridge_be", () -> BlockEntityType.Builder.of(
                    RepRSBridgeBlockEntity::new, ModBlocks.REPRSBRIDGE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
