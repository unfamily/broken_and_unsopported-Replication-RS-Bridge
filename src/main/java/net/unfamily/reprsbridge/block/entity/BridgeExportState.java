package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * Rappresenta lo stato di esportazione di un bridge.
 */
public interface BridgeExportState {
    /**
     * @return il numero di slot disponibili
     */
    int getSlots();

    /**
     * Espande i candidati di esportazione per una risorsa.
     *
     * @param storage lo storage root
     * @param resource la risorsa da esportare
     * @return una collezione di risorse candidate
     */
    Collection<ResourceKey> expandExportCandidates(RootStorage storage, ResourceKey resource);

    /**
     * Controlla se la risorsa esportata è valida.
     *
     * @param requested la risorsa richiesta
     * @param exported la risorsa esportata
     * @return true se la risorsa esportata è valida, false altrimenti
     */
    boolean isExportedResourceValid(ResourceKey requested, ResourceKey exported);

    /**
     * @param slot lo slot
     * @return la risorsa richiesta per lo slot specificato
     */
    @Nullable
    ResourceKey getRequestedResource(int slot);

    /**
     * @param slot lo slot
     * @return la quantità richiesta per lo slot specificato
     */
    long getRequestedAmount(int slot);

    /**
     * @param slot lo slot
     * @return la risorsa esportata per lo slot specificato
     */
    @Nullable
    ResourceKey getExportedResource(int slot);

    /**
     * @param slot lo slot
     * @return la quantità esportata per lo slot specificato
     */
    long getExportedAmount(int slot);

    /**
     * Imposta lo slot di esportazione.
     *
     * @param slot lo slot
     * @param resource la risorsa da esportare
     * @param amount la quantità da esportare
     */
    void setExportSlot(int slot, ResourceKey resource, long amount);

    /**
     * Riduce la quantità esportata.
     *
     * @param slot lo slot
     * @param amount la quantità da ridurre
     */
    void shrinkExportedAmount(int slot, long amount);

    /**
     * Aumenta la quantità esportata.
     *
     * @param slot lo slot
     * @param amount la quantità da aumentare
     */
    void growExportedAmount(int slot, long amount);

    /**
     * Inserisce una risorsa.
     *
     * @param resource la risorsa da inserire
     * @param amount la quantità da inserire
     * @param action l'azione da eseguire
     * @return la quantità inserita
     */
    long insert(ResourceKey resource, long amount, Action action);

    /**
     * Estrae una risorsa.
     *
     * @param resource la risorsa da estrarre
     * @param amount la quantità da estrarre
     * @param action l'azione da eseguire
     * @return la quantità estratta
     */
    long extract(ResourceKey resource, long amount, Action action);
} 