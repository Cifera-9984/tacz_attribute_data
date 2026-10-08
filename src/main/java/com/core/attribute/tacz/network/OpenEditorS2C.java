package com.core.attribute.tacz.network;

import com.core.attribute.tacz.client.ClientPacketHandlers;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record OpenEditorS2C(ItemStack stack) {
    public static void encode(OpenEditorS2C message, FriendlyByteBuf buffer) {
        buffer.writeItem(message.stack);
    }

    public static OpenEditorS2C decode(FriendlyByteBuf buffer) {
        return new OpenEditorS2C(buffer.readItem());
    }

    public static void handle(OpenEditorS2C message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn((Dist)Dist.CLIENT, () -> () -> ClientPacketHandlers.openEditor(message.stack)));
        context.setPacketHandled(true);
    }
}

