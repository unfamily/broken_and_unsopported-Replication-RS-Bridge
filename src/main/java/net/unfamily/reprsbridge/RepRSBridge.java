package net.unfamily.reprsbridge;

import net.unfamily.reprsbridge.block.ModBlocks;
import net.unfamily.reprsbridge.block.entity.ModBlockEntities;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntityP;
import net.unfamily.reprsbridge.block.entity.RepRSBridgeBlockEntityF;
import net.unfamily.reprsbridge.block.custom.RepRSBridgeBl;
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
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.unfamily.reprsbridge.item.ModItems;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.buuz135.replication.block.MatterPipeBlock;

import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(RepRSBridge.MOD_ID)
public class RepRSBridge
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "rep_rs_bridge";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

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

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Registra gli eventi del mod
        NeoForge.EVENT_BUS.register(this);
        
        LOGGER.info("RepRSBridge common setup complete");

        // Register the network element factory for the Replication mod
        // This is crucial for making the connection to the Replication network work
        event.enqueueWork(() -> {
            try {
                // Directly register the factory for DefaultMatterNetworkElement as done by Replication
                NetworkElementRegistry.INSTANCE.addFactory(DefaultMatterNetworkElement.ID, new DefaultMatterNetworkElement.Factory());
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
        // Registra le capabilities del bridge per entrambe le entità
        RepRSBridgeCapabilities.register(event);
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event)
    {
        // Add the bridge to Refined Storage's main creative tab
        if (event.getTabKey().location().toString().equals("refinedstorage:main")) {
            event.accept(ModBlocks.REPRSBRIDGE.get());
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        //RepRSBridgeBlockEntityP.setWorldUnloading(false);
        //RepRSBridgeBlockEntityF.setWorldSaving(false);
    }

    /**
     * Gestisce l'evento di arresto del server
     * Imposta il flag per evitare aggiornamenti durante l'arresto
     */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        //RepRSBridgeBlockEntityF.setWorldSaving(true);
        //RepRSBridgeBlockEntityP.setWorldUnloading(true);
        LOGGER.info("RepRSBridge: Server in arresto, disattivati aggiornamenti");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Client setup
        }
    }

    /**
     * Register the mod namespace in the list of allowed namespaces for Replication cables
     */
    private void registerWithReplicationMod() {
        LOGGER.info("Registering RepRSBridge with Replication mod");

        // Add the mod namespace to the list of allowed namespaces
        MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block -> 
            block.getClass().getName().contains(MOD_ID)
        );
        
        // Aggiungi un predicato specifico per il nostro blocco bridge
        MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block ->
            block instanceof net.unfamily.reprsbridge.block.custom.RepRSBridgeBl
        );
        
        // Aggiungi un predicato basato sul nome del blocco registrato
        MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block ->
            block == ModBlocks.REPRSBRIDGE.get()
        );

        LOGGER.info("Successfully registered with Replication mod");
    }

}
