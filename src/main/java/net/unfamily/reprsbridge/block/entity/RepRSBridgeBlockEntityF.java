package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.common.Platform;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
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
            BlockEntities.INSTANCE.getInterface(),
            pos,
            state,
            new BridgeNetworkNode(Platform.INSTANCE.getConfig().getInterface().getEnergyUsage())
        );
    }
    
    /**
     * Imposta l'entità principale per la comunicazione
     */
    public void setMainEntity(RepRSBridgeBlockEntityP mainEntity) {
        this.mainEntity = mainEntity;
    }

    /**
     * Ottiene l'entità principale
     */
    public RepRSBridgeBlockEntityP getMainEntity() {
        return mainEntity;
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
} 