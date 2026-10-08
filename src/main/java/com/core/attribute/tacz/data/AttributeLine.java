package com.core.attribute.tacz.data;

import com.core.attribute.tacz.data.AttributeType;
import net.minecraft.nbt.CompoundTag;

public record AttributeLine(AttributeType type, String text, boolean hidden) {
    public static final String TAG_TYPE = "Type";
    public static final String TAG_TEXT = "Text";
    public static final String TAG_HIDDEN = "Hidden";

    public static AttributeLine of(String text) {
        return new AttributeLine(AttributeType.detect(text), text == null ? "" : text, false);
    }

    public static AttributeLine of(AttributeType type, String valueText) {
        AttributeType safeType;
        AttributeType attributeType = safeType = type == null ? AttributeType.CUSTOM : type;
        if (AttributeLine.looksLikeFullLine(valueText)) {
            return AttributeLine.of(valueText);
        }
        return new AttributeLine(safeType, AttributeLine.composeText(safeType, valueText), false);
    }

    public static AttributeLine fromTag(CompoundTag tag) {
        String text = tag.getString(TAG_TEXT);
        AttributeType detected = AttributeType.detect(text);
        AttributeType stored = tag.contains(TAG_TYPE) ? AttributeType.byId(tag.getString(TAG_TYPE)) : detected;
        AttributeType type = stored == AttributeType.CUSTOM ? detected : stored;
        return new AttributeLine(type, text, tag.getBoolean(TAG_HIDDEN));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_TYPE, this.type.id());
        tag.putString(TAG_TEXT, this.text);
        tag.putBoolean(TAG_HIDDEN, this.hidden);
        return tag;
    }

    public String label() {
        int colon = this.normalizedText().indexOf(58);
        if (colon > 0) {
            return this.normalizedText().substring(0, colon).trim();
        }
        return this.type.displayName();
    }

    public String valueText() {
        int colon = this.normalizedText().indexOf(58);
        if (colon >= 0 && colon + 1 < this.normalizedText().length()) {
            return this.normalizedText().substring(colon + 1).trim();
        }
        if (this.type == AttributeType.CUSTOM) {
            return this.normalizedText();
        }
        return "";
    }

    public AttributeLine withText(String value) {
        return new AttributeLine(AttributeType.detect(value), value, this.hidden);
    }

    public AttributeLine withType(AttributeType value) {
        AttributeType safeType = value == null ? AttributeType.CUSTOM : value;
        String editorValue = this.type == AttributeType.CUSTOM ? this.text : this.valueText();
        return new AttributeLine(safeType, AttributeLine.composeText(safeType, editorValue), this.hidden);
    }

    public AttributeLine withValueText(String value) {
        if (AttributeLine.looksLikeFullLine(value)) {
            AttributeLine parsed = AttributeLine.of(value);
            return new AttributeLine(parsed.type(), parsed.text(), this.hidden);
        }
        return new AttributeLine(this.type, AttributeLine.composeText(this.type, this.label(), value), this.hidden);
    }

    public AttributeLine withHidden(boolean value) {
        return new AttributeLine(this.type, this.text, value);
    }

    public String editorValue() {
        return this.type == AttributeType.CUSTOM ? this.normalizedText() : this.valueText();
    }

    public boolean hasEditorContent() {
        return this.type == AttributeType.CUSTOM ? !this.normalizedText().isBlank() : !this.valueText().isBlank();
    }

    private static String composeText(AttributeType type, String valueText) {
        return AttributeLine.composeText(type, type.displayName(), valueText);
    }

    private static String composeText(AttributeType type, String label, String valueText) {
        String value;
        String string = value = valueText == null ? "" : valueText.trim();
        if (type == AttributeType.CUSTOM) {
            return value;
        }
        String safeLabel = label == null || label.isBlank() ? type.displayName() : label.trim();
        return safeLabel + ": " + value;
    }

    private static boolean looksLikeFullLine(String value) {
        return value != null && (value.indexOf(58) > 0 || value.indexOf(65306) > 0);
    }

    private String normalizedText() {
        return this.text == null ? "" : this.text.replace('\uff1a', ':');
    }
}

