package damage.engine.client.gui;

import damage.engine.DamageEngineClient;
import damage.engine.DamageEngineConfig;
import damage.engine.hud.DamageHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public class DamageConfigScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    
    private CategoryListWidget categoryList;
    private ConfigOptionListWidget optionList;
    
    private final List<CategoryEntry> categories = new ArrayList<>();
    private int selectedCategoryIndex = 0;
    
    private double lastOptionScroll = 0;
    private double lastCategoryScroll = 0;
    
    private boolean expandDamageColors = false;
    private boolean expandInfoConfig = false;
    private boolean expandDamageDisplayConfig = false;
    private boolean expandDamageDisplayAppearance = false;
    private boolean expandInfoAppearance = false;
    
    private int resetButtonState = 0;
    private long resetButtonActionTime = 0;
    
    private boolean isNavigating = false;
    
    private boolean isBinding = false;
    
    private float smoothY = -1;

    private static long handCursor = 0;
    private static long arrowCursor = 0;
    private static long ibeamCursor = 0;
    private static long vResizeCursor = 0;
    private static long hResizeCursor = 0;

    public DamageConfigScreen(Screen parent) {
        super(Text.translatable("title.damage-engine.config"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    private final List<StyledButton> footerButtons = new ArrayList<>();

    private static class PlainTextButton extends ClickableWidget {
        private final Runnable onPress;
        private final int hoverColor;
        private final int defaultColor;
        private boolean forceHover = false;

        public PlainTextButton(int x, int y, int width, int height, Text message, Runnable onPress, int defaultColor, int hoverColor) {
            super(x, y, width, height, message);
            this.onPress = onPress;
            this.defaultColor = defaultColor;
            this.hoverColor = hoverColor;
        }
        
        public void setForceHover(boolean forceHover) {
            this.forceHover = forceHover;
        }

        @Override
        public boolean mouseClicked(Click click, boolean modifiers) {
            if (this.active && this.visible && click.button() == 0) {
                 double mouseX = MinecraftClient.getInstance().mouse.getX() * (double)MinecraftClient.getInstance().getWindow().getScaledWidth() / (double)MinecraftClient.getInstance().getWindow().getWidth();
                 double mouseY = MinecraftClient.getInstance().mouse.getY() * (double)MinecraftClient.getInstance().getWindow().getScaledHeight() / (double)MinecraftClient.getInstance().getWindow().getHeight();
                 
                 if (this.isMouseOver(mouseX, mouseY)) {
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    this.onPress.run();
                    return true;
                 }
            }
            return false;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int color = (isHovered() || forceHover) ? hoverColor : defaultColor;
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            this.appendDefaultNarrations(builder);
        }
        @Override
        public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
            if (MinecraftClient.getInstance().currentScreen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(soundManager);
            }
        }
    }
    
    static class StyledButton extends ClickableWidget {
        private final Runnable onPress;

        public StyledButton(int x, int y, int width, int height, Text message, Runnable onPress) {
            super(x, y, width, height, message);
            this.onPress = onPress;
        }
        
        @Override
        public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
            if (MinecraftClient.getInstance().currentScreen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else if (MinecraftClient.getInstance().currentScreen instanceof HudEditorScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(soundManager);
            }
        }
        
        @Override
        public boolean mouseClicked(Click click, boolean modifiers) {
            if (this.active && this.visible && click.button() == 0) {
                 double mouseX = MinecraftClient.getInstance().mouse.getX() * (double)MinecraftClient.getInstance().getWindow().getScaledWidth() / (double)MinecraftClient.getInstance().getWindow().getWidth();
                 double mouseY = MinecraftClient.getInstance().mouse.getY() * (double)MinecraftClient.getInstance().getWindow().getScaledHeight() / (double)MinecraftClient.getInstance().getWindow().getHeight();
                 
                 if (this.isMouseOver(mouseX, mouseY)) {
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    this.onPress.run();
                    return true;
                 }
            }
            return false;
        }
        
        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
            
            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
            context.fill(x, y, x + w, y + 1, borderColor); 
            context.fill(x, y + h - 1, x + w, y + h, borderColor); 
            context.fill(x, y, x + 1, y + h, borderColor); 
            context.fill(x + w - 1, y, x + w, y + h, borderColor); 
            
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            this.appendDefaultNarrations(builder);
        }
    }

    @Override
    protected void init() {
        if (handCursor == 0) handCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR);
        if (arrowCursor == 0) arrowCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
        if (ibeamCursor == 0) ibeamCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR);
        if (vResizeCursor == 0) vResizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR);
        if (hResizeCursor == 0) hResizeCursor = GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR);

        try {
            if (optionList != null) lastOptionScroll = optionList.getScrollY();
            if (categoryList != null) lastCategoryScroll = categoryList.getScrollY();

            this.clearChildren();
            categories.clear();
            footerButtons.clear();
            
            int leftWidth = 120;
            int footerY = this.height - 35;
            int topY = 0; 
            
            int listHeight = footerY; 
            
            categoryList = new CategoryListWidget(this.client, leftWidth, listHeight, topY, 25);
            categoryList.setX(0);
            
            categoryList.addEntryPublic(new CategorySpacerEntry(this)); 
            
            optionList = new ConfigOptionListWidget(this.client, this.width - leftWidth, listHeight, topY, 26);
            optionList.setX(leftWidth);
            
            initCategories();
            initOptionEntries();
            
            if (categoryList != null) categoryList.setScrollY(lastCategoryScroll);
            if (optionList != null) optionList.setScrollY(lastOptionScroll);
            
            this.addDrawableChild(categoryList);
            this.addDrawableChild(optionList);
            
            int buttonWidth = 80;
            int buttonY = footerY + 5;
            
            StyledButton saveBtn = new StyledButton(this.width - buttonWidth - 10, buttonY, buttonWidth, 20, Text.translatable("button.damage-engine.save_close").withColor(0xFFB7F3C8), () -> {
                config.save();
                GLFW.glfwSetCursor(client.getWindow().getHandle(), arrowCursor);
                this.client.setScreen(parent);
            });
            footerButtons.add(saveBtn);
            this.addDrawableChild(saveBtn);
            
            StyledButton cancelBtn = new StyledButton(this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20, Text.translatable("gui.cancel").withColor(0xFFFC887E), () -> {
                config.load();
                GLFW.glfwSetCursor(client.getWindow().getHandle(), arrowCursor);
                this.client.setScreen(parent);
            });
            footerButtons.add(cancelBtn);
            this.addDrawableChild(cancelBtn);
            
            int onColor = 0xFFB5F0C6;
            int offColor = 0xFFFC887E;
            final StyledButton[] previewBtnRef = new StyledButton[1];
            previewBtnRef[0] = new StyledButton(10, buttonY, 80, 20, Text.translatable("text.damage-engine.preview").append(": ").append(Text.literal(config.previewEnabled ? "ON" : "OFF").withColor(config.previewEnabled ? onColor : offColor)), () -> {
                config.previewEnabled = !config.previewEnabled;
                previewBtnRef[0].setMessage(Text.translatable("text.damage-engine.preview").append(": ").append(Text.literal(config.previewEnabled ? "ON" : "OFF").withColor(config.previewEnabled ? onColor : offColor)));
            });
            footerButtons.add(previewBtnRef[0]);
            this.addDrawableChild(previewBtnRef[0]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void refreshOptions() {
        if (optionList == null) return;
        double scroll = optionList.getScrollY();
        optionList.clearEntriesPublic();
        initOptionEntries();
        optionList.setScrollY(scroll);
    }
    
    private void initCategories() {
        categories.clear();
        categoryList.clearEntriesPublic();
        
        categoryList.addEntryPublic(new CategorySpacerEntry(this)); 
        
        addCategory("category.damage_engine.general", 0);
        addCategory("category.damage_engine.appearance", 1);
        addCategory("category.damage_engine.keybinds", 2);
        addCategory("category.damage_engine.other", 3);
    }

    private void initOptionEntries() {
        addHeader("category.damage_engine.general");
        
        addOption(new BooleanOptionEntry("option.damage-engine.showHud", config.showHud, v -> config.showHud = v));
        
        addOption(new ExpandableHeaderEntry("option.damage-engine.info_config", expandInfoConfig, v -> {
            expandInfoConfig = v;
            refreshOptions();
        }));
        
        if (expandInfoConfig) {
            addOption(new BooleanOptionEntry("option.damage-engine.showInfo", config.showInfo, v -> config.showInfo = v));
            addOption(new NumericEntry("option.damage-engine.infoTrackTime", config.infoTrackTime, v -> config.infoTrackTime = v));
        }
        
        addOption(new ExpandableHeaderEntry("option.damage-engine.damage_display_config", expandDamageDisplayConfig, v -> {
            expandDamageDisplayConfig = v;
            refreshOptions();
        }));
        
        if (expandDamageDisplayConfig) {
            addOption(new BooleanOptionEntry("option.damage-engine.resetEnabled", config.resetEnabled, v -> config.resetEnabled = v));
            addOption(new NumericEntry("option.damage-engine.resetTime", config.resetTime, v -> config.resetTime = v));
            addOption(new BooleanOptionEntry("option.damage-engine.showProgressBar", config.showProgressBar, v -> config.showProgressBar = v));
            addOption(new BooleanOptionEntry("option.damage-engine.showDamageHistory", config.showDamageHistory, v -> config.showDamageHistory = v));
            addOption(new NumericEntry("option.damage-engine.historyDisappearanceTime", config.historyDisappearanceTime, v -> config.historyDisappearanceTime = v));
            addOption(new IntegerSliderEntry("option.damage-engine.historyLimit", config.historyLimit, 1, 50, v -> config.historyLimit = v, true));
        }
        
        addHeader("category.damage_engine.appearance");
        addOption(new ButtonActionEntry("option.damage-engine.edit_pos_label", "button.damage-engine.adjust", () -> {
            playClickSound();
            this.client.setScreen(new HudEditorScreen(this));
        }));

        addOption(new ExpandableHeaderEntry("option.damage-engine.info_appearance", expandInfoAppearance, v -> {
            expandInfoAppearance = v;
            refreshOptions();
        }));
        
        if (expandInfoAppearance) {
            addOption(new HexColorEntry("option.damage-engine.infoBarColor", config.infoBarColor, v -> config.infoBarColor = v));
            addOption(new HexColorEntry("option.damage-engine.infoBackgroundColor", config.infoBackgroundColor, v -> config.infoBackgroundColor = v));
            addOption(new IntegerSliderEntry("option.damage-engine.infoBackgroundOpacity", config.infoBackgroundOpacity, 0, 100, v -> config.infoBackgroundOpacity = v, true));
        }
        
        addOption(new ExpandableHeaderEntry("option.damage-engine.damage_display_appearance", expandDamageDisplayAppearance, v -> {
            expandDamageDisplayAppearance = v;
            refreshOptions();
        }));
        
        if (expandDamageDisplayAppearance) {
            addOption(new HexColorEntry("option.damage-engine.progressBarColor", config.progressBarColor, v -> config.progressBarColor = v));
            addOption(new HexColorEntry("option.damage-engine.comboColor", config.comboColor, v -> config.comboColor = v));
            addOption(new HexColorEntry("option.damage-engine.historyColor", config.historyColor, v -> config.historyColor = v));
            addOption(new HexColorEntry("option.damage-engine.critColor", config.critColor, v -> config.critColor = v));
            
            addOption(new ExpandableHeaderEntry("option.damage-engine.colors", expandDamageColors, v -> {
                expandDamageColors = v;
                refreshOptions();
            }));
            
            if (expandDamageColors) {
                 config.damageThresholds.sort((a, b) -> Float.compare(a.threshold, b.threshold));
                 
                 for (DamageEngineConfig.DamageThreshold dt : new ArrayList<>(config.damageThresholds)) {
                     addOption(new DamageThresholdEntry(dt, () -> {
                         config.damageThresholds.remove(dt);
                         refreshOptions();
                     }));
                 }
                 
                 addOption(new AddButtonEntry(() -> {
                     float nextVal = 0f;
                     if (!config.damageThresholds.isEmpty()) {
                         nextVal = config.damageThresholds.get(config.damageThresholds.size() - 1).threshold + 50f;
                     }
                     config.damageThresholds.add(new DamageEngineConfig.DamageThreshold(nextVal, 0xFFFFFFFF));
                     refreshOptions();
                 }));
            }
        }
        
        addHeader("category.damage_engine.keybinds");
        
        if (DamageEngineClient.configKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.config", DamageEngineClient.configKeyBinding));
        }
        if (DamageEngineClient.toggleHudKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.toggle_hud", DamageEngineClient.toggleHudKeyBinding));
        }
        if (DamageEngineClient.clearDamageKeyBinding != null) {
            addOption(new KeybindEntry("key.damage_engine.clear_damage", DamageEngineClient.clearDamageKeyBinding));
        }
        
        addHeader("category.damage_engine.other");
        addOption(new BooleanOptionEntry("option.damage-engine.hideOnF1", config.hideOnF1, v -> config.hideOnF1 = v));
        addOption(new BooleanOptionEntry(Text.translatable("option.damage-engine.debugMode").withColor(0xFFFBFB54), config.debugMode, v -> config.debugMode = v));
        
        addOption(new ResetButtonEntry("option.damage-engine.reset", () -> {
            config.resetToDefaults();
            DamageEngineClient.resetKeyBindings();
            KeyBinding.updateKeysByCode();
            this.client.options.write();
            refreshOptions();
        }));
        
        addOption(new SpacerEntry(30));
        addOption(new SpacerEntry(30));
    }
    
    private void addCategory(String key, int id) {
        CategoryEntry cat = new CategoryEntry(this, Text.translatable(key), id, 0);
        categoryList.addEntryPublic(cat);
        categories.add(cat);
    }
    
    private void addHeader(String key) {
        int currentIndex = optionList.children().size();
        
        String keyString = Text.translatable(key).getString();
        
        for (CategoryEntry cat : categories) {
            if (cat.text.getString().equals(keyString)) {
                cat.targetIndex = currentIndex;
                break;
            }
        }
        
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
            for (int i = 0; i < cat.targetIndex; i++) {
                y += 26;
            }
            isNavigating = true;
            optionList.setTargetScroll(y);
        }
    }
    


    public void setBinding(boolean binding) {
        this.isBinding = binding;
    }
    
    public void playClickSound() {
        try {
             PositionedSoundInstance sound = PositionedSoundInstance.ambient(SoundEvents.UI_BUTTON_CLICK.value());
             
             try {
                 java.lang.reflect.Field volField = net.minecraft.client.sound.AbstractSoundInstance.class.getDeclaredField("volume");
                 volField.setAccessible(true);
                 volField.setFloat(sound, 0.15F);
             } catch (Exception ignored) {
             }
             
             MinecraftClient.getInstance().getSoundManager().play(sound);
        } catch (Exception e) {
             DamageEngineClient.LOGGER.error("Failed to play sound", e);
        }
    }

        @Override
        public boolean keyPressed(KeyInput keyInput) {
            int keyCode = keyInput.key();
            int scanCode = keyInput.scancode();
            int modifiers = keyInput.modifiers();
            
            if (isBinding) {
                if (optionList != null) {
                    for (OptionEntry entry : optionList.children()) {
                        if (entry instanceof KeybindEntry ke) {
                            if (ke.isBinding()) {
                                if (ke.button.keyPressed(keyCode, scanCode, modifiers)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    setBinding(false);
                    return true;
                }
            }
            return super.keyPressed(keyInput);
        }

    @Override
    public boolean mouseClicked(Click click, boolean modifiers) {
        if (optionList != null) {
            for (OptionEntry entry : optionList.children()) {
                for (Element child : entry.children()) {
                    if (child instanceof TextFieldWidget tf) {
                        tf.setFocused(false);
                    }
                }
            }
        }
        
        if (isBinding) {
             if (optionList != null) {
                 for (OptionEntry entry : optionList.children()) {
                     if (entry instanceof KeybindEntry ke && ke.isBinding()) {
                         ke.bindMouse(click.button());
                         return true;
                     }
                 }
             }
             setBinding(false);
             return true;
         }
        return super.mouseClicked(click, modifiers);
    }

    @Override
    public void close() {
        GLFW.glfwSetCursor(client.getWindow().getHandle(), arrowCursor);
        super.close();
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x40000000); 
        
        if (categoryList != null) categoryList.render(context, mouseX, mouseY, delta);
        if (optionList != null) {
            optionList.render(context, mouseX, mouseY, delta);
            optionList.updateSmoothScroll(delta);
        }
        
        if (optionList != null && !isNavigating) {
            int hoveredIndex = optionList.getHoveredEntryIndex(mouseX, mouseY);
            if (hoveredIndex != -1) {
                 int bestCatId = -1;
                 for (CategoryEntry cat : categories) {
                     if (cat.targetIndex <= hoveredIndex) {
                         bestCatId = cat.id;
                     } else {
                         break;
                     }
                 }
                 if (bestCatId != -1) {
                     selectedCategoryIndex = bestCatId;
                 }
            }
        }
        
        int leftWidth = 120;
        int footerY = this.height - 35;
        
        context.fill(leftWidth - 1, 0, leftWidth, footerY, 0xFF555555);
        context.fill(0, footerY - 1, this.width, footerY, 0xFF555555);
        
        context.fill(0, 25, leftWidth, 26, 0xFF555555);
        
        for (StyledButton btn : footerButtons) {
            btn.render(context, mouseX, mouseY, delta);
        }
        
        context.drawTextWithShadow(this.textRenderer, Text.translatable("category.damage_engine"), 10, 10, 0xFFFFFFFF);
        
        if (config.previewEnabled) {
            new DamageHud().renderPreview(context, this.width/2, this.height/2);
        }
        
        updateCursor(mouseX, mouseY);
    }
    
    private void updateCursor(double mouseX, double mouseY) {
        if (client == null) return;
        long windowHandle = client.getWindow().getHandle();
        long cursor = arrowCursor;
        
        boolean found = false;
        
        if (optionList != null) {
             if (optionList.isScrolling()) {
                 cursor = vResizeCursor;
                 found = true;
             } else if (mouseX >= optionList.getScrollbarX() && mouseX <= optionList.getScrollbarX() + 6 && mouseY >= optionList.getY() && mouseY <= optionList.getBottom()) {
                 cursor = handCursor;
                 found = true;
             }
        }
        
        if (!found) {
            for (StyledButton btn : footerButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    cursor = handCursor;
                    found = true;
                    break;
                }
            }
        }
        
        if (!found && categoryList != null && categoryList.isMouseOver(mouseX, mouseY)) {
             int index = categoryList.getHoveredEntryIndex(mouseX, mouseY);
             if (index != -1) {
                 cursor = handCursor;
                 found = true;
             }
        }
        
        if (!found && optionList != null && optionList.isMouseOver(mouseX, mouseY)) {
             int index = optionList.getHoveredEntryIndex(mouseX, mouseY);
             if (index != -1) {
                 OptionEntry entry = optionList.children().get(index);
                 if (entry instanceof BooleanOptionEntry bo) {
                     if (bo.button.isMouseOver(mouseX, mouseY)) { cursor = handCursor; found = true; }
                 } else if (entry instanceof ExpandableHeaderEntry) {
                     cursor = handCursor; found = true;
                } else if (entry instanceof AddButtonEntry abe) {
                     if (abe.button.isMouseOver(mouseX, mouseY)) { cursor = handCursor; found = true; }
                 } else if (entry instanceof ButtonActionEntry bae) {
                     if (bae.button.isMouseOver(mouseX, mouseY)) { cursor = handCursor; found = true; }
                 } else if (entry instanceof ResetButtonEntry rbe) {
                     if (rbe.button.isMouseOver(mouseX, mouseY)) { cursor = handCursor; found = true; }
                 } else if (entry instanceof KeybindEntry ke) {
                     if (ke.button.isMouseOver(mouseX, mouseY)) { cursor = handCursor; found = true; }
                 } else if (entry instanceof DamageThresholdEntry dte) {
                     if (dte.delBtn.isMouseOver(mouseX, mouseY)) { cursor = handCursor; found = true; }
                     else if (dte.valField.isMouseOver(mouseX, mouseY) || dte.colField.isMouseOver(mouseX, mouseY)) { cursor = ibeamCursor; found = true; }
                 } else if (entry instanceof HexColorEntry hce) {
                     if (hce.field.isMouseOver(mouseX, mouseY)) { cursor = ibeamCursor; found = true; }
                 } else if (entry instanceof NumericEntry ne) {
                     if (ne.field.isMouseOver(mouseX, mouseY)) { cursor = ibeamCursor; found = true; }
                 } else if (entry instanceof IntegerSliderEntry ise) {
                     if (ise.slider.isMouseOver(mouseX, mouseY)) { cursor = hResizeCursor; found = true; }
                 }
             }
        }
        
        GLFW.glfwSetCursor(windowHandle, cursor);
    }
    
    
    private static class CategorySpacerEntry extends CategoryEntry {
        public CategorySpacerEntry(DamageConfigScreen screen) {
            super(screen, Text.empty(), -1, -1);
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {}
        @Override public boolean mouseClicked(Click click, boolean modifiers) { return false; }
    }

    private static class CategoryEntry extends ElementListWidget.Entry<CategoryEntry> {
        private final Text text;
        private final int id;
        private int targetIndex;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private final DamageConfigScreen screen;
        
        public CategoryEntry(DamageConfigScreen screen, Text text, int id, int targetIndex) { 
            this.screen = screen;
            this.text = text; 
            this.id = id; 
            this.targetIndex = targetIndex;
        }
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int index = screen.categoryList.children().indexOf(this);
            if (index == -1) return;
            
            int y = screen.categoryList.getRowTopPublic(index);
            int x = screen.categoryList.getRowLeft();
            
            boolean isSelected = screen.selectedCategoryIndex == id;
            
            if (isSelected) {
                if (screen.smoothY == -1) {
                    screen.smoothY = y;
                } else {
                    screen.smoothY += (y - screen.smoothY) * 0.4f * delta;
                }
                context.fill(x, (int)screen.smoothY + 2, x + 2, (int)screen.smoothY + 25 - 2, 0xFFFFFFFF);
            }

            context.drawTextWithShadow(client.textRenderer, text, x + 10, y + 8, 0xFFFFFFFF);
        }
        
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
        
        @Override
        public boolean mouseClicked(Click click, boolean modifiers) {
            screen.selectCategory(id);
            screen.playClickSound();
            return true;
        }
    }
    
    private class CategoryListWidget extends ElementListWidget<CategoryEntry> {
        public CategoryListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, 25);
        }
        
        @Override
        protected void drawMenuListBackground(DrawContext context) {
        }
        public void clearEntriesPublic() { this.clearEntries(); }
        
        public int getRowTopPublic(int index) {
            return this.getY() + index * this.itemHeight - (int)this.getScrollY();
        }
        
        public int getHoveredEntryIndex(double mouseX, double mouseY) {
             if (mouseX < this.getX() || mouseX > this.getRight() || mouseY < this.getY() || mouseY > this.getBottom()) {
                 return -1;
             }
             int i = MathHelper.floor(mouseY - (double)this.getY() - 0 + this.getScrollY() - 4.0D);
             int index = i / 25;
             if (index >= 0 && index < this.getEntryCount()) {
                 return index;
             }
             return -1;
        }
        
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
             this.enableScissor(context);
             this.renderList(context, mouseX, mouseY, delta);
             context.disableScissor();
        }

        @Override public int getRowWidth() { return 100; }
        public void addEntryPublic(CategoryEntry entry) { this.addEntry(entry); }
        @Override public int getRowLeft() { return 0; }
        @Override protected void drawHeaderAndFooterSeparators(DrawContext context) {}
    }
    
    private abstract static class OptionEntry extends ElementListWidget.Entry<OptionEntry> {
        private float hoverAlpha = 0;
        
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            DamageConfigScreen screen = (DamageConfigScreen) MinecraftClient.getInstance().currentScreen;
            if (screen == null || screen.optionList == null) return;
            
            int index = screen.optionList.children().indexOf(this);
            if (index == -1) return;
            
            int y = screen.optionList.getRowTopPublic(index);
            int x = screen.optionList.getRowLeft();
            int entryWidth = screen.optionList.getRowWidth();
            int entryHeight = 25; 
            
            boolean isHovered = mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY < y + entryHeight;
            
            if (isHovered && shouldHighlight()) {
                hoverAlpha += (0.15f - hoverAlpha) * 0.4f * delta;
                int alpha = (int)(hoverAlpha * 255);
                context.fill(x - 200, y, x + entryWidth + 200, y + entryHeight, (alpha << 24) | 0xFFFFFF);
            } else {
                hoverAlpha += (0.0f - hoverAlpha) * 0.4f * delta;
                if (hoverAlpha > 0.01f) {
                    int alpha = (int)(hoverAlpha * 255);
                    context.fill(x - 200, y, x + entryWidth + 200, y + entryHeight, (alpha << 24) | 0xFFFFFF);
                }
            }
            renderContent(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
        }
        
        public abstract void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta);
        
        protected boolean shouldHighlight() { return true; }
        
        @Override
        public boolean mouseClicked(Click click, boolean modifiers) {
            double mouseX = MinecraftClient.getInstance().mouse.getX() * (double)MinecraftClient.getInstance().getWindow().getScaledWidth() / (double)MinecraftClient.getInstance().getWindow().getWidth();
            double mouseY = MinecraftClient.getInstance().mouse.getY() * (double)MinecraftClient.getInstance().getWindow().getScaledHeight() / (double)MinecraftClient.getInstance().getWindow().getHeight();
            
            for (Element child : this.children()) {
                if (child instanceof TextFieldWidget tf) {
                    if (tf.isMouseOver(mouseX, mouseY)) {
                        tf.setFocused(true);
                        return true;
                    } else {
                        tf.setFocused(false);
                    }
                } else if (child.mouseClicked(click, modifiers)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean mouseReleased(Click click) {
            for (Element child : this.children()) {
                if (child.mouseReleased(click)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean mouseDragged(Click click, double deltaX, double deltaY) {
            for (Element child : this.children()) {
                if (child.mouseDragged(click, deltaX, deltaY)) {
                    return true;
                }
            }
            return false;
        }
                  
        @Override
        public boolean keyPressed(KeyInput keyInput) {
            for (Element child : this.children()) {
                if (child.keyPressed(keyInput)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean charTyped(CharInput charInput) {
            for (Element child : this.children()) {
                if (child.charTyped(charInput)) {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public boolean keyReleased(KeyInput keyInput) {
             for (Element child : this.children()) {
                if (child.keyReleased(keyInput)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    private class ConfigOptionListWidget extends ElementListWidget<OptionEntry> {
        private double targetScroll = 0;
        private boolean isSmoothScrolling = false;
        private boolean scrolling = false;

        public ConfigOptionListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, 26);
        }
        
        public boolean isScrolling() { return scrolling; }
        
        @Override
        public boolean mouseClicked(Click click, boolean modifiers) {
            double mouseX = MinecraftClient.getInstance().mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
            double mouseY = MinecraftClient.getInstance().mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
            
            if (mouseX >= getScrollbarX() && mouseX <= getScrollbarX() + 6 && mouseY >= this.getY() && mouseY <= this.getBottom()) {
                 scrolling = true;
            }
            return super.mouseClicked(click, modifiers);
        }
        
        @Override
        public boolean mouseReleased(Click click) {
            scrolling = false;
            return super.mouseReleased(click);
        }
        
        @Override
        protected void drawMenuListBackground(DrawContext context) {
        }
        
        public int getRowTopPublic(int index) {
             return this.getY() + index * this.itemHeight - (int)this.getScrollY();
        }

        public int getHoveredEntryIndex(double mouseX, double mouseY) {
             if (mouseX < this.getX() || mouseX > this.getRight() || mouseY < this.getY() || mouseY > this.getBottom()) {
                 return -1;
             }
             int i = MathHelper.floor(mouseY - (double)this.getY() - 0 + this.getScrollY() - 4.0D);
             int index = i / 26;
             if (index >= 0 && index < this.getEntryCount()) {
                 return index;
             }
             return -1;
        }
        
        public void clearEntriesPublic() { this.clearEntries(); }
        @Override public int getRowWidth() { return this.width - 20; } 
        public void addEntryPublic(OptionEntry entry) { this.addEntry(entry); }

        @Override
        public int getRowLeft() { return this.getX() + 10; }
        public int getScrollbarX() { return this.getRight() - 6; }
        
        @Override
        protected void drawHeaderAndFooterSeparators(DrawContext context) {}
        
        @Override
        public boolean mouseDragged(Click click, double deltaX, double deltaY) {
             return super.mouseDragged(click, deltaX, deltaY);
        }
        
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
             this.enableScissor(context);
             this.renderList(context, mouseX, mouseY, delta);
             context.disableScissor();
             
             int scrollbarX = this.getScrollbarX();
             int scrollbarY = this.getY();
             int scrollbarHeight = this.getHeight();
             int contentHeight = this.getMaxScrollY() + this.getHeight();
             
             if (contentHeight > this.getHeight()) {
                 int barHeight = (int)((float)(this.getHeight() * this.getHeight()) / (float)contentHeight);
                 barHeight = Math.max(32, barHeight);
                 if (barHeight > this.getHeight()) barHeight = this.getHeight();
                 
                 int barTop = (int)this.getScrollY() * (this.getHeight() - barHeight) / (this.getMaxScrollY()) + this.getY();
                 if (barTop < this.getY()) barTop = this.getY();
                 
                 context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0x80000000);
                 context.fill(scrollbarX, barTop, scrollbarX + 6, barTop + barHeight, 0xA0FFFFFF);
            }
       }

        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            DamageConfigScreen.this.isNavigating = false;
            this.targetScroll = this.getScrollY() - verticalAmount * 80.0;
            this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.getMaxScrollY()));
            this.isSmoothScrolling = true;
            return true;
        }
        
        public void setTargetScroll(double scroll) {
            this.targetScroll = Math.max(0, Math.min(scroll, this.getMaxScrollY()));
            this.isSmoothScrolling = true;
        }
        
        public void updateSmoothScroll(float delta) {
            if (isSmoothScrolling) {
                double current = this.getScrollY();
                if (Math.abs(current - targetScroll) < 0.5) {
                    this.setScrollY(targetScroll);
                    isSmoothScrolling = false;
                    DamageConfigScreen.this.isNavigating = false;
                } else {
                    this.setScrollY(MathHelper.lerp(0.5f * delta, current, targetScroll));
                }
            } else {
                targetScroll = this.getScrollY();
                DamageConfigScreen.this.isNavigating = false;
            }
        }
    }
    
    
    private static class HeaderEntry extends OptionEntry {
        private final Text text;
        private final MinecraftClient client = MinecraftClient.getInstance();
        public HeaderEntry(Text text) { this.text = text; }
        @Override
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawCenteredTextWithShadow(client.textRenderer, text, x + entryWidth / 2, y + 9, 0xFFFFFFFF);
        }
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }

    private static class BooleanOptionEntry extends OptionEntry {
        private final StyledButton button;
        private final Text label;
        private final Text hint;
        private boolean state;
        private final MinecraftClient client = MinecraftClient.getInstance();
        
        public BooleanOptionEntry(String key, boolean initial, Consumer<Boolean> onToggle) {
            this(Text.translatable(key), initial, onToggle,
                "option.damage-engine.resetEnabled".equals(key) ? Text.translatable("hint.damage-engine.resetEnabled")
                    : "option.damage-engine.showInfo".equals(key) ? Text.translatable("hint.damage-engine.showInfo")
                    : null);
        }

        public BooleanOptionEntry(Text label, boolean initial, Consumer<Boolean> onToggle) {
            this(label, initial, onToggle, null);
        }
        
        public BooleanOptionEntry(Text label, boolean initial, Consumer<Boolean> onToggle, Text hint) {
            this.state = initial;
            this.label = label;
            this.hint = hint;
            int onColor = 0xFFB5F0C6;
            int offColor = 0xFFFC887E;
            
            final StyledButton[] btnRef = new StyledButton[1];
            btnRef[0] = new StyledButton(0, 0, 100, 20, Text.translatable(state ? "options.on" : "options.off").withColor(state ? onColor : offColor), () -> {
                state = !state;
                btnRef[0].setMessage(Text.translatable(state ? "options.on" : "options.off").withColor(state ? onColor : offColor));
                onToggle.accept(state);
            });
            this.button = btnRef[0];
        }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (hint != null) {
                context.drawTextWithShadow(client.textRenderer, label, x, y + 4, 0xFFFFFFFF);
                context.drawTextWithShadow(client.textRenderer, hint, x, y + 14, 0xFFA0A0A0);
            } else {
                context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFFFF);
            }
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false); 
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
    
    private static class StyledSliderWidget extends SliderWidget {
        private final boolean showValue;
        private final boolean soundOnPress;
        private final boolean soundOnRelease;
        private boolean pendingReleaseSound;
        private double valueBeforeInteraction;
        
        public StyledSliderWidget(int x, int y, int width, int height, Text text, double value, boolean showValue, boolean soundOnPress, boolean soundOnRelease) {
            super(x, y, width, height, text, value);
            this.showValue = showValue;
            this.soundOnPress = soundOnPress;
            this.soundOnRelease = soundOnRelease;
        }
        
        @Override
        protected void updateMessage() {} 
        
        @Override
        protected void applyValue() {} 

        private void playConfiguredClickSound() {
            if (MinecraftClient.getInstance().currentScreen instanceof DamageConfigScreen s) {
                s.playClickSound();
            } else {
                super.playDownSound(MinecraftClient.getInstance().getSoundManager());
            }
        }
        
        @Override
        public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
             if (!soundOnPress) return;
             playConfiguredClickSound();
        }
        
        @Override
        public boolean mouseClicked(Click click, boolean modifiers) {
            boolean handled = super.mouseClicked(click, modifiers);
            if (handled && soundOnRelease) {
                pendingReleaseSound = true;
                valueBeforeInteraction = this.value;
            }
            return handled;
        }
        
        @Override
        public boolean mouseReleased(Click click) {
            boolean handled = super.mouseReleased(click);
            if (soundOnRelease && pendingReleaseSound) {
                pendingReleaseSound = false;
                if (Math.abs(this.value - valueBeforeInteraction) > 1.0E-9) {
                    playConfiguredClickSound();
                }
            }
            return handled;
        }
        
        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            
            context.fill(x, y, x + w, y + h, 0x20000000);
            
            int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(x, y, x + w, y + 1, borderColor); 
            context.fill(x, y + h - 1, x + w, y + h, borderColor); 
            context.fill(x, y, x + 1, y + h, borderColor); 
            context.fill(x + w - 1, y, x + w, y + h, borderColor); 
            
            int handleWidth = 8;
            int handleX = x + (int)(this.value * (double)(w - handleWidth));
            int handleY = y;
            
            int handleBorderColor = (isHovered() || isFocused()) ? 0xFFFFFFFF : 0xFFCCCCCC;
            
            context.fill(handleX, handleY, handleX + handleWidth, handleY + h, 0xFF000000); 
            
            context.fill(handleX, handleY, handleX + handleWidth, handleY + 1, handleBorderColor);
            context.fill(handleX, handleY + h - 1, handleX + handleWidth, handleY + h, handleBorderColor);
            context.fill(handleX, handleY, handleX + 1, handleY + h, handleBorderColor);
            context.fill(handleX + handleWidth - 1, handleY, handleX + handleWidth, handleY + h, handleBorderColor);
            
            if (showValue) {
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
            }
        }
    }

    private static class IntegerSliderEntry extends OptionEntry {
        private final StyledSliderWidget slider;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        
        public IntegerSliderEntry(String key, int current, int min, int max, Consumer<Integer> onChange, boolean soundOnReleaseOnly) {
            this.label = Text.translatable(key);
            float minF = (float)min;
            float maxF = (float)max;
            float currentF = (float)current;
            
            this.slider = new StyledSliderWidget(0, 0, 100, 20, Text.literal(String.valueOf(current)), (currentF - minF) / (maxF - minF), true, !soundOnReleaseOnly, soundOnReleaseOnly) {
                @Override protected void updateMessage() { 
                    int val = (int)Math.round(minF + this.value * (maxF - minF));
                    this.setMessage(Text.literal(String.valueOf(val))); 
                }
                @Override protected void applyValue() { 
                    int val = (int)Math.round(minF + this.value * (maxF - minF));
                    onChange.accept(val); 
                }
            };
        }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFFFF);
            slider.setX(x + entryWidth - 110);
            slider.setY(y + 2);
            slider.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(slider); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(slider); }
    }
    
    private class ResetButtonEntry extends OptionEntry {
        private final StyledButton button;
        private final Runnable onReset;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();

        public ResetButtonEntry(String key, Runnable onReset) {
            this.label = Text.translatable(key);
            this.onReset = onReset;
            int color = 0xFFFC887E; 
            Text btnText = Text.translatable("gui.reset");
            
            if (resetButtonState == 1) {
                 btnText = Text.translatable("gui.confirm").append("?");
            } else if (resetButtonState == 2) {
                 color = 0xFFB5F0C6;
                 btnText = Text.translatable("text.damage-engine.reset_done");
            }
            
            this.button = new StyledButton(0, 0, 100, 20, btnText.copy().withColor(color), () -> handleClick());
        }
        
        private void handleClick() {
            if (resetButtonState == 0) {
                resetButtonState = 1;
                button.setMessage(Text.translatable("gui.confirm").append("?").withColor(0xFFFC887E));
                resetButtonActionTime = System.currentTimeMillis();
            } else if (resetButtonState == 1) {
                resetButtonState = 2;
                resetButtonActionTime = System.currentTimeMillis();
                onReset.run();
                button.setMessage(Text.translatable("text.damage-engine.reset_done").withColor(0xFFB5F0C6));
            }
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            if (resetButtonState == 2) {
                if (System.currentTimeMillis() - resetButtonActionTime > 3000) {
                    resetButtonState = 0;
                    button.setMessage(Text.translatable("gui.reset").withColor(0xFFFC887E));
                }
            } else if (resetButtonState == 1) {
                if (System.currentTimeMillis() - resetButtonActionTime > 5000) {
                    resetButtonState = 0;
                    button.setMessage(Text.translatable("gui.reset").withColor(0xFFFC887E));
                }
            }
            
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFC887E);
            
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
    
    private static class ExpandableHeaderEntry extends OptionEntry {
        private final PlainTextButton button;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private boolean expanded;
        private final Consumer<Boolean> onToggle;
        
        public ExpandableHeaderEntry(String key, boolean expanded, Consumer<Boolean> onToggle) {
            this.label = Text.translatable(key);
            this.expanded = expanded;
            this.onToggle = onToggle;
            
            this.button = new PlainTextButton(0, 0, 20, 20, Text.literal(expanded ? "-" : "+"), () -> onToggle.accept(!expanded), 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            boolean isHovered = hovered || (mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight);
            int color = isHovered ? 0xFFFBFB54 : 0xFFFFFFFF;
            
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, color);
            
            button.setX(x + entryWidth - 25);
            button.setY(y + 2);
            button.setFocused(false);
            button.setForceHover(isHovered);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override
        public boolean mouseClicked(Click click, boolean modifiers) {
             if (this.button.mouseClicked(click, modifiers)) return true;
             
             if (this.onToggle != null) {
                 this.onToggle.accept(!expanded);
                 if (MinecraftClient.getInstance().currentScreen instanceof DamageConfigScreen s) {
                     s.playClickSound();
                 }
                 return true;
             }
             return false;
        }
        
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }

    private static class DamageThresholdEntry extends OptionEntry {
        private final TextFieldWidget valField;
        private final TextFieldWidget colField;
        private final PlainTextButton delBtn;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private int currentColor;
        
        public DamageThresholdEntry(DamageEngineConfig.DamageThreshold dt, Runnable onDelete) {
            this.currentColor = dt.color;
            
            this.valField = new TextFieldWidget(client.textRenderer, 0, 0, 40, 20, Text.empty());
            this.valField.setDrawsBackground(false);
            this.valField.setText(String.format("%.0f", dt.threshold));
            this.valField.setChangedListener(s -> {
                try { dt.threshold = Float.parseFloat(s); } catch(Exception ignored){}
            });
            
            this.colField = new TextFieldWidget(client.textRenderer, 0, 0, 55, 20, Text.empty());
            this.colField.setDrawsBackground(false);
            this.colField.setMaxLength(7);
            this.colField.setText("#" + String.format("%06X", dt.color & 0xFFFFFF));
            this.colField.setChangedListener(s -> {
                try { 
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int v = (int)Long.parseLong(hex, 16);
                    dt.color = v;
                    this.currentColor = v;
                } catch(Exception ignored){}
            });
            
            this.delBtn = new PlainTextButton(0, 0, 20, 20, Text.literal("-"), onDelete, 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rightX = x + entryWidth;
            
            delBtn.setX(rightX - 25); 
            delBtn.setY(y + 2);
            delBtn.setWidth(20);
            delBtn.setForceHover(hovered);
            
            int previewSize = 18;
            int previewX = rightX - 25 - 5 - previewSize;
            int previewY = y + 2 + 1;
            
            int colBoxW = 55;
            int colBoxX = previewX - 5 - colBoxW;
            int colBoxY = y + 2;
            int colBoxH = 20;
            
            colField.setX(colBoxX + 4);
            colField.setY(colBoxY + 6);
            colField.setWidth(colBoxW - 8);
            colField.setHeight(12);
            
            int valBoxW = 40;
            int valBoxX = colBoxX - 5 - valBoxW;
            int valBoxY = y + 2;
            int valBoxH = 20;
            
            valField.setX(valBoxX + 4);
            valField.setY(valBoxY + 6);
            valField.setWidth(valBoxW - 8);
            valField.setHeight(12); 
            
            Text label = Text.translatable("text.damage-engine.damage_reach");
            
            int labelColor = 0xFFFFFFFF; 
            
            context.drawTextWithShadow(client.textRenderer, label, x + 10, y + 8, labelColor);
            
            context.fill(valBoxX, valBoxY, valBoxX + valBoxW, valBoxY + valBoxH, 0x20000000);
            int vx = valBoxX, vy = valBoxY, vw = valBoxW, vh = valBoxH;
            int valBorderColor = (valField.isFocused() || valField.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(vx, vy, vx + vw, vy + 1, valBorderColor);
            context.fill(vx, vy + vh - 1, vx + vw, vy + vh, valBorderColor);
            context.fill(vx, vy, vx + 1, vy + vh, valBorderColor);
            context.fill(vx + vw - 1, vy, vx + vw, vy + vh, valBorderColor);
            
            context.fill(colBoxX, colBoxY, colBoxX + colBoxW, colBoxY + colBoxH, 0x20000000);
            int cx = colBoxX, cy = colBoxY, cw = colBoxW, ch = colBoxH;
            int colBorderColor = (colField.isFocused() || colField.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(cx, cy, cx + cw, cy + 1, colBorderColor);
            context.fill(cx, cy + ch - 1, cx + cw, cy + ch, colBorderColor);
            context.fill(cx, cy, cx + 1, cy + ch, colBorderColor);
            context.fill(cx + cw - 1, cy, cx + cw, cy + ch, colBorderColor);
            
            valField.render(context, mouseX, mouseY, tickDelta);
            colField.render(context, mouseX, mouseY, tickDelta);
            delBtn.render(context, mouseX, mouseY, tickDelta);
            
            context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        
        @Override
        public List<? extends Element> children() {
            return java.util.Arrays.asList(valField, colField, delBtn);
        }
        
        @Override
        public List<? extends Selectable> selectableChildren() {
            return java.util.Arrays.asList(valField, colField, delBtn);
        }
    }

    private static class AddButtonEntry extends OptionEntry {
        private final PlainTextButton button;
        
        public AddButtonEntry(Runnable action) {
            this.button = new PlainTextButton(0, 0, 20, 20, Text.literal("+"), action, 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rightX = x + entryWidth;
            button.setX(rightX - 25);
            button.setY(y + 2);
            button.setFocused(false);
            button.setForceHover(hovered);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
    
    private static class ButtonActionEntry extends OptionEntry {
        private final StyledButton button;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        public ButtonActionEntry(String labelKey, String buttonKey, Runnable action) {
            this.label = Text.translatable(labelKey); 
            this.button = new StyledButton(0, 0, 100, 20, Text.translatable(buttonKey), action);
        }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFFFF);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setFocused(false);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
    
    private static class NumericEntry extends OptionEntry {
        private final TextFieldWidget field;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        public NumericEntry(String key, float initial, Consumer<Float> onChange) {
            this.label = Text.translatable(key);
            this.field = new TextFieldWidget(client.textRenderer, 0, 0, 100, 20, Text.empty());
            this.field.setDrawsBackground(false);
            this.field.setText(String.valueOf(initial));
            this.field.setChangedListener(s -> { try { onChange.accept(Float.parseFloat(s)); } catch(Exception ignored){} });
        }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFFFF);
            
            int boxX = x + entryWidth - 110;
            int boxY = y + 2;
            int boxW = 100;
            int boxH = 20;
            
            field.setX(boxX + 4);
            field.setY(boxY + 6);
            field.setWidth(boxW - 8);
            field.setHeight(12);
            
            context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x20000000);
            int fx = boxX, fy = boxY, fw = boxW, fh = boxH;
            int borderColor = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(fx, fy, fx + fw, fy + 1, borderColor);
            context.fill(fx, fy + fh - 1, fx + fw, fy + fh, borderColor);
            context.fill(fx, fy, fx + 1, fy + fh, borderColor);
            context.fill(fx + fw - 1, fy, fx + fw, fy + fh, borderColor);
            
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
            this.field = new TextFieldWidget(client.textRenderer, 0, 0, 75, 20, Text.empty());
            this.field.setDrawsBackground(false);
            this.field.setMaxLength(7);
            this.field.setText("#" + String.format("%06X", initial & 0xFFFFFF));
            this.field.setChangedListener(s -> { 
                try { 
                    String hex = s.startsWith("#") ? s.substring(1) : s;
                    int val = (int)Long.parseLong(hex, 16);
                    this.currentColor = val;
                    onChange.accept(val); 
                } catch(Exception ignored){} 
            });
        }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFFFF);
            
            int startX = x + entryWidth - 110;
            int boxX = startX;
            int boxY = y + 2;
            int boxW = 75;
            int boxH = 20;
            
            field.setX(boxX + 4);
            field.setY(boxY + 6);
            field.setWidth(boxW - 8);
            field.setHeight(12);
            
            context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0x20000000);
            int fx = boxX, fy = boxY, fw = boxW, fh = boxH;
            int borderColor = (field.isFocused() || field.isMouseOver(mouseX, mouseY)) ? 0xFFFFFFFF : 0xFFA0A0A0;
            context.fill(fx, fy, fx + fw, fy + 1, borderColor);
            context.fill(fx, fy + fh - 1, fx + fw, fy + fh, borderColor);
            context.fill(fx, fy, fx + 1, fy + fh, borderColor);
            context.fill(fx + fw - 1, fy, fx + fw, fy + fh, borderColor);
            
            field.render(context, mouseX, mouseY, tickDelta);
            
            int previewSize = 17;
            int previewX = startX + 80;
            
            int previewY = y + 2 + 1; 
            
            context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(field); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(field); }
    }
    
    private static class SpacerEntry extends OptionEntry {
        public SpacerEntry(int height) { }
        @Override
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        }
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }
    
    private static class KeybindEntry extends OptionEntry {
        private final Text label;
        private final BindingButton button;
        private final KeyBinding keyBinding;
        
        private class BindingButton extends ClickableWidget {
            private final Runnable onPress;

            public BindingButton(int x, int y, int width, int height, Text message, Runnable onPress) {
                super(x, y, width, height, message);
                this.onPress = onPress;
            }
            
            @Override
            public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
                if (MinecraftClient.getInstance().currentScreen instanceof DamageConfigScreen s) {
                    s.playClickSound();
                } else {
                    super.playDownSound(soundManager);
                }
            }
            
            @Override
            public boolean mouseClicked(Click click, boolean modifiers) {
                 if (this.active && this.visible && click.button() == 0) {
                     double mouseX = MinecraftClient.getInstance().mouse.getX() * (double)MinecraftClient.getInstance().getWindow().getScaledWidth() / (double)MinecraftClient.getInstance().getWindow().getWidth();
                     double mouseY = MinecraftClient.getInstance().mouse.getY() * (double)MinecraftClient.getInstance().getWindow().getScaledHeight() / (double)MinecraftClient.getInstance().getWindow().getHeight();
                     
                     if (this.isMouseOver(mouseX, mouseY)) {
                        this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                        this.onPress.run();
                        return true;
                     }
                 }
                 return false;
            }
            
            @Override
            protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
                context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x20000000);
                
                int borderColor = isHovered() ? 0xFFFFFFFF : 0xFFA0A0A0;
                int x = getX(); int y = getY(); int w = getWidth(); int h = getHeight();
                context.fill(x, y, x + w, y + 1, borderColor); 
                context.fill(x, y + h - 1, x + w, y + h, borderColor); 
                context.fill(x, y, x + 1, y + h, borderColor); 
                context.fill(x + w - 1, y, x + w, y + h, borderColor); 
                
                context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(), getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
            }
            
            @Override
            protected void appendClickableNarrations(NarrationMessageBuilder builder) {
                this.appendDefaultNarrations(builder);
            }
            
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (isBinding()) {
                    if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                        keyBinding.setBoundKey(InputUtil.UNKNOWN_KEY);
                    } else {
                        keyBinding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(keyCode));
                    }
                    finishBinding();
                    return true;
                }
                return false;
            }
        }

        private boolean binding = false;
        
        public KeybindEntry(String keyName, KeyBinding keyBinding) {
            this.label = Text.translatable(keyName);
            this.keyBinding = keyBinding;
            
            this.button = new BindingButton(0, 0, 100, 20, net.minecraft.text.Text.literal("BIND"), () -> {
                setBinding(true);
            });
            updateMessage();
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
        
        public void bindMouse(int button) {
            keyBinding.setBoundKey(InputUtil.Type.MOUSE.createFromCode(button));
            finishBinding();
        }
        
        private void updateMessage() {
            Text text;
            KeyBinding conflict = null;
            
            if (binding) {
                MutableText boundText = keyBinding.isUnbound() 
                    ? Text.translatable("key.damage_engine.not_bound") 
                    : keyBinding.getBoundKeyLocalizedText().copy();
                text = Text.literal("> ").withColor(0xFFFFFF55).append(boundText.formatted(Formatting.UNDERLINE).withColor(0xFFFFFFFF)).append(Text.literal(" <").withColor(0xFFFFFF55));
            } else {
                if (keyBinding.isUnbound()) {
                    text = Text.translatable("key.damage_engine.not_bound").withColor(0xFFFFFFFF);
                } else {
                    MutableText keyText = keyBinding.getBoundKeyLocalizedText().copy();
                    
                    for (KeyBinding kb : MinecraftClient.getInstance().options.allKeys) {
                        if (kb != keyBinding && kb.equals(keyBinding)) {
                            conflict = kb;
                            break;
                        }
                    }
                    
                    if (conflict != null) {
                        text = Text.literal("[ ").withColor(0xFFFFFF55).append(keyText.withColor(0xFFFFFFFF)).append(Text.literal(" ]").withColor(0xFFFFFF55));
                    } else {
                        text = keyText;
                    }
                }
            }
            
            button.setMessage(text);
            
            if (conflict != null) {
                Text tooltipText = Text.translatable("text.damage-engine.key_conflict");
                button.setTooltip(Tooltip.of(tooltipText));
            } else {
                button.setTooltip(null);
            }
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x, y + 8, 0xFFFFFFFF);
            
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.setWidth(100); 
            button.setFocused(false);
            
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
}
