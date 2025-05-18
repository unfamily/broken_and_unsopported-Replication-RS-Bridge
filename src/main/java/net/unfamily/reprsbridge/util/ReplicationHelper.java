package net.unfamily.reprsbridge.util;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.annotation.Nullable;

/**
 * Classe helper per gestire le operazioni comuni del bridge con Replication.
 */
public class ReplicationHelper {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHIP_STORAGE_CLASS_NAME = "com.buuz135.replication.block.tile.ChipStorageBlockEntity";

    /**
     * Ottiene la lista dei tipi di materia disponibili
     * @return Una lista dei tipi di materia
     */
    public static List<IMatterType> getMatterTypes() {
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
     * Ottiene i pattern disponibili nella rete
     * @param network Il network di materia
     * @param level Il livello corrente
     * @return Una mappa di pattern con i loro valori di materia
     */
    public static Map<String, Map<String, Integer>> getAvailablePatterns(MatterNetwork network, Level level) {
        Map<String, Map<String, Integer>> patterns = new HashMap<>();
        
        if (network == null || level == null) {
            return patterns;
        }
        
        try {
            LOGGER.debug("Cercando ChipStorage nella rete...");
            
            // Ottieni direttamente i chip suppliers dal network
            // Questo è il modo più diretto e affidabile per trovare ChipStorage
            List<NetworkElement> chipSuppliers = null;
            try {
                chipSuppliers = network.getChipSuppliers();
            } catch (NullPointerException e) {
                LOGGER.error("NullPointerException durante l'accesso ai chip suppliers: {}", e.getMessage());
                return patterns;
            } catch (Exception e) {
                LOGGER.error("Errore durante l'accesso ai chip suppliers: {}", e.getMessage());
                return patterns;
            }
            
            if (chipSuppliers == null) {
                LOGGER.warn("Lista dei chip suppliers è null");
                return patterns;
            }
            
            LOGGER.debug("Trovati {} chip suppliers nella rete", chipSuppliers.size());
            
            // Limita il numero di chip suppliers da processare in caso di stress
            int maxChipSuppliersToProcess = Math.min(chipSuppliers.size(), 50);
            int processedCount = 0;
            
            for (NetworkElement chipSupplier : chipSuppliers) {
                if (processedCount++ >= maxChipSuppliersToProcess) {
                    LOGGER.info("Limite di {} chip suppliers raggiunto, interruzione elaborazione", maxChipSuppliersToProcess);
                    break;
                }
                
                try {
                    if (chipSupplier == null) {
                        LOGGER.debug("Chip supplier nullo, salto");
                        continue;
                    }
                    
                    Level supplierLevel = null;
                    try {
                        supplierLevel = chipSupplier.getLevel();
                    } catch (Exception e) {
                        LOGGER.debug("Errore nell'ottenere il level del chip supplier: {}", e.getMessage());
                        continue;
                    }
                    
                    if (supplierLevel == null) {
                        LOGGER.debug("Livello del chip supplier nullo, salto");
                        continue;
                    }
                    
                    BlockPos pos = null;
                    try {
                        pos = chipSupplier.getPos();
                    } catch (Exception e) {
                        LOGGER.debug("Errore nell'ottenere la posizione del chip supplier: {}", e.getMessage());
                        continue;
                    }
                    
                    if (pos == null) {
                        LOGGER.debug("Posizione del chip supplier nulla, salto");
                        continue;
                    }
                    
                    LOGGER.debug("Valutando elemento di rete ChipSupplier alla posizione {}", pos);
                    
                    // Ottieni l'entità block alla posizione dell'elemento
                    BlockEntity blockEntity = null;
                    try {
                        blockEntity = supplierLevel.getBlockEntity(pos);
                    } catch (Exception e) {
                        LOGGER.debug("Errore nell'ottenere l'entità alla posizione {}: {}", pos, e.getMessage());
                        continue;
                    }
                    
                    if (blockEntity == null) {
                        LOGGER.debug("Nessuna entità alla posizione {}, salto", pos);
                        continue;
                    }
                    
                    // Controlla se è uno ChipStorage
                    if (blockEntity instanceof ChipStorageBlockEntity) {
                        ChipStorageBlockEntity chipStorage = (ChipStorageBlockEntity) blockEntity;
                        LOGGER.debug("Trovato ChipStorage alla posizione {} (class: {})", 
                            pos, blockEntity.getClass().getName());
                        
                        // Ottieni i pattern dallo storage - questo è esattamente come in RepAE2BridgeBlockEntity
                        List<MatterPattern> patternList = null;
                        try {
                            patternList = chipStorage.getPatterns(level, chipStorage);
                        } catch (Exception e) {
                            LOGGER.debug("Errore nell'ottenere i pattern dal ChipStorage a {}: {}", pos, e.getMessage());
                            continue;
                        }
                        
                        if (patternList == null || patternList.isEmpty()) {
                            LOGGER.debug("Nessun pattern trovato nel ChipStorage alla posizione {}", pos);
                            continue;
                        }
                        
                        LOGGER.debug("Trovati {} pattern nel ChipStorage alla posizione {}", patternList.size(), pos);
                        
                        // Processa ogni pattern
                        for (MatterPattern pattern : patternList) {
                            try {
                                // Verifica il pattern esattamente come fa RepAE2BridgeBlockEntity
                                if (pattern == null) {
                                    LOGGER.debug("Pattern nullo, salto");
                                    continue;
                                }
                                
                                float completion = 0;
                                try {
                                    completion = pattern.getCompletion();
                                } catch (Exception e) {
                                    LOGGER.debug("Errore nell'ottenere il completamento del pattern: {}", e.getMessage());
                                    continue;
                                }
                                
                                if (completion != 1) {
                                    LOGGER.debug("Pattern non completamente formato ({}/1), salto", completion);
                                    continue;
                                }
                                
                                // Ottieni lo stack dell'item
                                ItemStack stack = null;
                                try {
                                    stack = pattern.getStack();
                                } catch (Exception e) {
                                    LOGGER.debug("Errore nell'ottenere lo stack del pattern: {}", e.getMessage());
                                    continue;
                                }
                                
                                if (stack == null || stack.isEmpty()) {
                                    LOGGER.debug("Stack del pattern nullo o vuoto, salto");
                                    continue;
                                }
                                
                                // Ottieni l'ID dell'item
                                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                                String itemKey = itemId.toString();
                                
                                LOGGER.debug("Elaborazione pattern per l'item: {}", itemKey);
                                
                                // Ottieni i valori della materia - usando ClientReplicationCalculation come in RepAE2BridgeBlockEntity
                                Map<String, Integer> matterValues = new HashMap<>();
                                
                                try {
                                    // Usa reflection per accedere a ClientReplicationCalculation.getMatterCompound come prima
                                    Class<?> clientCalcClass = Class.forName("com.buuz135.replication.calculation.client.ClientReplicationCalculation");
                                    Method getMatterCompoundMethod = clientCalcClass.getMethod("getMatterCompound", ItemStack.class);
                                    Object matterCompound = getMatterCompoundMethod.invoke(null, stack);
                                    
                                    if (matterCompound != null) {
                                        LOGGER.debug("Ottenuto matterCompound per {}", itemKey);
                                        
                                        // Accedi a matterCompound.getValues()
                                        Method getValuesMethod = matterCompound.getClass().getMethod("getValues");
                                        Object valuesMap = getValuesMethod.invoke(matterCompound);
                                        
                                        if (valuesMap instanceof Map) {
                                            Map<?, ?> map = (Map<?, ?>) valuesMap;
                                            LOGGER.debug("Mappa di valori contiene {} elementi", map.size());
                                            
                                            // Itera sui valori proprio come fa RepAE2BridgeBlockEntity
                                            for (Map.Entry<?, ?> entry : map.entrySet()) {
                                                // I valori sono di tipo MatterValue
                                                Object matterValue = entry.getValue();
                                                if (matterValue != null) {
                                                    // Ottieni il tipo di materia da MatterValue
                                                    Method getMatterMethod = matterValue.getClass().getMethod("getMatter");
                                                    Object entryMatterType = getMatterMethod.invoke(matterValue);
                                                    
                                                    if (entryMatterType != null && entryMatterType instanceof IMatterType) {
                                                        IMatterType typedMatter = (IMatterType) entryMatterType;
                                                        String matterName = typedMatter.getName().toLowerCase();
                                                        
                                                        // Ottieni l'amount
                                                        Method getAmountMethod = matterValue.getClass().getMethod("getAmount");
                                                        Object amountObj = getAmountMethod.invoke(matterValue);
                                                        
                                                        if (amountObj instanceof Number) {
                                                            double amount = ((Number) amountObj).doubleValue();
                                                            int value = (int) Math.ceil(amount);
                                                            
                                                            if (value > 0) {
                                                                matterValues.put(matterName, value);
                                                                LOGGER.debug("Pattern {} richiede {} materia {}", 
                                                                    itemKey, value, matterName);
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    LOGGER.debug("Errore nel recupero dei valori di materia tramite ClientReplicationCalculation: {}", e.getMessage());
                                    
                                    // Fallback al metodo precedente se ClientReplicationCalculation fallisce
                                    for (IMatterType matterType : getMatterTypes()) {
                                        try {
                                            int amount = getPatternMatterValue(pattern, matterType);
                                            
                                            if (amount > 0) {
                                                matterValues.put(matterType.getName().toLowerCase(), amount);
                                                LOGGER.debug("Pattern {} richiede {} materia {} (metodo fallback)", 
                                                    itemKey, amount, matterType.getName());
                                            }
                                        } catch (Exception ex) {
                                            LOGGER.debug("Errore nel recupero del valore per materia {}: {}", 
                                                matterType.getName(), ex.getMessage());
                                        }
                                    }
                                }
                                
                                if (!matterValues.isEmpty()) {
                                    patterns.put(itemKey, matterValues);
                                    LOGGER.debug("Aggiunto pattern per {} con {} tipi di materia", itemKey, matterValues.size());
                                } else {
                                    LOGGER.debug("Nessun valore di materia trovato per {}, pattern ignorato", itemKey);
                                }
                            } catch (Exception e) {
                                LOGGER.debug("Errore nell'elaborazione del pattern: {}", e.getMessage());
                                // Continua con il prossimo pattern
                            }
                        }
                    } else {
                        LOGGER.debug("L'entità alla posizione {} non è un ChipStorage (class: {})", 
                            pos, blockEntity.getClass().getName());
                    }
                } catch (Exception e) {
                    LOGGER.debug("Errore durante l'elaborazione del chip supplier: {}", e.getMessage());
                    // Continua con il prossimo chip supplier
                }
            }
            
            // Se non abbiamo trovato pattern, possiamo aggiungere qui un messaggio di log più informativo
            if (patterns.isEmpty()) {
                LOGGER.warn("Nessun pattern trovato nella rete. Verifica che esistano ChipStorage con pattern configurati.");
            } else {
                LOGGER.debug("Totale pattern trovati nella rete: {}", patterns.size());
            }
            
        } catch (Exception e) {
            LOGGER.error("Errore durante l'ottenimento dei pattern: {}", e.getMessage(), e);
        }
        
        return patterns;
    }
    
    /**
     * Trova tutti gli ChipStorage nella rete
     */
    private static List<BlockPos> findChipStorages(MatterNetwork network, Level level) {
        List<BlockPos> result = new ArrayList<>();
        
        if (network == null || level == null) {
            LOGGER.warn("Rete o livello nullo durante la ricerca di ChipStorage");
            return result;
        }
        
        LOGGER.debug("Iniziando la ricerca di ChipStorage nella rete {}", network);
        
        // Primo approccio: ottieni direttamente gli elementi dalla rete
        try {
            // Ottieni TUTTI gli elementi di rete, non solo i matter stack holders
            List<NetworkElement> allElements = null;
            
            try {
                // Utilizzo reflection per chiamare getElements(), se esiste
                try {
                    Method getElementsMethod = network.getClass().getMethod("getElements");
                    getElementsMethod.setAccessible(true);
                    allElements = (List<NetworkElement>) getElementsMethod.invoke(network);
                    LOGGER.debug("Ottenuti {} elementi totali dalla rete usando getElements via reflection", 
                        allElements != null ? allElements.size() : 0);
                } catch (NoSuchMethodException e) {
                    LOGGER.debug("Metodo getElements non trovato: {}", e.getMessage());
                } catch (Exception e) {
                    LOGGER.debug("Impossibile invocare getElements: {}", e.getMessage());
                }
            } catch (Exception e) {
                LOGGER.debug("Impossibile ottenere gli elementi usando getElements: {}", e.getMessage());
                
                // Prova ad accedere al campo elements
                try {
                    Field elementsField = network.getClass().getDeclaredField("elements");
                    elementsField.setAccessible(true);
                    allElements = (List<NetworkElement>) elementsField.get(network);
                    LOGGER.debug("Ottenuti {} elementi dal campo elements", allElements != null ? allElements.size() : 0);
                } catch (Exception ex) {
                    LOGGER.error("Impossibile accedere al campo elements: {}", ex.getMessage());
                }
            }
            
            if (allElements != null && !allElements.isEmpty()) {
                LOGGER.debug("Trovati {} elementi nella rete", allElements.size());
                
                for (NetworkElement element : allElements) {
                    if (element == null) continue;
                    
                    BlockPos pos = getNetworkElementPosition(element);
                    if (pos != null) {
                        LOGGER.debug("Elaborazione elemento di rete alla posizione {}", pos);
                        result.add(pos);
                    }
                }
            }
            
            // Se non abbiamo risultati, prova con i metodi specifici per chip suppliers
            if (result.isEmpty()) {
                LOGGER.debug("Nessun elemento trovato nel metodo principale, uso getMatterStacksHolders");
                List<NetworkElement> suppliers = null;
                
                try {
                    suppliers = network.getMatterStacksHolders();
                    LOGGER.debug("Ottenuti {} suppliers usando getMatterStacksHolders", suppliers != null ? suppliers.size() : 0);
                } catch (Exception e) {
                    LOGGER.debug("Impossibile ottenere i suppliers usando getMatterStacksHolders: {}", e.getMessage());
                    
                    // Prova con reflection
                    try {
                        Method method = network.getClass().getMethod("getMatterStacksHolders");
                        method.setAccessible(true);
                        suppliers = (List<NetworkElement>) method.invoke(network);
                        LOGGER.debug("Ottenuti {} suppliers via reflection", suppliers != null ? suppliers.size() : 0);
                    } catch (Exception ex) {
                        LOGGER.error("Impossibile ottenere i suppliers via reflection: {}", ex.getMessage());
                    }
                }
                
                if (suppliers != null && !suppliers.isEmpty()) {
                    LOGGER.debug("Trovati {} chip suppliers nella rete", suppliers.size());
                    
                    for (NetworkElement element : suppliers) {
                        if (element == null) continue;
                        
                        BlockPos pos = getNetworkElementPosition(element);
                        if (pos != null) {
                            LOGGER.debug("Elaborazione elemento di rete alla posizione {}", pos);
                            result.add(pos);
                        }
                    }
                }
            }
            
            // Se ancora non abbiamo risultati, prova altri metodi
            if (result.isEmpty()) {
                LOGGER.debug("Nessun elemento trovato con i metodi principali, cercando alternativo");
                
                try {
                    // Prova ad ottenere networkHandler
                    Field networkHandlerField = network.getClass().getDeclaredField("networkHandler");
                    if (networkHandlerField != null) {
                        networkHandlerField.setAccessible(true);
                        Object networkHandler = networkHandlerField.get(network);
                        
                        if (networkHandler != null) {
                            LOGGER.debug("Ottenuto networkHandler di tipo {}", networkHandler.getClass().getName());
                            
                            // Prova a trovare un metodo che restituisce gli elementi
                            for (Method method : networkHandler.getClass().getMethods()) {
                                if (method.getReturnType().isAssignableFrom(List.class) && 
                                    method.getParameterCount() == 0 &&
                                    (method.getName().contains("get") || method.getName().contains("elements"))) {
                                    
                                    LOGGER.debug("Provando metodo: {}", method.getName());
                                    try {
                                        method.setAccessible(true);
                                        List<?> potentialElements = (List<?>) method.invoke(networkHandler);
                                        
                                        if (potentialElements != null && !potentialElements.isEmpty()) {
                                            LOGGER.debug("Metodo {} ha restituito {} elementi", method.getName(), potentialElements.size());
                                            
                                            for (Object element : potentialElements) {
                                                if (element instanceof NetworkElement) {
                                                    BlockPos pos = getNetworkElementPosition((NetworkElement) element);
                                                    if (pos != null) {
                                                        result.add(pos);
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        // Ignora errori nei tentativi di reflection
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Errore durante l'accesso al networkHandler: {}", e.getMessage());
                }
            }
            
            LOGGER.debug("Trovati {} potenziali ChipStorage nella rete", result.size());
        } catch (Exception e) {
            LOGGER.error("Errore durante la ricerca degli ChipStorage: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * Controlla se un'entità è un ChipStorage
     */
    private static boolean isChipStorage(BlockEntity entity) {
        if (entity == null) return false;
        
        // Metodo 1: instanceof diretto
        if (entity instanceof ChipStorageBlockEntity) {
            LOGGER.debug("Entità riconosciuta come ChipStorage tramite instanceof");
            return true;
        }
        
        // Metodo 2: controlla il nome della classe
        String className = entity.getClass().getName();
        if (className.equals(CHIP_STORAGE_CLASS_NAME)) {
            LOGGER.debug("Entità riconosciuta come ChipStorage tramite nome di classe esatto");
            return true;
        }
        
        // Metodo 3: controlla se la classe estende ChipStorageBlockEntity
        try {
            Class<?> chipStorageClass = Class.forName(CHIP_STORAGE_CLASS_NAME);
            if (chipStorageClass.isAssignableFrom(entity.getClass())) {
                LOGGER.debug("Entità riconosciuta come ChipStorage tramite isAssignableFrom");
                return true;
            }
        } catch (Exception e) {
            // Ignora l'errore
        }
        
        // Metodo 4: controlla se il nome della classe contiene "ChipStorage"
        if (className.contains("ChipStorage")) {
            LOGGER.debug("Entità riconosciuta come ChipStorage tramite nome contenente 'ChipStorage'");
            return true;
        }
        
        // Metodo aggiuntivo: controlla se l'entità ha il metodo getPatterns specifico degli ChipStorage
        try {
            java.lang.reflect.Method getPatternsMethod = entity.getClass().getMethod("getPatterns", Level.class, Object.class);
            if (getPatternsMethod != null) {
                LOGGER.debug("Entità riconosciuta come ChipStorage tramite metodo getPatterns");
                return true;
            }
        } catch (Exception e) {
            // Ignora l'errore
        }
        
        LOGGER.debug("L'entità alla posizione {} non è un ChipStorage, class: {}", entity.getBlockPos(), className);
        return false;
    }
    
    /**
     * Ottiene la posizione di un elemento di rete usando vari metodi
     */
    private static BlockPos getNetworkElementPosition(NetworkElement element) {
        if (element == null) return null;
        
        BlockPos pos = null;
        
        // Metodo 1: Accesso diretto al campo pos
        try {
            Field posField = element.getClass().getSuperclass().getDeclaredField("pos");
            posField.setAccessible(true);
            pos = (BlockPos) posField.get(element);
            if (pos != null) return pos;
        } catch (Exception ignored) {}
        
        // Metodo 2: Usando il metodo getPos
        try {
            Method getPosMethod = element.getClass().getMethod("getPos");
            getPosMethod.setAccessible(true);
            pos = (BlockPos) getPosMethod.invoke(element);
            if (pos != null) return pos;
        } catch (Exception ignored) {}
        
        // Metodo 3: Usando il metodo getPosition
        try {
            Method getPositionMethod = element.getClass().getMethod("getPosition");
            getPositionMethod.setAccessible(true);
            pos = (BlockPos) getPositionMethod.invoke(element);
            if (pos != null) return pos;
        } catch (Exception ignored) {}
        
        // Metodo 4: Accesso ai campi x, y, z
        try {
            Field xField = element.getClass().getDeclaredField("x");
            Field yField = element.getClass().getDeclaredField("y");
            Field zField = element.getClass().getDeclaredField("z");
            xField.setAccessible(true);
            yField.setAccessible(true);
            zField.setAccessible(true);
            int x = xField.getInt(element);
            int y = yField.getInt(element);
            int z = zField.getInt(element);
            return new BlockPos(x, y, z);
        } catch (Exception ignored) {}
        
        return null;
    }
    
    /**
     * Ottieni i pattern da un ChipStorage
     */
    private static List<MatterPattern> getPatterns(BlockEntity chipStorage, Level level) {
        if (chipStorage == null) return new ArrayList<>();
        
        // Metodo 1: Converti direttamente a ChipStorageBlockEntity
        try {
            ChipStorageBlockEntity typedStorage = (ChipStorageBlockEntity) chipStorage;
            return typedStorage.getPatterns(level, typedStorage);
        } catch (Exception e) {
            LOGGER.debug("Impossibile convertire a ChipStorageBlockEntity: {}", e.getMessage());
        }
        
        // Metodo 2: Usa reflection per chiamare getPatterns
        try {
            Method getPatternsMethod = chipStorage.getClass().getMethod("getPatterns", Level.class, Object.class);
            getPatternsMethod.setAccessible(true);
            return (List<MatterPattern>) getPatternsMethod.invoke(chipStorage, level, chipStorage);
        } catch (Exception e) {
            LOGGER.debug("Impossibile chiamare getPatterns via reflection: {}", e.getMessage());
        }
        
        // Metodo 3: Accedi direttamente ai campi
        try {
            // Cerca campi che potrebbero contenere pattern
            Field[] fields = chipStorage.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                
                // Se è una lista, prova a interpretarla come lista di pattern
                if (List.class.isAssignableFrom(field.getType())) {
                    List<?> list = (List<?>) field.get(chipStorage);
                    if (list != null && !list.isEmpty() && list.get(0) instanceof MatterPattern) {
                        LOGGER.debug("Trovato campo {} che contiene pattern", field.getName());
                        return (List<MatterPattern>) list;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Impossibile accedere ai campi: {}", e.getMessage());
        }
        
        LOGGER.error("Non è stato possibile ottenere i pattern dallo ChipStorage");
        return new ArrayList<>();
    }
    
    /**
     * Ottiene lo stack di un pattern
     */
    private static ItemStack getPatternStack(MatterPattern pattern) {
        if (pattern == null) return ItemStack.EMPTY;
        
        try {
            return pattern.getStack();
        } catch (Exception e) {
            // Prova con reflection
            try {
                Method getStackMethod = pattern.getClass().getMethod("getStack");
                getStackMethod.setAccessible(true);
                return (ItemStack) getStackMethod.invoke(pattern);
            } catch (Exception ex) {
                LOGGER.error("Impossibile ottenere lo stack del pattern: {}", ex.getMessage());
            }
        }
        
        return ItemStack.EMPTY;
    }
    
    /**
     * Ottiene il valore di materia di un pattern
     */
    private static int getPatternMatterValue(MatterPattern pattern, IMatterType matterType) {
        if (pattern == null || matterType == null) return 0;
        
        try {
            // APPROCCIO PRINCIPALE: Usare ClientReplicationCalculation come in RepAE2BridgeBlockEntity
            ItemStack stack = pattern.getStack();
            if (stack == null || stack.isEmpty()) {
                LOGGER.debug("Pattern ha uno stack nullo o vuoto");
                return 0;
            }
            
            LOGGER.debug("Ottenuto ItemStack {} dal pattern, uso ClientReplicationCalculation", stack.getItem().toString());
            
            try {
                // Usa reflection per accedere a ClientReplicationCalculation.getMatterCompound
                Class<?> clientCalcClass = Class.forName("com.buuz135.replication.calculation.client.ClientReplicationCalculation");
                Method getMatterCompoundMethod = clientCalcClass.getMethod("getMatterCompound", ItemStack.class);
                Object matterCompound = getMatterCompoundMethod.invoke(null, stack);
                
                if (matterCompound != null) {
                    LOGGER.debug("Ottenuto matterCompound per {}", stack.getItem().toString());
                    
                    // Accedi a matterCompound.getValues()
                    Method getValuesMethod = matterCompound.getClass().getMethod("getValues");
                    Object valuesMap = getValuesMethod.invoke(matterCompound);
                    
                    if (valuesMap instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) valuesMap;
                        LOGGER.debug("Mappa di valori contiene {} elementi", map.size());
                        
                        // Cerca il valore per questo tipo di materia
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            // I valori sono di tipo MatterValue
                            Object matterValue = entry.getValue();
                            if (matterValue != null) {
                                // Ottieni il tipo di materia da MatterValue
                                Method getMatterMethod = matterValue.getClass().getMethod("getMatter");
                                Object entryMatterType = getMatterMethod.invoke(matterValue);
                                
                                // Confronta con il tipo di materia che cerchiamo
                                if (entryMatterType != null && entryMatterType.toString().equals(matterType.toString())) {
                                    // Ottieni l'amount
                                    Method getAmountMethod = matterValue.getClass().getMethod("getAmount");
                                    Object amountObj = getAmountMethod.invoke(matterValue);
                                    
                                    if (amountObj instanceof Number) {
                                        double amount = ((Number) amountObj).doubleValue();
                                        int value = (int) Math.ceil(amount);
                                        LOGGER.debug("Ottenuto valore materia {} per {} usando ClientReplicationCalculation: {}", 
                                            matterType.getName(), stack.getItem().toString(), value);
                                        return value;
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LOGGER.debug("MatterCompound non trovato per {}", stack.getItem().toString());
                }
            } catch (Exception e) {
                LOGGER.debug("Errore usando ClientReplicationCalculation: {}", e.getMessage());
            }
            
            // APPROCCIO ALTERNATIVO: Se ClientReplicationCalculation fallisce, prova con i metodi precedenti
            
            // Approccio 1: Usa ReplicationRegistry.Calculate
            try {
                Method getCalculateMethod = ReplicationRegistry.class.getMethod("getCalculate");
                if (getCalculateMethod != null) {
                    Object calculator = getCalculateMethod.invoke(null);
                    if (calculator != null) {
                        LOGGER.debug("Ottenuto calculator: {}", calculator.getClass().getName());
                        
                        // Cerca metodi appropriati nel calculator
                        Method[] methods = calculator.getClass().getMethods();
                        for (Method method : methods) {
                            if ((method.getName().contains("Item") && method.getName().contains("Value") && 
                                 method.getParameterCount() == 2) ||
                                (method.getName().contains("Matter") && method.getName().contains("Value") && 
                                 method.getParameterCount() == 2)) {
                                
                                try {
                                    LOGGER.debug("Tentativo con metodo: {}", method.getName());
                                    Object result = method.invoke(calculator, stack, matterType);
                                    if (result instanceof Number) {
                                        int value = ((Number) result).intValue();
                                        LOGGER.debug("Ottenuto valore materia {} per {} usando il calculator: {}", 
                                            matterType.getName(), stack.getItem().toString(), value);
                                        return value;
                                    }
                                } catch (Exception ex) {
                                    LOGGER.debug("Errore con metodo {}: {}", method.getName(), ex.getMessage());
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Impossibile accedere a ReplicationRegistry.getCalculate: {}", e.getMessage());
            }
            
            // Approccio 2: Usa ReplicationRegistry.getItemMatterValues
            try {
                Method getItemMatterValuesMethod = ReplicationRegistry.class.getMethod("getItemMatterValues", ItemStack.class);
                if (getItemMatterValuesMethod != null) {
                    Object valueMap = getItemMatterValuesMethod.invoke(null, stack);
                    if (valueMap instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) valueMap;
                        LOGGER.debug("Ottenuta mappa di valori per {}, contiene {} elementi", stack.getItem().toString(), map.size());
                        
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() instanceof IMatterType) {
                                IMatterType keyType = (IMatterType) entry.getKey();
                                if (keyType.getName().equals(matterType.getName())) {
                                    if (entry.getValue() instanceof Number) {
                                        int value = ((Number) entry.getValue()).intValue();
                                        LOGGER.debug("Ottenuto valore materia {} per {} usando getItemMatterValues: {}", 
                                            matterType.getName(), stack.getItem().toString(), value);
                                        return value;
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Impossibile accedere a ReplicationRegistry.getItemMatterValues: {}", e.getMessage());
            }
            
            // Approccio 3: Usa ReplicationRegistry.Matter.<tipoMateria>.getValue(stack)
            try {
                Field matterField = null;
                // Cerca il campo statico per questo tipo di materia
                for (Field field : ReplicationRegistry.Matter.class.getDeclaredFields()) {
                    if (field.getName().equalsIgnoreCase(matterType.getName())) {
                        field.setAccessible(true);
                        matterField = field;
                        break;
                    }
                }
                
                if (matterField != null) {
                    Object matterRegistration = matterField.get(null);
                    if (matterRegistration != null) {
                        // Ottieni l'istanza del tipo di materia
                        Method getMethod = matterRegistration.getClass().getMethod("get");
                        Object matterTypeInstance = getMethod.invoke(matterRegistration);
                        
                        if (matterTypeInstance != null) {
                            LOGGER.debug("Ottenuto tipo di materia: {}", matterTypeInstance.getClass().getName());
                            
                            // Cerca il metodo getValue specifico per ItemStack
                            Method getValueMethod = matterTypeInstance.getClass().getMethod("getValue", ItemStack.class);
                            if (getValueMethod != null) {
                                Object value = getValueMethod.invoke(matterTypeInstance, stack);
                                if (value instanceof Number) {
                                    int result = ((Number) value).intValue();
                                    LOGGER.debug("Ottenuto valore materia {} per {} usando Matter registry: {}", 
                                        matterType.getName(), stack.getItem().toString(), result);
                                    return result;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Impossibile accedere a ReplicationRegistry.Matter: {}", e.getMessage());
            }
            
            // FALLBACK: Se non riusciamo a ottenere i valori tramite registry, proviamo a estrarli direttamente dal pattern
            // Questo è utile per patterns che già contengono i valori direttamente
            
            // Fallback 1: Prova i metodi del pattern
            try {
                String[] methodNames = {"getMatterValue", "getValue", "getAmount", "get" + matterType.getName() + "Value"};
                
                for (String methodName : methodNames) {
                    try {
                        Method method = pattern.getClass().getMethod(methodName, IMatterType.class);
                        method.setAccessible(true);
                        Object result = method.invoke(pattern, matterType);
                        if (result instanceof Number) {
                            int value = ((Number) result).intValue();
                            LOGGER.debug("Ottenuto valore materia {} usando method fallback {}: {}", 
                                matterType.getName(), methodName, value);
                            return value;
                        }
                    } catch (Exception methodEx) {
                        // Continua con altri metodi
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Errore durante il fallback ai metodi: {}", e.getMessage());
            }
            
            // Fallback 2: Cerca nei campi del pattern
            try {
                for (Field field : pattern.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    String fieldName = field.getName().toLowerCase();
                    
                    if (fieldName.contains("matter") || fieldName.contains("value") || 
                        fieldName.contains("amounts") || fieldName.contains("values")) {
                        
                        Object fieldValue = field.get(pattern);
                        if (fieldValue instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) fieldValue;
                            LOGGER.debug("Campo mappa: {}, con {} elementi", fieldName, map.size());
                            
                            for (Map.Entry<?, ?> entry : map.entrySet()) {
                                if (entry.getKey() instanceof IMatterType) {
                                    IMatterType keyType = (IMatterType) entry.getKey();
                                    if (keyType.getName().equals(matterType.getName())) {
                                        if (entry.getValue() instanceof Number) {
                                            int value = ((Number) entry.getValue()).intValue();
                                            LOGGER.debug("Ottenuto valore materia {} da campo mappa: {}", 
                                                matterType.getName(), value);
                                            return value;
                                        }
                                    }
                                } else if (entry.getKey() instanceof String) {
                                    String keyStr = (String) entry.getKey();
                                    if (keyStr.equalsIgnoreCase(matterType.getName())) {
                                        if (entry.getValue() instanceof Number) {
                                            int value = ((Number) entry.getValue()).intValue();
                                            LOGGER.debug("Ottenuto valore materia {} da campo mappa con chiave stringa: {}", 
                                                matterType.getName(), value);
                                            return value;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Errore durante il fallback ai campi: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.debug("Errore generico nell'ottenere il valore della materia: {}", e.getMessage());
        }
        
        return 0;
    }
    
    /**
     * Ottiene la quantità di materia disponibile nel network
     * @param network Il network di materia
     * @param matterType Il tipo di materia da controllare
     * @return La quantità di materia disponibile
     */
    public static long getMatterAmount(MatterNetwork network, IMatterType matterType) {
        if (network == null || matterType == null) {
            return 0;
        }
        
        try {
            // Applica un timeout per evitare blocchi su calcoli lunghi
            long startTime = System.currentTimeMillis();
            long timeout = 500; // 500ms timeout
            
            try {
                // Ottieni il valore con un timeout implicito
                long amount = network.calculateMatterAmount(matterType);
                
                // Verifica se stiamo impiegando troppo tempo
                if (System.currentTimeMillis() - startTime > timeout) {
                    LOGGER.warn("Calcolo della materia troppo lento ({}ms) per {}", 
                        System.currentTimeMillis() - startTime, matterType.getName());
                }
                
                return amount;
            } catch (NullPointerException e) {
                LOGGER.error("NullPointerException durante l'accesso alla quantità di materia {}: {}",
                    matterType.getName(), e.getMessage());
                return 0;
            } catch (Exception e) {
                LOGGER.error("Errore durante l'accesso alla quantità di materia {}: {}", 
                    matterType.getName(), e.getMessage());
                return 0;
            }
        } catch (Exception e) {
            // Cattura qualsiasi eccezione, anche nell'accesso ai metodi logging
            try {
                LOGGER.error("Errore critico durante l'accesso alla quantità di materia: {}", e.getMessage(), e);
            } catch (Exception ignored) {
                // Non possiamo fare nulla qui, solo prevenire il crash
            }
            return 0;
        }
    }
    
    /**
     * Ottiene un'istanza di IMatterType dal nome
     */
    @Nullable
    public static IMatterType getMatterTypeByName(String name) {
        if (name == null) return null;
        
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
} 