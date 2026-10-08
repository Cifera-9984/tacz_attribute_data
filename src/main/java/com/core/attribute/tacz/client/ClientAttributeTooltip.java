package com.core.attribute.tacz.client;

import com.core.attribute.tacz.data.AttributeLine;
import com.core.attribute.tacz.data.AttributeType;
import com.core.attribute.tacz.tooltip.AttributeTooltip;
import com.core.attribute.tacz.util.LegacyText;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import org.joml.Matrix4f;

public class ClientAttributeTooltip
implements ClientTooltipComponent {
    private final List<AttributeLine> lines;
    private int width;

    public ClientAttributeTooltip(AttributeTooltip tooltip) {
        this.lines = tooltip.lines();
        this.width = 0;
        Font font = Minecraft.getInstance().font;
        for (AttributeLine line : this.lines) {
            this.width = Math.max(this.width, font.width((FormattedText)ClientAttributeTooltip.lineToComponent(line)));
        }
    }

    public int getHeight() {
        return this.lines.size() * 10;
    }

    public int getWidth(Font font) {
        return this.width;
    }

    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
    }

    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
        int offset = y;
        for (AttributeLine line : this.lines) {
            MutableComponent component = ClientAttributeTooltip.lineToComponent(line);
            font.drawInBatch(component.getVisualOrderText(), (float)x, (float)offset, 0xFFFFFF, false, matrix, (MultiBufferSource)bufferSource, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            offset += 10;
        }
    }

    private static MutableComponent lineToComponent(AttributeLine line) {
        AttributeType type = line.type();
        if (type == AttributeType.CUSTOM && !line.text().contains(":") && !line.text().contains("\uff1a")) {
            return LegacyText.parse(line.text(), type.color());
        }
        String label = line.label();
        String value = line.valueText();
        MutableComponent left = LegacyText.parse(label + ": ", type.color());
        if (value.isBlank()) {
            return left.append((Component)LegacyText.parse(line.text().replace(label + ":", "").trim(), type.color()));
        }
        return left.append((Component)LegacyText.parse(value, type.color()));
    }
}

