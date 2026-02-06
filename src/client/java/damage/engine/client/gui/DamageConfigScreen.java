package damage.engine.client.gui;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.text.Style;

public class DamageConfigScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    
    private CategoryListWidget categoryList;
    private ConfigOptionListWidget optionList;
    private boolean previewEnabled = false;
    
    private final List<CategoryEntry> categories = new ArrayList<>();
    private int selectedCategoryIndex = 0;

    public DamageConfigScreen(Screen parent) {
        super(Text.translatable("title.damage-engine.config"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    private final List<ButtonWidget> footerButtons = new ArrayList<>();

    @Override
    protected void init() {
        this.clearChildren();
        categories.clear();
        footerButtons.clear();
        
        int footerHeight = 40;
        int headerHeight = 30;
        int leftWidth = 120;
        int footerY = this.height - 30;
        
        // Full height lists limited by footer position to allow "masking"
        // The list will render from 0 to footerY.
        int listHeight = footerY; 
        
        // 1. Left Side: Category List
        // Pass this.height as total height, and footerY as bottom
        categoryList = new CategoryListWidget(this.client, leftWidth, this.height, headerHeight, footerY, 25);
        categoryList.setLeftPos(0);
        
        // 2. Right Side: Option List
        // Start from leftWidth (120) and take remaining width
        optionList = new ConfigOptionListWidget(this.client, this.width - leftWidth, this.height, 0, footerY, 30);
        optionList.setLeftPos(leftWidth);
        
        initOptions();
        
        this.addDrawableChild(categoryList);
        this.addDrawableChild(optionList);
        
        // 3. Footer Buttons
        int buttonWidth = 80;
        int buttonY = footerY + 5; // Centered vertically in the 30px footer
        
        // Save
        ButtonWidget saveBtn = ButtonWidget.builder(Text.translatable("button.damage-engine.save_close").setStyle(Style.EMPTY.withColor(0xFFB7F3C8)), b -> {
            config.save();
            this.client.setScreen(parent);
        }).dimensions(this.width - buttonWidth - 10, buttonY, buttonWidth, 20).build();
        footerButtons.add(saveBtn);
        this.addSelectableChild(saveBtn);
        
        // Cancel
        ButtonWidget cancelBtn = ButtonWidget.builder(Text.translatable("gui.cancel"), b -> {
            config.load();
            this.client.setScreen(parent);
        }).dimensions(this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20).build();
        footerButtons.add(cancelBtn);
        this.addSelectableChild(cancelBtn);
        
        // Preview Toggle
        ButtonWidget previewBtn = ButtonWidget.builder(Text.translatable("text.damage-engine.preview").append(Text.literal(": " + (previewEnabled ? "ON" : "OFF")).setStyle(Style.EMPTY.withColor(previewEnabled ? 0xFFB5F0C6 : 0xFFFFFFFF))), b -> {
            previewEnabled = !previewEnabled;
            b.setMessage(Text.translatable("text.damage-engine.preview").append(Text.literal(": " + (previewEnabled ? "ON" : "OFF")).setStyle(Style.EMPTY.withColor(previewEnabled ? 0xFFB5F0C6 : 0xFFFFFFFF))));
        }).dimensions(10, buttonY, 80, 20).build();
        footerButtons.add(previewBtn);
        this.addSelectableChild(previewBtn);
    }
    
    private void initOptions() {
        // --- General ---
        addCategory("category.damage_engine.general", 0);
        addOption(new BooleanOptionEntry("option.damage-engine.showDamage", config.showDamage, v -> config.showDamage = v));
        addOption(new BooleanOptionEntry("option.damage-engine.showProgressBar", config.showProgressBar, v -> config.showProgressBar = v));
        
        // --- Appearance ---
        addCategory("category.damage_engine.appearance", 1);
        // Corrected keys for label and button text
        addOption(new ButtonActionEntry("option.damage-engine.edit_pos_label", "button.damage-engine.edit_pos", () -> this.client.setScreen(new HudEditorScreen(this))));
        
        addOption(new SliderEntry("option.damage-engine.scale", config.hudScale, 0.5f, 2.5f, v -> config.hudScale = v));
        addOption(new HexColorEntry("option.damage-engine.progressBarColor", config.progressBarColor, v -> config.progressBarColor = v));
        
        // --- Keybinds ---
        addCategory("category.damage_engine.keybinds", 2);
        // Replace custom keybind entries with a button to open MC Keybinds Screen
        addOption(new ButtonActionEntry("category.damage_engine.keybinds", "options.controls", () -> 
             this.client.setScreen(new net.minecraft.client.gui.screen.option.KeybindsScreen(this, this.client.options))));
        
        // --- Other ---
        addCategory("category.damage_engine.other", 3);
        addOption(new NumericEntry("option.damage-engine.resetTime", config.resetTime, v -> config.resetTime = v));
        
        // Reset Button
        // Removed as requested
        
        // Spacer for bottom scrolling
        addOption(new SpacerEntry(30));
        addOption(new SpacerEntry(30));
    }
    
    private void addCategory(String key, int id) {
        CategoryEntry cat = new CategoryEntry(this, Text.translatable(key), id, optionList.children().size());
        categoryList.addEntryPublic(cat);
        categories.add(cat);
        
        optionList.addEntryPublic(new HeaderEntry(Text.translatable(key)));
    }
    
    private void addOption(OptionEntry entry) {
        optionList.addEntryPublic(entry);
    }
    
    private void selectCategory(int index) {
        selectedCategoryIndex = index;
        if (index >= 0 && index < categories.size()) {
            CategoryEntry cat = categories.get(index);
            double y = 0;
            // Crude scroll calculation
            for (int i = 0; i < cat.targetIndex; i++) {
                y += 30; // approx height
            }
            // Use smooth scrolling to target
            optionList.setTargetScroll(y);
        }
    }
    
    private void updateActiveCategory() {
        // Implementation omitted for simplicity to avoid complexity, logic in selectCategory is enough for navigation
    }

    private boolean isBinding = false;

    public void setBinding(boolean binding) {
        this.isBinding = binding;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // First check binding mode
        if (isBinding) {
            // Check if ESC was pressed - if so, consume it HERE to prevent closing screen
            // The actual binding logic is handled by the button's keyPressed, 
            // but we need to ensure the event doesn't propagate to Screen.close()
            
            // Try to let children handle it first
            if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
            
            // If children didn't consume it (e.g. they don't have focus?),
            // we MUST consume ESC if we are in binding mode.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                return true;
            }
        }
        
        // Normal mode
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            config.save(); // Should we save on ESC? Usually Cancel, but MC options usually save.
            // But wait, footer Cancel button reloads.
            // Let's assume ESC = Cancel/Back without saving? 
            // Or usually ESC in config screens means "Done" -> Save.
            // Footer has "Save & Close". So ESC should probably match that or match Cancel.
            // The user said "Press ESC to Save & Exit" in lang file.
            // So we should save.
            config.save();
            this.client.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Forward clicks to buttons first
        for (ButtonWidget btn : footerButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 1. Draw main background with blur if enabled in options
        // renderBackground calls renderInGameBackground if world != null
        this.renderBackground(context);
        
        // Update smooth scroll
        optionList.updateSmoothScroll(delta);
        
        // 2. Render Lists
        super.render(context, mouseX, mouseY, delta);
        
        // 3. Draw Separator Lines
        int leftWidth = 120;
        int footerY = this.height - 30;
        
        // Removed solid fill for left panel as requested "Unified background"
        // context.fill(0, 0, leftWidth, footerY - 1, 0x40000000); 
        
        // Vertical line (Ensure it doesn't go below footerY)
        context.fill(leftWidth - 1, 0, leftWidth, footerY, 0xFF555555);
        
        // Horizontal line (above footer)
        context.fill(0, footerY - 1, this.width, footerY, 0xFF555555);
        
        // 4. Draw Footer Background (Transparent but masks list due to list height limit)
        // We do NOT draw a solid fill here because user requested "Transparent".
        // The list masking is handled by the list widget's height/scissor.
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200); // Bump Z-index
        
        // 5. Render Footer Buttons manually
        for (ButtonWidget btn : footerButtons) {
            btn.render(context, mouseX, mouseY, delta);
        }
        context.getMatrices().pop();
        
        // 6. Header
        context.drawTextWithShadow(this.textRenderer, Text.translatable("category.damage_engine"), 6, 10, 0xFFFFFF);
        
        if (previewEnabled) {
            new DamageHud().renderPreview(context, this.width/2, this.height/2);
        }
    }
    
    // ================= WIDGETS =================
    
    private static class CategoryEntry extends ElementListWidget.Entry<CategoryEntry> {
        private final Text text;
        private final int id;
        private final int targetIndex;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private final DamageConfigScreen screen;
        
        public CategoryEntry(DamageConfigScreen screen, Text text, int id, int targetIndex) { 
            this.screen = screen;
            this.text = text; 
            this.id = id; 
            this.targetIndex = targetIndex;
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean isSelected = screen.selectedCategoryIndex == id;
            if (isSelected) {
                context.fill(x, y + 2, x + 2, y + entryHeight - 2, 0xFFFFFFFF);
            }
            int color = isSelected ? 0xFFFFFFFF : 0xFFAAAAAA;
            // Left align text: x + 10
            context.drawTextWithShadow(client.textRenderer, text, x + 10, y + 8, color);
        }
        
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            screen.selectCategory(id);
            return true;
        }
    }
    
    private class CategoryListWidget extends ElementListWidget<CategoryEntry> {
        public CategoryListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
            this.centerListVertically = false;
        }
        
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.left && mouseX <= this.right && mouseY >= this.top && mouseY <= this.bottom;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
             // Removed solid background fill to fix "Left side background still there"
             // context.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0x40000000);
             
             this.enableScissor(context);
             this.renderList(context, mouseX, mouseY, delta);
             context.disableScissor();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOver(mouseX, mouseY)) {
                double relativeY = mouseY - this.top + this.getScrollAmount();
                int index = (int)(relativeY / this.itemHeight);
                if (index >= 0 && index < this.children().size()) {
                    this.children().get(index).mouseClicked(mouseX, mouseY, button);
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override public int getRowWidth() { return this.width; } // Full width
        public void addEntryPublic(CategoryEntry entry) { this.addEntry(entry); }
        
        public void setLeftPos(int x) { 
            this.left = x;
            this.right = x + this.width;
        }
        
        @Override public int getRowLeft() { return this.left; }
        public int getScrollbarPositionX() { return -10; } 
    }
    
    private abstract static class OptionEntry extends ElementListWidget.Entry<OptionEntry> {}
    
    private class ConfigOptionListWidget extends ElementListWidget<OptionEntry> {
        private double targetScroll = 0;
        private boolean isSmoothScrolling = false;

        public ConfigOptionListWidget(MinecraftClient client, int width, int height, int top, int bottom, int itemHeight) {
            super(client, width, height, top, bottom, itemHeight);
            this.centerListVertically = false;
        }
        @Override public int getRowWidth() { return this.width - 20; } 
        public void addEntryPublic(OptionEntry entry) { this.addEntry(entry); }
        public void setLeftPos(int x) { 
            this.left = x;
            this.right = x + this.width;
        }
        @Override public int getRowLeft() { return this.left + 10; }
        public int getScrollbarPositionX() { return this.client.getWindow().getScaledWidth() - 6; }
        
        // Since render() is final in ClickableWidget (which ElementListWidget inherits from),
        // we must override renderWidget().
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
             this.enableScissor(context);
             this.renderList(context, mouseX, mouseY, delta);
             context.disableScissor();
             
             // Scrollbar
             int scrollbarX = this.getScrollbarPositionX();
             int scrollbarY = this.top;
             int scrollbarHeight = this.bottom - this.top;
             int contentHeight = this.getMaxScroll() + scrollbarHeight;
             
             if (contentHeight > scrollbarHeight) {
                 int barHeight = (int)((float)(scrollbarHeight * scrollbarHeight) / (float)contentHeight);
                 barHeight = Math.max(32, barHeight);
                 if (barHeight > scrollbarHeight) barHeight = scrollbarHeight;
                 
                 int barTop = (int)this.getScrollAmount() * (scrollbarHeight - barHeight) / (this.getMaxScroll()) + this.top;
                 if (barTop < this.top) barTop = this.top;
                 
                 // Draw Track (Dark background)
                context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0x80000000);
                
                // Draw Bar (Pale White as requested)
                context.fill(scrollbarX, barTop, scrollbarX + 6, barTop + barHeight, 0xA0FFFFFF);
            }
       }
        
        // Removed @Override because this method does not exist in 1.20.1
        protected void drawHeaderAndFooterSeparators(DrawContext context) {}
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Check if clicking scrollbar area
            int scrollbarX = this.getScrollbarPositionX();
            // Expanded hit area
            if (mouseX >= scrollbarX - 10) {
                // If we click here, we want to start dragging, even if we missed the bar itself (jump to position?)
                // Default behavior handles bar click. We want to ensure we capture focus/drag.
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            // Do NOT call super.mouseScrolled() to avoid immediate scrolling snap
            // We implement our own logic entirely
            this.targetScroll = this.getScrollAmount() - amount * 40.0; // Increased speed
            this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.getMaxScroll()));
            this.isSmoothScrolling = true;
            return true;
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            // Check if dragging scrollbar
            // Only if scrollbar is visible
            int contentHeight = this.getMaxScroll() + this.height;
            if (contentHeight > this.height) {
                 int scrollbarX = this.getScrollbarPositionX();
                 // Expand hit area slightly
                 // Check if mouseX is within a reasonable range of the scrollbar (e.g., within 20px) to allow easier grabbing
                 // Since getScrollbarPositionX is at the far right (this.width - 6), and bar width is 6.
                 // We want to grab if mouseX is > scrollbarX - 10.
                 if (draggingScrollbar || (mouseX >= scrollbarX - 10)) {
                     draggingScrollbar = true;
                     // Calculate new scroll amount based on mouse movement
                     double scrollFactor = (double)this.getMaxScroll() / (double)(this.height - 30); // Approximate bar height factor
                     
                     double barHeight = (double)(this.height * this.height) / (double)contentHeight;
                     barHeight = Math.max(32, barHeight);
                     double trackHeight = this.height - barHeight;
                     
                     if (trackHeight > 0) {
                         // We need to calculate how much the scroll changes per pixel of mouse movement
                         // The bar moves trackHeight pixels for getMaxScroll() content movement
                         // So 1 pixel of bar movement = getMaxScroll() / trackHeight pixels of content movement
                         double scrollPerPixel = this.getMaxScroll() / trackHeight;
                         
                         double newScroll = this.getScrollAmount() + deltaY * scrollPerPixel;
                         this.setScrollAmount(newScroll);
                         this.targetScroll = newScroll; // Sync target
                         this.isSmoothScrolling = false; // Disable smooth
                         return true;
                     }
                 }
            }
            return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        
        private boolean draggingScrollbar = false;
        
        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            draggingScrollbar = false;
            return super.mouseReleased(mouseX, mouseY, button);
        }
        
        public void setTargetScroll(double scroll) {
            this.targetScroll = Math.max(0, Math.min(scroll, this.getMaxScroll()));
            this.isSmoothScrolling = true;
        }
        
        public void updateSmoothScroll(float delta) {
            if (isSmoothScrolling) {
                double current = this.getScrollAmount();
                if (Math.abs(current - targetScroll) < 0.5) {
                    this.setScrollAmount(targetScroll);
                    isSmoothScrolling = false;
                } else {
                    this.setScrollAmount(MathHelper.lerp(0.5f * delta, current, targetScroll));
                }
            } else {
                targetScroll = this.getScrollAmount();
            }
        }
    }
    
    // --- Option Widgets ---
    
    private static class HeaderEntry extends OptionEntry {
        private final Text text;
        private final MinecraftClient client = MinecraftClient.getInstance();
        public HeaderEntry(Text text) { this.text = text; }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, text, x, y + 15, 0xFFFFFF);
            context.fill(x, y + 25, x + entryWidth, y + 26, 0xFF888888);
        }
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }

    private static class BooleanOptionEntry extends OptionEntry {
        private final ButtonWidget button;
        private final Text label;
        private boolean state;
        private final MinecraftClient client = MinecraftClient.getInstance();
        
        public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
            this.state = initial;
            this.label = Text.translatable(key, "");
            // Use user requested #B5F0C6 for ON
            this.button = ButtonWidget.builder(Text.translatable(state ? "options.on" : "options.off").setStyle(Style.EMPTY.withColor(state ? 0xFFB5F0C6 : 0xFFFFFFFF)), b -> {
                state = !state;
                b.setMessage(Text.translatable(state ? "options.on" : "options.off").setStyle(Style.EMPTY.withColor(state ? 0xFFB5F0C6 : 0xFFFFFFFF)));
                onToggle.accept(state);
            }).dimensions(0, 0, 100, 20).build();
        }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Draw text without shadow for cleaner look with dark colors if requested, but label is white here
            context.drawTextWithShadow(client.textRenderer, label, x, y + 6, 0xFFFFFF);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
    
    // ... (其他类没有变化，省略以节省空间，但我必须写入完整文件)
    
    private static class SliderEntry extends OptionEntry {
        private final SliderWidget slider;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        
        public SliderEntry(String key, float current, float min, float max, Consumer<Float> onChange) {
            this.label = Text.translatable(key);
            this.slider = new SliderWidget(0, 0, 100, 20, Text.literal(String.format("%.1f", current)), (current - min) / (max - min)) {
                @Override protected void updateMessage() { this.setMessage(Text.literal(String.format("%.1f", min + this.value * (max - min)))); }
                @Override protected void applyValue() { onChange.accept((float)(min + this.value * (max - min))); }
            };
        }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 6, 0xFFFFFF);
            slider.setX(x + entryWidth - 110);
            slider.setY(y + 2);
            slider.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(slider); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(slider); }
    }
    
    private static class ResetButtonEntry extends OptionEntry {
        public ResetButtonEntry(String key, Runnable action) {}
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {}
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }
    
    private static class ButtonActionEntry extends OptionEntry {
        private final ButtonWidget button;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        public ButtonActionEntry(String labelKey, String buttonKey, Runnable action) {
            this.label = Text.translatable(labelKey); 
            this.button = ButtonWidget.builder(Text.translatable(buttonKey), b -> action.run()).dimensions(0, 0, 100, 20).build();
        }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 6, 0xFFFFFF);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (this.button.mouseClicked(mouseX, mouseY, button)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }
    
    private static class NumericEntry extends OptionEntry {
        private final TextFieldWidget field;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        public NumericEntry(String key, float initial, Consumer<Float> onChange) {
            this.label = Text.translatable(key);
            this.field = new TextFieldWidget(client.textRenderer, 0, 0, 100, 20, Text.empty());
            this.field.setText(String.valueOf(initial));
            this.field.setChangedListener(s -> { try { onChange.accept(Float.parseFloat(s)); } catch(Exception ignored){} });
        }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 6, 0xFFFFFF);
            field.setX(x + entryWidth - 110);
            field.setY(y + 2);
            field.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(field); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(field); }
    }
    
    private static class HexColorEntry extends OptionEntry {
        private final TextFieldWidget field;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private int currentColor;

        public HexColorEntry(String key, int initial, Consumer<Integer> onChange) {
            this.label = Text.translatable(key);
            this.currentColor = initial;
            // Width 75. 75 + 5 + 20 = 100
            this.field = new TextFieldWidget(client.textRenderer, 0, 0, 75, 20, Text.empty());
            this.field.setMaxLength(6); // Limit to 6 chars
            this.field.setText(String.format("%06X", initial & 0xFFFFFF)); // Ensure 6 digit hex
            this.field.setChangedListener(s -> { 
                try { 
                    int val = (int)Long.parseLong(s, 16);
                    this.currentColor = val;
                    onChange.accept(val); 
                } catch(Exception ignored){} 
            });
        }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 6, 0xFFFFFF);
            
            // Layout: [Input Field (75)] [Spacing (5)] [Preview Box (20)]
            // Total Width: 100
            // Start X: x + entryWidth - 110
            
            int startX = x + entryWidth - 110;
            
            field.setX(startX);
            field.setY(y + 2);
            field.render(context, mouseX, mouseY, tickDelta);
            
            // Render Preview Box
            // Reduced by 3px total size (20 -> 17) as requested ("Too big by 3px")
            // Actually, if "Too big by 3px", maybe they mean height?
            // Assuming requested size is 17x17 (20-3) to fit nicely?
            // Or maybe 20px is physically larger than the text field visual height?
            // TextFieldWidget height is 20, but internal rendering might be smaller visually.
            // Let's reduce size to 17px and center it.
            
            int previewSize = 17;
            int previewX = startX + 80; // 75 + 5
            
            // Center vertically: (20 - 17) / 2 = 1.5 -> 1 or 2 offset
            int previewY = y + 2 + 1; 
            
            // Draw border (Black outer, White inner?) or just White border
            // User asked for "Add border for better visibility"
            // Let's do a 1px White border around the color.
            
            // Outer Border (White)
            context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            
            // Inner Color
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(field); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(field); }
    }
    
    private static class TextLabelEntry extends OptionEntry {
        private final Text text;
        private final MinecraftClient client = MinecraftClient.getInstance();
        public TextLabelEntry(String str) { this.text = Text.literal(str); }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, text, x, y + 10, 0xFFFFFF);
        }
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }
    
    private static class SpacerEntry extends OptionEntry {
        private final int height;
        public SpacerEntry(int height) { this.height = height; }
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Empty
        }
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }
    
    private static class KeybindEntry extends OptionEntry {
        private final Text label;
        private final BindingButton button;
        private final KeyBinding keyBinding;
        
        // Custom ButtonWidget that intercepts inputs when binding
        private class BindingButton extends ButtonWidget {
            public BindingButton(int x, int y, int width, int height, Text message, PressAction onPress) {
                super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            }
            
            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (isBinding()) {
                    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                        keyBinding.setBoundKey(InputUtil.UNKNOWN_KEY);
                    } else {
                        keyBinding.setBoundKey(InputUtil.fromKeyCode(keyCode, scanCode));
                    }
                    finishBinding();
                    return true; // Consume event
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (isBinding()) {
                    keyBinding.setBoundKey(InputUtil.Type.MOUSE.createFromCode(button));
                    finishBinding();
                    return true; // Consume event
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        private boolean binding = false;
        
        public KeybindEntry(String keyName, KeyBinding keyBinding) {
            this.label = Text.translatable(keyName);
            this.keyBinding = keyBinding;
            
            this.button = new BindingButton(0, 0, 100, 20, getBindingText(), b -> {
                setBinding(true);
            });
        }
        
        private void setBinding(boolean val) {
            this.binding = val;
            if (MinecraftClient.getInstance().currentScreen instanceof DamageConfigScreen s) {
                s.setBinding(val);
            }
            updateMessage();
        }
        
        private boolean isBinding() { return binding; }
        
        private void finishBinding() {
            setBinding(false);
            MinecraftClient.getInstance().options.write();
            KeyBinding.updateKeysByCode();
        }
        
        private Text getBindingText() {
            if (binding) return Text.literal("> <").setStyle(Style.EMPTY.withColor(0xFFFFFF55));
            if (keyBinding.isUnbound()) return Text.literal("<Unbound>").setStyle(Style.EMPTY.withColor(0xFFAAAAAA));
            return keyBinding.getBoundKeyLocalizedText();
        }
        
        private void updateMessage() {
            button.setMessage(getBindingText());
        }
        
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x, y + 6, 0xFFFFFF);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
}
