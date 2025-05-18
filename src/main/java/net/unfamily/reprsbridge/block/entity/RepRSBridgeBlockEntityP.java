package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.node.GraphNetworkComponent;
import com.refinedmods.refinedstorage.api.storage.StorageImpl;
import com.refinedmods.refinedstorage.api.storage.tracked.TrackedStorage;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.security.Permission;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;


import com.buuz135.replication.block.tile.ReplicationMachine;
import com.buuz135.replication.calculation.client.ClientReplicationCalculation;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.calculation.MatterValue;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.annotation.Save;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.api.task.ReplicationTask;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.unfamily.reprsbridge.item.ModItems;
import com.buuz135.replication.ReplicationRegistry;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.unfamily.reprsbridge.util.ReplicationHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.unfamily.reprsbridge.block.ModBlocks;
import net.unfamily.reprsbridge.block.custom.RepRSBridgeBl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Queue;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Iterator;
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.lang.StringBuilder;
import java.util.Objects;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;


/**
 * BlockEntity for the RepRSBridge that connects the RS network with the Replication matter network
 */
public class RepRSBridgeBlockEntityP extends ReplicationMachine<RepRSBridgeBlockEntityP> {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Constant for the number of ticks before processing accumulated requests
    private static final int REQUEST_ACCUMULATION_TICKS = 100;

    // Constant for initialization delay in ticks (3 seconds = 60 ticks)
    private static final int INITIALIZATION_DELAY = 60;

    // Variable to track if the networks have been initialized
    private byte initialized = 0;

    // Counter for initialization ticks
    private int initializationTicks = 0;

    // Queue of pending patterns
    private final Queue<ResourceKey> pendingPatterns = new LinkedList<>();
    private final Map<ResourceKey, List<ResourceAmount>> pendingInputs = new HashMap<>();

    // Flag to track if the node has been created
    private boolean nodeCreated = false;

    // Flag to indicate if we should try to reconnect to networks
    private boolean shouldReconnect = false;

    // Terminal components
    @Save
    private InventoryComponent<RepRSBridgeBlockEntityP> output;
    @Save
    private int sortingTypeValue;
    @Save
    private int sortingDirection;
    @Save
    private int matterOpediaSortingTypeValue;
    @Save
    private int matterOpediaSortingDirection;
    private TerminalPlayerTracker terminalPlayerTracker;

    // Unique identifier for this block
    @Save
    private UUID blockId;

    // Map to track requests for patterns
    private final Map<UUID, Map<ItemStack, Integer>> patternRequests = new HashMap<>();
    // Map to track active tasks with source information
    private final Map<UUID, Map<String, TaskSourceInfo>> activeTasks = new HashMap<>();
    // Map to track requests by source block
    private final Map<UUID, Map<ItemStack, Integer>> patternRequestsBySource = new HashMap<>();
    // Temporary counters for crafting requests
    private final Map<UUID, Map<ItemWithSourceId, Integer>> requestCounters = new HashMap<>();
    private int requestCounterTicks = 0;

    // Timer for periodic pattern updates
    private int patternUpdateTicks = 0;
    private static final int PATTERN_UPDATE_INTERVAL = 100; // Update every 5 seconds (100 ticks)

    // Cache of matter insufficient warnings to avoid repetitions
    private Map<String, Long> lastMatterWarnings = new HashMap<>();
    // Minimum time between consecutive warnings for the same item (in ticks)
    private static final int WARNING_COOLDOWN = 600; // 30 seconds

    // Static flag to track world unloading state
    private static boolean worldUnloading = false;

    // Refined Storage network state
    private boolean connected = false;
    private boolean active = false;
    private Network rsNetwork;
    
    // Riferimento all'entità BlockEntity di Refined Storage
    private RepRSBridgeBlockEntityF refinedStorageEntity;

    // Timestamp dell'ultimo log per evitare di riempire i log
    private static long lastLogTime = 0;

    /**
     * Sets the world unloading state
     * Called from the mod main class when the server is stopping
     */
    public static void setWorldUnloading(boolean unloading) {
        worldUnloading = unloading;
        LOGGER.info("Bridge: World unloading state set to {}", unloading);
    }

    /**
     * Checks if the world is currently unloading
     */
    public static boolean isWorldUnloading() {
        return worldUnloading;
    }

    /**
     * Static method to create a ticker for this block entity.
     * Used by the block class to register the ticker.
     */
    public static <T extends BlockEntity> BlockEntityTicker<T> createTicker(Level level, BlockEntityType<T> actualType, BlockEntityType<RepRSBridgeBlockEntityP> expectedType) {
        return level.isClientSide() ? null : 
            (level1, pos, state, blockEntity) -> ((RepRSBridgeBlockEntityP) blockEntity).serverTick(level1, pos, state, (RepRSBridgeBlockEntityP)blockEntity);
    }

