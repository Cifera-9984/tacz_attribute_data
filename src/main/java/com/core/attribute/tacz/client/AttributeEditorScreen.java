package com.core.attribute.tacz.client;

import com.core.attribute.tacz.data.AttributeData;
import com.core.attribute.tacz.data.AttributeLine;
import com.core.attribute.tacz.data.AttributeType;
import com.core.attribute.tacz.data.GunOverrides;
import com.core.attribute.tacz.data.ItemProbe;
import com.core.attribute.tacz.network.NetworkHandler;
import com.core.attribute.tacz.network.SaveAttributesC2S;
import com.core.attribute.tacz.util.LegacyText;
import com.tacz.guns.api.item.IGun;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * Accordion-style attribute editor.
 *
 * <p>Every feature lives in its own folding card (item info, attributes, enchantments,
 * description, display settings). Cards animate their height smoothly open and closed,
 * the column scrolls, and the content is clipped to the card body so widgets slide away
 * naturally instead of popping. A live preview column on the right renders the authentic
 * inventory tooltip of the item as it is being edited.</p>
 */
public class AttributeEditorScreen extends Screen {

    // ---- layout constants ---------------------------------------------
    private static final int HEADER_H = 26;
    private static final int CARD_GAP = 6;
    private static final int ROW_H = 24;
    private static final int ROW_CARD_H = 22;
    private static final int WIDGET_H = 18;
    /**
     * Height shared by every text input and the rounded backdrop behind it. They must use
     * the exact same y and height, otherwise the vanilla {@code EditBox} centres its text
     * inside its own bounds and the text ends up looking off-centre in our backdrop.
     */
    private static final int INPUT_H = 20;
    private static final int GRIP_W = 16;
    private static final int ACTIONS_TOTAL = GRIP_W + 2 + 18; // grip + delete
    private static final int CHIP_H = 20;
    private static final int CHIP_GAP = 4;
    private static final int CHIP_ROW_GAP = 4;
    private static final int CHIP_PAD = 14;
    private static final int PREVIEW_W = 250;
    private static final int PREVIEW_MIN_W = 180;
    private static final int CARD_MIN_W = 300;
    private static final int PANEL_MAX_W = 860;
    /** Height of one two-column ballistic input row inside the gun card. */
    private static final int GUN_ROW_H = 24;
    /** Fixed label column width for the gun card's small inline fields. */
    private static final int GUN_LABEL_W = 34;
    /** Band at the bottom of the panel holding the author credit and licence notice. */
    private static final int FOOTER_H = 36;

    private static final AttributeType[] EDITABLE_TYPES = new AttributeType[]{
            AttributeType.DAMAGE, AttributeType.PVE_DAMAGE, AttributeType.ARMOR_PENETRATION,
            AttributeType.HEADSHOT_BONUS, AttributeType.GUN_LEVEL, AttributeType.DURABILITY,
            AttributeType.ATTACK_DAMAGE, AttributeType.ATTACK_SPEED,
            AttributeType.ARMOR, AttributeType.ARMOR_TOUGHNESS, AttributeType.HEALTH,
            AttributeType.PVE_DEFENSE_REDUCTION, AttributeType.KNOCKBACK_RESISTANCE,
            AttributeType.UNBREAKABLE, AttributeType.ENCHANT_GLINT};

    private enum Card {
        ITEM, ATTRIBUTES, GUN, ENCHANTS, LORE, DISPLAY
    }

    /**
     * Author credit links shown in the panel footer. Clicking one asks for confirmation
     * and then opens it in the system browser.
     */
    private enum Platform {
        BILIBILI("bilibili", "https://space.bilibili.com/531575630"),
        GITHUB("GitHub", "https://github.com/Cifera-9984"),
        QQ("QQ群", "https://qm.qq.com/q/ob0YjsS0RW");

        final String label;
        final String url;

        Platform(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }

    /** Must match {@link net.minecraftforge.fml.loading.FMLLoader} metadata for attribution. */
    private static final String AUTHOR_NAME = "Mc_BaiLu";
    private static final String LICENCE_NOTICE = "本模组免费分享使用，严禁售卖圈钱";

    private final ItemStack previewStack;
    private final List<AttributeLine> attributeLines = new ArrayList<>();
    private final List<AttributeLine> loreLines = new ArrayList<>();
    private final LinkedHashMap<Enchantment, Integer> enchantEdits = new LinkedHashMap<>();
    private final List<RowWidgets> rowWidgets = new ArrayList<>();
    private final List<ChipLayout> chipLayouts = new ArrayList<>();
    private final List<ChipButton> chipButtons = new ArrayList<>();
    private final String originalName;
    private final boolean hadCustomName;
    private String nameDraft;
    private EditBox nameBox;
    private PurpleButton clearButton;
    private PurpleButton saveButton;
    private PurpleButton enchantButton;
    private PurpleButton loreButton;
    private PurpleButton displayToggle;
    private PurpleButton attributeToggle;
    private PurpleButton flagsButton;
    private float scroll;
    private float scrollTarget;
    private int pendingFocusIndex = -1;
    private boolean hideVanillaPanel = true;
    private int hideFlags;
    private boolean hideMainHandAttributes;
    /** Whether the mod's own attribute lines are hidden in the tooltip (default on). */
    private boolean hideAttributeLabels = true;

    // ---- gun card (only shown while editing a TACZ gun) ----------------
    /** Whether the edited stack is a TACZ gun; when false the gun card is invisible. */
    private final boolean gunCard;
    /** Ammo id the gun has been switched to, or {@code null} to keep the gun's own. */
    private String gunAmmoId;
    private double gunRecoil = GunOverrides.RECOIL_DEFAULT;
    private final LinkedHashMap<String, String> gunBulletDrafts = new LinkedHashMap<>();
    private final List<GunField> gunFields = new ArrayList<>();
    private RecoilSlider recoilSlider;
    /** Y of the clickable ammo row, cached by the layout pass for hit testing. */
    private int gunAmmoRowY;

    // ---- drag & drop ---------------------------------------------------
    private int dragIndex = -1;
    private int dropIndex = -1;
    private int dragGrabOffset;
    private float dragY;

    // ---- animated card state ------------------------------------------
    private final float[] expand = new float[Card.values().length];
    private final float[] expandTarget = new float[Card.values().length];
    private final int[] cardY = new int[Card.values().length];
    private final int[] bodyY = new int[Card.values().length];
    private final int[] bodyH = new int[Card.values().length];
    private int chipAreaH;
    private int totalContentH;

    // ---- cached layout (avoids re-positioning every settled frame) -----
    private boolean layoutDirty = true;
    private int appliedScroll = Integer.MIN_VALUE;
    private int appliedDragIndex = -2;
    private int appliedDragY = Integer.MIN_VALUE;
    private final int[] appliedExpand = new int[Card.values().length];

    private long openTime = System.currentTimeMillis();
    private long lastFrameNanos;
    private Platform hoveredPlatform;
    private float[] rowHoverValues = new float[0];

    // ---- cached live tooltip ------------------------------------------
    private List<Component> tooltipCache;
    private String tooltipSignature = "";

    public AttributeEditorScreen(ItemStack stack) {
        super(Component.literal("自定义属性设定"));
        this.previewStack = stack.copy();
        this.originalName = this.previewStack.getHoverName().getString();
        this.hadCustomName = this.previewStack.hasCustomHoverName();
        this.nameDraft = this.originalName;
        this.hideFlags = AttributeData.hideFlagMask(stack);
        this.hideMainHandAttributes = AttributeData.hideMainHandAttributes(stack);
        this.hideAttributeLabels = AttributeData.hideAttributeLabels(stack);
        this.enchantEdits.putAll(AttributeData.readEnchantments(stack));

        List<AttributeLine> savedLines = AttributeData.getLines(stack);
        for (AttributeLine line : savedLines) {
            if (line.type() == AttributeType.CUSTOM) {
                this.loreLines.add(line);
            } else if (indexOfType(line.type()) < 0) {
                // "one attribute per type" - drop duplicates coming from older data
                this.attributeLines.add(line);
            }
        }
        if (this.attributeLines.isEmpty() && !AttributeData.hasData(stack)) {
            this.attributeLines.addAll(defaultAttributeLines(stack));
        }
        if (this.attributeLines.isEmpty()) {
            this.attributeLines.add(AttributeLine.of(AttributeType.DAMAGE, ItemProbe.currentValue(stack, AttributeType.DAMAGE)));
        }
        if (this.loreLines.isEmpty()) {
            this.loreLines.add(AttributeLine.of(AttributeType.CUSTOM, ""));
        }
        this.hideVanillaPanel = !AttributeData.hasData(stack) || AttributeData.hideVanillaPanel(stack);

        // Gun card: only for TACZ guns, seeded from the saved overrides and, where a field
        // was never overridden, from the gun's real current value so no row is blank.
        this.gunCard = stack.getItem() instanceof IGun;
        if (this.gunCard) {
            String savedAmmo = GunOverrides.readAmmo(stack);
            this.gunAmmoId = savedAmmo.isBlank() ? null : savedAmmo;
            this.gunRecoil = GunOverrides.recoilMultiplier(stack);
            java.util.Map<String, Double> savedBullet = GunOverrides.readBullet(stack);
            for (String[] field : GunOverrides.BULLET_FIELDS) {
                Double saved = savedBullet.get(field[0]);
                this.gunBulletDrafts.put(field[0],
                        saved != null ? fmt(saved) : ItemProbe.currentBulletValue(stack, field[0]));
            }
        }

        // ITEM + ATTRIBUTES open by default, the rest collapsed.
        for (Card card : Card.values()) {
            float value = card == Card.ITEM || card == Card.ATTRIBUTES ? 1f : 0f;
            this.expand[card.ordinal()] = value;
            this.expandTarget[card.ordinal()] = value;
        }
    }

