package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import com.refinedmods.refinedstorage.common.api.support.resource.ResourceContainer;
import com.refinedmods.refinedstorage.common.content.BlockEntities;
import com.refinedmods.refinedstorage.common.content.ContentNames;
import com.refinedmods.refinedstorage.common.content.Items;
import com.refinedmods.refinedstorage.common.support.BlockEntityWithDrops;
import com.refinedmods.refinedstorage.common.support.FilterWithFuzzyMode;
import com.refinedmods.refinedstorage.common.support.containermenu.NetworkNodeExtendedMenuProvider;
import com.refinedmods.refinedstorage.common.support.exportingindicator.ExportingIndicator;
import com.refinedmods.refinedstorage.common.support.exportingindicator.ExportingIndicators;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.support.resource.ResourceContainerData;
import com.refinedmods.refinedstorage.common.support.resource.ResourceContainerImpl;
import com.refinedmods.refinedstorage.common.upgrade.UpgradeContainer;
import com.refinedmods.refinedstorage.common.upgrade.UpgradeDestinations;
import com.refinedmods.refinedstorage.common.util.ContainerUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.reprsbridge.block.custom.RepRSBridgeBl;
import net.unfamily.reprsbridge.block.ModBlocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;

/**
 * BlockEntity dedicata per la connessione con la rete Refined Storage.
 * Questa classe si integra con la rete Refined Storage.
 */
