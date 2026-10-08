package com.core.attribute.tacz.network;

import com.core.attribute.tacz.AttributePermissions;
import com.core.attribute.tacz.network.NetworkHandler;
import com.core.attribute.tacz.network.OpenEditorS2C;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record RequestOpenEditorC2S() {
    public static void encode(RequestOpenEditorC2S message, FriendlyByteBuf buffer) {
    }

    public static RequestOpenEditorC2S decode(FriendlyByteBuf buffer) {
        return new RequestOpenEditorC2S();
    }

    public static void handle(RequestOpenEditorC2S message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (!AttributePermissions.canEdit(player)) {
                player.displayClientMessage((Component)Component.translatable((String)"message.tacz_attribute_data.no_permission"), true);
                return;
            }
            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty()) {
                return;
            }
            NetworkHandler.sendTo(player, new OpenEditorS2C(stack.copy()));
        });
        context.setPacketHandled(true);
    }
}

