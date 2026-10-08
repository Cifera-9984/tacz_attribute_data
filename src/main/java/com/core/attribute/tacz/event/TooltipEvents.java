package com.core.attribute.tacz.event;

import com.core.attribute.tacz.data.AttributeData;
import com.core.attribute.tacz.data.AttributeLine;
import com.core.attribute.tacz.data.AttributeType;
import com.core.attribute.tacz.util.LegacyText;
import com.tacz.guns.api.item.IGun;
import java.util.ListIterator;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tacz_attribute_data")
public final class TooltipEvents {
    private TooltipEvents() {
    }

    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!AttributeData.hasData(stack)) {
            return;
        }
        boolean hideOriginalPanel = AttributeData.hideVanillaPanel(stack);
        boolean taczGun = stack.getItem() instanceof IGun;
        AttributeData.applyTooltipHideFlags(stack, hideOriginalPanel);
        if (hideOriginalPanel) {
            TooltipEvents.removeOriginalTooltipPanel(event, taczGun);
        } else if (AttributeData.hideMainHandAttributes(stack)) {
            TooltipEvents.removeMainHandAttributeModifiers(event);
        }
        // The mod's own attribute lines are opt-in: hidden unless the "属性标签" switch is on.
        if (AttributeData.hideAttributeLabels(stack) || !AttributeData.hasVisibleLines(stack)) {
            return;
        }
        event.getToolTip().add(Component.empty());
        for (AttributeLine line : AttributeData.getLines(stack)) {
            if (line.hidden() || line.text().isBlank()) continue;
            event.getToolTip().add(TooltipEvents.toComponent(line));
        }
    }

    private static MutableComponent toComponent(AttributeLine line) {
        if (line.type() == AttributeType.CUSTOM && !line.text().contains(":") && !line.text().contains("\uff1a")) {
            return LegacyText.parse(line.text(), line.type().color());
        }
        String value = line.valueText();
        MutableComponent component = LegacyText.parse(line.label() + ": ", line.type().color());
        if (value.isBlank()) {
            return component.append((Component)LegacyText.parse(line.text(), line.type().color()));
        }
        return component.append((Component)LegacyText.parse(value, line.type().color()));
    }

    private static void removeOriginalTooltipPanel(ItemTooltipEvent event, boolean taczGun) {
        ListIterator iterator = event.getToolTip().listIterator();
        if (iterator.hasNext()) {
            iterator.next();
        }
        while (iterator.hasNext()) {
            String text = ((Component)iterator.next()).getString();
            if (!TooltipEvents.isVanillaAttributeLine(text) && !TooltipEvents.isAdvancedDebugLine(text) && (!taczGun || !TooltipEvents.isTaczGunLine(text))) continue;
            iterator.remove();
        }
    }

    private static void removeMainHandAttributeModifiers(ItemTooltipEvent event) {
        ListIterator iterator = event.getToolTip().listIterator();
        boolean mainHandSection = false;
        while (iterator.hasNext()) {
            String text = ((Component)iterator.next()).getString();
            if (TooltipEvents.isMainHandAttributeHeader(text)) {
                iterator.remove();
                mainHandSection = true;
                continue;
            }
            if (mainHandSection && TooltipEvents.isVanillaModifierValue(text)) {
                iterator.remove();
                continue;
            }
            if (text.isBlank()) continue;
            mainHandSection = false;
        }
    }

    private static boolean isVanillaAttributeLine(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        return text.isBlank() || text.contains("when in") || text.contains("\u5728\u4e3b\u624b") || text.contains("\u5728\u526f\u624b") || text.contains("\u88c5\u5907\u65f6") || text.contains("attack damage") || text.contains("attack speed") || text.contains("armor") || text.contains("armor toughness") || text.contains("knockback resistance") || text.contains("max health") || text.contains("\u653b\u51fb\u4f24\u5bb3") || text.contains("\u653b\u51fb\u901f\u5ea6") || text.contains("\u62a4\u7532") || text.contains("\u76d4\u7532") || text.contains("\u76d4\u7532\u97e7\u6027") || text.contains("\u6700\u5927\u751f\u547d");
    }

    private static boolean isMainHandAttributeHeader(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        return text.contains("when in main hand") || text.contains("\u5728\u4e3b\u624b\u65f6") || text.contains("\u5728\u4e3b\u624b");
    }

    private static boolean isVanillaModifierValue(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        return text.contains("attack damage") || text.contains("attack speed") || text.contains("attack knockback") || text.contains("knockback resistance") || text.contains("armor") || text.contains("armor toughness") || text.contains("max health") || text.contains("movement speed") || text.contains("\u653b\u51fb\u4f24\u5bb3") || text.contains("\u653b\u51fb\u901f\u5ea6") || text.contains("\u653b\u51fb\u51fb\u9000") || text.contains("\u51fb\u9000\u6297\u6027") || text.contains("\u62a4\u7532") || text.contains("\u76d4\u7532") || text.contains("\u76d4\u7532\u97e7\u6027") || text.contains("\u6700\u5927\u751f\u547d") || text.contains("\u79fb\u52a8\u901f\u5ea6");
    }

    private static boolean isTaczGunLine(String raw) {
        String text = raw.toLowerCase(Locale.ROOT);
        String compact = text.replace(" ", "");
        return compact.contains("tooltip.tacz.gun") || compact.contains("\u7ecf\u9a8c\u7b49\u7ea7") || compact.contains("\u67aa\u79cd") || compact.contains("\u4f24\u5bb3") || compact.contains("\u539f\u7248\u62a4\u7532\u7a7f\u900f") || compact.contains("\u62a4\u7532\u7a7f\u900f") || compact.contains("\u7206\u5934\u4f24\u5bb3") || compact.contains("\u79fb\u52a8\u901f\u5ea6") || compact.contains("\u5f53\u524d\u8010\u4e45") || compact.contains("\u6e38\u620f\u5185\u6309\u4e0b") || compact.contains("\u6539\u88c5\u754c\u9762") || text.contains("gun level") || text.contains("gun type") || text.contains("head shot") || text.contains("headshot") || text.contains("armor ignore") || text.contains("armor penetration") || text.contains("movement speed") || text.contains("durability") || text.contains("press") || text.contains("refit") || text.contains("tarkov") || text.contains("contagin");
    }

    private static boolean isAdvancedDebugLine(String raw) {
        String text = raw.trim().toLowerCase(Locale.ROOT);
        return text.contains("nbt:") || text.contains("nbt\uff1a") || text.contains("\u4e2a\u6807\u7b7e") || text.contains(" tags") || text.contains("gunid:") || text.contains("ammoid:") || text.contains("attachmentid:") || text.contains("uuid:") || text.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");
    }
}

