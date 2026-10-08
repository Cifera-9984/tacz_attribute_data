package com.core.attribute.tacz.client;

import com.core.attribute.tacz.client.ClientAttributeTooltip;
import com.core.attribute.tacz.network.NetworkHandler;
import com.core.attribute.tacz.network.RequestOpenEditorC2S;
import com.core.attribute.tacz.tooltip.AttributeTooltip;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

public final class ClientEvents {
    public static final String CATEGORY = "key.categories.tacz_attribute_data";
    public static final KeyMapping OPEN_EDITOR = new KeyMapping("key.tacz_attribute_data.open_editor", InputConstants.Type.KEYSYM, 85, "key.categories.tacz_attribute_data");

    private ClientEvents() {
    }

    @Mod.EventBusSubscriber(modid="tacz_attribute_data", value={Dist.CLIENT})
    public static final class ForgeBus {
        private ForgeBus() {
        }

        @SubscribeEvent
        public static void clientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.screen != null) {
                return;
            }
            while (OPEN_EDITOR.consumeClick()) {
                NetworkHandler.CHANNEL.sendToServer((Object)new RequestOpenEditorC2S());
            }
        }
    }

    @Mod.EventBusSubscriber(modid="tacz_attribute_data", bus=Mod.EventBusSubscriber.Bus.MOD, value={Dist.CLIENT})
    public static final class ModBus {
        private ModBus() {
        }

        @SubscribeEvent
        public static void registerKeys(RegisterKeyMappingsEvent event) {
            event.register(OPEN_EDITOR);
        }

        @SubscribeEvent
        public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(AttributeTooltip.class, ClientAttributeTooltip::new);
        }
    }
}

