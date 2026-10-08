package com.core.attribute.tacz.network;

import com.core.attribute.tacz.AttributePermissions;
import com.core.attribute.tacz.data.AttributeData;
import com.core.attribute.tacz.util.LegacyText;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

public record SaveAttributesC2S(CompoundTag payload) {
    public static final String ROOT_PAYLOAD = "Root";
    public static final String CUSTOM_NAME = "CustomName";
    public static final String CLEAR_NAME = "ClearName";

    public static void encode(SaveAttributesC2S message, FriendlyByteBuf buffer) {
        buffer.writeNbt(message.payload);
    }

    public static SaveAttributesC2S decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return new SaveAttributesC2S(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(SaveAttributesC2S message, Supplier<NetworkEvent.Context> contextSupplier) {
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
            if (message.payload.contains(ROOT_PAYLOAD, 10)) {
                AttributeData.writeRoot(stack, message.payload.getCompound(ROOT_PAYLOAD));
            }
            if (message.payload.contains(AttributeData.ENCHANTMENTS, 9)) {
                AttributeData.applyEnchantTag(stack, message.payload.getList(AttributeData.ENCHANTMENTS, 10));
            }
            if (message.payload.getBoolean(CLEAR_NAME)) {
                stack.resetHoverName();
            } else if (message.payload.contains(CUSTOM_NAME, 8)) {
                String name = message.payload.getString(CUSTOM_NAME).trim();
                if (name.isEmpty()) {
                    stack.resetHoverName();
                } else {
                    stack.setHoverName((Component)LegacyText.parse(name, null));
                }
            }
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        });
        context.setPacketHandled(true);
    }
}

