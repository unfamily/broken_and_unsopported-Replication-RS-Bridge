package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.api.storage.external.ExternalStorageProvider;

import javax.annotation.Nullable;

/**
 * Provider di storage esterno per il bridge.
 */
public interface BridgeExternalStorageProvider extends ExternalStorageProvider {
    /**
     * @return il nodo di rete bridge associato a questo provider
     */
    @Nullable
    BridgeNetworkNode getBridge();
} 