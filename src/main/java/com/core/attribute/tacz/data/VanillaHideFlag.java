package com.core.attribute.tacz.data;

public enum VanillaHideFlag {
    ENCHANTMENTS(1, "\u9644\u9b54"),
    ATTRIBUTE_MODIFIERS(2, "\u81ea\u5b9a\u4e49\u5c5e\u6027"),
    UNBREAKABLE(4, "\u65e0\u6cd5\u7834\u574f"),
    CAN_DESTROY(8, "\u53ef\u4ee5\u6467\u6bc1"),
    CAN_PLACE_ON(16, "\u53ef\u4ee5\u653e\u7f6e\u5728"),
    ITEM_INFO(32, "\u7269\u54c1\u4fe1\u606f"),
    DYE(64, "\u67d3\u8272\u4fe1\u606f"),
    UPGRADES(128, "\u5347\u7ea7\u4fe1\u606f");

    private final int mask;
    private final String displayName;

    private VanillaHideFlag(int mask, String displayName) {
        this.mask = mask;
        this.displayName = displayName;
    }

    public int mask() {
        return this.mask;
    }

    public String displayName() {
        return this.displayName;
    }
}

