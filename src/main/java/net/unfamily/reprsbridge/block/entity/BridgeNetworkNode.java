package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.autocrafting.AutocraftingNetworkComponent;
import com.refinedmods.refinedstorage.api.network.impl.node.AbstractNetworkNode;
import com.refinedmods.refinedstorage.api.network.impl.node.externalstorage.ExposedExternalStorage;
import com.refinedmods.refinedstorage.api.network.node.NetworkNodeActor;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.api.storage.root.RootStorage;

import java.util.Collection;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

public class BridgeNetworkNode extends AbstractNetworkNode {
    private long energyUsage;

    private final Actor actor = new NetworkNodeActor(this);
    @Nullable
    private BridgeExportState exportState;
    @Nullable
    private BridgeTransferResult[] lastResults;
    private ToLongFunction<ResourceKey> transferQuotaProvider = resource -> Long.MAX_VALUE;
    private OnMissingResources onMissingResources = OnMissingResources.EMPTY;

    public BridgeNetworkNode(final long energyUsage) {
        this.energyUsage = energyUsage;
    }

    public void setTransferQuotaProvider(final ToLongFunction<ResourceKey> transferQuotaProvider) {
        this.transferQuotaProvider = transferQuotaProvider;
    }

    public void setOnMissingResources(final OnMissingResources onMissingResources) {
        this.onMissingResources = onMissingResources;
    }

    public boolean isActingAsExternalStorage() {
        if (network == null) {
            return false;
        }
        return network.getComponent(StorageNetworkComponent.class).hasSource(
            this::isStorageAnExternalStorageProviderThatReferencesMe
        );
    }

    private boolean isStorageAnExternalStorageProviderThatReferencesMe(final Storage storage) {
        return storage instanceof ExposedExternalStorage proxy
            && proxy.getExternalStorageProvider() instanceof BridgeExternalStorageProvider bridgeProvider
            && bridgeProvider.getBridge() == this;
    }

    @Nullable
    public BridgeExportState getExportState() {
        return exportState;
    }

    public void setEnergyUsage(final long energyUsage) {
        this.energyUsage = energyUsage;
    }

    public void setExportState(@Nullable final BridgeExportState exportState) {
        this.exportState = exportState;
        this.lastResults = exportState != null
            ? new BridgeTransferResult[exportState.getSlots()]
            : null;
    }

    @Override
    public void doWork() {
        super.doWork();
        if (exportState == null || network == null || !isActive()) {
            return;
        }
        final RootStorage storage = network.getComponent(StorageNetworkComponent.class);
        for (int i = 0; i < exportState.getSlots(); ++i) {
            updateSlot(exportState, i, storage);
        }
    }

    private void updateSlot(final BridgeExportState state, final int index, final RootStorage storage) {
        final ResourceKey want = state.getRequestedResource(index);
        final ResourceKey got = state.getExportedResource(index);
        if (want == null && got != null) {
            clearSlot(state, index, got, storage);
        } else if (want != null && got == null) {
            updateEmptySlot(state, index, want, storage);
        } else if (want != null) {
            final boolean valid = state.isExportedResourceValid(want, got);
            if (!valid) {
                clearSlot(state, index, got, storage);
            } else {
                updateSlot(state, index, got, storage);
            }
        }
    }

    private void updateSlot(final BridgeExportState state,
                            final int slot,
                            final ResourceKey got,
                            final RootStorage storage) {
        final long wantedAmount = state.getRequestedAmount(slot);
        final long currentAmount = state.getExportedAmount(slot);
        final long difference = wantedAmount - currentAmount;
        if (difference > 0) {
            extractMoreFromStorage(state, slot, got, difference, storage);
        } else if (difference < 0) {
            insertOverflowToStorage(state, slot, got, difference, storage);
        }
    }

    private void clearSlot(final BridgeExportState state,
                           final int slot,
                           final ResourceKey got,
                           final RootStorage storage) {
        final long currentAmount = state.getExportedAmount(slot);
        final long inserted = storage.insert(
            got,
            Math.min(currentAmount, transferQuotaProvider.applyAsLong(got)),
            Action.EXECUTE,
            actor
        );
        if (inserted == 0) {
            updateResult(slot, BridgeTransferResult.STORAGE_DOES_NOT_ACCEPT_RESOURCE);
            return;
        }
        state.shrinkExportedAmount(slot, inserted);
        updateResult(slot, BridgeTransferResult.EXPORTED);
    }

