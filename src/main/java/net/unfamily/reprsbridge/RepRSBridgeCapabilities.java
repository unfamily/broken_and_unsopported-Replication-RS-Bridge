package net.unfamily.reprsbridge;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.unfamily.reprsbridge.block.ModBlocks;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntity;


/**
 * Classe per gestire la registrazione delle capabilities del bridge
 */
public class RepRSBridgeCapabilities {

    /**
     * Registra le capabilities del bridge
     * @param event L'evento di registrazione delle capabilities
     */
    public static void register(RegisterCapabilitiesEvent event) {
        // Registra la capability ItemHandler per il bridge
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, blockPos, blockState, blockEntity, direction) -> {
            if (blockEntity instanceof RepRSBridgeBlockEntity bridge && (direction == Direction.UP || direction == null)) {
                return bridge.getOutput();
            }
            return null;
        }, ModBlocks.REPRSBRIDGE.get());
    }
}
