package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.common.api.support.network.ConnectionSink;
import com.refinedmods.refinedstorage.common.support.network.ColoredConnectionStrategy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.reprsbridge.block.ModBlocks;
import net.unfamily.reprsbridge.block.custom.RepRSBridgeBl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Strategia di connessione per il bridge che consente la connessione visiva con i cavi di Refined Storage.
 * Implementazione modificata per consentire il merging delle reti attraverso il bridge.
 */
public class BridgeConnectionStrategy extends ColoredConnectionStrategy {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeConnectionStrategy.class);
    private final BlockPos originPos;
    private Supplier<Level> levelSupplier;
    
    // Memorizza le connessioni trovate per migliorare l'efficienza
    private final Map<Direction, List<BlockPos>> foundRSCables = new HashMap<>();
    
    public BridgeConnectionStrategy(final Supplier<BlockState> blockStateProvider, final BlockPos origin) {
        super(blockStateProvider, origin);
        this.originPos = origin;
    }
    
    /**
     * Imposta il supplier per ottenere il level, necessario per trovare cavi RS collegati
     */
    public void setLevelSupplier(Supplier<Level> levelSupplier) {
        this.levelSupplier = levelSupplier;
    }

    @Override
    public void addOutgoingConnections(final ConnectionSink sink) {
        if (levelSupplier == null || levelSupplier.get() == null) {
            LOGGER.warn("Level supplier non disponibile, impossibile creare through connection");
            return;
        }
        
        Level level = levelSupplier.get();
        
        // Prima, individua tutti i cavi RS collegati
        scanForRSCables(level);
        
        // Se ci sono almeno due direzioni con cavi RS, crea connessioni dirette tra tutti
        if (hasMultipleRSConnections()) {
            LOGGER.debug("Trovate multiple connessioni RS, creo rete condivisa");
            createCrossDirectionalConnections(sink);
        }
        
        // Aggiungiamo anche connessioni dirette in tutte le direzioni
        for (final Direction direction : Direction.values()) {
            sink.tryConnectInSameDimension(origin.relative(direction), direction.getOpposite());
        }
    }
    
    /**
     * Scansiona tutte le direzioni e memorizza le posizioni dei cavi RS collegati
     */
    private void scanForRSCables(Level level) {
        foundRSCables.clear();
        
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = origin.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            
            if (isRefinedStorageCable(neighborState)) {
                LOGGER.debug("Cavo RS trovato nella direzione {} alla posizione {}", direction, neighborPos);
                foundRSCables.computeIfAbsent(direction, k -> new ArrayList<>()).add(neighborPos);
            }
        }
    }
    
    /**
     * Verifica se ci sono connessioni RS in più direzioni
     */
    private boolean hasMultipleRSConnections() {
        return foundRSCables.size() >= 2;
    }
    
    /**
     * Crea connessioni incrociate tra tutti i cavi RS trovati
     */
    private void createCrossDirectionalConnections(ConnectionSink sink) {
        // Per ogni direzione con cavi RS...
        for (Map.Entry<Direction, List<BlockPos>> entry : foundRSCables.entrySet()) {
            Direction direction = entry.getKey();
            List<BlockPos> cables = entry.getValue();
            
            // ...connetti a tutti gli altri cavi RS in altre direzioni
            for (Map.Entry<Direction, List<BlockPos>> otherEntry : foundRSCables.entrySet()) {
                Direction otherDirection = otherEntry.getKey();
                List<BlockPos> otherCables = otherEntry.getValue();
                
                // Non connettere una direzione a sé stessa
                if (direction == otherDirection) {
                    continue;
                }
                
                LOGGER.debug("Creazione di connessioni incrociate tra direzione {} e {}", direction, otherDirection);
                
                // Connetti ogni cavo in questa direzione a ogni cavo in altra direzione
                for (BlockPos cable : cables) {
                    for (BlockPos otherCable : otherCables) {
                        // Crea una connessione diretta in entrambi i versi
                        LOGGER.debug("  -> Connessione diretta tra {} e {}", cable, otherCable);
                        
                        // Connessione da cable a otherCable
                        sink.tryConnectInSameDimension(otherCable, direction);
                        
                        // Connessione inversa da otherCable a cable
                        sink.tryConnectInSameDimension(cable, otherDirection);
                        
                        // Collega anche al bridge stesso (per sicurezza)
                        sink.tryConnectInSameDimension(origin, direction.getOpposite());
                        sink.tryConnectInSameDimension(origin, otherDirection.getOpposite());
                    }
                }
            }
        }
    }

    @Override
    public boolean canAcceptIncomingConnection(final Direction incomingDirection, final BlockState connectingState) {
        // Verifica se il blocco che si sta connettendo è un cavo di Refined Storage
        boolean isRefinedStorageCable = isRefinedStorageCable(connectingState);
        
        // Se è un cavo RS, accetta sempre la connessione
        if (isRefinedStorageCable) {
            LOGGER.debug("Connessione RS accettata dalla direzione {}", incomingDirection);
            
            // Forza anche la scansione per altre connessioni RS
            if (levelSupplier != null && levelSupplier.get() != null) {
                Level level = levelSupplier.get();
                
                // Aggiungiamo il nuovo cavo alla mappa delle connessioni
                BlockPos newCablePos = origin.relative(incomingDirection);
                foundRSCables.computeIfAbsent(incomingDirection, k -> new ArrayList<>()).add(newCablePos);
                
                // Ri-scansiona per altre connessioni
                scanForRSCables(level);
            }
            
            return true;
        }
        
        // Se è uno dei nostri blocchi bridge, accetta sempre la connessione
        if (connectingState.getBlock() instanceof RepRSBridgeBl) {
            return true;
        }
        
        // Altrimenti verifica i colori come prima
        if (!colorsAllowConnecting(connectingState)) {
            return false;
        }
        
        // Accettiamo connessioni da qualsiasi direzione
        return true;
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