package com.core.attribute.tacz.client;

import com.core.attribute.tacz.client.AttributeEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

public final class ClientPacketHandlers {
    private ClientPacketHandlers() {
    }

    public static void openEditor(ItemStack stack) {
        Minecraft.getInstance().setScreen((Screen)new AttributeEditorScreen(stack));
    }
}