    // ---- geometry ------------------------------------------------------

    private int panelW() {
        return Math.min(PANEL_MAX_W, this.width - 24);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        // Vertically centred, so the panel stays put while it grows and shrinks.
        return Math.max(8, (this.height - panelH()) / 2);
    }

    /**
     * The panel hugs its content instead of always filling the screen, which keeps the
     * page from looking oversized when only a couple of cards are open. When the content
     * is taller than the window the panel caps out and the card column starts scrolling.
     */
    private int panelH() {
        int content = 0;
        for (Card card : Card.values()) {
            if (!this.cardVisible(card)) {
                continue;
            }
            content += HEADER_H + Math.round(this.fullBodyHeight(card) * this.expand[card.ordinal()]) + CARD_GAP;
        }
        content = Math.max(0, content - CARD_GAP);
        // 44px of chrome above the viewport, 6px below it, then the footer band.
        return Math.max(150, Math.min(this.height - 20, content + 50 + FOOTER_H));
    }

    /**
     * The live info column is always kept when there is room for a usable card column
     * plus a (possibly narrower) info column. It shrinks instead of disappearing, so the
     * preview does not depend on the window being resized to a magic width.
     */
    private boolean previewEnabled() {
        return panelW() - 28 - 12 - CARD_MIN_W >= PREVIEW_MIN_W;
    }

    private int previewW() {
        if (!this.previewEnabled()) {
            return 0;
        }
        return Math.max(PREVIEW_MIN_W, Math.min(PREVIEW_W, panelW() - 28 - 12 - CARD_MIN_W));
    }

    private int previewX() {
        return cardX() + cardW() + 12;
    }

    private int cardX() {
        return panelX() + 14;
    }

    private int cardW() {
        return panelW() - 28 - (this.previewEnabled() ? this.previewW() + 12 : 0);
    }

    private int contentTop() {
        return panelY() + 44;
    }

    private int contentBottom() {
        return panelY() + panelH() - FOOTER_H - 6;
    }

    private int footerY() {
        return panelY() + panelH() - FOOTER_H;
    }

    private int viewportH() {
        return contentBottom() - contentTop();
    }

    private record ChipLayout(AttributeType type, int x, int y, int w, int h) {
    }

    private record ChipButton(AttributeType type, PurpleButton button) {
    }

    private static final class RowWidgets {
        final int index;
        final AttributeType type;
        final int labelWidth;
        final EditBox box;
        final GripHandle grip;
        final PurpleButton del;

        RowWidgets(int index, AttributeType type, int labelWidth, EditBox box, GripHandle grip, PurpleButton del) {
            this.index = index;
            this.type = type;
            this.labelWidth = labelWidth;
            this.box = box;
            this.grip = grip;
            this.del = del;
        }
    }

    /** One ballistic override row of the gun card: a short label plus its input box. */
    private static final class GunField {
        final String key;
        final String label;
        final EditBox box;

        GunField(String key, String label, EditBox box) {
            this.key = key;
            this.label = label;
            this.box = box;
        }
    }

    /** Three-bar drag grip drawn to the left of the delete button. */
    private static final class GripHandle extends AbstractWidget {
        private float hover;
        private long lastNanos;

        GripHandle() {
            super(0, 0, GRIP_W, WIDGET_H, Component.empty());
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            float dt = UiKit.frameDelta(this.lastNanos);
            this.lastNanos = System.nanoTime();
            this.hover = UiKit.approach(this.hover, this.isHoveredOrFocused() ? 1f : 0f, dt, 14f);
            if (this.hover > 0.01f) {
                UiKit.fillRounded(graphics, this.getX(), this.getY(), this.getWidth(), this.getHeight(), 6,
                        UiKit.scaleAlpha(0x33FFFFFF, this.hover));
            }
            int cx = this.getX() + this.getWidth() / 2;
            int cy = this.getY() + this.getHeight() / 2;
            int color = UiKit.lerpColor(UiKit.TEXT_MUTED, UiKit.ACCENT_BRIGHT, this.hover);
            for (int i = -1; i <= 1; i++) {
                graphics.fill(cx - 4, cy + i * 3 - 1, cx + 4, cy + i * 3, color);
            }
        }

        @Override
        protected void updateWidgetNarration(@NotNull NarrationElementOutput output) {
        }
    }

    // ---- widget building ----------------------------------------------

    @Override
    protected void init() {
        this.openTime = System.currentTimeMillis();
        this.syncRowBoxes();
        this.rebuildAllWidgets();
    }

    private void rebuildAllWidgets() {
        this.clearWidgets();
        this.setFocused((GuiEventListener) null);
        this.rowWidgets.clear();
        this.chipLayouts.clear();
        this.chipButtons.clear();

        this.rebuildChips();
        this.rebuildHeader();
        this.rebuildItemCard();
        this.rebuildAttributeCard();
        this.rebuildGunCard();
        this.rebuildEnchantCard();
        this.rebuildLoreCard();
        this.rebuildDisplayCard();

        this.layoutDirty = true;
        this.applyLayout();

        if (this.pendingFocusIndex >= 0) {
            int index = this.pendingFocusIndex;
            this.pendingFocusIndex = -1;
            this.expandTarget[Card.ATTRIBUTES.ordinal()] = 1f;
            this.revealRow(index);
            this.focusRow(index);
        }
    }

    private void rebuildChips() {
        int left = cardX() + 12;
        int right = cardX() + cardW() - 12;
        int y = 0;
        int x = left;
        int bottom = 0;
        for (AttributeType type : EDITABLE_TYPES) {
            int w = this.font.width(type.displayName()) + CHIP_PAD * 2;
            if (x + w > right && x > left) {
                x = left;
                y += CHIP_H + CHIP_ROW_GAP;
            }
            this.chipLayouts.add(new ChipLayout(type, x, y, w, CHIP_H));
            x += w + CHIP_GAP;
            bottom = Math.max(bottom, y + CHIP_H);
        }
        // Always reserve one row so the section keeps a stable height and can show a
        // hint when every attribute is already present.
        this.chipAreaH = Math.max(CHIP_H, bottom);

        for (ChipLayout chip : this.chipLayouts) {
            AttributeType type = chip.type();
            boolean present = this.indexOfType(type) >= 0;
            PurpleButton button = new PurpleButton(0, 0, chip.w(), chip.h(),
                    Component.literal(type.displayName()),
                    b -> this.toggleAttribute(type),
                    present ? UiKit.SUCCESS : UiKit.TEXT_SECONDARY,
                    present ? PurpleButton.Style.PRIMARY : PurpleButton.Style.GHOST);
            this.addRenderableWidget(button);
            this.chipButtons.add(new ChipButton(type, button));
        }
    }

    private void rebuildHeader() {
        this.saveButton = new PurpleButton(0, 0, 96, 24,
                Component.literal("保存"), b -> this.saveAndClose(), UiKit.TEXT_PRIMARY, PurpleButton.Style.PRIMARY);
        this.addRenderableWidget(this.saveButton);
    }