public class RepRSBridgeBlockEntityF extends AbstractBaseNetworkNodeContainerBlockEntity<BridgeNetworkNode>
implements NetworkNodeExtendedMenuProvider<BridgeData>, BlockEntityWithDrops {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    // Stato della rete Refined Storage
    private boolean active = false;
    private boolean connected = false;
    
    // Riferimento alla BlockEntity principale per la comunicazione
    private RepRSBridgeBlockEntityP mainEntity;
    
    // Contenitori per risorse
    private final List<ResourceAmount> filterItems = new ArrayList<>();
    private final List<ResourceAmount> exportedItems = new ArrayList<>();
    private final List<ExportingIndicator> exportingIndicators = new ArrayList<>();
    
    /**
     * Costruttore per RepRSBridgeBlockEntityF
     */
    public RepRSBridgeBlockEntityF(BlockPos pos, BlockState state) {
        super(
            ModBlockEntities.REPRSBRIDGE_F_BE.get(),
            pos,
            state,
            new BridgeNetworkNode(Platform.INSTANCE.getConfig().getInterface().getEnergyUsage())
        );
        
        // Il nodo viene registrato automaticamente dal sistema di Refined Storage
        // attraverso la classe AbstractBaseNetworkNodeContainerBlockEntity
        LOGGER.debug("Nodo Refined Storage creato alla posizione {}", pos);
    }
    
    /**
     * Sovrascriviamo questo metodo per utilizzare la nostra strategia di connessione personalizzata
     */
    @Override
    protected InWorldNetworkNodeContainer createMainContainer(BridgeNetworkNode networkNode) {
        // Utilizziamo la nostra strategia di connessione personalizzata
        // che permette la connessione con i cavi di Refined Storage
        BridgeConnectionStrategy connectionStrategy = new BridgeConnectionStrategy(this::getBlockState, getBlockPos());
        
        // Forniamo l'accesso al level per la strategia di connessione "through"
        connectionStrategy.setLevelSupplier(() -> this.level);
        
        // Log per debug
        LOGGER.debug("Creata strategia di connessione personalizzata per il nodo alla posizione {}", getBlockPos());
        
        return new BridgeNetworkNodeContainer(
            this,
            networkNode,
            "main",
            connectionStrategy
        );
    }
    
    /**
     * Imposta l'entità principale per la comunicazione
     */
    public void setMainEntity(RepRSBridgeBlockEntityP mainEntity) {
        this.mainEntity = mainEntity;
        
        // Quando impostiamo l'entità principale, notifichiamo anche lo stato della rete
        Network network = mainNetworkNode.getNetwork();
        if (mainEntity != null && network != null) {
            mainEntity.onRefinedStorageNetworkChanged(true, network);
            LOGGER.debug("Entità principale collegata e notificata dello stato della rete");
        }
        
        // Marca come modificato per assicurarsi che il mondo salvi il cambiamento
        setChanged();
    }

    /**
     * Ottiene l'entità principale
     */
    public RepRSBridgeBlockEntityP getMainEntity() {
        return mainEntity;
    }
    
    /**
     * Restituisce il nodo di rete per la capability
     */
    public NetworkNode getNode() {
        // Accediamo direttamente al campo mainNetworkNode ereditato dalla classe AbstractNetworkNodeContainerBlockEntity
        return this.mainNetworkNode;
    }
    
    /**
     * Disconnette questa entità dalla rete
     */
    public void disconnectFromNetwork() {
        if (level != null && !level.isClientSide()) {
            active = false;
            connected = false;
            
            // Notifica l'entità principale
            if (mainEntity != null) {
                mainEntity.onRefinedStorageNetworkChanged(false, null);
            }
            
            // La disconnessione dalla rete avviene automaticamente quando il blocco viene rimosso
            // grazie alla classe AbstractBaseNetworkNodeContainerBlockEntity
            
            // Marca come modificato per assicurarsi che il mondo salvi il cambiamento
            setChanged();
        }
    }
    
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        
        // Salva lo stato della rete RS
        tag.putBoolean("rs_connected", connected);
        tag.putBoolean("rs_active", active);
    }
    
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        
        // Carica lo stato della rete RS
        if (tag.contains("rs_connected")) {
            connected = tag.getBoolean("rs_connected");
        }
        if (tag.contains("rs_active")) {
            active = tag.getBoolean("rs_active");
        }
    }
    
    /**
     * Verifica se l'entità è attiva
     */
    public boolean isActive() {
        return active;
    }
    
    @Override
    public Component getName() {
        return Component.literal("RS Bridge");
    }

    @Override
    public BridgeData getMenuData() {
        // Creiamo i dati dei contenitori vuoti per ora
        ResourceContainerData filterData = new ResourceContainerData(new ArrayList<>());
        ResourceContainerData exportedData = new ResourceContainerData(new ArrayList<>());
        
        return new BridgeData(
            filterData,
            exportedData,
            new ArrayList<>(exportingIndicators)
        );
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BridgeData> getMenuCodec() {
        return BridgeData.STREAM_CODEC;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        // Implementazione del menu container
        return null; // TODO: Implementare il container menu appropriato
    }
    
    @Override
    public NonNullList<ItemStack> getDrops() {
        // Restituisce gli oggetti da droppare quando il blocco viene distrutto
        return NonNullList.create();
    }
    
    /**
     * Override del metodo setChanged per gestire la sincronizzazione della rete
     */
    @Override
    public void setChanged() {
        super.setChanged();
        
        // Quando l'entità viene modificata, verifica lo stato della connessione
        if (level != null && !level.isClientSide()) {
            // Forza un aggiornamento esplicito del nodo di rete
            LOGGER.debug("Bridge: Aggiornamento dell'entità RS, verifico lo stato della rete");
            
            // Se il nodo è null o non è connesso, forza la riconnessione
            Network network = mainNetworkNode.getNetwork();
            
            if (network == null && level.hasNeighborSignal(getBlockPos())) {
                // Il nodo non è connesso e c'è un segnale di redstone nelle vicinanze,
                // potrebbe essere necessario riconnettere il nodo
                LOGGER.debug("Bridge: Il nodo non è connesso, forzo una riconnessione");
                
                // Forza un aggiornamento del blocco per ricreare il nodo
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
            
            // Se siamo connessi a una rete, notifica l'entità principale
            if (mainEntity != null && network != null) {
                mainEntity.onRefinedStorageNetworkChanged(true, network);
                LOGGER.debug("Bridge: Entità principale notificata dello stato della rete: connessa");
            } else if (mainEntity != null) {
                mainEntity.onRefinedStorageNetworkChanged(false, null);
                LOGGER.debug("Bridge: Entità principale notificata dello stato della rete: disconnessa");
            }
        }
    }
    
    /**
     * Override del metodo onLoad per gestire la sincronizzazione della rete al caricamento
     */
    @Override
    public void onLoad() {
        super.onLoad();
        
        if (level != null && !level.isClientSide()) {
            LOGGER.debug("Bridge RS: onLoad chiamato alla posizione {}", worldPosition);
            
            // Forza un aggiornamento di tutti i blocchi adiacenti
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);
                
                // Se il blocco adiacente è un cavo RS, forza un aggiornamento
                if (isRefinedStorageCable(neighborState)) {
                    LOGGER.debug("Bridge RS: Forzo aggiornamento blocco RS a {}", neighborPos);
                    level.neighborChanged(neighborPos, getBlockState().getBlock(), worldPosition);
                }
            }
            
            // Forza un setChanged per aggiornare lo stato della rete
            setChanged();
            
            // Schedule a delayed update to ensure network merging
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(20, () -> {
                    // Force network recalculation after delay
                    LOGGER.debug("Bridge RS: Forzo ricalcolo rete dopo delay per {}", worldPosition);
                    if (mainNetworkNode != null) {
                        setChanged();
                    }
                    
                    // Force block update to notify neighbors
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    
                    // Notify all adjacent blocks again
                    for (Direction direction : Direction.values()) {
                        BlockPos neighborPos = worldPosition.relative(direction);
                        level.neighborChanged(neighborPos, getBlockState().getBlock(), worldPosition);
                    }
                }));
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