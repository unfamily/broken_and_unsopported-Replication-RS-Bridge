package net.unfamily.reprsbridge;

import net.unfamily.reprsbridge.block.ModBlocks;
import net.unfamily.reprsbridge.block.entity.ModBlockEntities;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntity;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.block_network.element.NetworkElementRegistry;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
//import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.unfamily.reprsbridge.item.ModItems;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.buuz135.replication.block.MatterPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import com.refinedmods.refinedstorage.api.network.Network;


// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RepRSBridge.MOD_ID)
public class RepRSBridge
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "rep_rs_bridge";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // Define the capability for INetworkNode
    private static final BlockCapability<NetworkNode, Void> NETWORK_NODE =
        BlockCapability.createVoid(ResourceLocation.parse("refinedstorage:network_node"), NetworkNode.class);


    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public RepRSBridge(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the event for capabilities
        modEventBus.addListener(this::registerCapabilities);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register the item to a creative tab
        modEventBus.addListener(this::addCreative);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        //modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // Register modules
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        // LOGGER.info("HELLO FROM COMMON SETUP");

        // if (Config.logDirtBlock)
        //     LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

        // LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        // Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));

        // Register the network element factory for the Replication mod
        // This is crucial for making the connection to the Replication network work
        event.enqueueWork(() -> {
            try {
                // Directly register the factory for DefaultMatterNetworkElement as done by Replication
                // LOGGER.info("Registering DefaultMatterNetworkElement factory for Replication integration");
                NetworkElementRegistry.INSTANCE.addFactory(DefaultMatterNetworkElement.ID, new DefaultMatterNetworkElement.Factory());
                // LOGGER.info("Replication network integration complete");
            } catch (Exception e) {
                LOGGER.error("Failed to register with Replication network system", e);
            }
        });

        // Register our mod's namespace as an allowed namespace for Replication cables
        event.enqueueWork(() -> {
            // This ensures it runs on the main thread
            registerWithReplicationMod();
        });
    }

    // Register bridge capabilities
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Register the NetworkNode capability for the bridge block
        event.registerBlock(
            NETWORK_NODE,
            (level, pos, state, be, context) -> {
                if (be instanceof RepRSBridgeBlockEntity bridge) {
                    // Restituisce un adattatore che implementa NetworkNode
                    return new NetworkNodeAdapter(bridge);
                }
                return null;
            },
            ModBlocks.REPRSBRIDGE.get()
        );

        // Registra le capabilities del bridge per il trasferimento di item
        RepRSBridgeCapabilities.register(event);

        // Log that capabilities have been registered
        // LOGGER.info("RS Bridge capacities registered successfully");
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        // Add the bridge to Refined Storage's main creative tab
        if (event.getTabKey().location().toString().equals("refinedstorage:main")) {
            event.accept(ModBlocks.REPRSBRIDGE.get());
            // LOGGER.info("Added RepRSBridge to RS creative tab");
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // LOGGER.info("RepRSBridge: Server starting");
        RepRSBridgeBlockEntity.setWorldUnloading(false);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event)
    {
        LOGGER.info("RepRSBridge: Server stopping, notifying bridges to prepare for unload");

        // Set the static flag in the BlockEntity class
        RepRSBridgeBlockEntity.setWorldUnloading(true);

        LOGGER.info("RepRSBridge: All bridges notified of world unload");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // LOGGER.info("RepRSBridge: Client setup");
        }
    }

    /**
     * Register the mod namespace in the list of allowed namespaces for Replication cables
     */
    private void registerWithReplicationMod() {
        // LOGGER.info("Registering RepRSBridge with Replication mod");

        // Add the mod namespace to the list of allowed namespaces
        MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block ->
            block.getClass().getName().contains(MOD_ID)
        );

        // LOGGER.info("Successfully registered with Replication mod");
    }

    /**
     * Classe adattatore che implementa l'interfaccia NetworkNode per il nostro bridge
     * Questo evita la necessità di implementare direttamente NetworkNode nel BlockEntity
     */
    public static class NetworkNodeAdapter implements NetworkNode {
        private final RepRSBridgeBlockEntity bridge;
            
        public NetworkNodeAdapter(RepRSBridgeBlockEntity bridge) {
            this.bridge = bridge;
        }
            
        @Override
        public void setNetwork(@Nullable Network network) {
            bridge.setRsNetwork(network);
        }
            
        @Override
        public Network getNetwork() {
            return bridge.getRsNetwork();
        }
    }
}
