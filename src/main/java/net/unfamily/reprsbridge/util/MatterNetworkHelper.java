package net.unfamily.reprsbridge.util;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.unfamily.reprsbridge.item.ModItems;
import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.network.MatterNetwork;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classe di utilità per interfacciarsi con l'API di Replication
 * Utilizza reflection quando necessario per accedere alle API protette
 */
public class MatterNetworkHelper {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * Ottiene tutti i tipi di materia disponibili
     */
    public static List<IMatterType> getAllMatterTypes() {
        return List.of(
            ReplicationRegistry.Matter.EARTH.get(),
            ReplicationRegistry.Matter.NETHER.get(),
            ReplicationRegistry.Matter.ORGANIC.get(),
            ReplicationRegistry.Matter.ENDER.get(),
            ReplicationRegistry.Matter.METALLIC.get(),
            ReplicationRegistry.Matter.PRECIOUS.get(),
            ReplicationRegistry.Matter.LIVING.get(),
            ReplicationRegistry.Matter.QUANTUM.get()
        );
    }
    
    /**
     * Ottiene la quantità di materia disponibile nella rete
     */
    public static long getMatterAmount(MatterNetwork network, IMatterType matterType) {
        if (network == null || matterType == null) {
            return 0;
        }
        
        try {
            // Prima prova: accesso diretto al campo pubblico
            return getDirectMatterAmount(network, matterType);
        } catch (Exception e) {
            try {
                // Seconda prova: accesso tramite reflection
                return getReflectiveMatterAmount(network, matterType);
            } catch (Exception ex) {
                LOGGER.error("Errore nell'accesso alla quantità di materia: {}", ex.getMessage());
                return 0;
            }
        }
    }
    
