package net.unfamily.reprsbridge.block.entity;

/**
 * Rappresenta i possibili risultati di un'operazione di trasferimento del bridge.
 */
public enum BridgeTransferResult {
    EXPORTED,
    RESOURCE_MISSING,
    STORAGE_DOES_NOT_ACCEPT_RESOURCE,
    AUTOCRAFTING_STARTED,
    AUTOCRAFTING_MISSING_RESOURCES
} 