    private void rebuildItemCard() {
        this.nameBox = new GlassEditBox(this.font, 0, 0, 120, 18, Component.empty());
        this.nameBox.setMaxLength(64);
        this.nameBox.setTextColor(0xFFF2F2F7);
        this.nameBox.setBordered(false);
        this.nameBox.setValue(this.nameDraft);
        this.nameBox.setHint(Component.literal("物品名称").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        this.addRenderableWidget(this.nameBox);
        this.clearButton = new PurpleButton(0, 0, 58, 22, Component.literal("清除"),
                b -> {
                    this.nameBox.setValue("");
                    this.invalidateTooltip();
                }, UiKit.TEXT_SECONDARY, PurpleButton.Style.GLASS);
        this.addRenderableWidget(this.clearButton);
    }

    private void rebuildAttributeCard() {
        for (int i = 0; i < this.attributeLines.size(); i++) {
            final int index = i;
            AttributeLine line = this.attributeLines.get(i);
            int labelWidth = this.font.width(labelFor(line)) + 16;

            EditBox box = new GlassEditBox(this.font, 0, 0, 60, 18, Component.empty());
            box.setMaxLength(96);
            box.setBordered(false);
            box.setValue(line.editorValue());
            box.setTextColor(valueColor(line));
            box.setHint(line.editorValue().isBlank()
                    ? Component.literal(suggestionFor(line)).withStyle(net.minecraft.ChatFormatting.DARK_GRAY)
                    : null);
            this.addRenderableWidget(box);

            GripHandle grip = new GripHandle();
            this.addRenderableWidget(grip);

            PurpleButton del = new PurpleButton(0, 0, 20, WIDGET_H, Component.literal("✕"),
                    b -> {
                        this.syncRowBoxes();
                        if (index >= 0 && index < this.attributeLines.size()) {
                            this.attributeLines.remove(index);
                        }
                        this.invalidateTooltip();
                        this.rebuildAllWidgets();
                    }, UiKit.TEXT_PRIMARY, PurpleButton.Style.DANGER);
            this.addRenderableWidget(del);

            this.rowWidgets.add(new RowWidgets(index, line.type(), labelWidth, box, grip, del));
        }
    }

    /**
     * Gun card widgets: one input box per ballistic override plus the recoil slider. The
     * card only exists for TACZ guns; for anything else this builds nothing and the whole
     * card is skipped by the layout and render passes.
     */
    private void rebuildGunCard() {
        this.gunFields.clear();
        this.recoilSlider = null;
        if (!this.gunCard) {
            return;
        }
        for (String[] field : GunOverrides.BULLET_FIELDS) {
            EditBox box = new GlassEditBox(this.font, 0, 0, 60, 18, Component.empty());
            box.setMaxLength(12);
            box.setBordered(false);
            box.setValue(this.gunBulletDrafts.getOrDefault(field[0], ""));
            box.setTextColor(0xFFE8E8F0);
            this.addRenderableWidget(box);
            this.gunFields.add(new GunField(field[0], field[1], box));
        }
        this.recoilSlider = new RecoilSlider(0, 0, 150, WIDGET_H, this.gunRecoil, value -> {
            this.gunRecoil = value;
            this.invalidateTooltip();
        });
        this.addRenderableWidget(this.recoilSlider);
    }

    /**
     * The enchant card is only a summary plus a button now: editing moved to its own
     * screen, where searching and managing levels no longer fight for the same few rows.
     */
    private void rebuildEnchantCard() {
        this.enchantButton = new PurpleButton(0, 0, 88, 22, Component.literal("编辑附魔"),
                b -> {
                    this.syncRowBoxes();
                    this.minecraft.setScreen(new EnchantEditorScreen(this, this.previewStack, this.enchantEdits));
                }, UiKit.SUCCESS, PurpleButton.Style.PRIMARY);
        this.addRenderableWidget(this.enchantButton);
    }

    /** Comma separated preview of the applied enchantments, for the card subtitle area. */
    private String enchantSummary() {
        if (this.enchantEdits.isEmpty()) {
            return "未添加附魔 · 点击右侧按钮编辑";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Enchantment, Integer> entry : this.enchantEdits.entrySet()) {
            if (sb.length() > 0) {
                sb.append("、");
            }
            sb.append(entry.getKey().getFullname(1).getString()).append(" Lv.").append(entry.getValue());
        }
        return sb.toString();
    }

    private void rebuildLoreCard() {
        this.loreButton = new PurpleButton(0, 0, 96, 22, Component.literal("编辑描述"),
                b -> {
                    this.syncRowBoxes();
                    this.minecraft.setScreen(new LoreEditorScreen(this, this.previewStack, this.loreLines));
                }, UiKit.ACCENT_BRIGHT, PurpleButton.Style.PRIMARY);
        this.addRenderableWidget(this.loreButton);
    }

    private void rebuildDisplayCard() {
        this.displayToggle = new PurpleButton(0, 0, 150, 24,
                Component.literal(this.hideVanillaPanel ? "隐藏标签: 开" : "隐藏标签: 关"),
                b -> {
                    this.syncRowBoxes();
                    this.hideVanillaPanel = !this.hideVanillaPanel;
                    this.invalidateTooltip();
                    this.rebuildAllWidgets();
                }, this.hideVanillaPanel ? UiKit.ACCENT_BRIGHT : UiKit.TEXT_SECONDARY,
                this.hideVanillaPanel ? PurpleButton.Style.PRIMARY : PurpleButton.Style.GLASS);
        this.attributeToggle = new PurpleButton(0, 0, 150, 24,
                Component.literal(this.hideAttributeLabels ? "属性标签: 隐藏" : "属性标签: 显示"),
                b -> {
                    this.syncRowBoxes();
                    this.hideAttributeLabels = !this.hideAttributeLabels;
                    this.invalidateTooltip();
                    this.rebuildAllWidgets();
                }, this.hideAttributeLabels ? UiKit.ACCENT_BRIGHT : UiKit.TEXT_SECONDARY,
                this.hideAttributeLabels ? PurpleButton.Style.PRIMARY : PurpleButton.Style.GLASS);
        this.flagsButton = new PurpleButton(0, 0, 150, 24, Component.literal("隐藏标签设置"),
                b -> {
                    this.syncRowBoxes();
                    this.minecraft.setScreen(new HideFlagsEditorScreen(this, this.previewStack, this.hideFlags, this.hideMainHandAttributes));
                }, UiKit.TEXT_PRIMARY, PurpleButton.Style.GLASS);
        this.addRenderableWidget(this.displayToggle);
        this.addRenderableWidget(this.attributeToggle);
        this.addRenderableWidget(this.flagsButton);
    }

    // ---- measurement + layout -----------------------------------------

    private int fullBodyHeight(Card card) {
        return switch (card) {
            case ITEM -> 34;
            // chipTop is a fixed 16px below the body top, then the chip area, an 18px
            // gap and one 24px row per attribute, plus bottom padding.
            case ATTRIBUTES -> 16 + this.chipAreaH + 18 + this.attributeLines.size() * ROW_H + 8;
            case GUN -> this.gunBodyHeight();
            case ENCHANTS -> 32;
            case LORE -> 32;
            case DISPLAY -> 88;
        };
    }

    /** Height of the gun card body: ammo row, recoil slider row, then the bullet grid. */
    private int gunBodyHeight() {
        if (!this.gunCard) {
            return 0;
        }
        int rows = (GunOverrides.BULLET_FIELDS.length + 1) / 2;
        // 8 pad + 24 ammo + 6 gap + 24 recoil + 16 section label + grid + 8 pad.
        return 8 + 24 + 6 + 24 + 16 + rows * GUN_ROW_H + 8;
    }

    private int gunAmmoY() {
        return this.bodyY[Card.GUN.ordinal()] + 8;
    }

    private int gunRecoilY() {
        return this.gunAmmoY() + 30;
    }

    /** First ballistic row, leaving a 16px band above it for the section label. */
    private int gunBulletTop() {
        return this.gunRecoilY() + 40;
    }

    private boolean cardVisible(Card card) {
        return card != Card.GUN || this.gunCard;
    }

    /** Y of the first palette chip inside the attributes card. */
    private int chipTop(Card card) {
        return this.bodyY[card.ordinal()] + 16;
    }

    /** Y of the first attribute row inside the attributes card. */
    private int rowsTop(Card card) {
        return this.chipTop(card) + this.chipAreaH + 18;
    }

    private void applyLayout() {
        int x = cardX();
        int w = cardW();
        int offset = Math.round(this.scroll);
        int y = contentTop() - offset;
        int contentSum = 0;

        for (Card card : Card.values()) {
            int i = card.ordinal();
            if (!this.cardVisible(card)) {
                // Park the hidden card off-screen so nothing can accidentally hit it.
                this.cardY[i] = -10000;
                this.bodyY[i] = -10000;
                this.bodyH[i] = 0;
                continue;
            }
            int full = this.fullBodyHeight(card);
            int visible = Math.round(full * this.expand[i]);
            this.cardY[i] = y;
            this.bodyY[i] = y + HEADER_H;
            this.bodyH[i] = visible;
            y += HEADER_H + visible + CARD_GAP;
            contentSum += HEADER_H + visible + CARD_GAP;
        }
        // Measured independently of the current scroll offset so clamping stays stable.
        this.totalContentH = contentSum - CARD_GAP;
        this.clampScroll();

        // The panel grows and shrinks with its content, so the header has to be
        // repositioned every layout pass instead of only when widgets are rebuilt.
        this.positionFixed(this.saveButton, panelX() + panelW() - 14 - 96, panelY() + 10, 96, 24, true);

        this.layoutItemCard(x, w);
        this.layoutAttributeCard(x, w);
        this.layoutGunCard(x, w);
        this.layoutEnchantCard(x, w);
        this.layoutLoreCard(x, w);
        this.layoutDisplayCard(x, w);
    }

    /** Re-lays out only when something actually moved, so idle frames stay cheap. */
    private void layoutIfNeeded() {
        if (!this.layoutNeedsUpdate()) {
            return;
        }
        this.applyLayout();
        this.appliedScroll = Math.round(this.scroll);
        this.appliedDragIndex = this.dragIndex;
        this.appliedDragY = Math.round(this.dragY);
        for (Card card : Card.values()) {
            int i = card.ordinal();
            this.appliedExpand[i] = Math.round(this.expand[i] * 200f);
        }
        this.layoutDirty = false;
    }

    private boolean layoutNeedsUpdate() {
        if (this.layoutDirty) {
            return true;
        }
        if (Math.round(this.scroll) != this.appliedScroll) {
            return true;
        }
        if (this.dragIndex != this.appliedDragIndex) {
            return true;
        }
        if (this.dragIndex >= 0 && Math.round(this.dragY) != this.appliedDragY) {
            return true;
        }
        for (Card card : Card.values()) {
            int i = card.ordinal();
            if (Math.round(this.expand[i] * 200f) != this.appliedExpand[i]) {
                return true;
            }
        }
        return false;
    }

    private void layoutItemCard(int x, int w) {
        int i = Card.ITEM.ordinal();
        int bodyInnerY = this.bodyY[i] + 7;
        int clearX = x + w - 12 - 54;
        int inputX = x + 62;
        int inputW = Math.max(60, clearX - 10 - inputX);
        this.positionFixed(this.nameBox, inputX + 7, bodyInnerY, Math.max(40, inputW - 14), INPUT_H,
                this.inView(i, bodyInnerY, bodyInnerY + INPUT_H));
        this.positionFixed(this.clearButton, clearX, bodyInnerY, 54, INPUT_H,
                this.inView(i, bodyInnerY, bodyInnerY + INPUT_H));
    }

    private void layoutAttributeCard(int x, int w) {
        int i = Card.ATTRIBUTES.ordinal();
        int left = x + 12;
        int innerW = w - 24;
        int chipY = this.chipTop(Card.ATTRIBUTES);
        for (ChipButton chip : this.chipButtons) {
            ChipLayout layout = this.chipLayoutOf(chip.type());
            if (layout == null) {
                continue;
            }
            int cy = chipY + layout.y();
            this.positionFixed(chip.button(), left + (layout.x() - (cardX() + 12)), cy,
                    layout.w(), layout.h(), this.inView(i, cy, cy + layout.h()));
        }
        int rowsTop = this.rowsTop(Card.ATTRIBUTES);
        for (RowWidgets rw : this.rowWidgets) {
            boolean dragging = rw.index == this.dragIndex;
            int rowY = dragging ? Math.round(this.dragY) : rowsTop + this.slotFor(rw.index) * ROW_H;
            int actionsX = left + innerW - 4 - ACTIONS_TOTAL;
            int valueX = left + 14 + rw.labelWidth + 6;
            int valueW = Math.max(50, actionsX - 8 - valueX);
            // A row being dragged follows the cursor, so it is only clipped by the
            // viewport; every other row must also stay inside the card body.
            boolean visible = dragging
                    ? this.inViewport(rowY, rowY + ROW_CARD_H)
                    : this.inView(i, rowY, rowY + ROW_CARD_H);
            this.positionFixed(rw.box, valueX + 7, rowY + 1, Math.max(24, valueW - 14), INPUT_H, visible);
            this.positionFixed(rw.grip, actionsX, rowY + 2, GRIP_W, WIDGET_H, visible);
            this.positionFixed(rw.del, actionsX + GRIP_W + 2, rowY + 2, 18, WIDGET_H, visible);
        }
    }

    /** Final slot of row {@code index} while a drag is in progress. */
    private int slotFor(int index) {
        if (this.dragIndex < 0 || this.dropIndex < 0) {
            return index;
        }
        if (index == this.dragIndex) {
            return this.dropIndex;
        }
        if (this.dragIndex < this.dropIndex && index > this.dragIndex && index <= this.dropIndex) {
            return index - 1;
        }
        if (this.dragIndex > this.dropIndex && index >= this.dropIndex && index < this.dragIndex) {
            return index + 1;
        }
        return index;
    }

    private void layoutGunCard(int x, int w) {
        if (!this.gunCard) {
            return;
        }
        int i = Card.GUN.ordinal();
        int left = x + 12;
        int innerW = w - 24;
        this.gunAmmoRowY = this.gunAmmoY();

        int recoilY = this.gunRecoilY();
        if (this.recoilSlider != null) {
            int sliderW = Math.min(170, Math.max(90, innerW - 120));
            this.positionFixed(this.recoilSlider, left + innerW - sliderW, recoilY + 2, sliderW, WIDGET_H,
                    this.inView(i, recoilY, recoilY + WIDGET_H));
        }

        int columns = 2;
        int colGap = 12;
        int colW = (innerW - colGap) / columns;
        int top = this.gunBulletTop();
        for (int idx = 0; idx < this.gunFields.size(); idx++) {
            GunField field = this.gunFields.get(idx);
            int col = idx % columns;
            int row = idx / columns;
            int cellX = left + col * (colW + colGap);
            int cellY = top + row * GUN_ROW_H;
            int inputX = cellX + GUN_LABEL_W;
            int inputW = Math.max(30, colW - GUN_LABEL_W - 4);
            this.positionFixed(field.box, inputX + 6, cellY + 1, Math.max(20, inputW - 12), INPUT_H,
                    this.inView(i, cellY, cellY + INPUT_H));
        }
    }

    private void layoutEnchantCard(int x, int w) {
        int i = Card.ENCHANTS.ordinal();
        int y = this.bodyY[i] + 6;
        this.positionFixed(this.enchantButton, x + w - 12 - 88, y, 88, 22, this.inView(i, y, y + 22));
    }

    private void layoutLoreCard(int x, int w) {
        int i = Card.LORE.ordinal();
        int y = this.bodyY[i] + 7;
        this.positionFixed(this.loreButton, x + w - 12 - 88, y, 88, 22, this.inView(i, y, y + 22));
    }

    private void layoutDisplayCard(int x, int w) {
        int i = Card.DISPLAY.ordinal();
        int bodyInnerY = this.bodyY[i] + 7;
        this.positionFixed(this.displayToggle, x + 12, bodyInnerY, 150, 24,
                this.inView(i, bodyInnerY, bodyInnerY + 24));
        this.positionFixed(this.attributeToggle, x + 12, bodyInnerY + 26, 150, 24,
                this.inView(i, bodyInnerY + 26, bodyInnerY + 50));
        this.positionFixed(this.flagsButton, x + 12, bodyInnerY + 52, 150, 24,
                this.inView(i, bodyInnerY + 52, bodyInnerY + 76));
    }

    private ChipLayout chipLayoutOf(AttributeType type) {
        for (ChipLayout layout : this.chipLayouts) {
            if (layout.type() == type) {
                return layout;
            }
        }
        return null;
    }

    private void positionFixed(PurpleButton button, int x, int y, int w, int h, boolean visible) {
        button.setPosition(x, y);
        button.setWidth(w);
        button.setHeight(h);
        button.visible = visible;
    }

    private void positionFixed(GripHandle grip, int x, int y, int w, int h, boolean visible) {
        grip.setPosition(x, y);
        grip.setWidth(w);
        grip.setHeight(h);
        grip.visible = visible;
    }

    private void positionFixed(RecoilSlider slider, int x, int y, int w, int h, boolean visible) {
        slider.setX(x);
        slider.setY(y);
        slider.setWidth(w);
        slider.setHeight(h);
        slider.visible = visible;
        slider.active = visible;
    }

    private void positionFixed(EditBox box, int x, int y, int w, int h, boolean visible) {
        box.setX(x);
        box.setY(y);
        box.setWidth(w);
        box.setHeight(h);
        box.visible = visible;
        if (!visible && box.isFocused()) {
            box.setFocused(false);
        }
    }

    /** Visible when inside the card body and inside the scrolling viewport. */
    private boolean inView(int card, int widgetTop, int widgetBottom) {
        if (this.expand[card] < 0.02f || this.bodyH[card] <= 0) {
            return false;
        }
        if (widgetTop < this.bodyY[card] - 1) {
            return false;
        }
        if (widgetBottom > this.bodyY[card] + this.bodyH[card] + 1) {
            return false;
        }
        return this.inViewport(widgetTop, widgetBottom);
    }

    /** Visible when inside the scrolling viewport only (used for the dragged row). */
    private boolean inViewport(int widgetTop, int widgetBottom) {
        return widgetBottom <= contentBottom() + 1 && widgetTop >= contentTop() - 1;
    }

    private void clampScroll() {
        float max = Math.max(0, this.totalContentH - viewportH());
        // Only the target is hard-clamped; the live offset is pulled into range by the
        // scroll animation so collapsing a card never snaps the view.
        this.scrollTarget = Math.max(0f, Math.min(this.scrollTarget, max));
    }

    private void revealRow(int index) {
        int i = Card.ATTRIBUTES.ordinal();
        int rowsTop = this.rowsTop(Card.ATTRIBUTES);
        int rowY = rowsTop + index * ROW_H;
        float target = this.scrollTarget;
        if (rowY < contentTop()) {
            target -= contentTop() - rowY + 8;
        } else if (rowY + ROW_H > contentBottom()) {
            target += rowY + ROW_H - contentBottom() + 8;
        }
        this.scrollTarget = target;
        this.clampScroll();
        // Jump straight there so the freshly added row can take focus within this frame.
        this.scroll = this.scrollTarget;
        this.layoutDirty = true;
        this.applyLayout();
    }

    private void focusRow(int index) {
        for (RowWidgets rw : this.rowWidgets) {
            if (rw.index == index && rw.box.visible) {
                this.setFocused(rw.box);
                return;
            }
        }
    }

    // ---- behaviour -----------------------------------------------------

    private int indexOfType(AttributeType type) {
        for (int i = 0; i < this.attributeLines.size(); i++) {
            if (this.attributeLines.get(i).type() == type) {
                return i;
            }
        }
        return -1;
    }

    /** Chip click: add the type when absent, remove it when already present. */
    private void toggleAttribute(AttributeType type) {
        this.syncRowBoxes();
        int existing = this.indexOfType(type);
        if (existing >= 0) {
            this.attributeLines.remove(existing);
        } else {
            this.attributeLines.add(AttributeLine.of(type, initialValueFor(type)));
            this.pendingFocusIndex = this.attributeLines.size() - 1;
        }
        this.invalidateTooltip();
        this.rebuildAllWidgets();
    }

    /** Switch types start switched on; everything else starts from the item's real value. */
    private String initialValueFor(AttributeType type) {
        if (type.toggle()) {
            return "开";
        }
        return ItemProbe.currentValue(this.previewStack, type);
    }

    private void syncRowBoxes() {
        if (this.nameBox != null) {
            this.nameDraft = this.nameBox.getValue();
        }
        for (RowWidgets rw : this.rowWidgets) {
            if (rw.index < 0 || rw.index >= this.attributeLines.size()) {
                continue;
            }
            this.attributeLines.set(rw.index, this.attributeLines.get(rw.index).withValueText(rw.box.getValue()));
        }
        this.syncGunFields();
    }

    /** Copies the gun card's live inputs back into the drafts used for saving. */
    private void syncGunFields() {
        for (GunField field : this.gunFields) {
            this.gunBulletDrafts.put(field.key, field.box.getValue());
        }
    }

    private void invalidateTooltip() {
        this.tooltipCache = null;
    }

    private void saveAndClose() {
        this.syncRowBoxes();
        CompoundTag root = AttributeData.copyRoot(this.previewStack);
        ArrayList<AttributeLine> combined = new ArrayList<>(this.attributeLines);
        combined.addAll(this.loreLines);
        AttributeData.setLines(root, combined);
        root.putBoolean("HideVanillaPanel", this.hideVanillaPanel);
        AttributeData.setHideFlagMask(root, this.hideFlags);
        AttributeData.setHideMainHandAttributes(root, this.hideMainHandAttributes);
        AttributeData.setHideAttributeLabels(root, this.hideAttributeLabels);
        if (this.gunCard) {
            GunOverrides.write(root, this.gunAmmoId, this.gunRecoil, this.parseGunBullets());
        }
        CompoundTag payload = new CompoundTag();
        payload.put("Root", (Tag) root);
        payload.put(AttributeData.ENCHANTMENTS, (Tag) AttributeData.writeEnchantTag(this.enchantEdits));
        String name = this.nameDraft.trim();
        if (name.isEmpty()) {
            payload.putBoolean("ClearName", true);
        } else if (!(!name.equals(this.originalName) || this.hadCustomName && LegacyText.hasFormattingCode(name))) {
            payload.putBoolean("ClearName", false);
        } else {
            payload.putString("CustomName", name);
        }
        NetworkHandler.CHANNEL.sendToServer(new SaveAttributesC2S(payload));
        this.onClose();
    }

    void updateHideSettings(int flags, boolean hideMainHandAttributes) {
        this.hideFlags = flags;
        this.hideMainHandAttributes = hideMainHandAttributes;
        this.invalidateTooltip();
    }

    /** The ballistic rows that hold a parseable number, keyed by their NBT name. */
    private java.util.Map<String, Double> parseGunBullets() {
        java.util.LinkedHashMap<String, Double> values = new java.util.LinkedHashMap<>();
        for (GunField field : this.gunFields) {
            String raw = this.gunBulletDrafts.getOrDefault(field.key, "").trim().replace("%", "");
            if (raw.isBlank() || raw.equals("-")) {
                continue;
            }
            try {
                values.put(field.key, Double.parseDouble(raw));
            } catch (NumberFormatException ignored) {
                // A half typed value is simply skipped rather than blocking the save.
            }
        }
        return values;
    }

    /** Compact number formatting for the card subtitle. */
    private static String fmt(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.001) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(Math.round(value * 100.0) / 100.0);
    }

