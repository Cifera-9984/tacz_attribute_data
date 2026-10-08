package com.core.attribute.tacz.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

public final class LegacyText {
    private LegacyText() {
    }

    public static MutableComponent parse(String text, ChatFormatting defaultColor) {
        Style baseStyle;
        String input = text == null ? "" : text;
        Style currentStyle = baseStyle = defaultColor == null ? Style.EMPTY : Style.EMPTY.withColor(defaultColor);
        MutableComponent result = Component.empty();
        StringBuilder plainText = new StringBuilder();
        for (int i = 0; i < input.length(); ++i) {
            ChatFormatting formatting;
            char current = input.charAt(i);
            if ((current == '&' || current == '\u00a7') && i + 1 < input.length() && (formatting = ChatFormatting.getByCode((char)Character.toLowerCase(input.charAt(i + 1)))) != null) {
                LegacyText.append(result, plainText, currentStyle);
                currentStyle = LegacyText.apply(currentStyle, baseStyle, formatting);
                ++i;
                continue;
            }
            plainText.append(current);
        }
        LegacyText.append(result, plainText, currentStyle);
        return result;
    }

    public static boolean hasFormattingCode(String text) {
        if (text == null) {
            return false;
        }
        int i = 0;
        while (i + 1 < text.length()) {
            char prefix = text.charAt(i);
            if ((prefix == '&' || prefix == '\u00a7') && ChatFormatting.getByCode((char)Character.toLowerCase(text.charAt(i + 1))) != null) {
                return true;
            }
            ++i;
        }
        return false;
    }

    private static Style apply(Style current, Style base, ChatFormatting formatting) {
        if (formatting == ChatFormatting.RESET) {
            return base;
        }
        if (formatting.isColor()) {
            return Style.EMPTY.withColor(formatting);
        }
        return current.applyFormat(formatting);
    }

    private static void append(MutableComponent result, StringBuilder plainText, Style style) {
        if (plainText.isEmpty()) {
            return;
        }
        result.append((Component)Component.literal((String)plainText.toString()).setStyle(style));
        plainText.setLength(0);
    }
}

