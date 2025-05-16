package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.common.api.support.network.ConnectionStrategy;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.support.network.InWorldNetworkNodeContainerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Container per il nodo di rete del bridge.
 * Versione migliorata per supportare il merging delle reti.
 */
public class BridgeNetworkNodeContainer extends InWorldNetworkNodeContainerImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeNetworkNodeContainer.class);
    private final BlockEntity blockEntity;
    private final ConnectionStrategy connectionStrategy;
    
    /**
     * Costruttore per BridgeNetworkNodeContainer.
     *
     * @param blockEntity La BlockEntity che contiene questo container
     * @param networkNode Il nodo di rete
     * @param id L'ID del container
     * @param connectionStrategy La strategia di connessione da utilizzare
     */
    public BridgeNetworkNodeContainer(BlockEntity blockEntity, NetworkNode networkNode, String id, ConnectionStrategy connectionStrategy) {
        super(blockEntity, networkNode, id, 0, connectionStrategy, () -> null);
        this.blockEntity = blockEntity;
        this.connectionStrategy = connectionStrategy;
        
        // Invece di override del metodo update(), facciamo l'aggiornamento forzato qui
        forceNeighborUpdates();
    }
    
    /**
     * Metodo che forza l'aggiornamento dei blocchi vicini
     * Chiamato dal costruttore e può essere chiamato manualmente quando necessario
     */
    public void forceNeighborUpdates() {
        if (blockEntity == null || blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide()) {
            return;
        }
        
        BlockPos pos = blockEntity.getBlockPos();
        Level level = blockEntity.getLevel();
        
        LOGGER.debug("BridgeNetworkNodeContainer: Aggiornamento forzato delle connessioni per {}", pos);
            
        // Tenta di trovare cavi RS adiacenti e forza il loro aggiornamento
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            
            // Se il blocco adiacente è un cavo RS, forza un aggiornamento
            if (isRefinedStorageCable(neighborState)) {
                LOGGER.debug("BridgeNetworkNodeContainer: Forzo aggiornamento blocco RS a {}", neighborPos);
                level.neighborChanged(neighborPos, blockEntity.getBlockState().getBlock(), pos);
            }
        }
    }
    
    /**
     * Verifica se un BlockState appartiene a un cavo Refined Storage
     */
    private boolean isRefinedStorageCable(BlockState state) {
        String blockClass = state.getBlock().getClass().getName().toLowerCase();
        return blockClass.contains("refinedstorage") && 
               (blockClass.contains("cable") || blockClass.equals("cableblock"));
    }
}