    /**
     * Server-side tick method to handle network connections and reconnections
     * Called by the base class ReplicationMachine
     */
    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state, RepRSBridgeBlockEntityP blockEntity) {
        if (level.isClientSide() || !(blockEntity instanceof RepRSBridgeBlockEntityP entity)) {
            return;
        }
        
        LOGGER.debug("RepRSBridgeBlockEntityP.serverTick chiamato a {}", pos);
        
        // Verifica e aggiorna l'entità RS se necessario
        entity.updateRefinedStorageEntity();
        
        // Il resto del metodo rimane invariato
        if (entity.initializationTicks < INITIALIZATION_DELAY) {
            entity.initializationTicks++;
            if (entity.initializationTicks >= INITIALIZATION_DELAY && entity.initialized == 0) {
                entity.initialized = 1;
                // Forza l'aggiornamento delle connessioni
                entity.forceNeighborUpdates();
                entity.updateConnectedState();
            }
        }
        
        // Processa le richieste di pattern accumulate
        entity.processAccumulatedRequests();
        
        // Verifica e aggiorna lo stato della connessione
        entity.updateConnectedState();
        
        // Verifica se c'è necessità di riconnettere le reti
        if (entity.shouldReconnect) {
            entity.shouldReconnect = false;
            entity.forceNeighborUpdates();
        }
    }

    /**
     * Forza manualmente l'attivazione del serverTick
     */
    public void forceServerTick() {
        if (level != null && !level.isClientSide()) {
            LOGGER.info("Bridge: Forzo manualmente l'esecuzione di serverTick a {}", worldPosition);
            serverTick(level, worldPosition, getBlockState(), this);
        }
    }

    public RepRSBridgeBlockEntityP(BlockPos pos, BlockState blockState) {
        super((BasicTileBlock<RepRSBridgeBlockEntityP>) ModBlocks.REPRSBRIDGE.get(),
                ModBlockEntities.REPRSBRIDGE_P_BE.get(),
                pos,
                blockState);

        // Generate a unique identifier for this block
        this.blockId = UUID.randomUUID();

        // Initialize terminal component
        this.terminalPlayerTracker = new TerminalPlayerTracker();
        this.sortingTypeValue = 0;
        this.sortingDirection = 1;
        this.matterOpediaSortingTypeValue = 0;
        this.matterOpediaSortingDirection = 1;

        // Initialize the output inventory component with 18 slots (9x2)
        this.output = new InventoryComponent<RepRSBridgeBlockEntityP>("output", 11, 131, 9*2)
                .setRange(9, 2)
                .setComponentHarness(this)
                .setInputFilter((stack, slot) -> true); // Allows insertion of any item
        this.addInventory(this.output);
    }

    @NotNull
    @Override
    public RepRSBridgeBlockEntityP getSelf() {
        return this;
    }

    /**
     * Check if there is another bridge in the specified direction
     * @param direction The direction to check
     * @return true if there is another bridge in the specified direction
     */
    private boolean hasBridgeInDirection(Direction direction) {
        if (level != null) {
            BlockPos neighborPos = worldPosition.relative(direction);
            return level.getBlockEntity(neighborPos) instanceof RepRSBridgeBlockEntityP;
        }
        return false;
    }

    @Override
    protected NetworkElement createElement(Level level, BlockPos pos) {
        try {
            return new DefaultMatterNetworkElement(level, pos) {
                @Override
                public boolean canConnectFrom(Direction direction) {
                    BlockPos neighborPos = pos.relative(direction);
                    if (level.getBlockEntity(neighborPos) instanceof RepRSBridgeBlockEntityP) {
                        return false;
                    }
                    return super.canConnectFrom(direction);
                }
            };
        } catch (Exception e) {
            LOGGER.error("Failed to create Replication network element: {}", e.getMessage());
            return null; // O un fallback sicuro
        }
    }

    /**
     * Called when the BlockEntity is loaded or after placement
     */
    @Override
    public void onLoad() {
        // First initialize the Replication network (as done by the base class)
        super.onLoad();
        LOGGER.info("Bridge: onLoad chiamato a {}", worldPosition);

        // Initialize the RS node if it hasn't been done
        if (!nodeCreated && level != null && !level.isClientSide()) {
            try {
                nodeCreated = true;
                forceNeighborUpdates();
                updateConnectedState();
                
                // Forza l'attivazione del tick
                forceServerTick();
                
                // Schedula un tick per garantire che il blocco venga aggiornato
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.scheduleTick(worldPosition, level.getBlockState(worldPosition).getBlock(), 10);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to initialize RS node: {}", e.getMessage());
                shouldReconnect = true;
            }
        }
    }

    /**
     * Update the visual connection state of the block
     */
    private void updateConnectedState() {
        if (level != null && !level.isClientSide()) {
            BlockState currentState = level.getBlockState(worldPosition);
            if (currentState.getBlock() == ModBlocks.REPRSBRIDGE.get()) {
                boolean isConnected = isActive() && getReplicationNetwork() != null;
                if (currentState.getValue(RepRSBridgeBl.CONNECTED) != isConnected) {
                    level.setBlock(worldPosition, currentState.setValue(
                            RepRSBridgeBl.CONNECTED, isConnected), 3);
                }
            }
        }
    }

    /**
     * Check if there is an RS controller in the network by checking if there are active RS cables adjacent
     * @return true if an active RS cable is found, which is probably connected to a controller
     */
    private boolean hasRSNetworkConnection() {
        if (level != null && !level.isClientSide()) {
            // In Refined Storage, dobbiamo controllare se il nodo è connesso a una rete
            return active;
        }
        return false;
    }

    /**
     * Force updates to adjacent blocks
     */
    private void forceNeighborUpdates() {
        if (level != null && !level.isClientSide()) {
            // Force an update of the block itself first
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

            // Then force updates to adjacent blocks
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(direction);
                BlockState neighborState = level.getBlockState(neighborPos);
                if (!neighborState.isAir()) {
                    // Notify the adjacent block first (to trigger its connections)
                    level.neighborChanged(neighborPos, getBlockState().getBlock(), worldPosition);
                }
            }
        }
    }

    /**
     * Handle when a neighboring block changes
     * Called from the RepRSBridgeBl block
     * @param fromPos Position of the changed neighbor
     */
    public void handleNeighborChanged(BlockPos fromPos) {
        if (level != null && !level.isClientSide()) {
                try {
                    // If the changed block is another bridge, ignore the update
                    Direction directionToNeighbor = null;
                    for (Direction dir : Direction.values()) {
                        if (worldPosition.relative(dir).equals(fromPos)) {
                            directionToNeighbor = dir;
                            break;
                        }
                    }

                    if (directionToNeighbor != null && level.getBlockEntity(fromPos) instanceof RepRSBridgeBlockEntityP) {
                        // Debug log disabled for production
                        // LOGGER.debug("Bridge: Ignored update from another bridge at {}", fromPos);
                        return;
                    }

                    // Forza un nuovo tentativo di connessione al network
                    LOGGER.debug("Bridge: Blocco vicino cambiato in posizione {}, aggiorno la connessione", fromPos);
                    
                    // Se abbiamo un'entità Refined Storage, assicuriamoci che sia connessa
                    if (refinedStorageEntity != null) {
                        // Notifica l'entità RS che potrebbe essere necessario riconnettersi
                        refinedStorageEntity.setChanged();
                        LOGGER.debug("Bridge: Notificata l'entità RS di aggiornare la connessione");
                    }
                    
                    // Notifica gli adiacenti che ci siamo aggiornati
                    forceNeighborUpdates();
                    updateConnectedState();
            } catch (Exception e) {
                LOGGER.error("Errore durante il handleNeighborChanged: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Explicitly disconnect this block from both networks
     * Called when the block is removed
     */
    public void disconnectFromNetworks() {
        // Disconnect from the RS network
        if (level != null && !level.isClientSide()) {
            // In Refined Storage, dobbiamo rimuovere il nodo dalla rete
            nodeCreated = false;
            connected = false;
            active = false;
            rsNetwork = null;
        }

        // The disconnection from the Replication network is handled in super.setRemoved()
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide()) {
            // Destroy the node when the block is removed
            nodeCreated = false;
            connected = false;
            active = false;
            rsNetwork = null;
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide()) {
            // Destroy the node when the chunk is unloaded
            nodeCreated = false;
            connected = false;
            active = false;
            rsNetwork = null;
            shouldReconnect = true; // Mark for reconnection when the chunk is reloaded
        }
        super.onChunkUnloaded();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Save the state of the RS node
        tag.putBoolean("connected", connected);
        tag.putBoolean("active", active);
        // Also save the node creation flag
        tag.putBoolean("nodeCreated", nodeCreated);
        // Save the reconnection flag
        tag.putBoolean("shouldReconnect", shouldReconnect);
        // Save the block's unique identifier
        if (blockId != null) {
            tag.putUUID("blockId", blockId);
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Load the state of the RS node
        if (tag.contains("connected")) {
            connected = tag.getBoolean("connected");
        }
        if (tag.contains("active")) {
            active = tag.getBoolean("active");
        }
        // Load the node creation flag
        if (tag.contains("nodeCreated")) {
            nodeCreated = tag.getBoolean("nodeCreated");
        }
        // Load the reconnection flag
        if (tag.contains("shouldReconnect")) {
            shouldReconnect = tag.getBoolean("shouldReconnect");
        }
        // Load the block's unique identifier
        if (tag.contains("blockId")) {
            blockId = tag.getUUID("blockId");
        } else {
            // If no ID exists, generate a new one
            blockId = UUID.randomUUID();
        }
    }

    /**
     * Utility method to get the Replication network with consistent naming
     * @return The Replication matter network
     */
    public MatterNetwork getReplicationNetwork() {
        if (level == null || level.isClientSide()) {
            return null;
        }
        try {
            NetworkManager networkManager = NetworkManager.get(level);
            if (networkManager == null) {
                LOGGER.warn("NetworkManager not found");
                return null;
            }
            NetworkElement element = networkManager.getElement(worldPosition);
            if (element == null) {
                element = createElement(level, worldPosition);
                if (element != null) {
                    networkManager.addElement(element);
                    forceNeighborUpdates();
                }
            }
            if (element != null && element.getNetwork() instanceof MatterNetwork matterNetwork) {
                return matterNetwork;
            }
        } catch (Exception e) {
            LOGGER.error("Error accessing Replication network: {}", e.getMessage());
        }
        return null;
    }

    // =================== Utility methods ===================

    public boolean isActive() {
        return active;
    }

    // =================== Terminal implementation ===================

    @Override
    public ItemInteractionResult onActivated(Player playerIn, InteractionHand hand, Direction facing, double hitX, double hitY, double hitZ) {
        if (!level.isClientSide() && playerIn instanceof ServerPlayer serverPlayer) {
            // Apri il GUI del terminale
            openGui(playerIn);
            
            // Aggiorna i pattern in RS
            updateAvailablePatterns();
        }
        return ItemInteractionResult.SUCCESS;
    }

    public TerminalPlayerTracker getTerminalPlayerTracker() {
        return terminalPlayerTracker;
    }

    public InventoryComponent<RepRSBridgeBlockEntityP> getOutput() {
        return output;
    }

    /**
     * Method to receive items from the Replicator
     * @param stack The item stack to insert
     * @return true if the insertion was successful, false otherwise
     */
    public boolean receiveItemFromReplicator(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // Debug log disabled for production
        // LOGGER.debug("Bridge: Received item from replicator: " + stack.getDisplayName().getString());

        // First try to insert directly into the output inventory
        ItemStack remainingStack = stack.copy();
        ItemStack notInserted = ItemHandlerHelper.insertItem(this.output, remainingStack, false);

        // If insertion was completely successful
        if (notInserted.isEmpty()) {
            // Notify the change
            this.setChanged();
            return true;
        }

        // If we inserted some items but not all
        if (notInserted.getCount() < remainingStack.getCount()) {
            this.setChanged();
            return true;
        }

        return false;
    }

    /**
     * Handles a completed task from the Replication network
     * @param task The completed task
     * @param stack The produced item stack
     */
    public void handleCompletedTask(IReplicationTask task, ItemStack stack) {
        // Try to insert the item into the RS network or local inventory
        boolean inserted = receiveItemFromReplicator(stack);

        if (!inserted) {
            // Debug log disabled for production
            // LOGGER.debug("Bridge: Failed to insert item from completed task: {}", stack.getDisplayName().getString());
        }

        // Update request counters
        UUID sourceId = this.blockId;
        if (task.getSource().equals(this.worldPosition)) {
            // Decrement the counter for this pattern in source-specific requests
            Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.getOrDefault(sourceId, new HashMap<>());
            int currentCount = sourceRequests.getOrDefault(stack, 0);
            if (currentCount > 0) {
                sourceRequests.put(stack, currentCount - 1);
                patternRequestsBySource.put(sourceId, sourceRequests);
            }

            // Also update the global counter for backward compatibility
            Map<ItemStack, Integer> globalRequests = patternRequests.getOrDefault(sourceId, new HashMap<>());
            int currentGlobalCount = globalRequests.getOrDefault(stack, 0);
            if (currentGlobalCount > 0) {
                globalRequests.put(stack, currentGlobalCount - 1);
                patternRequests.put(sourceId, globalRequests);
                //LOGGER.info("Bridge: Task completed for {}, remaining {} active requests",
                //    pattern.getItem().getDescriptionId(), currentGlobalCount - 1);
            }
        }
    }

    // =================== Utility methods ===================

    public static class TerminalPlayerTracker {
        private List<ServerPlayer> players;
        private List<UUID> uuidsToRemove;
        private List<ServerPlayer> playersToAdd;

        public TerminalPlayerTracker() {
            this.players = new ArrayList<>();
            this.uuidsToRemove = new ArrayList<>();
            this.playersToAdd = new ArrayList<>();
        }

        public void checkIfValid() {
            var output = new ArrayList<>(playersToAdd);
            var input = new ArrayList<>(players);
            for (ServerPlayer serverPlayer : input) {
                if (!this.uuidsToRemove.contains(serverPlayer.getUUID())) {
                    output.add(serverPlayer);
                }
            }
            this.players = output;
            this.uuidsToRemove = new ArrayList<>();
            this.playersToAdd = new ArrayList<>();
        }

        public void removePlayer(ServerPlayer serverPlayer) {
            this.uuidsToRemove.add(serverPlayer.getUUID());
        }

        public void addPlayer(ServerPlayer serverPlayer) {
            this.playersToAdd.add(serverPlayer);
        }

        public List<ServerPlayer> getPlayers() {
            return players;
        }
    }

    // Map IMatterType to virtual item
    private Item getItemForMatterType(IMatterType type) {
        String name = type.getName();
        if (name.equalsIgnoreCase("earth")) return ModItems.EARTH_MATTER.get();
        if (name.equalsIgnoreCase("nether")) return ModItems.NETHER_MATTER.get();
        if (name.equalsIgnoreCase("organic")) return ModItems.ORGANIC_MATTER.get();
        if (name.equalsIgnoreCase("ender")) return ModItems.ENDER_MATTER.get();
        if (name.equalsIgnoreCase("metallic")) return ModItems.METALLIC_MATTER.get();
        if (name.equalsIgnoreCase("precious")) return ModItems.PRECIOUS_MATTER.get();
        if (name.equalsIgnoreCase("living")) return ModItems.LIVING_MATTER.get();
        if (name.equalsIgnoreCase("quantum")) return ModItems.QUANTUM_MATTER.get();
        return null;
    }

    // Utility to recognize virtual matter items
    private boolean isVirtualMatterItem(Item item) {
        // Check if the item is a virtual matter item
        // Invece di usare ReplicationRegistry.getMatterTypes(), accediamo direttamente ai tipi noti
        List<IMatterType> matterTypes = List.of(
            ReplicationRegistry.Matter.EARTH.get(),
            ReplicationRegistry.Matter.NETHER.get(),
            ReplicationRegistry.Matter.ORGANIC.get(),
            ReplicationRegistry.Matter.ENDER.get(),
            ReplicationRegistry.Matter.METALLIC.get(),
            ReplicationRegistry.Matter.PRECIOUS.get(),
            ReplicationRegistry.Matter.LIVING.get(),
            ReplicationRegistry.Matter.QUANTUM.get()
        );
        
        for (IMatterType matterType : matterTypes) {
            Item matterItem = getItemForMatterType(matterType);
            if (matterItem != null && matterItem.equals(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the unique identifier for this block
     * @return the UUID of this block
     */
    public UUID getBlockId() {
        return blockId;
    }

    /**
     * Get the number of active requests for this specific block
     * @return the total number of active requests for this block
     */
    public int getActiveRequestsForThisBlock() {
        Map<ItemStack, Integer> requests = patternRequestsBySource.getOrDefault(this.blockId, new HashMap<>());
        return requests.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Get the number of active requests for a specific item from this block
     * @param item the item to check
     * @return the number of active requests for the item from this block
     */
    public int getActiveRequestsForItem(Item item) {
        Map<ItemStack, Integer> requests = patternRequestsBySource.getOrDefault(this.blockId, new HashMap<>());
        return requests.entrySet().stream()
                .filter(entry -> entry.getKey().getItem() == item)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    /**
     * Get the total number of active requests across all blocks
     * @return the total number of active requests
     */
    public int getTotalActiveRequests() {
        int total = 0;
        for (Map<ItemStack, Integer> sourceRequests : patternRequestsBySource.values()) {
            total += sourceRequests.values().stream().mapToInt(Integer::intValue).sum();
        }
        return total;
    }

    /**
     * Helper class to track an item with its source block ID
     */
    public static class ItemWithSourceId {
        private final ItemStack itemStack;
        private final UUID sourceId;

        public ItemWithSourceId(ItemStack itemStack, UUID sourceId) {
            this.itemStack = itemStack.copy(); // Create a copy to avoid reference issues
            this.sourceId = sourceId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSourceId() {
            return sourceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemWithSourceId that = (ItemWithSourceId) o;
            return ItemStack.matches(itemStack, that.itemStack) &&
                    Objects.equals(sourceId, that.sourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemStack.getItem(), sourceId);
        }
    }

    /**
     * Helper class to track task information including source block
     */
    public static class TaskSourceInfo {
        private final ItemStack itemStack;
        private final UUID sourceId;

        public TaskSourceInfo(ItemStack itemStack, UUID sourceId) {
            this.itemStack = itemStack.copy(); // Create a copy to avoid reference issues
            this.sourceId = sourceId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSourceId() {
            return sourceId;
        }
    }

    /**
     * Metodo chiamato dalla block entity di Refined Storage quando lo stato della rete cambia
     * @param isConnected Se la rete è connessa
     * @param network L'oggetto Network di Refined Storage
     */
    public void onRefinedStorageNetworkChanged(boolean isConnected, Network network) {
        this.active = isConnected;
        this.connected = isConnected;
        this.rsNetwork = network;
        
        // Aggiorna lo stato visivo
        updateConnectedState();
    }

    /**
     * Aggiorna l'entità Refined Storage, ricreandola se necessario
     */
    public void updateRefinedStorageEntity() {
        if (level == null || level.isClientSide()) {
            return;
        }

        try {
            // Verifica se il chunk è caricato prima di procedere
            if (!level.hasChunkAt(worldPosition)) {
                LOGGER.debug("Il chunk per la posizione {} non è caricato, non posso aggiornare l'entità RS", worldPosition);
                return;
            }

            // Verifica se il blocco può fornire l'entità RS
            if (level.getBlockState(worldPosition).getBlock() instanceof RepRSBridgeBl block) {
                RepRSBridgeBlockEntityF blockRefinedEntity = block.getRefinedStorageEntity();
                if (blockRefinedEntity != null && !blockRefinedEntity.isRemoved()) {
                    // L'entità è già presente nel blocco, la utilizziamo
                    if (refinedStorageEntity != blockRefinedEntity) {
                        refinedStorageEntity = blockRefinedEntity;
                        refinedStorageEntity.setMainEntity(this);
                        LOGGER.debug("Utilizzata entità RS esistente dal blocco");
                    }
                    return;
                }
            }

            // Prima verifica se possiamo recuperare un'entità esistente nel mondo
            BlockEntity existingEntity = level.getBlockEntity(worldPosition);
            if (existingEntity instanceof RepRSBridgeBlockEntityF) {
                // Se esiste già un'entità RS nella stessa posizione ma non è collegata a questa entità principale
                if (refinedStorageEntity != existingEntity) {
                    LOGGER.debug("Trovata entità RS esistente nel mondo alla posizione {}, la riutilizzo", worldPosition);
                    refinedStorageEntity = (RepRSBridgeBlockEntityF) existingEntity;
                    refinedStorageEntity.setMainEntity(this);
                    
                    // Aggiorna anche il riferimento nel blocco
                    if (level.getBlockState(worldPosition).getBlock() instanceof RepRSBridgeBl block) {
                        block.updateEntities(this, refinedStorageEntity);
                    }
                    
                    return;
                }
            }

            // Se abbiamo già un'entità RS, verifichiamo che sia valida
            if (refinedStorageEntity != null) {
                // Verifica se l'entità è valida e correttamente registrata nel mondo
                if (!refinedStorageEntity.isRemoved()) {
                    if (refinedStorageEntity.getLevel() == null) {
                        // L'entità esiste ma non è registrata nel mondo
                        LOGGER.debug("Ripristino l'entità RS alla posizione {}", worldPosition);
                        
                        // Prima di registrare, verifica se il chunk è valido
                        if (level.getChunkAt(worldPosition) != null) {
                            // Rimuovi eventuali altre entità BlockEntity dello stesso tipo per evitare conflitti
                            if (existingEntity instanceof RepRSBridgeBlockEntityF && existingEntity != refinedStorageEntity) {
                                LOGGER.debug("Rimosso conflitto con entità RS duplicata");
                            }
                            
                            // Registra l'entità nel mondo
                            level.setBlockEntity(refinedStorageEntity);
                            
                            // Notifica esplicita al chunk
                            if (level instanceof ServerLevel serverLevel) {
                                serverLevel.getChunkSource().blockChanged(worldPosition);
                                // Marca il chunk come modificato
                                level.getChunkAt(worldPosition).setUnsaved(true);
                            }
                        }
                    }
                    // Notifica l'entità RS di aggiornare la sua connessione
                    refinedStorageEntity.setChanged();
                    
                    // Aggiorna anche il riferimento nel blocco
                    if (level.getBlockState(worldPosition).getBlock() instanceof RepRSBridgeBl block) {
                        block.updateEntities(this, refinedStorageEntity);
                    }
                    
                    return; // Entità esistente e valida, non è necessario ricrearla
                }
            }
            
            // A questo punto dobbiamo creare una nuova entità RS
            try {
                BlockState state = level.getBlockState(worldPosition);
                RepRSBridgeBlockEntityF rsEntity = new RepRSBridgeBlockEntityF(worldPosition, state);
                rsEntity.setMainEntity(this);
                this.setRefinedStorageEntity(rsEntity);
                
                // Registra l'entità RS nel mondo
                level.setBlockEntity(rsEntity);
                
                // Verifica che l'entità sia stata registrata correttamente
                BlockEntity checkEntity = level.getBlockEntity(worldPosition);
                if (checkEntity instanceof RepRSBridgeBlockEntityP) {
                    LOGGER.debug("L'entità RS non è stata registrata correttamente, il tipo è RepRSBridgeBlockEntityP");
                    
                    // In questo caso, potrebbe esserci un problema con il sistema di BlockEntity multiple
                    // Registra l'entità nuovamente con un approccio alternativo se disponibile
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.getChunkSource().blockChanged(worldPosition);
                        level.getChunkAt(worldPosition).setUnsaved(true);
                    }
                }
                
                // Notifica esplicita al chunk
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.getChunkSource().blockChanged(worldPosition);
                    level.getChunkAt(worldPosition).setUnsaved(true);
                }
                
                // Aggiorna anche il riferimento nel blocco
                if (state.getBlock() instanceof RepRSBridgeBl block) {
                    block.updateEntities(this, rsEntity);
                }
                
                LOGGER.debug("Creata nuova entità RS alla posizione {}", worldPosition);
            } catch (Exception e) {
                LOGGER.error("Errore durante la creazione dell'entità RS: {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            LOGGER.error("Errore generale durante l'aggiornamento dell'entità RS: {}", e.getMessage(), e);
        }
    }

    /**
     * Imposta l'entità BlockEntity di Refined Storage
     */
    public void setRefinedStorageEntity(RepRSBridgeBlockEntityF entity) {
        this.refinedStorageEntity = entity;
        LOGGER.debug("Impostata entità Refined Storage per l'entità principale");
    }
    
    /**
     * Ottiene l'entità BlockEntity di Refined Storage
     */
    public RepRSBridgeBlockEntityF getRefinedStorageEntity() {
        return this.refinedStorageEntity;
    }

    /**
     * Ottiene la quantità di materia di un certo tipo nel network
     * Implementazione reale che utilizza l'API di Replication
     * 
     * @param matterType Il tipo di materia da ottenere
     * @return La quantità reale di materia
     */
    private long getMatterAmount(IMatterType matterType) {
        // Verifica se esiste un network di Replication
        MatterNetwork matterNetwork = getReplicationNetwork();
        if (matterNetwork == null) {
            return 0;
        }
        
        try {
            // In Replication, viene utilizzato il metodo calculateMatterAmount
            // per ottenere la quantità totale di materia di un certo tipo nel network
            return matterNetwork.calculateMatterAmount(matterType);
                    } catch (Exception e) {
            LOGGER.error("Errore durante l'accesso alla quantità di materia: {}", e.getMessage(), e);
            // In caso di errore, ritorna 0 (come richiesto)
            return 0;
        }
    }

    /**
     * Aggiorna i pattern disponibili nella rete RS
     */
    private void updateAvailablePatterns() {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        // Per ora questo metodo è vuoto ma potrebbe essere implementato in futuro
        // per sincronizzare i pattern tra Replication e Refined Storage
        LOGGER.debug("Aggiornamento dei pattern disponibili");
    }

    /**
     * Ottiene la mappa dei pattern disponibili nel network
     * Implementazione reale che utilizza l'API di Replication
     * 
     * @return Mappa di pattern nel formato item_id -> (matter_type -> amount)
     */
    private Map<String, Map<String, Integer>> getAvailablePatterns() {
        Map<String, Map<String, Integer>> patterns = new HashMap<>();
        
        // Verifica se esiste un network di Replication
        MatterNetwork matterNetwork = getReplicationNetwork();
        if (matterNetwork == null) {
            return patterns;
        }
        
        try {
            // Utilizza la classe helper per ottenere i pattern
            return ReplicationHelper.getAvailablePatterns(matterNetwork, level);
        } catch (Exception e) {
            LOGGER.error("Errore durante l'ottenimento dei pattern: {}", e.getMessage(), e);
            // Non forniamo pattern di esempio in caso di errore, come richiesto
        }
        
        return patterns;
    }

    /**
     * Verifica e mantiene le connessioni ai network di Replication e Refined Storage
     */
    private void ensureNetworkConnections() {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        // Verifica la connessione al network di Replication
        MatterNetwork matterNetwork = getReplicationNetwork();
        if (matterNetwork == null) {
            try {
                // Tenta di ricreare la connessione
                NetworkElement element = createElement(level, worldPosition);
                if (element != null) {
                    NetworkManager.get(level).addElement(element);
                    forceNeighborUpdates();
                }
            } catch (Exception e) {
                LOGGER.debug("Non è stato possibile connettersi al network di Replication: {}", e.getMessage());
            }
        }
        
        // Verifica anche la connessione a Refined Storage attraverso l'entità secondaria
        if (refinedStorageEntity != null) {
            // Assicurati che l'entità RS sia registrata correttamente
            if (!refinedStorageEntity.isRemoved() && refinedStorageEntity.getLevel() == null) {
                // L'entità esiste ma non è registrata nel mondo
                LOGGER.debug("Ripristino l'entità RS alla posizione {}", worldPosition);
                level.setBlockEntity(refinedStorageEntity);
            }
            
            // Notifica l'entità RS di aggiornare la sua connessione se necessario
            refinedStorageEntity.setChanged();
        } else if (!level.isClientSide()) {
            // Se l'entità RS non esiste, creala
            try {
                BlockState state = level.getBlockState(worldPosition);
                RepRSBridgeBlockEntityF rsEntity = new RepRSBridgeBlockEntityF(worldPosition, state);
                rsEntity.setMainEntity(this);
                this.setRefinedStorageEntity(rsEntity);
                level.setBlockEntity(rsEntity);
                LOGGER.debug("Creata nuova entità RS alla posizione {}", worldPosition);
            } catch (Exception e) {
                LOGGER.error("Errore durante la creazione dell'entità RS: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Processa le richieste di pattern accumulate e invia task al network di Replication
     */
    private void processAccumulatedRequests() {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        // Ottieni il network di Replication
        MatterNetwork matterNetwork = getReplicationNetwork();
        if (matterNetwork == null) {
            // Non possiamo processare richieste senza una connessione al network
            return;
        }
        
        // Processa le richieste pendenti per ogni fonte
        for (Map.Entry<UUID, Map<ItemWithSourceId, Integer>> entry : requestCounters.entrySet()) {
            UUID sourceId = entry.getKey();
            Map<ItemWithSourceId, Integer> requests = entry.getValue();
            
            // Processa le richieste per questa fonte
            Iterator<Map.Entry<ItemWithSourceId, Integer>> iterator = requests.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ItemWithSourceId, Integer> requestEntry = iterator.next();
                ItemWithSourceId itemWithSource = requestEntry.getKey();
                int count = requestEntry.getValue();
                
                if (count <= 0) {
                    // Rimuovi le richieste completate
                    iterator.remove();
                    continue;
                }
                
                // Crea un task di replicazione per questa richiesta
                ItemStack itemStack = itemWithSource.getItemStack();
                boolean taskCreated = createReplicationTask(itemStack, sourceId);
                
                if (taskCreated) {
                    // Decrementa il contatore se il task è stato creato con successo
                    requestEntry.setValue(count - 1);
                }
            }
        }
    }
    
    /**
     * Crea un task di replicazione per un item specifico
     * Versione semplificata che usa un approccio più generico per compatibilità con l'API
     * 
     * @param itemStack L'item da replicare
     * @param sourceId L'ID del blocco che ha richiesto la replicazione
     * @return true se il task è stato creato con successo
     */
    private boolean createReplicationTask(ItemStack itemStack, UUID sourceId) {
        if (level == null || level.isClientSide() || itemStack.isEmpty()) {
            return false;
        }
        
        try {
        // Ottieni il network di Replication
        MatterNetwork matterNetwork = getReplicationNetwork();
        if (matterNetwork == null) {
                return false;
            }
            
            // Verifica se c'è abbastanza materia (solo come esempio, non un controllo reale)
            // In una implementazione reale, dovrebbe controllare i valori di materia del pattern e confrontarli con quelli disponibili
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
            boolean hasSufficientMatter = true; // Placeholder
            
            if (!hasSufficientMatter) {
                warnAboutInsufficientMatter(itemStack);
                return false;
            }
            
            // Simulazione della creazione del task
            // Una implementazione semplificata per compatibilità
            // Qui simuliamo un task creato correttamente
            String taskId = "task_" + UUID.randomUUID().toString();
            Map<String, TaskSourceInfo> sourceTasks = activeTasks.computeIfAbsent(sourceId, k -> new HashMap<>());
            sourceTasks.put(taskId, new TaskSourceInfo(itemStack, sourceId));
            
            LOGGER.debug("Task di replicazione creato per: {}", itemStack.getDisplayName().getString());
            return true;
        } catch (Exception e) {
            LOGGER.error("Errore durante la creazione del task di replicazione: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Getter per il contatore di tick delle richieste
     */
    public int getRequestCounterTicks() {
        return requestCounterTicks;
    }

    /**
     * Ottiene un'istanza di IMatterType dal nome
     */
    @Nullable
    private IMatterType getMatterTypeByName(String name) {
        if (name.equalsIgnoreCase("earth")) return ReplicationRegistry.Matter.EARTH.get();
        if (name.equalsIgnoreCase("nether")) return ReplicationRegistry.Matter.NETHER.get();
        if (name.equalsIgnoreCase("organic")) return ReplicationRegistry.Matter.ORGANIC.get();
        if (name.equalsIgnoreCase("ender")) return ReplicationRegistry.Matter.ENDER.get();
        if (name.equalsIgnoreCase("metallic")) return ReplicationRegistry.Matter.METALLIC.get();
        if (name.equalsIgnoreCase("precious")) return ReplicationRegistry.Matter.PRECIOUS.get();
        if (name.equalsIgnoreCase("living")) return ReplicationRegistry.Matter.LIVING.get();
        if (name.equalsIgnoreCase("quantum")) return ReplicationRegistry.Matter.QUANTUM.get();
        return null;
    }

    /**
     * Invia un avviso riguardo l'insufficiente materia per replicare un item
     */
    private void warnAboutInsufficientMatter(ItemStack itemStack) {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        // Limita gli avvisi per evitare spam nel log
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        String itemKey = itemId.toString();
        long currentTime = System.currentTimeMillis();
        long lastWarningTime = lastMatterWarnings.getOrDefault(itemKey, 0L);
        
        if (currentTime - lastWarningTime > WARNING_COOLDOWN * 50) { // 50 ms per tick
            LOGGER.warn("Materia insufficiente per replicare: {}", itemStack.getDisplayName().getString());
            lastMatterWarnings.put(itemKey, currentTime);
        }
    }

    /**
     * Forza un aggiornamento della rete
     * Questo metodo verifica lo stato di entrambe le reti e aggiorna le entità necessarie
     */
    public void forceNetworkUpdate() {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        try {
            LOGGER.debug("Forzo aggiornamento delle reti alla posizione {}", worldPosition);
            
            // Forza un ricaricamento del chunk
            if (level.hasChunkAt(worldPosition)) {
                level.getChunkAt(worldPosition).setUnsaved(true);
                
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.getChunkSource().blockChanged(worldPosition);
                }
            }
            
            // Verifica la rete Replication
            MatterNetwork matterNetwork = getReplicationNetwork();
            if (matterNetwork == null) {
                try {
                    // Tenta di ricreare la connessione
                    NetworkElement element = createElement(level, worldPosition);
                    if (element != null) {
                        NetworkManager.get(level).addElement(element);
                        forceNeighborUpdates();
                    }
                } catch (Exception e) {
                    LOGGER.debug("Non è stato possibile connettersi al network di Replication: {}", e.getMessage());
                }
            }
            
            // Verifica la rete Refined Storage
            updateRefinedStorageEntity();
            
            // Se abbiamo un'entità RS, forza una riconnessione
            if (refinedStorageEntity != null) {
                refinedStorageEntity.reconnectToNetwork();
            }
            
            // Forza un aggiornamento del blocco
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            
            // Notifica i blocchi adiacenti
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = worldPosition.relative(direction);
                level.neighborChanged(neighborPos, getBlockState().getBlock(), worldPosition);
            }
            
            // Aggiorna lo stato visivo
            updateConnectedState();
            
            LOGGER.debug("Aggiornamento forzato delle reti completato");
        } catch (Exception e) {
            LOGGER.error("Errore durante l'aggiornamento forzato delle reti: {}", e.getMessage(), e);
        }
    }

    /**
     * Chiamato quando il blocco viene tick-ato dal sistema di tick schedulati
     */
    public void onScheduledTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        
        // Aggiorna l'entità Refined Storage
        updateRefinedStorageEntity();
        
        // Verifica lo stato della connessione
        updateConnectedState();
        
        // Se abbiamo un'entità RS, verifica anche il suo stato
        if (refinedStorageEntity != null && refinedStorageEntity.getLevel() != null) {
            refinedStorageEntity.setChanged();
        }
    }
}