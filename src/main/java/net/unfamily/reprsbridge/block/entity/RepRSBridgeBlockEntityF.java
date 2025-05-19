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
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.api.storage.StorageView;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.StorageImpl;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.reprsbridge.block.custom.RepRSBridgeBl;
import net.unfamily.reprsbridge.block.ModBlocks;
import net.unfamily.reprsbridge.RepRSBridge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.api.IMatterType;
import net.unfamily.reprsbridge.util.ReplicationHelper;
import net.unfamily.reprsbridge.item.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.lang.reflect.Method;

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
    
    // Riferimento al MatterNetwork di Replication
    private MatterNetwork matterNetwork;
    
    // Timestamp per la sincronizzazione delle reti
    private long lastNetworkSyncTime = 0;
    
    // Contenitori per risorse
    private final List<ResourceAmount> filterItems = new ArrayList<>();
    private final List<ResourceAmount> exportedItems = new ArrayList<>();
    private final List<ExportingIndicator> exportingIndicators = new ArrayList<>();
    
    // Storage virtuale per gli item simulati
    private final Map<ResourceKey, Long> virtualItems = new HashMap<>();
    private final VirtualStorage virtualStorage = new VirtualStorage();
    
    // Variabili statiche per gestire il salvataggio e lo scaricamento
    private static boolean worldSaving = false;
    private static boolean safeToUpdate = true;
    
    // Variabile statica per salvare lo stato dello storage tra le ricreazioni dell'entità
    private static Map<BlockPos, Map<ResourceKey, Long>> savedVirtualItems = new HashMap<>();
    
    // Thread di monitoraggio per la rimozione di item
    private Thread monitorThread = null;
    
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
    
    public void removeNetwork()
    {
       disconnectFromNetwork();
       mainNetworkNode.setNetwork(null);
       mainEntity = null;
       matterNetwork = null;
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
     * Imposta lo stato di salvataggio del mondo
     * Questo metodo dovrebbe essere chiamato dai listener degli eventi del server
     */
    public static void setWorldSaving(boolean saving) {
        worldSaving = saving;
        safeToUpdate = !saving;
        LOGGER.info("Bridge RS: Impostato stato di salvataggio del mondo a {}", saving);
        
        // Reset the connection flags when world is no longer saving
        if (!saving) {
            LOGGER.info("Bridge RS: Ripristino stato connessioni dopo salvataggio");
        }
    }
    
    /**
     * Disconnette questa entità dalla rete
     */
    public void disconnectFromNetwork() {
        if (level != null && !level.isClientSide()) {
            try {
                // Imposta un flag per evitare ulteriori aggiornamenti
                safeToUpdate = false;
                
                LOGGER.debug("Disconnessione dell'entità RS alla posizione {}", worldPosition);
                
                // Verifica se siamo effettivamente connessi
                Network network = mainNetworkNode.getNetwork();
                if (network != null) {
                    try {
                        // Rimuovi lo storage virtuale dalla rete prima della disconnessione
                        if (network.getComponent(StorageNetworkComponent.class) instanceof RootStorage rootStorage) {
                            if (rootStorage.hasSource(s -> s == virtualStorage)) {
                                try {
                                    rootStorage.removeSource(virtualStorage);
                                    LOGGER.debug("Storage virtuale rimosso dalla rete RS");
                                } catch (Exception e) {
                                    LOGGER.debug("Errore rimuovendo lo storage virtuale: {}", e.getMessage());
                                }
                            }
                        }
                        
                        // Ferma il thread di monitoraggio, se esiste
                        if (monitorThread != null && monitorThread.isAlive()) {
                            monitorThread.interrupt();
                            LOGGER.debug("Thread di monitoraggio interrotto");
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Errore durante la pulizia degli storage: {}", e.getMessage());
                    }
                    
                    try {
                        // Forza l'aggiornamento della rete
                        // Questo è importante per garantire che la rete non mantenga riferimenti invalidi
                        if (network.getComponent(GraphNetworkComponent.class) != null) {
                            mainNetworkNode.setNetwork(null);
                            LOGGER.debug("Rimosso riferimento alla rete dal nodo principale");
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Errore durante il reset del network: {}", e.getMessage());
                    }
                }
                
                // Imposta lo stato
                active = false;
                connected = false;
                
                // Notifica l'entità principale
                if (mainEntity != null) {
                    try {
                        mainEntity.onRefinedStorageNetworkChanged(false, null);
                        LOGGER.debug("Entità principale notificata della disconnessione");
                    } catch (Exception e) {
                        LOGGER.debug("Errore notificando l'entità principale: {}", e.getMessage());
                    }
                }
                
                // Marca come modificato per assicurarsi che il mondo salvi il cambiamento
                // Ma evita di farlo se stiamo già salvando
                if (!worldSaving) {
                    setChanged();
                }
                
                // Salva lo stato dello storage virtuale nella memoria statica
                // per preservarlo durante la ricostruzione dell'entità
                if (!virtualItems.isEmpty()) {
                    savedVirtualItems.put(worldPosition, new HashMap<>(virtualItems));
                    LOGGER.debug("Salvati {} item virtuali per la posizione {}", virtualItems.size(), worldPosition);
                }
            } catch (Exception e) {
                LOGGER.error("Errore durante la disconnessione dalla rete: {}", e.getMessage(), e);
            } finally {
                // Ripristina il flag per consentire aggiornamenti futuri
                safeToUpdate = true;
            }
        }
    }
    
    /**
     * Gestisce il problema della riconnessione ciclica
     * Questo metodo tenta di riconnettere l'entità alla rete in modo pulito
     */
    public void reconnectToNetwork() {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        try {
            LOGGER.debug("Tentativo di riconnessione alla rete RS alla posizione {}", worldPosition);
            
            // Verifica se siamo già connessi
            if (mainNetworkNode.getNetwork() != null && active) {
                LOGGER.debug("L'entità è già connessa a una rete RS, nessuna azione necessaria");
                return;
            }
            
            // Prima verifica se il chunk è caricato
            if (!level.hasChunkAt(worldPosition)) {
                LOGGER.debug("Il chunk per la posizione {} non è caricato, non posso riconnettere", worldPosition);
                return;
            }
            
            // Ripristina lo stato dello storage virtuale dalla memoria statica
            if (savedVirtualItems.containsKey(worldPosition)) {
                virtualItems.clear();
                virtualItems.putAll(savedVirtualItems.get(worldPosition));
                LOGGER.debug("Ripristinati {} item virtuali dalla memoria statica", virtualItems.size());
                // Rimuovi dalla cache dopo il ripristino per evitare ripristini multipli
                // savedVirtualItems.remove(worldPosition);
            }
            
            // Imposta il flag di sicurezza prima di iniziare la ricostruzione
            safeToUpdate = true;
            
            // Metodo 1: Forza l'aggiornamento dei blocchi vicini
            forceNeighborUpdates();
            LOGGER.debug("Forzato aggiornamento dei blocchi vicini per ricostruire la rete");
            
            // Reset del network node è fondamentale
            if (mainNetworkNode != null) {
                // Solo se non abbiamo già una rete
                if (mainNetworkNode.getNetwork() == null) {
                    try {
                        // Imposta lo stato a null e poi forza un aggiornamento
                        mainNetworkNode.setNetwork(null);
                        
                        // Forza il mainNetworkNodeContainer ad aggiornare lo stato
                        setChanged();
                        
                        LOGGER.debug("Reset e aggiornamento forzato del nodo di rete");
                    } catch (Exception e) {
                        LOGGER.debug("Errore durante l'aggiornamento del nodo: {}", e.getMessage());
                    }
                }
            }
            
            // Forza l'aggiornamento dei blocchi adiacenti per attivare la rete
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(direction);
                
                // Verifica se il blocco adiacente è un cavo RS
                if (isRefinedStorageCable(level.getBlockState(neighborPos))) {
                    LOGGER.debug("Forzo aggiornamento blocco RS a {}", neighborPos);
                    level.neighborChanged(neighborPos, level.getBlockState(worldPosition).getBlock(), worldPosition);
                    
                    // Forza anche un aggiornamento visivo e di stato per il blocco adiacente
                    level.sendBlockUpdated(neighborPos, level.getBlockState(neighborPos), 
                                          level.getBlockState(neighborPos), 3);
                }
            }
            
            // Forza il chunk a salvare i cambiamenti
            level.getChunkAt(worldPosition).setUnsaved(true);
            
            // Notifica esplicita di cambiamento al chunk
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().blockChanged(worldPosition);
            }
            
            // Sequenza di tentativi di connessione tramite task ritardati
            if (level instanceof ServerLevel serverLevel) {
                // Primo task dopo 10 tick (0.5 secondi)
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(10, () -> {
                    try {
                        // Forza un secondo aggiornamento dei blocchi vicini
                        forceNeighborUpdates();
                        
                        // Verifica connessione immediata
                        Network network = mainNetworkNode.getNetwork();
                        if (network != null) {
                            LOGGER.debug("Rete trovata al primo tentativo, attendo per stabilizzare...");
                            // Non registriamo lo storage subito, ma attendiamo che la rete si stabilizzi
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Errore nel primo task ritardato: {}", e.getMessage());
                    }
                }));
                
                // Secondo task dopo 20 tick (1 secondo)
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(20, () -> {
                    // Verifica se ora siamo connessi
                    Network network = mainNetworkNode.getNetwork();
                    if (network != null) {
                        LOGGER.debug("Rete trovata al secondo tentativo, registrazione storage...");
                        // Registriamo lo storage con cautela
                        try {
                            registerVirtualStorage(network);
                            setChanged();
                            LOGGER.debug("Registrazione storage completata");
                        } catch (Exception e) {
                            LOGGER.debug("Errore durante la registrazione dello storage: {}", e.getMessage());
                        }
                    } else {
                        LOGGER.debug("Rete non trovata al secondo tentativo, provo ancora...");
                        forceNeighborUpdates();
                    }
                }));
                
                // Terzo task dopo 40 tick (2 secondi) - ultima chance
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(40, () -> {
                    Network network = mainNetworkNode.getNetwork();
                    if (network != null) {
                        LOGGER.debug("Rete trovata al terzo tentativo, registrazione storage...");
                        try {
                            registerVirtualStorage(network);
                            // Forza un aggiornamento dello storage per garantire la visibilità
                            forceStorageUpdate(network);
                            setChanged();
                            LOGGER.debug("Registrazione storage e aggiornamento forzato completati");
                        } catch (Exception e) {
                            LOGGER.debug("Errore durante l'aggiornamento finale: {}", e.getMessage());
                        }
                    } else {
                        LOGGER.debug("Rete non trovata nemmeno al terzo tentativo");
                        // Ultimo tentativo disperato: forza un aggiornamento completo del blocco
                        try {
                            // Forza un aggiornamento del blocco stesso
                            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                        } catch (Exception e) {
                            LOGGER.debug("Errore durante l'ultimo tentativo: {}", e.getMessage());
                        }
                    }
                }));
            }
            
            // Marca come modificato per assicurarsi che il mondo salvi il cambiamento
            setChanged();
            
            LOGGER.debug("Processo di riconnessione avviato");
        } catch (Exception e) {
            LOGGER.error("Errore durante la riconnessione alla rete: {}", e.getMessage(), e);
        }
    }

    // Metodo per forzare l'aggiornamento dei blocchi vicini e ricostruire la rete
    private void forceNeighborUpdates() {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        try {
            // Notifica tutti i blocchi adiacenti
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(direction);
                
                // Notifica il blocco che c'è stato un cambiamento
                level.neighborChanged(neighborPos, getBlockState().getBlock(), worldPosition);
                
                // Forza un aggiornamento visivo
                level.sendBlockUpdated(neighborPos, level.getBlockState(neighborPos), 
                                      level.getBlockState(neighborPos), 3);
            }
            
            // Forza un aggiornamento anche sul blocco stesso
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
            LOGGER.debug("Aggiornamento dei blocchi vicini forzato alla posizione {}", worldPosition);
        } catch (Exception e) {
            LOGGER.error("Errore durante l'aggiornamento dei blocchi vicini: {}", e.getMessage());
        }
    }

    @Override
    public void onChunkUnloaded() {
        // Quando il chunk viene scaricato, salviamo lo stato dello storage virtuale
        if (level != null && !level.isClientSide() && !virtualItems.isEmpty()) {
            // Disattiva temporaneamente gli aggiornamenti
            safeToUpdate = false;
            
            savedVirtualItems.put(worldPosition, new HashMap<>(virtualItems));
            LOGGER.debug("Chunk scaricato, salvati {} item virtuali per la posizione {}", virtualItems.size(), worldPosition);
            
            // Esegui la disconnessione ma evitando di chiamare setChanged()
            try {
                disconnectFromNetwork();
            } catch (Exception e) {
                LOGGER.error("Errore durante la disconnessione al chunk unload: {}", e.getMessage());
            }
            
            super.onChunkUnloaded();
            
            // Ripristina il flag per consentire aggiornamenti futuri
            safeToUpdate = true;
        } else {
            super.onChunkUnloaded();
        }
    }

    @Override
    public void setRemoved() {
        // Quando l'entità viene rimossa, eseguiamo una disconnessione pulita
        if (level != null && !level.isClientSide()) {
            // Disattiva temporaneamente gli aggiornamenti
            safeToUpdate = false;
            
            // Ferma il thread di monitoraggio
            if (monitorThread != null && monitorThread.isAlive()) {
                monitorThread.interrupt();
                LOGGER.debug("Thread di monitoraggio interrotto durante la rimozione dell'entità");
            }
            
            try {
                disconnectFromNetwork();
            } catch (Exception e) {
                LOGGER.error("Errore durante la disconnessione in setRemoved: {}", e.getMessage());
            }
            
            super.setRemoved();
            
            // Ripristina il flag per consentire aggiornamenti futuri
            safeToUpdate = true;
        } else {
            super.setRemoved();
        }
    }
    
    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // Disattiva temporaneamente gli aggiornamenti
        boolean oldSafeToUpdate = safeToUpdate;
        safeToUpdate = false;
        
        try {
            super.saveAdditional(tag, registries);
            
            // Salva lo stato della rete RS
            tag.putBoolean("rs_connected", connected);
            tag.putBoolean("rs_active", active);
            
            // Salva anche lo stato dello storage virtuale nella memoria statica
            // per preservarlo durante la ricostruzione dell'entità
            if (!virtualItems.isEmpty()) {
                savedVirtualItems.put(worldPosition, new HashMap<>(virtualItems));
                LOGGER.debug("Salvati {} item virtuali per la posizione {}", virtualItems.size(), worldPosition);
            }
        } finally {
            // Ripristina il flag allo stato precedente
            safeToUpdate = oldSafeToUpdate;
        }
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
        
        // Ripristina lo stato dello storage virtuale dalla memoria statica
        if (savedVirtualItems.containsKey(worldPosition)) {
            virtualItems.clear();
            virtualItems.putAll(savedVirtualItems.get(worldPosition));
            LOGGER.debug("Ripristinati {} item virtuali dalla memoria statica", virtualItems.size());
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

    /**
     * Aggiorna il menu (GUI) del bridge con le informazioni necessarie
     * Questo metodo è chiamato dal server quando il cliente richiede informazioni
     * 
     * @return Un oggetto BridgeData contenente le informazioni per il menu
     */
    @Override
    public BridgeData getMenuData() {
        // Ritorniamo null per evitare problemi di serializzazione
        // Non è necessario implementare questa interfaccia completamente
        return null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BridgeData> getMenuCodec() {
        return BridgeData.STREAM_CODEC;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        // Non apriremo un container GUI per questo blocco
        return null;
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
        // Se siamo in fase di salvataggio o non è sicuro aggiornare, evita aggiornamenti
        if (worldSaving || !safeToUpdate) {
            LOGGER.debug("Ignorato setChanged durante salvataggio/scaricamento per {}", worldPosition);
            super.setChanged();
            return;
        }
        
        super.setChanged();
        
        // Quando l'entità viene modificata, verifica lo stato della connessione
        if (level != null && !level.isClientSide()) {
            // Sincronizza il MatterNetwork dall'entità principale
            syncMatterNetwork();
            
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
            
            // Aggiungi un ritardo per dare il tempo al mondo di caricarsi completamente
            if (level instanceof ServerLevel serverLevel) {
                // Usa tre task ritardati per garantire che la rete sia completamente caricata
                
                // Prima verifica dopo 20 tick (1 secondo)
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(20, () -> {
                    LOGGER.debug("Bridge RS: Primo tentativo di connessione");
                    
                    // Verifica se siamo già connessi (potremmo esserci già a questo punto)
                    if (mainNetworkNode.getNetwork() != null) {
                        LOGGER.debug("Bridge RS: Già connesso alla rete, registrazione storage...");
                        try {
                            // Se siamo già connessi, registra lo storage
                            Network network = mainNetworkNode.getNetwork();
                            if (network != null) {
                                registerVirtualStorage(network);
                                LOGGER.debug("Bridge RS: Storage registrato con successo");
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Bridge RS: Errore registrazione storage: {}", e.getMessage());
                        }
                    } else {
                        LOGGER.debug("Bridge RS: Nessuna connessione trovata al primo tentativo");
                        // Forza l'aggiornamento dei blocchi vicini per attivare la rete
                        forceNeighborUpdates();
                    }
                }));
                
                // Seconda verifica dopo 40 tick (2 secondi)
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(40, () -> {
                    LOGGER.debug("Bridge RS: Secondo tentativo di connessione");
                    try {
                        // Se la rete non è ancora disponibile, forza un aggiornamento
                        if (mainNetworkNode.getNetwork() == null) {
                            LOGGER.debug("Bridge RS: Ancora nessuna connessione, forzo riconnessione");
                            reconnectToNetwork();
                        } else {
                            LOGGER.debug("Bridge RS: Verifica connessione stabilita");
                            // La rete è disponibile, verifica che lo storage sia registrato
                            Network network = mainNetworkNode.getNetwork();
                            if (network != null) {
                                // Controlla se lo storage virtuale è già registrato
                                boolean storageRegistrato = network.getComponent(StorageNetworkComponent.class) != null &&
                                                            network.getComponent(StorageNetworkComponent.class) instanceof RootStorage rootStorage &&
                                                            rootStorage.hasSource(s -> s == virtualStorage);
                                
                                if (!storageRegistrato) {
                                    LOGGER.debug("Bridge RS: Storage non ancora registrato, registro ora");
                                    registerVirtualStorage(network);
                                } else {
                                    LOGGER.debug("Bridge RS: Storage già registrato, tutto a posto");
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Bridge RS: Errore secondo tentativo: {}", e.getMessage());
                    }
                }));
                
                // Terza verifica dopo 100 tick (5 secondi) per essere sicuri
                serverLevel.getServer().tell(new net.minecraft.server.TickTask(100, () -> {
                    try {
                        if (mainNetworkNode.getNetwork() == null) {
                            LOGGER.debug("Bridge RS: Nessuna connessione dopo numerosi tentativi, ultimo tentativo");
                            // Ultimo disperato tentativo di connessione
                            reconnectToNetwork();
                        } else {
                            // Verifica finale che tutto sia a posto
                            Network network = mainNetworkNode.getNetwork();
                            if (network != null) {
                                boolean storageRegistrato = network.getComponent(StorageNetworkComponent.class) != null &&
                                                            network.getComponent(StorageNetworkComponent.class) instanceof RootStorage rootStorage &&
                                                            rootStorage.hasSource(s -> s == virtualStorage);
                                                                
                                if (!storageRegistrato) {
                                    LOGGER.debug("Bridge RS: Storage ancora non registrato, ultimo tentativo");
                                    registerVirtualStorage(network);
                                    // Forza un aggiornamento
                                    forceStorageUpdate(network);
                                } else {
                                    LOGGER.debug("Bridge RS: Tutto configurato correttamente");
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Bridge RS: Errore nell'ultimo tentativo: {}", e.getMessage());
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

    /**
     * Aggiorna il riferimento al MatterNetwork dall'entità principale
     * Questo metodo dovrebbe essere chiamato periodicamente per mantenere sincronizzate le reti
     */
    public void syncMatterNetwork() {
        // Limita la frequenza di sincronizzazione per evitare sovraccarichi
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastNetworkSyncTime < 1000) { // massimo una volta al secondo
            return;
        }
        
        if (mainEntity != null) {
            MatterNetwork network = mainEntity.getReplicationNetwork();
            if (network != this.matterNetwork) {
                this.matterNetwork = network;
                LOGGER.debug("MatterNetwork sincronizzato da entità principale a {}", worldPosition);
            }
        } else {
            this.matterNetwork = null;
        }
        
        lastNetworkSyncTime = currentTime;
    }
    
    /**
     * Ottiene il MatterNetwork corrente, sincronizzandolo se necessario
     * @return Il MatterNetwork di Replication, o null se non disponibile
     */
    public MatterNetwork getMatterNetwork() {
        syncMatterNetwork();
        return matterNetwork;
    }
    
    /**
     * Classe per lo storage virtuale
     * Questa classe implementa l'interfaccia Storage di Refined Storage per simulare
     * la presenza di item nel network ma solo in modalità estrazione (extract-only).
     * 
     * Lo storage virtuale è di "sola estrazione" (extract-only) perché:
     * 1. Il metodo insert() restituisce sempre 0, rifiutando qualsiasi tentativo di inserimento
     * 2. Il metodo extract() permette di estrarre gli item normalmente
     * 
     * Questo comportamento consente agli item di essere visualizzati nell'interfaccia di
     * Refined Storage e di essere utilizzati per crafting o estrazione, ma impedisce che 
     * vengano inseriti elementi dall'esterno in questo storage.
     * 
     * L'utilizzo principale è per rappresentare la materia presente nel network di Replication
     * come item virtuali all'interno del network di Refined Storage.
     */
    private class VirtualStorage implements Storage {
        // Capacità massima dello storage (per limitare l'inserimento)
        private static final long MAX_CAPACITY = Long.MAX_VALUE;
        
        // Lista interna degli item, per un comportamento più simile a StorageImpl
        private final com.refinedmods.refinedstorage.api.resource.list.MutableResourceList resourceList;
        private boolean initialized = false;
        
        public VirtualStorage() {
            try {
                // Creiamo una lista di risorse interna usando i metodi forniti dall'API
                Class<?> resourceListClass = Class.forName("com.refinedmods.refinedstorage.api.resource.list.MutableResourceListImpl");
                Method createMethod = resourceListClass.getMethod("create");
                this.resourceList = (com.refinedmods.refinedstorage.api.resource.list.MutableResourceList) createMethod.invoke(null);
            } catch (Exception e) {
                LOGGER.error("Errore nell'inizializzazione del VirtualStorage: {}", e.getMessage(), e);
                throw new RuntimeException("Impossibile inizializzare il VirtualStorage", e);
            }
        }
        
        /**
         * Sincronizza la mappa virtualItems interna con la lista di risorse
         */
        public void syncFromVirtualItems() {
            if (resourceList == null) {
                return;
            }
            
            try {
                // Svuota la lista attuale
                Method clearMethod = resourceList.getClass().getMethod("clear");
                clearMethod.invoke(resourceList);
                
                // Aggiungi tutti gli item dalla mappa alla lista
                Method addMethod = resourceList.getClass().getMethod("add", ResourceKey.class, long.class);
                
                for (Map.Entry<ResourceKey, Long> entry : virtualItems.entrySet()) {
                    if (entry.getValue() > 0) {
                        addMethod.invoke(resourceList, entry.getKey(), entry.getValue());
                    }
                }
                
                initialized = true;
                LOGGER.debug("VirtualStorage sincronizzato con {} item", virtualItems.size());
            } catch (Exception e) {
                LOGGER.error("Errore durante la sincronizzazione del VirtualStorage: {}", e.getMessage(), e);
            }
        }
        
        /**
         * Sincronizza la lista di risorse con la mappa virtualItems interna
         */
        public void syncToVirtualItems() {
            if (resourceList == null) {
                return;
            }
            
            try {
                // Svuota la mappa attuale
                virtualItems.clear();
                
                // Recupera tutti gli item dalla lista
                for (ResourceAmount amount : getAll()) {
                    if (amount.amount() > 0) {
                        virtualItems.put(amount.resource(), amount.amount());
                    }
                }
                
                LOGGER.debug("VirtualItems sincronizzato con {} item dalla lista interna", virtualItems.size());
            } catch (Exception e) {
                LOGGER.error("Errore durante la sincronizzazione dei VirtualItems: {}", e.getMessage(), e);
            }
        }
        
        /**
         * Implementazione di insert che rifiuta tutte le inserzioni.
         * Questo metodo è fondamentale per rendere lo storage di sola estrazione (extract-only).
         * Quando qualsiasi tentativo di inserimento viene effettuato, restituiamo 0 per indicare 
         * che nessun item è stato accettato. Questo permette di visualizzare gli item nel network
         * ma impedisce che vengano utilizzati come destinazione di trasferimento o crafting.
         */
        @Override
        public long insert(ResourceKey resource, long amount, Action action, Actor actor) {
            // Log dettagliato solo se non è una simulazione (per evitare spam nel log)
            if (action == Action.EXECUTE) {
                LOGGER.debug("[STORAGE VIRTUALE] Tentativo di inserimento rifiutato: {} x{} da {}",
                    resource, amount, actor != null ? actor.toString() : "sconosciuto");
            }
            
            // Rifiuta qualsiasi inserimento restituendo 0
            return 0;
        }
        
        /**
         * Implementazione di extract che permette l'estrazione di item dal network virtuale.
         * Questo metodo completa la funzionalità extract-only dello storage virtuale, consentendo
         * agli item di essere estratti e utilizzati nel network di Refined Storage.
         * L'estrazione aggiorna sia la lista interna che la mappa virtualItems.
         */
        @Override
        public long extract(ResourceKey resource, long amount, Action action, Actor actor) {
            try {
                // Blocca l'estrazione se non è sicuro operare
                if (!safeToUpdate || worldSaving) {
                    LOGGER.debug("[STORAGE VIRTUALE] Tentativo di estrazione bloccato (worldSaving={}): {}", 
                              worldSaving, resource);
                    return 0;
                }
                
                if (resource == null || amount <= 0) {
                    return 0;
                }
                
                // Verifica se l'item esiste nello storage
                long stored = virtualItems.getOrDefault(resource, 0L);
                if (stored <= 0) {
                    // Se non abbiamo la risorsa, logga solo se non è una simulazione
                    if (action == Action.EXECUTE) {
                        LOGGER.debug("[STORAGE VIRTUALE] Tentativo di estrazione fallito (risorsa non disponibile): {} da {}", 
                                  resource, actor);
                    }
                    return 0;
                }
                
                // Calcola l'ammontare che possiamo effettivamente estrarre
                long toExtract = Math.min(stored, amount);
                
                // Esegui effettivamente solo se non è una simulazione
                if (action == Action.EXECUTE) {
                    if (actor != null && actor.toString().contains("TaskImpl")) {
                        // Se l'estrazione viene da un processo di autocrafting, teniamo traccia
                        LOGGER.debug("[STORAGE VIRTUALE] Estrazione per autocrafting: {} x{}", resource, toExtract);
                    }
                    
                    // Aggiorna lo storage
                    virtualItems.put(resource, stored - toExtract);
                    
                    // Se l'ammontare è ora zero, rimuovi la chiave
                    if (virtualItems.get(resource) <= 0) {
                        virtualItems.remove(resource);
                    }
                    
                    // Aggiorna la resource list
                    virtualStorage.syncFromVirtualItems();
                    
                    // Debug log solo per grandi quantità o quando richiesto esplicitamente
                    if (toExtract > 1000 || LOGGER.isDebugEnabled()) {
                        LOGGER.debug("[STORAGE VIRTUALE] Estratto: {} x{} (rimanenti: {})", 
                                  resource, toExtract, stored - toExtract);
                    }
                    
                    // Marca come modificato per salvare lo stato
                    if (level != null && !level.isClientSide()) {
                        setChanged();
                    }
                }
                
                return toExtract;
            } catch (Exception e) {
                LOGGER.error("[STORAGE VIRTUALE] Errore durante l'estrazione: {}", e.getMessage(), e);
                return 0;
            }
        }
        
        @Override
        public long getStored() {
            // Assicurati che la lista interna sia sincronizzata
            if (!initialized) {
                syncFromVirtualItems();
            }
            
            try {
                // Usa la somma dei valori nella mappa virtualItems
                return virtualItems.values().stream().mapToLong(Long::longValue).sum();
            } catch (Exception e) {
                LOGGER.error("Errore durante il calcolo degli item immagazzinati: {}", e.getMessage(), e);
                return 0;
            }
        }
        
        @Override
        public Collection<ResourceAmount> getAll() {
            // Assicurati che la lista interna sia sincronizzata
            if (!initialized) {
                syncFromVirtualItems();
            }
            
            try {
                // Ottieni tutti gli item dalla lista interna
                Method copyStateMethod = resourceList.getClass().getMethod("copyState");
                @SuppressWarnings("unchecked")
                Collection<ResourceAmount> result = (Collection<ResourceAmount>) copyStateMethod.invoke(resourceList);
                
                // Se non ci sono risultati, prova a creare una lista manualmente
                if (result == null || result.isEmpty()) {
                    result = new ArrayList<>();
                    for (Map.Entry<ResourceKey, Long> entry : virtualItems.entrySet()) {
                        if (entry.getValue() > 0) {
                            result.add(new ResourceAmount(entry.getKey(), entry.getValue()));
                        }
                    }
                }
                
                return result;
            } catch (Exception e) {
                LOGGER.error("Errore durante il recupero degli item: {}", e.getMessage(), e);
                
                // Fallback: crea una lista manualmente
                List<ResourceAmount> fallbackResult = new ArrayList<>();
                for (Map.Entry<ResourceKey, Long> entry : virtualItems.entrySet()) {
                    if (entry.getValue() > 0) {
                        fallbackResult.add(new ResourceAmount(entry.getKey(), entry.getValue()));
                    }
                }
                return fallbackResult;
            }
        }
    }
    
    /**
     * Registra il nostro storage virtuale nella rete RS
     */
    private void registerVirtualStorage(Network network) {
        try {
            if (network != null) {
                LOGGER.warn("[REGISTRAZIONE] Inizio registrazione storage virtuale nella rete RS");
                StorageNetworkComponent storageNetworkComponent = network.getComponent(StorageNetworkComponent.class);
                if (storageNetworkComponent instanceof RootStorage rootStorage) {
                    // Verifica se il nostro storage è già stato aggiunto
                    if (!rootStorage.hasSource(s -> s == virtualStorage)) {
                        LOGGER.warn("[REGISTRAZIONE] Aggiunta nuovo storage virtuale al RootStorage");
                        
                        // Sincronizza lo storage virtuale prima di aggiungerlo
                        virtualStorage.syncFromVirtualItems();
                        
                        // Aggiunge lo storage alla rete
                        rootStorage.addSource(virtualStorage);
                        LOGGER.warn("[REGISTRAZIONE] Storage virtuale aggiunto con successo");
                        
                        // Forza un aggiornamento dello storage dopo l'aggiunta
                        forceStorageUpdate(network);
                        
                        // Prova ad aggiungere un listener per debug ma senza usare reflectio
                        try {
                            // Usa l'API pubblica, se disponibile
                            Method addListenerMethod = rootStorage.getClass().getMethod("addListener", 
                                Class.forName("com.refinedmods.refinedstorage.api.storage.root.RootStorageListener"));
                            
                            if (addListenerMethod != null) {
                                LOGGER.warn("[REGISTRAZIONE] Metodo addListener trovato, possibile aggiungere listener");
                            }
                        } catch (Exception e) {
                            // Ignora l'errore, è solo per debug
                            LOGGER.debug("[REGISTRAZIONE] Impossibile aggiungere listener: {}", e.getMessage());
                        }
                    } else {
                        LOGGER.warn("[REGISTRAZIONE] Storage virtuale già presente nella rete RS");
                        
                        // Sincronizza e forza l'aggiornamento comunque
                        virtualStorage.syncFromVirtualItems();
                        forceStorageUpdate(network);
                    }
                } else {
                    LOGGER.warn("[REGISTRAZIONE] Impossibile accedere al RootStorage della rete RS");
                }
                LOGGER.warn("[REGISTRAZIONE] Fine registrazione storage virtuale");
            }
        } catch (Exception e) {
            LOGGER.error("Errore durante la registrazione dello storage virtuale: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Registra un intercettore per monitorare e rimuovere automaticamente
     * gli item di materia virtuale inseriti nella rete
     */
    private void registerRootStorageInterceptor(RootStorage rootStorage) throws Exception {
        if (rootStorage == null) {
            return;
        }
        
        LOGGER.warn("[INTERCEPTOR] Installazione interceptor per la pulizia automatica degli item di materia virtuale");
        
        // Ferma eventuali thread precedenti
        if (monitorThread != null && monitorThread.isAlive()) {
            monitorThread.interrupt();
            LOGGER.warn("[INTERCEPTOR] Thread di monitoraggio precedente terminato");
        }
        
        // Non usiamo più reflection per accedere agli storages interni
        // Utilizziamo solo l'API pubblica
        
        // Inizia un thread di monitoraggio per intercettare e rimuovere gli item
        monitorThread = new Thread(() -> {
            LOGGER.warn("[INTERCEPTOR] Thread di monitoraggio avviato");
            
            while (level != null && !level.isClientSide() && !isRemoved()) {
                try {
                    // Verifica se siamo connessi alla rete
                    Network network = mainNetworkNode.getNetwork();
                    if (network == null) {
                        // Se la rete non è disponibile, interrompi il monitoraggio
                        break;
                    }
                    
                    // Pulisci gli item di materia virtuale non gestiti
                    cleanupVirtualMatterItems(network);
                    
                    // Attendi prima del prossimo controllo (ogni 2 secondi)
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    LOGGER.error("[INTERCEPTOR] Errore durante il monitoraggio: {}", e.getMessage(), e);
                    break;
                }
            }
            
            LOGGER.warn("[INTERCEPTOR] Thread di monitoraggio terminato");
        });
        
        // Imposta il thread come daemon per farlo terminare quando il server si spegne
        monitorThread.setDaemon(true);
        monitorThread.setName("RS-Bridge-ItemMonitor");
        monitorThread.start();
        
        LOGGER.warn("[INTERCEPTOR] Interceptor installato con successo");
    }
    
    /**
     * Forza un aggiornamento della vista degli item nel network
     */
    private void forceStorageUpdate(Network network) {
        // Se siamo in fase di salvataggio o non è sicuro aggiornare, evita aggiornamenti
        if (worldSaving || !safeToUpdate) {
            LOGGER.debug("Ignorato forceStorageUpdate durante salvataggio/scaricamento");
            return;
        }
        
        try {
            if (network != null) {
                LOGGER.warn("[AGGIORNAMENTO] Inizio aggiornamento vista nella rete RS");
                
                // In questa versione dell'API, dobbiamo notificare i listeners della rete
                if (network.getComponent(StorageNetworkComponent.class) instanceof RootStorage rootStorage) {
                    // Sincronizza lo storage prima dell'aggiornamento
                    virtualStorage.syncFromVirtualItems();
                    
                    // Prima di tutto, proviamo a notificare i listener direttamente
                    try {
                        Method getListenersMethod = rootStorage.getClass().getDeclaredMethod("getListeners");
                        getListenersMethod.setAccessible(true);
                        Object listeners = getListenersMethod.invoke(rootStorage);
                        
                        if (listeners instanceof Collection) {
                            @SuppressWarnings("unchecked")
                            Collection<Object> listenerCollection = (Collection<Object>) listeners;
                            
                            LOGGER.warn("[AGGIORNAMENTO] Notifica diretta a {} listener", listenerCollection.size());
                            
                            // Per ogni listener, chiama il metodo onChanged
                            for (Object listener : listenerCollection) {
                                try {
                                    for (Method method : listener.getClass().getMethods()) {
                                        if (method.getName().contains("onChange") || method.getName().equals("onChanged")) {
                                            try {
                                                method.invoke(listener);
                                                LOGGER.debug("[AGGIORNAMENTO] Notificato listener usando {}", method.getName());
                                            } catch (Exception e) {
                                                // Ignora errori specifici
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // Ignora errori per listener singoli
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.debug("[AGGIORNAMENTO] Impossibile notificare i listener direttamente: {}", e.getMessage());
                    }
                    
                    // Tenta di invalidare la cache di RootStorage
                    boolean methodCalled = false;
                    
                    // Prima prova il metodo invalidate() che è comune in molte implementazioni
                    try {
                        LOGGER.warn("[AGGIORNAMENTO] Provo metodo invalidate()");
                        Method invalidateMethod = rootStorage.getClass().getMethod("invalidate");
                        invalidateMethod.invoke(rootStorage);
                        LOGGER.warn("[AGGIORNAMENTO] Rete aggiornata usando invalidate()");
                        methodCalled = true;
                    } catch (Exception e) {
                        LOGGER.warn("[AGGIORNAMENTO] Il metodo invalidate() non è disponibile: {}", e.getMessage());
                    }
                    
                    // Se il metodo precedente fallisce, prova con altri metodi comuni
                    if (!methodCalled) {
                        LOGGER.warn("[AGGIORNAMENTO] Provo altri metodi di invalidazione");
                        for (Method method : rootStorage.getClass().getMethods()) {
                            if ((method.getName().contains("invalidate") || 
                                 method.getName().contains("notifyListener") || 
                                 method.getName().contains("notify") || 
                                 method.getName().contains("refresh") ||
                                 method.getName().contains("changed") ||
                                 method.getName().contains("markDirty")) && 
                                method.getParameterCount() == 0) {
                                
                                try {
                                    method.setAccessible(true);
                                    method.invoke(rootStorage);
                                    LOGGER.warn("[AGGIORNAMENTO] Aggiornata la rete usando metodo {}", method.getName());
                                    methodCalled = true;
                                    break;
                                } catch (Exception e) {
                                    // Continua con il prossimo metodo
                                }
                            }
                        }
                    }
                    
                    // Metodo drastico: forza un aggiornamento rimuovendo e riaggiungendo lo storage
                    // Ma solo se NON stiamo salvando o scaricando
                    if (!methodCalled && !worldSaving) {
                        try {
                            LOGGER.warn("[AGGIORNAMENTO] Tentativo di rimuovere e riaggiungere lo storage");
                            if (rootStorage.hasSource(s -> s == virtualStorage)) {
                                rootStorage.removeSource(virtualStorage);
                                virtualStorage.syncFromVirtualItems(); // Sincronizza di nuovo
                                rootStorage.addSource(virtualStorage);
                                LOGGER.warn("[AGGIORNAMENTO] Storage rimosso e riaggiunto con successo");
                                methodCalled = true;
                            }
                        } catch (Exception e) {
                            LOGGER.warn("[AGGIORNAMENTO] Errore durante la rimozione/aggiunta dello storage: {}", e.getMessage());
                        }
                    }
                    
                    if (!methodCalled) {
                        LOGGER.warn("[AGGIORNAMENTO] Non è stato possibile trovare un metodo appropriato per aggiornare la rete");
                    }
                    
                    LOGGER.warn("[AGGIORNAMENTO] Aggiornamento vista completato per {} item virtuali", virtualItems.size());
                }
                
                LOGGER.warn("[AGGIORNAMENTO] Fine aggiornamento vista nella rete RS");
            }
        } catch (Exception e) {
            LOGGER.error("Errore durante l'aggiornamento della vista degli item: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Metodo per rendere un ItemStack visibile nel network di Refined Storage
     * 
     * @param stack L'item da rendere visibile nel network
     * @param amount La quantità dell'item
     * @return true se l'operazione è riuscita, false altrimenti
     */
    public boolean addVirtualItemToNetwork(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return false;
        }
        
        try {
            LOGGER.warn("[ADD ITEM] Inizio procedura per aggiungere {} x{}", stack.getItem().getDescriptionId(), amount);
            
            // Crea un ResourceKey utilizzando il metodo corretto
            ResourceKey resourceKey = null;
            
            // Utilizzo diretto di ItemResource.ofItemStack
            try {
                // Questo crea un ItemResource che implementa ResourceKey direttamente
                Class<?> itemResourceClass = Class.forName("com.refinedmods.refinedstorage.common.support.resource.ItemResource");
                Method ofItemStackMethod = itemResourceClass.getMethod("ofItemStack", ItemStack.class);
                resourceKey = (ResourceKey) ofItemStackMethod.invoke(null, stack);
                LOGGER.warn("[ADD ITEM] Creato ResourceKey con ItemResource.ofItemStack: {}", resourceKey);
            } catch (Exception e) {
                LOGGER.warn("[ADD ITEM] Errore con ItemResource.ofItemStack: {}", e.getMessage());
                
                // Prova altri approcci
                try {
                    // Prova a utilizzare il ResourceFactory dell'API
                    Class<?> resourceFactoryClass = RefinedStorageApi.INSTANCE.getItemResourceFactory().getClass();
                    Method createMethod = resourceFactoryClass.getMethod("create", ItemStack.class);
                    Object resourceAmount = ((java.util.Optional<?>) createMethod.invoke(RefinedStorageApi.INSTANCE.getItemResourceFactory(), stack)).orElse(null);
                    
                    if (resourceAmount != null) {
                        // Estrai la ResourceKey dalla ResourceAmount
                        Method resourceMethod = resourceAmount.getClass().getMethod("resource");
                        resourceKey = (ResourceKey) resourceMethod.invoke(resourceAmount);
                        LOGGER.warn("[ADD ITEM] Creato ResourceKey usando ResourceFactory: {}", resourceKey);
                    }
                } catch (Exception ex) {
                    LOGGER.warn("[ADD ITEM] Errore con ResourceFactory: {}", ex.getMessage());
                }
            }
            
            // Se ancora non abbiamo un ResourceKey, prova a recuperarne uno esistente
            if (resourceKey == null) {
                // Prova prima a usare il sistema standard
                try {
                    for (ResourceKey key : virtualItems.keySet()) {
                        // Verifica se la chiave contiene il nome dell'item
                        if (key.toString().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())) {
                            resourceKey = key;
                            LOGGER.warn("[ADD ITEM] Trovato ResourceKey esistente: {}", key);
                            break;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("[ADD ITEM] Errore nella ricerca di ResourceKey esistenti: {}", e.getMessage());
                }
            }
            
            // Se ancora non abbiamo un ResourceKey, non possiamo procedere
            if (resourceKey == null) {
                LOGGER.warn("[ADD ITEM] Impossibile creare ResourceKey per l'item {}", stack.getItem().getDescriptionId());
                return false;
            }
            
            // Aggiungi l'item alla mappa degli item virtuali
            long currentAmount = virtualItems.getOrDefault(resourceKey, 0L);
            virtualItems.put(resourceKey, currentAmount + amount);
            
            // Aggiorna la lista interna
            if (virtualStorage.initialized) {
                virtualStorage.syncFromVirtualItems();
            }
            
            LOGGER.warn("[ADD ITEM] Item {} aggiunto con successo, quantità: {}", stack.getItem().getDescriptionId(), amount);
            return true;
        } catch (Exception e) {
            LOGGER.error("Errore durante l'aggiunta dell'item virtuale: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Rimuove un item virtuale dal network di Refined Storage
     * 
     * @param stack L'item da rimuovere
     * @param amount La quantità da rimuovere
     * @return true se l'operazione è riuscita, false altrimenti
     */
    public boolean removeVirtualItemFromNetwork(ItemStack stack, int amount) {
        if (stack.isEmpty() || amount <= 0) {
            return false;
        }
        
        try {
            // Verifica se siamo connessi alla rete Refined Storage
            Network rsNetwork = mainNetworkNode.getNetwork();
            if (rsNetwork == null) {
                LOGGER.debug("Non c'è una connessione alla rete Refined Storage, non posso rimuovere item virtuali");
                return false;
            }
            
            // Crea un identificatore unico per l'item basato sul suo ResourceLocation
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            
            // Cerca il ResourceKey corrispondente
            ResourceKey resourceKey = null;
            for (ResourceKey key : virtualItems.keySet()) {
                // Verifica se la chiave contiene il nome dell'item
                if (key.toString().contains(itemId)) {
                    resourceKey = key;
                    break;
                }
            }
            
            if (resourceKey == null) {
                LOGGER.debug("Item {} non trovato nello storage virtuale", stack.getItem().getDescriptionId());
                return false;
            }
            
            // Verifica se l'item è presente e in quantità sufficiente
            long currentAmount = virtualItems.getOrDefault(resourceKey, 0L);
            if (currentAmount < amount) {
                LOGGER.debug("Quantità insufficiente per l'item {}. Richiesto: {}, Disponibile: {}", 
                    stack.getItem().getDescriptionId(), amount, currentAmount);
                return false;
            }
            
            // Rimuovi la quantità richiesta o rimuovi completamente l'item
            long newAmount = currentAmount - amount;
            if (newAmount <= 0) {
                virtualItems.remove(resourceKey);
            } else {
                virtualItems.put(resourceKey, newAmount);
            }
            
            // Forza un aggiornamento della vista
            forceStorageUpdate(rsNetwork);
            
            LOGGER.debug("Item {} rimosso dalla rete con quantità {}", stack.getItem().getDescriptionId(), amount);
            return true;
        } catch (Exception e) {
            LOGGER.error("Errore durante la rimozione dell'item virtuale: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Ottiene la quantità di un item virtuale presente nel network
     * 
     * @param stack L'item da verificare
     * @return La quantità dell'item presente, o 0 se non presente
     */
    public long getVirtualItemAmount(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        
        try {
            // Crea un identificatore unico per l'item basato sul suo ResourceLocation
            String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            
            // Cerca il ResourceKey corrispondente
            for (Map.Entry<ResourceKey, Long> entry : virtualItems.entrySet()) {
                ResourceKey key = entry.getKey();
                // Verifica se la chiave contiene il nome dell'item
                if (key.toString().contains(itemId)) {
                    return entry.getValue();
                }
            }
            
            return 0;
        } catch (Exception e) {
            LOGGER.error("Errore durante il controllo della quantità dell'item virtuale: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Svuota lo storage virtuale, rimuovendo tutti gli item
     */
    public void clearVirtualItems() {
        virtualItems.clear();
        LOGGER.debug("Storage virtuale svuotato");
    }

    /**
     * Rimuove dalla rete Refined Storage gli item di materia virtuale che non dovrebbero essere presenti
     * 
     * @param network La rete RS in cui cercare e rimuovere gli item
     */
    public void cleanupVirtualMatterItems(Network network) {
        if (network == null) {
            return;
        }

        try {
            LOGGER.debug("[CLEANUP] Ricerca e rimozione degli item di materia virtuale non gestiti dalla bridge");
            
            // Ottieni il RootStorage dalla rete
            StorageNetworkComponent component = network.getComponent(StorageNetworkComponent.class);
            if (!(component instanceof RootStorage rootStorage)) {
                LOGGER.debug("[CLEANUP] Impossibile accedere al RootStorage");
                return;
            }
            
            // Usa la reflection per accedere ai metodi necessari
            // Ottieni tutti gli item presenti nel network
            try {
                Method getItemsMethod = rootStorage.getClass().getMethod("getAll");
                @SuppressWarnings("unchecked")
                Collection<ResourceAmount> allItems = (Collection<ResourceAmount>) getItemsMethod.invoke(rootStorage);
                
                if (allItems == null || allItems.isEmpty()) {
                    LOGGER.debug("[CLEANUP] Nessun item trovato nella rete");
                    return;
                }
                
                LOGGER.debug("[CLEANUP] Analisi di {} item nella rete", allItems.size());
                
                // Per ogni ResourceAmount, verifica se la ResourceKey rappresenta un item di materia virtuale
                int itemsDeleted = 0;
                for (ResourceAmount resourceAmount : allItems) {
                    ResourceKey resource = resourceAmount.resource();
                    
                    // Controlla se l'item corrisponde a un item di materia virtuale
                    if (isVirtualMatterItem(resource)) {
                        // Se non è nella nostra lista di item virtuali gestiti, rimuovilo
                        if (!virtualItems.containsKey(resource)) {
                            LOGGER.warn("[CLEANUP] Rilevato item di materia virtuale non gestito: {}", resource);
                            
                            // Usa il metodo extract di RootStorage per rimuovere l'item
                            Method extractMethod = rootStorage.getClass().getMethod("extract", ResourceKey.class, long.class, Action.class, Actor.class);
                            long extracted = (long) extractMethod.invoke(rootStorage, resource, resourceAmount.amount(), Action.EXECUTE, null);
                            
                            if (extracted > 0) {
                                LOGGER.warn("[CLEANUP] Rimosso {} x{} dalla rete", resource, extracted);
                                itemsDeleted++;
                            }
                        }
                    } else {
                        // Tenta anche di estrarre l'ItemStack dalla ResourceKey per verificare con ModItems
                        try {
                            Method getItemMethod = resource.getClass().getMethod("toItemStack");
                            Object itemStackObj = getItemMethod.invoke(resource);
                            
                            if (itemStackObj instanceof ItemStack stack && ModItems.isVirtualMatterItem(stack)) {
                                // Rimuovi l'item dalla rete
                                LOGGER.warn("[CLEANUP] Rilevato item di materia virtuale (via ItemStack): {}", resource);
                                
                                Method extractMethod = rootStorage.getClass().getMethod("extract", ResourceKey.class, long.class, Action.class, Actor.class);
                                long extracted = (long) extractMethod.invoke(rootStorage, resource, resourceAmount.amount(), Action.EXECUTE, null);
                                
                                if (extracted > 0) {
                                    LOGGER.warn("[CLEANUP] Rimosso {} x{} dalla rete", resource, extracted);
                                    itemsDeleted++;
                                }
                            }
                        } catch (Exception e) {
                            // Ignora, significa solo che non è stato possibile convertire la ResourceKey in ItemStack
                        }
                    }
                }
                
                if (itemsDeleted > 0) {
                    LOGGER.warn("[CLEANUP] Completato, rimossi {} item di materia virtuale non gestiti", itemsDeleted);
                } else {
                    LOGGER.debug("[CLEANUP] Nessun item di materia virtuale non gestito trovato");
                }
            } catch (Exception e) {
                LOGGER.error("[CLEANUP] Errore durante la pulizia degli item: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            LOGGER.error("[CLEANUP] Errore generale durante la pulizia: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Verifica se una ResourceKey rappresenta un item di materia virtuale
     */
    private boolean isVirtualMatterItem(ResourceKey resource) {
        if (resource == null) {
            return false;
        }
        
        try {
            // Estrai l'identificatore dell'item dalla ResourceKey
            String resourceStr = resource.toString().toLowerCase();
            
            // Verifica se la ResourceKey contiene l'ID del mod e uno dei nomi degli item di materia
            return resourceStr.contains(RepRSBridge.MOD_ID) && 
                  (resourceStr.contains("earth") || 
                   resourceStr.contains("nether") || 
                   resourceStr.contains("organic") || 
                   resourceStr.contains("ender") || 
                   resourceStr.contains("metallic") || 
                   resourceStr.contains("precious") || 
                   resourceStr.contains("living") || 
                   resourceStr.contains("quantum"));
        } catch (Exception e) {
            LOGGER.error("Errore durante la verifica dell'item di materia: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Metodo serverTick che viene chiamato periodicamente per aggiornare lo stato
     * Questo metodo si occupa di sincronizzare le quantità di materia tra le reti
     * 
     * Il metodo ottiene il MatterNetwork tramite l'entità principale,
     * legge le quantità di materia usando ReplicationHelper e le rende
     * visibili nel network Refined Storage come item virtuali.
     */
    public void serverTick(Level level, BlockPos pos, BlockState state, RepRSBridgeBlockEntityF blockEntity) {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        // Se siamo in fase di salvataggio, salta completamente il tick
        if (worldSaving || !safeToUpdate) {
            return;
        }
        
        LOGGER.warn("[TICK] Avvio sincronizzazione RS <-> Replication alla posizione {}", pos);
        
        // Sincronizza il MatterNetwork dall'entità principale
        syncMatterNetwork();
        
        // Ottieni il MatterNetwork
        MatterNetwork matterNetwork = getMatterNetwork();
        
        // Verifica se siamo connessi alla rete Refined Storage
        Network rsNetwork = mainNetworkNode.getNetwork();
        if (rsNetwork == null) {
            LOGGER.warn("[TICK] Nessuna connessione alla rete Refined Storage disponibile");
            
            // Se non c'è connessione, tenta una riconnessione solo ogni 20 tick (1 secondo)
            // per evitare di sovraccaricare il sistema con tentativi continui
            if (level.getGameTime() % 20 == 0) {
                LOGGER.debug("[TICK] Tentativo periodico di riconnessione alla rete RS");
                reconnectToNetwork();
            }
            return;
        }
        
        LOGGER.warn("[TICK] Connessione alla rete Refined Storage stabilita");
        
        // Prima di aggiornare gli item, rimuovi qualsiasi item di materia virtuale non gestito
        try {
            cleanupVirtualMatterItems(rsNetwork);
        } catch (Exception e) {
            LOGGER.error("[TICK] Errore durante la pulizia degli item virtuali: {}", e.getMessage());
            // Non interrompere l'esecuzione in caso di errore nella pulizia
        }
        
        // Verifica che il nostro storage virtuale sia registrato nella rete RS
        try {
            boolean storageRegistrato = false;
            if (rsNetwork.getComponent(StorageNetworkComponent.class) instanceof RootStorage rootStorage) {
                storageRegistrato = rootStorage.hasSource(s -> s == virtualStorage);
            }
            
            // Se lo storage non è registrato, registralo
            if (!storageRegistrato) {
                LOGGER.debug("[TICK] Storage virtuale non registrato, registro ora");
                registerVirtualStorage(rsNetwork);
            }
        } catch (Exception e) {
            LOGGER.error("[TICK] Errore durante la verifica/registrazione dello storage: {}", e.getMessage());
        }
        
        // Prima di aggiungere nuovi item, pulisci lo storage virtuale
        try {
            clearVirtualItems();
        } catch (Exception e) {
            LOGGER.error("[TICK] Errore durante la pulizia dello storage virtuale: {}", e.getMessage());
        }
        
        if (matterNetwork != null) {
            LOGGER.warn("[TICK] MatterNetwork trovato, inizio sincronizzazione materia");
            
            // Ottieni le quantità di materia dalla rete Replication
            List<IMatterType> matterTypes = ReplicationHelper.getMatterTypes();
            int itemsSincronizzati = 0;
            
            // Per ogni tipo di materia, crea un item virtuale
            for (IMatterType matterType : matterTypes) {
                try {
                    long amount = ReplicationHelper.getMatterAmount(matterNetwork, matterType);
                    
                    // Limita la quantità massima per evitare problemi di lag
                    long originalAmount = amount;
                    amount = Math.min(amount, 1000000); // Limita a 1.000.000 di item
                    
                    // Se c'è materia disponibile, aggiungila al network RS
                    if (amount > 0) {
                        LOGGER.debug("[TICK] Trovata materia {} con quantità {} (limitata da {})", 
                                    matterType.getName(), amount, originalAmount);
                        
                        Item matterItem = null;
                        String matterName = matterType.getName().toLowerCase();
                        
                        // Associa il tipo di materia all'item corrispondente
                        if (matterName.equals("earth")) matterItem = ModItems.EARTH_MATTER.get();
                        else if (matterName.equals("nether")) matterItem = ModItems.NETHER_MATTER.get();
                        else if (matterName.equals("organic")) matterItem = ModItems.ORGANIC_MATTER.get();
                        else if (matterName.equals("ender")) matterItem = ModItems.ENDER_MATTER.get();
                        else if (matterName.equals("metallic")) matterItem = ModItems.METALLIC_MATTER.get();
                        else if (matterName.equals("precious")) matterItem = ModItems.PRECIOUS_MATTER.get();
                        else if (matterName.equals("living")) matterItem = ModItems.LIVING_MATTER.get();
                        else if (matterName.equals("quantum")) matterItem = ModItems.QUANTUM_MATTER.get();
                        
                        if (matterItem != null) {
                            try {
                                // Crea un ItemStack con l'item di materia
                                ItemStack matterStack = new ItemStack(matterItem);
                                
                                // Aggiungi l'item alla mappa degli item virtuali
                                LOGGER.debug("[TICK] Tentativo di aggiungere item virtuale {} con quantità {}", 
                                           matterItem.getDescriptionId(), amount);
                                
                                // Aggiungi l'item virtuale usando il metodo configurato
                                boolean success = addVirtualItemToNetwork(matterStack, (int)amount);
                                if (success) {
                                    itemsSincronizzati++;
                                }
                                LOGGER.debug("[TICK] Aggiunta di {} {} con esito {}", 
                                           amount, matterItem.getDescriptionId(), success ? "SUCCESSO" : "FALLIMENTO");
                            } catch (Exception e) {
                                LOGGER.error("[TICK] Errore durante la creazione dell'item virtuale {}: {}", 
                                             matterItem.getDescriptionId(), e.getMessage());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("[TICK] Errore processando il tipo di materia {}: {}", 
                                 matterType.getName(), e.getMessage());
                }
            }
            
            // Applica tutte le modifiche e forza un aggiornamento dello storage
            try {
                virtualStorage.syncFromVirtualItems();
                
                // Forza un aggiornamento dello storage solo se abbiamo effettivamente sincronizzato degli item
                if (itemsSincronizzati > 0) {
                    forceStorageUpdate(rsNetwork);
                    LOGGER.debug("[TICK] Forzato aggiornamento dello storage dopo sincronizzazione di {} tipi di materia", 
                              itemsSincronizzati);
                }
                
                LOGGER.warn("[TICK] Sincronizzazione materia completata");
            } catch (Exception e) {
                LOGGER.error("[TICK] Errore durante la sincronizzazione finale: {}", e.getMessage());
            }
        } else {
            LOGGER.warn("[TICK] Nessun MatterNetwork trovato, sincronizzazione saltata");
        }
        
        // Logga lo stato degli item virtuali per debug solo ogni 20 tick (1 secondo)
        if (level.getGameTime() % 20 == 0) {
            try {
                LOGGER.debug("[STORAGE STATO] ==========================================");
                LOGGER.debug("[STORAGE STATO] Numero item presenti: {}", virtualItems.size());
                LOGGER.debug("[STORAGE STATO] Quantità totale: {}", virtualStorage.getStored());
                
                // Logga ogni elemento dello storage (limita a 10 per evitare spam)
                int count = 0;
                for (ResourceAmount amount : virtualStorage.getAll()) {
                    if (count++ < 10) {
                        LOGGER.debug("[STORAGE ITEM] {} x {}", amount.resource(), amount.amount());
                    } else if (count == 11) {
                        LOGGER.debug("[STORAGE ITEM] ... e altri {} item", virtualStorage.getAll().size() - 10);
                        break;
                    }
                }
                LOGGER.debug("[STORAGE STATO] ==========================================");
            } catch (Exception e) {
                LOGGER.error("[STORAGE STATO] Errore durante il logging dello stato: {}", e.getMessage());
            }
        }
        
        LOGGER.warn("[TICK] Fine sincronizzazione RS <-> Replication");
    }
} 