    private List<AttributeLine> defaultAttributeLines(ItemStack stack) {
        ArrayList<AttributeLine> lines = new ArrayList<>();
        if (stack.getItem() instanceof IGun) {
            addDefault(lines, stack, AttributeType.GUN_LEVEL);
            addDefault(lines, stack, AttributeType.DAMAGE);
            addDefault(lines, stack, AttributeType.PVE_DAMAGE);
            addDefault(lines, stack, AttributeType.ARMOR_PENETRATION);
            addDefault(lines, stack, AttributeType.DURABILITY);
            addDefault(lines, stack, AttributeType.HEADSHOT_BONUS);
        } else if (stack.getItem() instanceof ArmorItem) {
            addDefault(lines, stack, AttributeType.ARMOR);
            addDefault(lines, stack, AttributeType.HEALTH);
            addDefault(lines, stack, AttributeType.PVE_DEFENSE_REDUCTION);
        } else {
            addDefault(lines, stack, AttributeType.DAMAGE);
            addDefault(lines, stack, AttributeType.ATTACK_SPEED);
            addDefault(lines, stack, AttributeType.PVE_DAMAGE);
            addDefault(lines, stack, AttributeType.ARMOR_PENETRATION);
            addDefault(lines, stack, AttributeType.DURABILITY);
        }
        if (lines.isEmpty()) {
            lines.add(AttributeLine.of(AttributeType.DAMAGE, ""));
        }
        return lines;
    }

