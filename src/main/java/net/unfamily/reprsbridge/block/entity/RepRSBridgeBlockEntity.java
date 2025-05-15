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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import java.util.concurrent.Future;
import java.util.concurrent.CompletableFuture;
import java.lang.StringBuilder;
import java.util.Objects;


/**
 * BlockEntity for the RepRSBridge that connects the RS network with the Replication matter network
 */
public class RepRSBridgeBlockEntity extends ReplicationMachine<RepRSBridgeBlockEntity> {

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
    private InventoryComponent<RepRSBridgeBlockEntity> output;
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
     * Server-side tick method to handle network connections and reconnections
     * Called by the base class ReplicationMachine
     */
    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state, RepRSBridgeBlockEntity blockEntity) {
        super.serverTick(level, pos, state, blockEntity);
        
        if (level != null && !level.isClientSide()) {
            // Incrementa il contatore di inizializzazione
            if (initializationTicks < INITIALIZATION_DELAY) {
                initializationTicks++;
                
                // Dopo un certo ritardo, prova a inizializzare le connessioni
                if (initializationTicks >= INITIALIZATION_DELAY && initialized == 0) {
                    LOGGER.info("Bridge: Inizializzazione delle connessioni di rete a {}", worldPosition);
                    forceNeighborUpdates();
                    initialized = 1;
                }
            }
            
            // Se dovremmo riconnetterci, prova a farlo
            if (shouldReconnect && !nodeCreated) {
                try {
                    LOGGER.info("Bridge: Tentativo di riconnessione a {}", worldPosition);
                    nodeCreated = true;
                    forceNeighborUpdates();
                    updateConnectedState();
                    shouldReconnect = false;
                } catch (Exception e) {
                    LOGGER.error("Fallito tentativo di riconnessione: {}", e.getMessage());
                }
            }
            
            // Aggiorna lo stato visivo della connessione
            updateConnectedState();
        }
    }

    public RepRSBridgeBlockEntity(BlockPos pos, BlockState blockState) {
        super((BasicTileBlock<RepRSBridgeBlockEntity>) ModBlocks.REPRSBRIDGE.get(),
                ModBlockEntities.REPRSBRIDGE_BE.get(),
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
        this.output = new InventoryComponent<RepRSBridgeBlockEntity>("output", 11, 131, 9*2)
                .setRange(9, 2)
                .setComponentHarness(this)
                .setInputFilter((stack, slot) -> true); // Allows insertion of any item
        this.addInventory(this.output);
    }

    @NotNull
    @Override
    public RepRSBridgeBlockEntity getSelf() {
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
            return level.getBlockEntity(neighborPos) instanceof RepRSBridgeBlockEntity;
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
                    if (level.getBlockEntity(neighborPos) instanceof RepRSBridgeBlockEntity) {
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
        //LOGGER.info("Bridge: onLoad called at {}", worldPosition);

        // Initialize the RS node if it hasn't been done
        if (!nodeCreated && level != null && !level.isClientSide()) {
            try {
                nodeCreated = true;
                forceNeighborUpdates();
                updateConnectedState();
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
            // If the changed block is another bridge, ignore the update
            Direction directionToNeighbor = null;
            for (Direction dir : Direction.values()) {
                if (worldPosition.relative(dir).equals(fromPos)) {
                    directionToNeighbor = dir;
                    break;
                }
            }

            if (directionToNeighbor != null && level.getBlockEntity(fromPos) instanceof RepRSBridgeBlockEntity) {
                // Debug log disabled for production
                // LOGGER.debug("Bridge: Ignored update from another bridge at {}", fromPos);
                return;
            }

            // Check if there is an RS controller in the network
            boolean hasRSConnection = hasRSNetworkConnection();

            // If there is an RS connection and the node is not created, initialize the node
            if (hasRSConnection && !nodeCreated) {
                try {
                    // Debug log disabled for production
                    // LOGGER.debug("Bridge: Initializing RS node from handleNeighborChanged");
                    nodeCreated = true;

                    // Notify adjacent blocks
                    forceNeighborUpdates();

                    // Update the connection state visually
                    updateConnectedState();
                }catch (Exception e) {
                    LOGGER.error("Failed to initialize RS node: {}", e.getMessage());
                    shouldReconnect = true;
                }
            }
            // If the node exists, update only adjacent blocks
            else {
                forceNeighborUpdates();
            }

            // Update the visual state
            updateConnectedState();
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
     * Sets the Refined Storage network for this block entity
     * @param network The RS network to connect to
     */
    public void setRsNetwork(@Nullable Network network) {
        this.rsNetwork = network;
        
        if (network != null) {
            active = true;
            connected = true;
        } else {
            active = false;
            connected = false;
        }
        
        updateConnectedState();
    }

    /**
     * Gets the Refined Storage network for this block entity
     * @return The connected RS network or null if not connected
     */
    public Network getRsNetwork() {
        return rsNetwork;
    }

    /**
     * Utility method to get the Replication network with consistent naming
     * @return The Replication matter network
     */
    private MatterNetwork getReplicationNetwork() {
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
        // Do not call super.onActivated() that would open the GUI
        // Do not call openGui(playerIn) that would open the GUI

        // Keep only the part related to the RS pattern update
        if (!level.isClientSide() && playerIn instanceof ServerPlayer serverPlayer) {
            // Update the patterns in RS
            // ICraftingProvider.requestUpdate(mainNode); // Commentato perché non disponibile in RS
            // LOGGER.info("Bridge: Updating RS patterns from onActivated");
        }
        return ItemInteractionResult.SUCCESS;
    }

    public TerminalPlayerTracker getTerminalPlayerTracker() {
        return terminalPlayerTracker;
    }

    public InventoryComponent<RepRSBridgeBlockEntity> getOutput() {
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
}