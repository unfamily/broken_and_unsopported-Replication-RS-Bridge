package net.unfamily.reprsbridge.block.custom;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntityP;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntityF;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.INetworkDirectionalConnection;
import com.buuz135.replication.block.MatterPipeBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import org.slf4j.Logger;
import net.minecraft.world.entity.player.Player;
import net.unfamily.reprsbridge.block.entity.ModBlockEntities;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.server.TickTask;

public class RepRSBridgeBl extends BasicTileBlock<RepRSBridgeBlockEntityP> implements INetworkDirectionalConnection, EntityBlock {
    public static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<RepRSBridgeBl> CODEC = simpleCodec(RepRSBridgeBl::new);

    private static final Logger LOGGER = LogUtils.getLogger();

    // Add a property to display the connection status
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");
    // Add a property for Refined Storage activity state (like in InterfaceBlock)
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    
    // Proprietà per le connessioni direzionali
    public static final BooleanProperty C_NORTH = BooleanProperty.create("c_north");
    public static final BooleanProperty C_SOUTH = BooleanProperty.create("c_south");
    public static final BooleanProperty C_EAST = BooleanProperty.create("c_east");
    public static final BooleanProperty C_WEST = BooleanProperty.create("c_west");
    public static final BooleanProperty C_UP = BooleanProperty.create("c_up");
    public static final BooleanProperty C_DOWN = BooleanProperty.create("c_down");

    public RepRSBridgeBl(Properties properties) {
        super(properties, RepRSBridgeBlockEntityP.class);
        // Set the default state with connection to false and active to false
      
        
        // Register the block as connectable with Replication pipes
        // (Added in case the static module in ModBlocks is not executed in time)
        registerWithReplicationMod();
    }
    
