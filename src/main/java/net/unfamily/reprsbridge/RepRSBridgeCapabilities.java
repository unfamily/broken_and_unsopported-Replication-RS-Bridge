package net.unfamily.reprsbridge;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.unfamily.reprsbridge.block.ModBlocks;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntityP;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntityF;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;


/**
 * Classe per gestire la registrazione delle capabilities del bridge
 */
public class RepRSBridgeCapabilities {

    // Definisci la capability per NetworkNode
    private static final BlockCapability<NetworkNode, Void> NETWORK_NODE =
        BlockCapability.createVoid(ResourceLocation.parse("refinedstorage:network_node"), NetworkNode.class);

    /**
     * Registra le capabilities del bridge
     * @param event L'evento di registrazione delle capabilities
     */
    public static void register(RegisterCapabilitiesEvent event) {
        // Registra la capability ItemHandler per il bridge (Replication)
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, blockPos, blockState, blockEntity, direction) -> {
            if (blockEntity instanceof RepRSBridgeBlockEntityP bridge && (direction == Direction.UP || direction == null)) {
                return bridge.getOutput();
            }
            return null;
        }, ModBlocks.REPRSBRIDGE.get());
        
        // Registra la capability NetworkNode per il bridge (Refined Storage)
        event.registerBlock(
            NETWORK_NODE,
            (level, pos, state, be, context) -> {
                if (be instanceof RepRSBridgeBlockEntityF rsEntity) {
                    //return rsEntity.getNetworkNode();
                    return null;
                }
                return null;
            },
            ModBlocks.REPRSBRIDGE.get()
        );
    }
}
