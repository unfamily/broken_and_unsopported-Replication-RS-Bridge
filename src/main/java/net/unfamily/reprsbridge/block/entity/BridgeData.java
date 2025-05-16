package net.unfamily.reprsbridge.block.entity;

import com.refinedmods.refinedstorage.common.support.exportingindicator.ExportingIndicator;
import com.refinedmods.refinedstorage.common.support.resource.ResourceContainerData;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import static com.refinedmods.refinedstorage.common.util.PlatformUtil.enumStreamCodec;

public record BridgeData(ResourceContainerData filterContainerData,
                             ResourceContainerData exportedResourcesContainerData,
                             List<ExportingIndicator> exportingIndicators) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeData> STREAM_CODEC = StreamCodec.composite(
        ResourceContainerData.STREAM_CODEC, BridgeData::filterContainerData,
        ResourceContainerData.STREAM_CODEC, BridgeData::exportedResourcesContainerData,
        ByteBufCodecs.collection(ArrayList::new, enumStreamCodec(ExportingIndicator.values())),
        BridgeData::exportingIndicators,
        BridgeData::new
    );
}