    /**
     * Adds a default row seeded with the item's real current value. Types that have no
     * value on this item are skipped so the editor never opens with blank rows.
     */
    private static void addDefault(List<AttributeLine> lines, ItemStack stack, AttributeType type) {
        if (ItemProbe.hasValue(stack, type)) {
            lines.add(AttributeLine.of(type, ItemProbe.currentValue(stack, type)));
        }
    }

    private static String labelFor(AttributeLine line) {
        return line.type() == AttributeType.CUSTOM ? "Lore" : line.type().displayName();
    }

    private static int valueColor(AttributeLine line) {
        if (line.type() == AttributeType.GUN_LEVEL) {
            return 0xFFFF7070;
        }
        if (line.type() == AttributeType.CUSTOM) {
            return 0xFFB07CFF;
        }
        return 0xFFE8E8F0;
    }

    private static String suggestionFor(AttributeLine line) {
        return switch (line.type()) {
            case GUN_LEVEL -> "S";
            case DAMAGE, PVE_DAMAGE -> "14.0";
            case ARMOR_PENETRATION -> "75%";
            case DURABILITY -> "425";
            case ARMOR -> "12";
            case HEALTH -> "6";
            case PVE_DEFENSE_REDUCTION -> "20%";
            case HEADSHOT_BONUS -> "150%";
            case ATTACK_DAMAGE -> "8.0";
            case ATTACK_SPEED -> "1.0";
            case ARMOR_TOUGHNESS -> "2.0";
            case KNOCKBACK_RESISTANCE -> "0.25";
            case UNBREAKABLE, ENCHANT_GLINT -> "开";
            default -> "";
        };
    }

    private static int colorOf(AttributeType type) {
        Integer color = type.color().getColor();
        return color == null ? 0xFFAA55FF : (color | 0xFF000000);
    }