    /**s
     * Register this block as connectable by Replication pipes
     */
    private void registerWithReplicationMod() {
        try {
            // Check if the MatterPipeBlock class already exists
            if (MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS != null) {
                // Add a predicate for this specific block
                MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block -> block instanceof RepRSBridgeBl);
                // Debug log disabled for production
                // LOGGER.debug("Registered RepAE2BridgeBl with Replication mod pipes");
            }
        } catch (Exception e) {
            // Keep error logs for critical failures
            LOGGER.error("Failed to register block with Replication: " + e.getMessage());
        }
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTED);
        builder.add(ACTIVE);
        builder.add(C_NORTH);
        builder.add(C_SOUTH);
        builder.add(C_EAST);
        builder.add(C_WEST);
        builder.add(C_UP);
        builder.add(C_DOWN);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends BasicTileBlock<RepRSBridgeBlockEntityP>> codec() {
        return CODEC;
    }

    /* BLOCK ENTITY */

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<?> getTileEntityFactory() {
        return (pos, state) -> new RepRSBridgeBlockEntityP(pos, state);
    }
    
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Crea l'entità principale (Replication)
        return new RepRSBridgeBlockEntityP(pos, state);
    }
    
    /**
     * Questo metodo viene chiamato quando il blocco viene posizionato nel mondo
     * È il momento ideale per inizializzare le BlockEntity
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, entity, stack);
    }
    
    /**
     * Crea uno stato del blocco con i valori di connessione corretti
     */
    private BlockState createState(Level world, BlockPos pos, BlockState curr) {
        // Inizia con lo stato predefinito
        BlockState state = this.defaultBlockState();
        
        // Verifica le connessioni per ogni direzione
        for (Direction dir : Direction.values()) {
            // Ottieni la proprietà per questa direzione
            BooleanProperty dirProperty = getDirectionalProperty(dir);
            
            // Verifica se c'è un cavo RS in questa direzione
            boolean isConnected = isRefStorageCableInDirection(world, pos, dir);
            
            // Imposta la proprietà di connessione
            if (dirProperty != null) {
                state = state.setValue(dirProperty, isConnected);
            }
        }
        
        // Mantieni le proprietà connected e active dallo stato corrente, se disponibili
        if (curr != null && curr.getBlock() instanceof RepRSBridgeBl) {
            state = state.setValue(CONNECTED, curr.getValue(CONNECTED))
                         .setValue(ACTIVE, curr.getValue(ACTIVE));
        }
        
        return state;
    }
    
    /**
     * Verifica se c'è un cavo Refined Storage nella direzione specificata
     */
    private boolean isRefStorageCableInDirection(Level world, BlockPos pos, Direction dir) {
        // Ottieni la posizione del blocco adiacente
        BlockPos neighborPos = pos.relative(dir);
        
        // Ottieni lo stato del blocco adiacente
        BlockState neighborState = world.getBlockState(neighborPos);
        
        // Verifica se è un cavo Refined Storage
        return isRefinedStorageCable(neighborState);
    }
    
    /**
     * Verifica se un BlockState appartiene a un cavo Refined Storage
     */
    private boolean isRefinedStorageCable(BlockState state) {
        String blockClassName = state.getBlock().getClass().getSimpleName().toLowerCase();
        String blockFullName = state.getBlock().getClass().getName().toLowerCase();
        
        // Controllo specifico per i cavi Refined Storage
        boolean isRSCable = blockFullName.contains("refinedstorage") && 
                           (blockClassName.contains("cable") || 
                            blockClassName.equals("cableblock"));
        
        return isRSCable;
    }

    /**
     * Ottiene la proprietà booleana corrispondente a una direzione
     */
    private BooleanProperty getDirectionalProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> C_NORTH;
            case SOUTH -> C_SOUTH;
            case EAST -> C_EAST;
            case WEST -> C_WEST;
            case UP -> C_UP;
            case DOWN -> C_DOWN;
        };
    }
    
    /**
     * Restituisce lo stato appropriato quando il blocco viene piazzato
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.createState(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }
    
    /**
     * Questo metodo viene chiamato quando un blocco vicino cambia
     */
    @Override
    public void neighborChanged(BlockState state, Level worldIn, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, worldIn, pos, blockIn, fromPos, isMoving);
        
        // Verifica se il mondo è remoto
        if (worldIn.isClientSide()) {
            return;
        }
        
        // Crea un nuovo stato basato sulle connessioni attuali
        BlockState newState = this.createState(worldIn, pos, state);
        
        // Se lo stato è cambiato, aggiorna il blocco
        if (newState != state) {
            worldIn.setBlockAndUpdate(pos, newState);
        }
        
        // Aggiorna la BlockEntity
        if (!worldIn.isClientSide() && worldIn.getBlockEntity(pos) instanceof RepRSBridgeBlockEntityP mainEntity) {
            mainEntity.handleNeighborChanged(fromPos);
            
            // Riconnessione dell'entità Refined Storage
            // Prima ottieni l'attuale entità RS
            RepRSBridgeBlockEntityF currentRsEntity = mainEntity.getRefinedStorageEntity();
            
            // Cancella la vecchia entità RS se esiste
            if (currentRsEntity != null) {
                // Aggiungo un delay prima della disconnessione per dare tempo alla rete di stabilizzarsi
                LOGGER.debug("Attendo prima di riconnettere l'entità Refined Storage alla posizione {}", pos);
                
                // Utilizzo un task schedulato invece di una disconnessione immediata
                if (worldIn instanceof ServerLevel serverLevel) {
                    serverLevel.getServer().tell(new TickTask(20, () -> {
                        // Disconnetti dopo il delay
                        currentRsEntity.disconnectFromNetwork();
                        
                        //Ricreo l'entità dopo la disconnessione
                        RepRSBridgeBlockEntityF newRsEntity = new RepRSBridgeBlockEntityF(pos, state);
                        
                        // Collega le due entità
                        newRsEntity.setMainEntity(mainEntity);
                        mainEntity.setRefinedStorageEntity(newRsEntity);
                        
                        // Registra l'entità RS nel mondo
                        LOGGER.debug("Ricreo l'entità Refined Storage alla posizione {} dopo il delay", pos);
                        serverLevel.setBlockEntity(newRsEntity);
                        
                        // Forza un aggiornamento del blocco
                        serverLevel.sendBlockUpdated(pos, state, state, 3);
                    }));
                }
            }
            
            // Aggiorna lo stato connected e active
            boolean isConnected = mainEntity.isActive() && mainEntity.getNetwork() != null;
            boolean isActive = isConnected;
            
            if (newState.getValue(CONNECTED) != isConnected || newState.getValue(ACTIVE) != isActive) {
                newState = newState
                    .setValue(CONNECTED, isConnected)
                    .setValue(ACTIVE, isActive);
                
                worldIn.setBlockAndUpdate(pos, newState);
            }
        }
    }
    
    /**
     * Implementazione alternativa del pattern multi-BlockEntity
     * Questo metodo crea esplicitamente la seconda BlockEntity quando necessario
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        
        if (!level.isClientSide() && !isMoving) {
            // Ottieni l'entità principale
            BlockEntity mainEntity = level.getBlockEntity(pos);
            if (mainEntity instanceof RepRSBridgeBlockEntityP primaryEntity) {
                // Crea e registra esplicitamente l'entità RS solo quando necessario
                RepRSBridgeBlockEntityF rsEntity = new RepRSBridgeBlockEntityF(pos, state);
                
                // Collega le due entità
                rsEntity.setMainEntity(primaryEntity);
                primaryEntity.setRefinedStorageEntity(rsEntity);
                
                // Registra l'entità RS nel mondo (aggiungendo un nuovo tipo di BlockEntity nella stessa posizione)
                LOGGER.debug("Registrazione esplicita della BlockEntity RS alla posizione {}", pos);
                level.setBlockEntity(rsEntity);
            }
            
            // Aggiorna immediatamente lo stato del blocco
            BlockState newState = this.createState(level, pos, state);
            
            if (newState != state) {
                level.setBlockAndUpdate(pos, newState);
            }
            
            // Schedula un tick per verificare eventuali cambiamenti
            level.scheduleTick(pos, this, 5);
        }
    }
    
    /**
     * Gestisce i tick schedulati
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        
        // Verifica e aggiorna lo stato se necessario
        BlockState newState = this.createState(level, pos, state);
        
        if (newState != state) {
            level.setBlockAndUpdate(pos, newState);
        }

        // Assicuriamoci che l'entità RS esista e sia connessa
        BlockEntity mainEntity = level.getBlockEntity(pos);
        if (mainEntity instanceof RepRSBridgeBlockEntityP primaryEntity) {
            // Verifica se l'entità RS esiste
            RepRSBridgeBlockEntityF rsEntity = primaryEntity.getRefinedStorageEntity();
            
            if (rsEntity != null) {
                // Se l'entità esiste, forza un aggiornamento periodicamente
                rsEntity.setChanged();
            }
            
            // Aggiorna lo stato connected e active
            boolean isConnected = primaryEntity.isActive();
            
            if (state.getValue(CONNECTED) != isConnected) {
                level.setBlockAndUpdate(pos, state.setValue(CONNECTED, isConnected).setValue(ACTIVE, isConnected));
            }
        }
        
        // Schedula un altro tick per verificare periodicamente
        level.scheduleTick(pos, this, 20);
    }
    
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        // If the block has been removed or replaced
        if (!state.is(newState.getBlock())) {
            // Drop the block itself if not in creative mode
            if (!level.isClientSide) {
                ItemStack itemStack = new ItemStack(this);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), itemStack);
            }
            
            // Make sure the BlockEntity is properly removed
            if (level.getBlockEntity(pos) instanceof RepRSBridgeBlockEntityP blockEntity) {
                // Drop all items in the inventory
                var inventory = blockEntity.getOutput();
                for (int i = 0; i < inventory.getSlots(); i++) {
                    ItemStack stack = inventory.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
                
                // Force explicit disconnection from both networks
                blockEntity.disconnectFromNetworks();
            }
            level.removeBlockEntity(pos);
        }
        
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean canConnect(Level level, BlockPos pos, BlockState state, Direction direction) {
        // Allow connection from all directions for the Replication network
        return true;
    }

    // Ensure the block can always be harvested with any tool
    @Override
    public boolean canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos, Player player) {
        return true;
    }
    
    
    /**
     * Forza un aggiornamento del renderer per questo blocco
     */
    private void forceRenderUpdate(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide()) return;
        
        // Forza un aggiornamento esplicito del renderer con flag massimi
        level.sendBlockUpdated(pos, state, state, 3);
        
        // Notifica i chunk adiacenti
        level.updateNeighborsAt(pos, this);
        
        // Notifica il client di un cambiamento di stato
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().blockChanged(pos);
        }
    }

    /**
     * Questo metodo è chiamato per ottenere il ticker per la BlockEntity
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        
        // Ticker per l'entità Replication
        if (type == ModBlockEntities.REPRSBRIDGE_P_BE.get()) {
            return (lvl, pos, blockState, blockEntity) -> {
                if (blockEntity instanceof RepRSBridgeBlockEntityP entity) {
                    entity.serverTick(lvl, pos, blockState, entity);
                }
            };
        }
        
        return null;
    }
}
