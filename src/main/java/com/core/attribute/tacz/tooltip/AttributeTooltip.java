package com.core.attribute.tacz.tooltip;

import com.core.attribute.tacz.data.AttributeLine;
import com.core.attribute.tacz.data.AttributeType;
import java.util.List;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record AttributeTooltip(List<AttributeLine> lines) implements TooltipComponent
{
    public AttributeTooltip {
        lines = lines.stream().filter(line -> !line.hidden() && !line.text().isBlank()).toList();
    }

    public static AttributeTooltip of(List<AttributeLine> lines) {
        return new AttributeTooltip(lines);
    }

    public static String formatLabel(AttributeType type) {
        return type.displayName();
    }
}