    /**
     * Estrae materia dalla rete
     */
    public static boolean extractMatter(MatterNetwork network, IMatterType matterType, long amount) {
        if (network == null || matterType == null || amount <= 0) {
            return false;
        }
        
        try {
            // Prima prova: accesso diretto al metodo pubblico
            return extractDirectMatter(network, matterType, amount);
        } catch (Exception e) {
            try {
                // Seconda prova: accesso tramite reflection
                return extractReflectiveMatter(network, matterType, amount);
            } catch (Exception ex) {
                LOGGER.error("Errore nell'estrazione della materia: {}", ex.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Ottiene tutte le materie disponibili nella rete con le relative quantità
     */
    public static Map<IMatterType, Long> getAllAvailableMatter(MatterNetwork network) {
        Map<IMatterType, Long> result = new HashMap<>();
        
        if (network == null) {
            return result;
        }
        
        for (IMatterType type : getAllMatterTypes()) {
            long amount = getMatterAmount(network, type);
            if (amount > 0) {
                result.put(type, amount);
            }
        }
        
        return result;
    }
    
    /**
     * Accesso diretto alla quantità di materia
     */
    private static long getDirectMatterAmount(MatterNetwork network, IMatterType matterType) {
        // Implementazione con accesso diretto all'API
        // Questo fallirà se l'API non è esattamente questa
        Object matterInfo = getMatterInfo(network);
        if (matterInfo == null) {
            return 0;
        }
        
        // Cerca di chiamare il metodo get con il tipo di materia
        try {
            Method getMethod = matterInfo.getClass().getMethod("get", IMatterType.class);
            return (long) getMethod.invoke(matterInfo, matterType);
        } catch (Exception e) {
            throw new RuntimeException("Errore nell'accesso diretto alla quantità di materia", e);
        }
    }
    
    /**
     * Accesso tramite reflection alla quantità di materia
     */
    private static long getReflectiveMatterAmount(MatterNetwork network, IMatterType matterType) {
        // Implementazione con accesso tramite reflection
        try {
            Object matterInfo = getMatterInfo(network);
            if (matterInfo == null) {
                return 0;
            }
            
            // Cerca di trovare un metodo che accetti un IMatterType e restituisca un long
            for (Method method : matterInfo.getClass().getMethods()) {
                if (method.getParameterCount() == 1 && 
                    method.getParameterTypes()[0].isAssignableFrom(IMatterType.class) &&
                    (method.getReturnType() == long.class || method.getReturnType() == Long.class)) {
                    return (long) method.invoke(matterInfo, matterType);
                }
            }
            
            throw new RuntimeException("Nessun metodo compatibile trovato per ottenere la quantità di materia");
        } catch (Exception e) {
            throw new RuntimeException("Errore nell'accesso tramite reflection alla quantità di materia", e);
        }
    }
    
    /**
     * Estrazione diretta della materia
     */
    private static boolean extractDirectMatter(MatterNetwork network, IMatterType matterType, long amount) {
        // Implementazione con accesso diretto all'API
        Object matterInfo = getMatterInfo(network);
        if (matterInfo == null) {
            return false;
        }
        
        // Cerca di chiamare il metodo extract con il tipo di materia e la quantità
        try {
            Method extractMethod = matterInfo.getClass().getMethod("extract", IMatterType.class, long.class);
            return (boolean) extractMethod.invoke(matterInfo, matterType, amount);
        } catch (Exception e) {
            throw new RuntimeException("Errore nell'estrazione diretta della materia", e);
        }
    }
    
    /**
     * Estrazione tramite reflection della materia
     */
    private static boolean extractReflectiveMatter(MatterNetwork network, IMatterType matterType, long amount) {
        // Implementazione con accesso tramite reflection
        try {
            Object matterInfo = getMatterInfo(network);
            if (matterInfo == null) {
                return false;
            }
            
            // Cerca di trovare un metodo che accetti un IMatterType e un long, e restituisca un boolean
            for (Method method : matterInfo.getClass().getMethods()) {
                if (method.getParameterCount() == 2 &&
                    method.getParameterTypes()[0].isAssignableFrom(IMatterType.class) &&
                    (method.getParameterTypes()[1] == long.class || method.getParameterTypes()[1] == Long.class) &&
                    method.getReturnType() == boolean.class) {
                    return (boolean) method.invoke(matterInfo, matterType, amount);
                }
            }
            
            throw new RuntimeException("Nessun metodo compatibile trovato per estrarre la materia");
        } catch (Exception e) {
            throw new RuntimeException("Errore nell'estrazione tramite reflection della materia", e);
        }
    }
    
    /**
     * Ottiene l'oggetto MatterInfo dalla rete
     */
    private static Object getMatterInfo(MatterNetwork network) {
        try {
            // Prima prova: accesso diretto al metodo pubblico
            Method getMatterInfoMethod = network.getClass().getMethod("getMatterInfo");
            return getMatterInfoMethod.invoke(network);
        } catch (Exception e) {
            // Seconda prova: accesso tramite reflection ai campi
            try {
                // Cerca tra i campi dichiarati
                java.lang.reflect.Field[] fields = network.getClass().getDeclaredFields();
                for (java.lang.reflect.Field field : fields) {
                    field.setAccessible(true);
                    Object value = field.get(network);
                    if (value != null && field.getName().toLowerCase().contains("matter")) {
                        return value;
                    }
                }
                
                throw new RuntimeException("Campo MatterInfo non trovato");
            } catch (Exception ex) {
                throw new RuntimeException("Errore nell'accesso a MatterInfo", ex);
            }
        }
    }
    
    /**
     * Mappa un tipo di materia al corrispondente item virtuale
     */
    public static Item getItemForMatterType(IMatterType type) {
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
    
    /**
     * Mappa un item virtuale al corrispondente tipo di materia
     */
    @Nullable
    public static IMatterType getMatterTypeForItem(Item item) {
        if (item == ModItems.EARTH_MATTER.get()) return ReplicationRegistry.Matter.EARTH.get();
        if (item == ModItems.NETHER_MATTER.get()) return ReplicationRegistry.Matter.NETHER.get();
        if (item == ModItems.ORGANIC_MATTER.get()) return ReplicationRegistry.Matter.ORGANIC.get();
        if (item == ModItems.ENDER_MATTER.get()) return ReplicationRegistry.Matter.ENDER.get();
        if (item == ModItems.METALLIC_MATTER.get()) return ReplicationRegistry.Matter.METALLIC.get();
        if (item == ModItems.PRECIOUS_MATTER.get()) return ReplicationRegistry.Matter.PRECIOUS.get();
        if (item == ModItems.LIVING_MATTER.get()) return ReplicationRegistry.Matter.LIVING.get();
        if (item == ModItems.QUANTUM_MATTER.get()) return ReplicationRegistry.Matter.QUANTUM.get();
        return null;
    }
    
    /**
     * Verifica se un item è un item materia virtuale
     */
    public static boolean isVirtualMatterItem(Item item) {
        return getMatterTypeForItem(item) != null;
    }

    /**
     * Crea uno stack di item per un tipo di materia
     */
    public static ItemStack createMatterItemStack(IMatterType type) {
        Item item = getItemForMatterType(type);
        if (item != null) {
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }
} 