package com.core.attribute.tacz.network;

import com.core.attribute.tacz.network.OpenEditorS2C;
import com.core.attribute.tacz.network.RequestOpenEditorC2S;
import com.core.attribute.tacz.network.SaveAttributesC2S;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder.named((ResourceLocation)new ResourceLocation("tacz_attribute_data", "main")).networkProtocolVersion(() -> "1").clientAcceptedVersions("1"::equals).serverAcceptedVersions("1"::equals).simpleChannel();
    private static int id;

    private NetworkHandler() {
    }

    public static void register() {
        CHANNEL.registerMessage(id++, RequestOpenEditorC2S.class, RequestOpenEditorC2S::encode, RequestOpenEditorC2S::decode, RequestOpenEditorC2S::handle);
        CHANNEL.registerMessage(id++, OpenEditorS2C.class, OpenEditorS2C::encode, OpenEditorS2C::decode, OpenEditorS2C::handle);
        CHANNEL.registerMessage(id++, SaveAttributesC2S.class, SaveAttributesC2S::encode, SaveAttributesC2S::decode, SaveAttributesC2S::handle);
    }

    public static void sendTo(ServerPlayer player, Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}