    // ---- input ---------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.clickFooter(mouseX, mouseY)) {
            return true;
        }
        if (button == 0 && this.dragIndex < 0) {
            for (RowWidgets rw : this.rowWidgets) {
                if (rw.grip.visible && rw.grip.isMouseOver(mouseX, mouseY)) {
                    this.syncRowBoxes();
                    this.dragIndex = rw.index;
                    this.dropIndex = rw.index;
                    this.dragGrabOffset = (int) mouseY - rw.grip.getY();
                    this.dragY = rw.grip.getY();
                    this.layoutDirty = true;
                    return true;
                }
            }
        }
        if (button == 0 && this.clickGunAmmo(mouseX, mouseY)) {
            return true;
        }
        if (mouseY >= contentTop() && mouseY <= contentBottom()) {
            for (Card card : Card.values()) {
                if (!this.cardVisible(card)) {
                    continue;
                }
                int i = card.ordinal();
                int hy = this.cardY[i];
                if (mouseX >= cardX() && mouseX <= cardX() + cardW() && mouseY >= hy && mouseY < hy + HEADER_H) {
                    this.expandTarget[i] = this.expandTarget[i] > 0.5f ? 0f : 1f;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Opens the ammo picker when the gun card's main-ammo row is clicked. */
    private boolean clickGunAmmo(double mouseX, double mouseY) {
        if (!this.gunCard || this.expand[Card.GUN.ordinal()] < 0.4f) {
            return false;
        }
        int i = Card.GUN.ordinal();
        int rowY = this.gunAmmoRowY;
        int left = cardX() + 12;
        int right = cardX() + cardW() - 12;
        boolean inside = mouseX >= left && mouseX <= right && mouseY >= rowY && mouseY < rowY + 24
                && rowY >= this.bodyY[i] && rowY + 24 <= this.bodyY[i] + this.bodyH[i]
                && rowY >= contentTop() && rowY + 24 <= contentBottom();
        if (!inside) {
            return false;
        }
        this.syncRowBoxes();
        this.minecraft.setScreen(new AmmoPickerScreen(this, this.previewStack, this.gunAmmoId, this::applyPickedAmmo));
        return true;
    }

    private void applyPickedAmmo(String ammoId) {
        this.gunAmmoId = ammoId == null || ammoId.isBlank() ? null : ammoId;
        this.invalidateTooltip();
        this.rebuildAllWidgets();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.dragIndex >= 0) {
            int i = Card.ATTRIBUTES.ordinal();
            int rowsTop = this.rowsTop(Card.ATTRIBUTES);
            this.dragY = (float) mouseY - this.dragGrabOffset;
            int slot = Math.round((this.dragY - rowsTop) / ROW_H);
            this.dropIndex = Math.max(0, Math.min(slot, this.attributeLines.size() - 1));
            this.layoutDirty = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragIndex >= 0) {
            int from = this.dragIndex;
            int to = this.dropIndex;
            this.dragIndex = -1;
            this.dropIndex = -1;
            if (from != to && from >= 0 && from < this.attributeLines.size() && to >= 0) {
                AttributeLine moved = this.attributeLines.remove(from);
                this.attributeLines.add(Math.max(0, Math.min(to, this.attributeLines.size())), moved);
                this.invalidateTooltip();
            }
            this.rebuildAllWidgets();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= panelX() && mouseX <= panelX() + panelW()
                && mouseY >= contentTop() && mouseY <= contentBottom()) {
            this.scrollTarget -= (float) Math.signum(delta) * 56f;
            this.clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // ---- live tooltip preview -----------------------------------------

    /** Cheap fingerprint of everything that changes the previewed item. */
    private String currentSignature() {
        StringBuilder sb = new StringBuilder(160);
        sb.append(this.liveName()).append('|').append(this.hideVanillaPanel).append('|')
                .append(this.hideFlags).append('|').append(this.hideMainHandAttributes).append('|')
                .append(this.hideAttributeLabels).append('|');
        for (RowWidgets rw : this.rowWidgets) {
            sb.append(rw.index).append(':').append(rw.box.getValue()).append(';');
        }
        for (java.util.Map.Entry<Enchantment, Integer> entry : this.enchantEdits.entrySet()) {
            sb.append(ForgeRegistries.ENCHANTMENTS.getKey(entry.getKey())).append('/').append(entry.getValue()).append(',');
        }
        for (AttributeLine line : this.loreLines) {
            sb.append(line.editorValue()).append(';');
        }
        return sb.toString();
    }

    /** Name currently in the input box, falling back to the synced draft. */
    private String liveName() {
        return this.nameBox != null ? this.nameBox.getValue() : this.nameDraft;
    }

    /**
     * Builds a throw-away stack carrying the current edits and asks the game for its real
     * tooltip, so the preview matches the inventory info box exactly.
     */
    private List<Component> buildPreviewTooltip() {
        ArrayList<Component> lines = new ArrayList<>();
        try {
            ItemStack preview = this.previewStack.copy();
            String name = this.liveName().trim();
            if (name.isEmpty()) {
                preview.resetHoverName();
            } else if (!name.equals(this.originalName) || this.hadCustomName) {
                preview.setHoverName(LegacyText.parse(name, null));
            }
            ArrayList<AttributeLine> attrs = new ArrayList<>();
            for (RowWidgets rw : this.rowWidgets) {
                if (rw.index >= 0 && rw.index < this.attributeLines.size()) {
                    attrs.add(this.attributeLines.get(rw.index).withValueText(rw.box.getValue()));
                }
            }
            attrs.addAll(this.loreLines);
            CompoundTag root = AttributeData.copyRoot(preview);
            AttributeData.setLines(root, attrs);
            root.putBoolean("HideVanillaPanel", this.hideVanillaPanel);
            AttributeData.setHideFlagMask(root, this.hideFlags);
            AttributeData.setHideMainHandAttributes(root, this.hideMainHandAttributes);
            AttributeData.setHideAttributeLabels(root, this.hideAttributeLabels);
            AttributeData.writeRoot(preview, root);
            AttributeData.applyEnchantTag(preview, AttributeData.writeEnchantTag(this.enchantEdits));
            if (this.minecraft != null && this.minecraft.player != null) {
                lines.addAll(preview.getTooltipLines(this.minecraft.player, TooltipFlag.NORMAL));
            } else {
                lines.add(preview.getHoverName());
            }
        } catch (RuntimeException exception) {
            lines.clear();
            lines.add(Component.literal(this.originalName));
        }
        return lines;
    }

    private List<Component> previewTooltip() {
        String signature = this.currentSignature();
        if (this.tooltipCache == null || !signature.equals(this.tooltipSignature)) {
            this.tooltipSignature = signature;
            this.tooltipCache = this.buildPreviewTooltip();
        }
        return this.tooltipCache;
    }

    // ---- rendering -----------------------------------------------------

    private float openProgress() {
        long elapsed = System.currentTimeMillis() - this.openTime;
        return UiKit.easeOut(Math.min(1f, elapsed / 220f));
    }

    private void animateCards(float dt) {
        for (Card card : Card.values()) {
            int i = card.ordinal();
            this.expand[i] = UiKit.approach(this.expand[i], this.expandTarget[i], dt, 14f);
        }
    }

    private void animateScroll(float dt) {
        this.scroll = UiKit.approach(this.scroll, this.scrollTarget, dt, 18f);
    }

    private float rowHover(int index) {
        return index >= 0 && index < this.rowHoverValues.length ? this.rowHoverValues[index] : 0f;
    }

    private void updateRowHover(int mouseY, float dt) {
        int total = this.attributeLines.size();
        if (this.rowHoverValues.length != total) {
            float[] resized = new float[total];
            System.arraycopy(this.rowHoverValues, 0, resized, 0, Math.min(this.rowHoverValues.length, total));
            this.rowHoverValues = resized;
        }
        int i = Card.ATTRIBUTES.ordinal();
        int rowsTop = this.rowsTop(Card.ATTRIBUTES);
        for (int r = 0; r < total; r++) {
            int y = rowsTop + r * ROW_H;
            boolean hovered = this.dragIndex < 0 && this.expand[i] > 0.5f
                    && mouseY >= y && mouseY < y + ROW_CARD_H
                    && y >= this.bodyY[i] && y + ROW_CARD_H <= this.bodyY[i] + this.bodyH[i]
                    && y >= contentTop() && y + ROW_CARD_H <= contentBottom();
            this.rowHoverValues[r] = UiKit.approach(this.rowHoverValues[r], hovered ? 1f : 0f, dt, 12f);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float dt = UiKit.frameDelta(this.lastFrameNanos);
        this.lastFrameNanos = System.nanoTime();
        this.animateCards(dt);
        this.animateScroll(dt);
        this.layoutIfNeeded();
        this.updateRowHover(mouseY, dt);
        float p = this.openProgress();

        graphics.fill(0, 0, this.width, this.height, UiKit.scaleAlpha(UiKit.SCRIM, p));

        graphics.pose().pushPose();
        float scale = 0.985f + 0.015f * p;
        graphics.pose().translate(this.width / 2f, this.height / 2f, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.pose().translate(-this.width / 2f, -this.height / 2f, 0);
        graphics.setColor(1f, 1f, 1f, p);

        UiKit.glassPanel(graphics, panelX(), panelY(), panelW(), panelH(), 12);
        UiKit.drawScaled(graphics, this.font, "属性编辑器", panelX() + 18, panelY() + 13, 1.4f, UiKit.TEXT_PRIMARY);
        UiKit.drawShadowed(graphics, this.font, "点击标题展开 · 拖动 ☰ 排序 · 滚轮滚动",
                panelX() + 18, panelY() + 30, UiKit.TEXT_MUTED);

        // Clip the card column to its viewport so cards never spill over the panel
        // edges or slide up under the header while scrolling.
        graphics.enableScissor(cardX() - 4, contentTop(), cardX() + cardW() + 12, contentBottom());
        for (Card card : Card.values()) {
            this.renderCard(graphics, card, mouseX, mouseY);
        }
        graphics.disableScissor();
        this.renderScrollbar(graphics);

        if (this.previewEnabled()) {
            this.renderPreviewColumn(graphics);
        }

        this.hoveredPlatform = null;
        if (mouseY >= this.linkY() && mouseY <= this.linkY() + LINK_H) {
            for (Platform platform : Platform.values()) {
                int lx = this.linkX(platform);
                if (mouseX >= lx && mouseX <= lx + this.linkWidth(platform)) {
                    this.hoveredPlatform = platform;
                    break;
                }
            }
        }
        this.renderFooter(graphics);

        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();

        super.render(graphics, mouseX, mouseY, partialTick);

        // Drawn last and outside the panel transform so the info box lands on exact
        // screen coordinates and stays crisp. It is clipped to the viewport band so a
        // very long tooltip cannot spill over the header or the footer.
        if (this.previewEnabled()) {
            List<Component> tooltip = this.previewTooltip();
            if (!tooltip.isEmpty()) {
                int boxWidth = this.tooltipWidth(tooltip);
                int x = this.previewX() + 6;
                x = Math.min(x, Math.max(4, this.width - boxWidth - 6));
                graphics.enableScissor(0, contentTop(), this.width, contentBottom());
                graphics.renderComponentTooltip(this.font, tooltip, x, this.previewTooltipY());
                graphics.disableScissor();
            }
        }
    }

    /** Mirrors the width the vanilla tooltip renderer will use, so it can be clamped. */
    private int tooltipWidth(List<Component> lines) {
        int widest = 0;
        for (Component line : lines) {
            widest = Math.max(widest, this.font.width(line));
        }
        return widest + 12;
    }

    // ---- live info column bands ---------------------------------------
    // The column is split into fixed bands with explicit dividers so the item icon and
    // the info box can never overlap, whatever the tooltip ends up containing.
    private static final int PREVIEW_ICON_BAND_H = 44;

    private int previewTitleY() {
        return contentTop() + 7;
    }

    private int previewIconBandY() {
        return contentTop() + 30;
    }

    private int previewIconBandBottom() {
        return previewIconBandY() + PREVIEW_ICON_BAND_H;
    }

    /** The info box starts clear of the icon band, below a divider. */
    private int previewTooltipY() {
        return previewIconBandBottom() + 10;
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int max = Math.max(0, this.totalContentH - viewportH());
        if (max <= 0) {
            return;
        }
        int barX = cardX() + cardW() + 3;
        int top = contentTop();
        int trackH = viewportH();
        UiKit.fillRounded(graphics, barX, top, 3, trackH, 2, 0x33FFFFFF);
        int thumbH = Math.max(18, trackH * trackH / Math.max(1, this.totalContentH));
        int thumbY = top + Math.min(trackH - thumbH,
                Math.max(0, (int) ((trackH - thumbH) * this.scroll / max)));
        UiKit.fillRounded(graphics, barX, thumbY, 3, thumbH, 2, UiKit.ACCENT);
    }

    private void renderPreviewColumn(GuiGraphics graphics) {
        int x = previewX();
        int w = previewW();
        int top = contentTop();
        int height = contentBottom() - top;
        UiKit.fillRounded(graphics, x, top, w, height, 10, UiKit.scaleAlpha(UiKit.CARD_BG, 0.7f));
        UiKit.strokeRounded(graphics, x, top, w, height, 10, UiKit.CARD_BORDER);

        UiKit.accentBar(graphics, x + 10, previewTitleY() - 1, 10, UiKit.SUCCESS);
        UiKit.drawShadowed(graphics, this.font, "实时信息", x + 22, previewTitleY(), UiKit.TEXT_PRIMARY);
        graphics.fill(x + 10, top + 24, x + w - 10, top + 25, 0x22FFFFFF);

        int bandY = previewIconBandY();
        UiKit.fillRounded(graphics, x + 10, bandY, w - 20, PREVIEW_ICON_BAND_H, 8, 0x33000000);
        UiKit.strokeRounded(graphics, x + 10, bandY, w - 20, PREVIEW_ICON_BAND_H, 8, 0x22FFFFFF);
        int iconTop = bandY + (PREVIEW_ICON_BAND_H - 32) / 2;
        graphics.pose().pushPose();
        graphics.pose().translate(x + w / 2f - 16, iconTop, 0);
        graphics.pose().scale(2f, 2f, 1f);
        graphics.renderItem(this.previewStack, 0, 0);
        graphics.pose().popPose();

        int dividerY = previewIconBandBottom();
        graphics.fill(x + 10, dividerY, x + w - 10, dividerY + 1, 0x22FFFFFF);
    }

    private void renderCard(GuiGraphics graphics, Card card, int mouseX, int mouseY) {
        if (!this.cardVisible(card)) {
            return;
        }
        int i = card.ordinal();
        int x = cardX();
        int w = cardW();
        int y = this.cardY[i];
        int visible = this.bodyH[i];
        int total = HEADER_H + visible;
        if (y > contentBottom() || y + total < contentTop()) {
            return;
        }
        float open = this.expand[i];
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + HEADER_H
                && mouseY >= contentTop() && mouseY <= contentBottom();

        UiKit.fillRounded(graphics, x, y, w, total, 10, UiKit.scaleAlpha(UiKit.CARD_BG, 0.55f + 0.45f * open));
        UiKit.strokeRounded(graphics, x, y, w, total, 10,
                hovered ? UiKit.CARD_BORDER_HOVER : UiKit.CARD_BORDER);
        UiKit.accentBar(graphics, x + 6, y + 8, HEADER_H - 16, cardAccent(card));

        UiKit.drawShadowed(graphics, this.font, cardTitle(card), x + 18, y + 4, UiKit.TEXT_PRIMARY);
        UiKit.drawShadowed(graphics, this.font, cardSubtitle(card), x + 18, y + 15, UiKit.TEXT_MUTED);

        renderChevron(graphics, x + w - 18, y + HEADER_H / 2 + 1, open, hovered);

        if (visible > 1) {
            graphics.fill(x + 12, y + HEADER_H, x + w - 12, y + HEADER_H + 1,
                    UiKit.scaleAlpha(0x22FFFFFF, open));
        }

        if (open < 0.02f || visible < 2) {
            return;
        }
        graphics.pose().pushPose();
        switch (card) {
            case ITEM -> renderItemBody(graphics, x, w, i);
            case ATTRIBUTES -> renderAttributeBody(graphics, x, w, i);
            case GUN -> renderGunBody(graphics, x, w, i, mouseX, mouseY);
            case ENCHANTS -> renderEnchantBody(graphics, x, w, i);
            case LORE -> renderLoreBody(graphics, x, w, i);
            case DISPLAY -> renderDisplayBody(graphics, x, w, i);
        }
        graphics.pose().popPose();
    }

    private void renderChevron(GuiGraphics graphics, int cx, int cy, float open, boolean hovered) {
        int color = hovered ? UiKit.ACCENT_BRIGHT : UiKit.TEXT_SECONDARY;
        float angle = (1f - open) * (float) Math.PI / 2f; // 0 => pointing down, PI/2 => pointing right
        int len = 4;
        for (int t = -len; t <= len; t++) {
            double ax = t;
            double ay = Math.abs(t);
            int px = cx + (int) Math.round(ax * Math.cos(angle) - ay * Math.sin(angle));
            int py = cy + (int) Math.round(ax * Math.sin(angle) + ay * Math.cos(angle));
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    private void renderItemBody(GuiGraphics graphics, int x, int w, int i) {
        int bodyInnerY = this.bodyY[i] + 7;
        int clearX = x + w - 12 - 58;
        int inputX = x + 68;
        graphics.pose().pushPose();
        graphics.pose().translate(x + 12, bodyInnerY + 1, 0);
        graphics.pose().scale(1.25f, 1.25f, 1f);
        graphics.renderItem(this.previewStack, 0, 0);
        graphics.pose().popPose();
        UiKit.drawShadowed(graphics, this.font, "名称", x + 34, bodyInnerY + 6, UiKit.TEXT_SECONDARY);
        UiKit.inputBackdrop(graphics, inputX, bodyInnerY, Math.max(60, clearX - 10 - inputX), INPUT_H,
                this.nameBox != null && this.nameBox.isFocused(), 0f);
    }

    private void renderAttributeBody(GuiGraphics graphics, int x, int w, int i) {
        int bodyInnerY = this.bodyY[i];
        int left = x + 12;
        UiKit.accentBar(graphics, left, bodyInnerY + 5, 9, UiKit.ACCENT);
        UiKit.drawShadowed(graphics, this.font, "可选属性 · 点击已添加项可移除", left + 12, bodyInnerY + 3, UiKit.TEXT_SECONDARY);

        int chipY = this.chipTop(Card.ATTRIBUTES);
        for (ChipLayout chip : this.chipLayouts) {
            int cy = chipY + chip.y();
            if (cy + chip.h() > this.bodyY[i] + this.bodyH[i] || cy < this.bodyY[i]) {
                continue;
            }
            if (cy + chip.h() > contentBottom() || cy < contentTop()) {
                continue;
            }
            if (this.indexOfType(chip.type()) >= 0) {
                UiKit.fillRounded(graphics, chip.x() - 2, cy - 2, chip.w() + 4, chip.h() + 4, 8,
                        UiKit.scaleAlpha(0x4022C55E, 0.9f));
            }
        }

        int rowsTop = this.rowsTop(Card.ATTRIBUTES);
        UiKit.accentBar(graphics, left, rowsTop - 12, 9, UiKit.ACCENT_BRIGHT);
        UiKit.drawShadowed(graphics, this.font, "已有属性 (" + this.attributeLines.size() + ")",
                left + 12, rowsTop - 14, UiKit.TEXT_SECONDARY);

        int actionsX = left + (w - 24) - 4 - ACTIONS_TOTAL;
        for (RowWidgets rw : this.rowWidgets) {
            boolean dragging = rw.index == this.dragIndex;
            int rowY = dragging ? Math.round(this.dragY) : rowsTop + this.slotFor(rw.index) * ROW_H;
            if (!dragging && (rowY + ROW_CARD_H > this.bodyY[i] + this.bodyH[i] || rowY < this.bodyY[i])) {
                continue;
            }
            if (rowY + ROW_CARD_H > contentBottom() || rowY < contentTop()) {
                continue;
            }
            AttributeLine line = this.attributeLines.get(rw.index);
            float hover = dragging ? 1f : this.rowHover(rw.index);
            UiKit.fillRounded(graphics, left, rowY, w - 24, ROW_CARD_H, 8,
                    dragging ? 0xD82A2340 : UiKit.lerpColor(UiKit.CARD_BG, UiKit.CARD_BG_HOVER, hover));
            UiKit.strokeRounded(graphics, left, rowY, w - 24, ROW_CARD_H, 8,
                    dragging ? UiKit.ACCENT_BRIGHT : UiKit.lerpColor(UiKit.CARD_BORDER, UiKit.CARD_BORDER_HOVER, hover));
            UiKit.accentBar(graphics, left + 6, rowY + 6, 12, colorOf(rw.type));
            UiKit.drawShadowed(graphics, this.font, labelFor(line), left + 14, rowY + 8, colorOf(rw.type));

            int valueX = left + 14 + rw.labelWidth + 6;
            int valueW = Math.max(50, actionsX - 8 - valueX);
            boolean focused = rw.box.isFocused();
            UiKit.inputBackdrop(graphics, valueX, rowY + 1, valueW, INPUT_H, focused, hover * 0.6f);
        }

        // Drop indicator for the row currently being dragged.
        if (this.dragIndex >= 0 && this.dropIndex >= 0) {
            int indicatorY = rowsTop + this.dropIndex * ROW_H - 2;
            if (indicatorY >= contentTop() && indicatorY <= contentBottom()) {
                UiKit.fillRounded(graphics, left + 4, indicatorY, w - 32, 3, 1, UiKit.ACCENT_BRIGHT);
            }
        }

        if (this.attributeLines.isEmpty()) {
            UiKit.drawScaledCentered(graphics, this.font, "暂无属性，点击上方按钮添加",
                    x + w / 2, rowsTop + 4, 1f, UiKit.TEXT_MUTED);
        }
    }

    /**
     * The gun card body: a clickable "main ammo" row, the recoil multiplier slider and a
     * two-column grid of ballistic override inputs.
     */
    private void renderGunBody(GuiGraphics graphics, int x, int w, int i, int mouseX, int mouseY) {
        int left = x + 12;
        int innerW = w - 24;

        // ---- main ammo row (opens the ammo picker on click) ----
        int ammoY = this.gunAmmoY();
        ItemStack ammo = this.ammoDisplayStack();
        boolean ammoHovered = mouseX >= left && mouseX <= left + innerW && mouseY >= ammoY && mouseY < ammoY + 24
                && ammoY >= contentTop() && ammoY + 24 <= contentBottom();
        UiKit.rowCard(graphics, left, ammoY, innerW, 24, ammoHovered ? 1f : 0f);
        UiKit.accentBar(graphics, left + 6, ammoY + 6, 12, 0xFFFFB454);
        if (!ammo.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(left + 22, ammoY + 4, 0);
            graphics.renderItem(ammo, 0, 0);
            graphics.pose().popPose();
        }
        String ammoName = ammo.isEmpty() ? "未设置" : ammo.getHoverName().getString();
        String ammoIdText = this.gunAmmoId != null ? this.gunAmmoId : "枪械原弹药";
        UiKit.drawShadowed(graphics, this.font, UiKit.trim(this.font, ammoName, innerW - 140),
                left + 44, ammoY + 3, UiKit.TEXT_PRIMARY);
        UiKit.drawShadowed(graphics, this.font, UiKit.trim(this.font, ammoIdText, innerW - 140),
                left + 44, ammoY + 13, UiKit.TEXT_MUTED);
        String hint = "更换 ›";
        UiKit.drawShadowed(graphics, this.font, hint, left + innerW - 8 - this.font.width(hint), ammoY + 8,
                ammoHovered ? UiKit.ACCENT_BRIGHT : UiKit.TEXT_SECONDARY);

        // ---- recoil multiplier (the slider widget draws itself) ----
        int recoilY = this.gunRecoilY();
        UiKit.accentBar(graphics, left, recoilY + 5, 9, UiKit.ACCENT);
        UiKit.drawShadowed(graphics, this.font, "基础后座", left + 12, recoilY + 3, UiKit.TEXT_SECONDARY);

        // ---- ballistic overrides ----
        int top = this.gunBulletTop();
        int columns = 2;
        int colGap = 12;
        int colW = (innerW - colGap) / columns;
        UiKit.drawShadowed(graphics, this.font, "子弹参数", left, top - 16, UiKit.TEXT_SECONDARY);
        for (int idx = 0; idx < this.gunFields.size(); idx++) {
            GunField field = this.gunFields.get(idx);
            int col = idx % columns;
            int row = idx / columns;
            int cellX = left + col * (colW + colGap);
            int cellY = top + row * GUN_ROW_H;
            UiKit.drawShadowed(graphics, this.font, field.label, cellX, cellY + 6, UiKit.TEXT_MUTED);
            int inputX = cellX + GUN_LABEL_W;
            int inputW = Math.max(30, colW - GUN_LABEL_W - 4);
            UiKit.inputBackdrop(graphics, inputX, cellY + 1, inputW, INPUT_H, field.box.isFocused(), 0f);
        }
    }

    /** The ammo shown on the gun card: the override when set, otherwise the gun's own. */
    private ItemStack ammoDisplayStack() {
        ResourceLocation id = this.gunAmmoId != null
                ? ResourceLocation.tryParse(this.gunAmmoId)
                : ItemProbe.currentAmmoId(this.previewStack);
        return ItemProbe.ammoStack(id);
    }

    private void renderEnchantBody(GuiGraphics graphics, int x, int w, int i) {
        int bodyInnerY = this.bodyY[i] + 7;
        UiKit.drawShadowed(graphics, this.font, "附魔", x + 12, bodyInnerY + 6, UiKit.SUCCESS);
        String summary = UiKit.trim(this.font, this.enchantSummary(), w - 24 - 100);
        graphics.drawString(this.font, summary, x + 12 + this.font.width("附魔") + 10, bodyInnerY + 6,
                this.enchantEdits.isEmpty() ? UiKit.TEXT_MUTED : 0xFFA7F3D0, false);
    }

    private void renderLoreBody(GuiGraphics graphics, int x, int w, int i) {
        int bodyInnerY = this.bodyY[i] + 7;
        UiKit.drawShadowed(graphics, this.font, "描述", x + 12, bodyInnerY + 7, UiKit.ACCENT_BRIGHT);
        String summary = UiKit.trim(this.font, this.loreSummaryText(), w - 24 - 110);
        graphics.drawString(this.font, summary, x + 12 + this.font.width("描述") + 10, bodyInnerY + 7, 0xFFB07CFF, false);
    }

    private void renderDisplayBody(GuiGraphics graphics, int x, int w, int i) {
        int bodyInnerY = this.bodyY[i] + 7;
        String flags = "隐藏项 " + Integer.bitCount(this.hideFlags & 0xFF) + " / 8";
        graphics.drawString(this.font, flags, x + 12 + 158, bodyInnerY + 60, UiKit.TEXT_MUTED, false);
    }

    private String cardTitle(Card card) {
        return switch (card) {
            case ITEM -> "物品信息";
            case ATTRIBUTES -> "属性";
            case GUN -> "枪械数据";
            case ENCHANTS -> "附魔 (" + this.enchantEdits.size() + ")";
            case LORE -> "描述";
            case DISPLAY -> "显示设置";
        };
    }

    private String cardSubtitle(Card card) {
        return switch (card) {
            case ITEM -> this.nameDraft.isBlank() ? this.previewStack.getItem().getDescription().getString() : this.nameDraft;
            case ATTRIBUTES -> this.attributeLines.size() + " 项属性";
            case GUN -> this.gunSubtitle();
            case ENCHANTS -> this.enchantEdits.isEmpty() ? "未添加附魔" : "已添加 " + this.enchantEdits.size() + " 项";
            case LORE -> this.loreSummaryText();
            case DISPLAY -> (this.hideVanillaPanel ? "原版标签: 隐藏" : "原版标签: 显示")
                    + " · " + (this.hideAttributeLabels ? "属性标签: 隐藏" : "属性标签: 显示");
        };
    }

    /** Short summary for the gun card header: ammo, recoil and how many ballistics are set. */
    private String gunSubtitle() {
        String ammo = this.gunAmmoId != null ? this.gunAmmoId : "原弹药";
        int overridden = 0;
        for (GunField field : this.gunFields) {
            if (!this.gunBulletDrafts.getOrDefault(field.key, "").isBlank()) {
                overridden++;
            }
        }
        return ammo + " · 后座 " + fmt(this.gunRecoil) + "x · 弹道 " + overridden + "/" + GunOverrides.BULLET_FIELDS.length;
    }

    private static int cardAccent(Card card) {
        return switch (card) {
            case ITEM -> UiKit.ACCENT_BRIGHT;
            case ATTRIBUTES -> UiKit.ACCENT;
            case GUN -> 0xFFFFB454;
            case ENCHANTS -> UiKit.SUCCESS;
            case LORE -> UiKit.ACCENT_BRIGHT;
            case DISPLAY -> UiKit.TEXT_SECONDARY;
        };
    }

    private String loreSummaryText() {
        for (AttributeLine line : this.loreLines) {
            if (line == null || line.text().isBlank()) {
                continue;
            }
            return line.editorValue().isBlank() ? line.text() : line.editorValue();
        }
        return "未设置";
    }

    // ---- footer: author, links, licence notice -------------------------

    private static final int LINK_H = 18;
    private static final int ICON = 9;

    private int linkWidth(Platform platform) {
        return ICON + 6 + this.font.width(platform.label) + 14;
    }

    /** X of the link chip for {@code platform}, laid out right to left from the panel edge. */
    private int linkX(Platform platform) {
        int x = panelX() + panelW() - 16;
        for (int i = Platform.values().length - 1; i >= 0; i--) {
            Platform current = Platform.values()[i];
            x -= this.linkWidth(current);
            if (current == platform) {
                return x;
            }
            x -= 6;
        }
        return x;
    }

    private int linkY() {
        return this.footerY() + (FOOTER_H - LINK_H) / 2;
    }

    private void renderFooter(GuiGraphics graphics) {
        int top = this.footerY();
        graphics.fill(panelX() + 14, top, panelX() + panelW() - 14, top + 1, 0x22FFFFFF);

        int textY = top + (FOOTER_H - 8) / 2;
        String author = "作者 " + AUTHOR_NAME;
        int authorX = this.linkX(Platform.values()[0]) - 14 - this.font.width(author);
        UiKit.drawShadowed(graphics, this.font, author, authorX, textY, UiKit.TEXT_PRIMARY);

        int noticeX = panelX() + 18;
        if (authorX - 16 - this.font.width(LICENCE_NOTICE) > noticeX) {
            UiKit.drawShadowed(graphics, this.font, LICENCE_NOTICE, noticeX, textY, 0xFFFFB74D);
        }

        for (Platform platform : Platform.values()) {
            int x = this.linkX(platform);
            int y = this.linkY();
            int w = this.linkWidth(platform);
            boolean hovered = this.hoveredPlatform == platform;
            UiKit.fillRounded(graphics, x, y, w, LINK_H, 6,
                    hovered ? 0x44FFFFFF : 0x22FFFFFF);
            UiKit.strokeRounded(graphics, x, y, w, LINK_H, 6,
                    hovered ? UiKit.ACCENT_BRIGHT : UiKit.CARD_BORDER);
            drawPlatformIcon(graphics, platform, x + 7, y + (LINK_H - ICON) / 2,
                    hovered ? UiKit.ACCENT_BRIGHT : UiKit.TEXT_SECONDARY);
            UiKit.drawShadowed(graphics, this.font, platform.label, x + 7 + ICON + 6,
                    y + (LINK_H - 8) / 2, hovered ? UiKit.TEXT_PRIMARY : UiKit.TEXT_SECONDARY);
        }
    }

    /**
     * Simplified, hand-drawn glyphs - recognisable at this size without shipping any
     * third-party logo assets.
     */
    private static void drawPlatformIcon(GuiGraphics graphics, Platform platform, int x, int y, int color) {
        switch (platform) {
            case BILIBILI -> {
                // Rounded screen with two antennae and two eyes.
                UiKit.fillRounded(graphics, x, y + 2, ICON, ICON - 2, 2, color);
                graphics.fill(x + 2, y, x + 3, y + 2, color);
                graphics.fill(x + ICON - 3, y, x + ICON - 2, y + 2, color);
                graphics.fill(x + 3, y + 4, x + 4, y + 6, 0xFF101018);
                graphics.fill(x + ICON - 4, y + 4, x + ICON - 3, y + 6, 0xFF101018);
            }
            case GITHUB -> {
                // Cat head: round body with two ears.
                UiKit.fillRounded(graphics, x + 1, y + 2, ICON - 2, ICON - 3, 3, color);
                graphics.fill(x + 2, y, x + 3, y + 3, color);
                graphics.fill(x + ICON - 3, y, x + ICON - 2, y + 3, color);
                graphics.fill(x + 3, y + ICON - 2, x + ICON - 3, y + ICON - 1, color);
            }
            case QQ -> {
                // Penguin: body, head, beak and feet.
                UiKit.fillRounded(graphics, x + 2, y + 4, ICON - 4, ICON - 4, 3, color);
                UiKit.fillRounded(graphics, x + 3, y, ICON - 6, 5, 2, color);
                graphics.fill(x + ICON / 2 - 1, y + 4, x + ICON / 2 + 1, y + 5, 0xFF101018);
                graphics.fill(x + 1, y + ICON - 1, x + 4, y + ICON, color);
                graphics.fill(x + ICON - 4, y + ICON - 1, x + ICON - 1, y + ICON, color);
            }
        }
    }

    private boolean clickFooter(double mouseX, double mouseY) {
        if (mouseY < this.linkY() || mouseY > this.linkY() + LINK_H) {
            return false;
        }
        for (Platform platform : Platform.values()) {
            int x = this.linkX(platform);
            if (mouseX >= x && mouseX <= x + this.linkWidth(platform)) {
                openLink(platform.url);
                return true;
            }
        }
        return false;
    }

    private void openLink(String url) {
        if (this.minecraft == null) {
            return;
        }
        try {
            net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(url, this, true);
        } catch (RuntimeException exception) {
            net.minecraft.Util.getPlatform().openUri(url);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}