    private void updateEmptySlot(final BridgeExportState state,
                                 final int slot,
                                 final ResourceKey resource,
                                 final RootStorage storage) {
        final long wantedAmount = state.getRequestedAmount(slot);
        final long correctedAmount = Math.min(transferQuotaProvider.applyAsLong(resource), wantedAmount);
        final Collection<ResourceKey> candidates = state.expandExportCandidates(storage, resource);
        for (final ResourceKey candidate : candidates) {
            final long extracted = storage.extract(candidate, correctedAmount, Action.EXECUTE, actor);
            if (extracted > 0) {
                state.setExportSlot(slot, candidate, extracted);
                updateResult(slot, BridgeTransferResult.EXPORTED);
                return;
            }
        }
        if (network == null) {
            return;
        }
        final BridgeTransferResult result = onMissingResources.onMissingResources(resource, correctedAmount, actor,
            network);
        updateResult(slot, result);
    }

    private void extractMoreFromStorage(final BridgeExportState state,
                                        final int slot,
                                        final ResourceKey resource,
                                        final long amount,
                                        final RootStorage storage) {
        final long correctedAmount = Math.min(transferQuotaProvider.applyAsLong(resource), amount);
        final long extracted = storage.extract(resource, correctedAmount, Action.EXECUTE, actor);
        if (extracted == 0) {
            if (network == null) {
                return;
            }
            final BridgeTransferResult result =
                onMissingResources.onMissingResources(resource, correctedAmount, actor, network);
            updateResult(slot, result);
            return;
        }
        state.growExportedAmount(slot, extracted);
        updateResult(slot, BridgeTransferResult.EXPORTED);
    }

    private void insertOverflowToStorage(final BridgeExportState state,
                                         final int slot,
                                         final ResourceKey resource,
                                         final long amount,
                                         final RootStorage storage) {
        final long correctedAmount = Math.min(transferQuotaProvider.applyAsLong(resource), Math.abs(amount));
        final long inserted = storage.insert(resource, correctedAmount, Action.EXECUTE, actor);
        if (inserted == 0) {
            updateResult(slot, BridgeTransferResult.STORAGE_DOES_NOT_ACCEPT_RESOURCE);
            return;
        }
        state.shrinkExportedAmount(slot, inserted);
        updateResult(slot, BridgeTransferResult.EXPORTED);
    }

    private void updateResult(final int slot, final BridgeTransferResult result) {
        if (lastResults == null) {
            return;
        }
        lastResults[slot] = result;
    }

    @Nullable
    public BridgeTransferResult getLastResult(final int slot) {
        if (lastResults == null) {
            return null;
        }
        return lastResults[slot];
    }

    @Override
    public long getEnergyUsage() {
        return energyUsage;
    }

    @FunctionalInterface
    public interface OnMissingResources {
        OnMissingResources EMPTY = (resource, amount, a, network)
            -> BridgeTransferResult.RESOURCE_MISSING;

        BridgeTransferResult onMissingResources(ResourceKey resource, long amount, Actor actor, Network network);
    }

    public static class AutocraftOnMissingResources implements OnMissingResources {
        @Override
        public BridgeTransferResult onMissingResources(final ResourceKey resource,
                                                          final long amount,
                                                          final Actor actor,
                                                          final Network network) {
            final AutocraftingNetworkComponent autocrafting = network.getComponent(AutocraftingNetworkComponent.class);
            if (!autocrafting.getPatternsByOutput(resource).isEmpty()) {
                final var ensureResult = autocrafting.ensureTask(resource, amount, actor);
                final boolean success = ensureResult == AutocraftingNetworkComponent.EnsureResult.TASK_CREATED
                    || ensureResult == AutocraftingNetworkComponent.EnsureResult.TASK_ALREADY_RUNNING;
                return success
                    ? BridgeTransferResult.AUTOCRAFTING_STARTED
                    : BridgeTransferResult.AUTOCRAFTING_MISSING_RESOURCES;
            }
            return BridgeTransferResult.RESOURCE_MISSING;
        }
    }
}