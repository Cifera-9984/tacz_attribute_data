package com.core.attribute.tacz;

import com.core.attribute.tacz.network.NetworkHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(value="tacz_attribute_data")
public class TaczAttributeData {
    public static final String MOD_ID = "tacz_attribute_data";

    public TaczAttributeData() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        NetworkHandler.register();
    }
}

