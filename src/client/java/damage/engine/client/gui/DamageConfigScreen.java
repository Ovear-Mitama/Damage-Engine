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

/**
 * 伤害引擎配置屏幕
 * 提供对模组所有设置的图形化管理
 */
public class DamageConfigScreen extends Screen {
    private final Screen parent;
    private final DamageEngineConfig config;
    
    private CategoryListWidget categoryList;
    private ConfigOptionListWidget optionList;
    private boolean previewEnabled = false;
    
    private final List<CategoryEntry> categories = new ArrayList<>();
    private int selectedCategoryIndex = 0;
    
    // 状态保存
    private double lastOptionScroll = 0;
    private double lastCategoryScroll = 0;
    
    // 界面状态
    private boolean expandDamageColors = false; // 默认收起（应要求）
    
    // 重置按钮状态（跨刷新持久化）
    private int resetButtonState = 0; // 0=重置, 1=确认, 2=完成
    private long resetButtonActionTime = 0;
    
    // 导航状态
    private boolean isNavigating = false;
    
    // 按键绑定状态
    private boolean isBinding = false;

    public DamageConfigScreen(Screen parent) {
        super(Text.translatable("title.damage-engine.config"));
        this.parent = parent;
        this.config = DamageEngineConfig.getInstance();
    }

    private final List<ButtonWidget> footerButtons = new ArrayList<>();

    private static class TextButtonWidget extends ButtonWidget {
        private final int defaultColor;
        private final int hoverColor;

        public TextButtonWidget(int x, int y, int width, int height, Text message, PressAction onPress, int defaultColor, int hoverColor) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.defaultColor = defaultColor;
            this.hoverColor = hoverColor;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int color = this.isHovered() ? hoverColor : defaultColor;
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, this.getMessage(), this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
        }
    }

    @Override
    protected void init() {
        try {
            // 如果重新初始化，保存滚动状态
            if (optionList != null) lastOptionScroll = optionList.getScrollAmount();
            if (categoryList != null) lastCategoryScroll = categoryList.getScrollAmount();

            this.clearChildren();
            categories.clear();
            footerButtons.clear();
            
            int footerHeight = 40;
            int headerHeight = 30;
            int leftWidth = 120;
            int footerY = this.height - 30;
            
            // 全高列表，受页脚位置限制以允许"遮罩"
            // 列表将从 0 渲染到 footerY。
            int listHeight = footerY; 
            
            // 1. 左侧：分类列表
            categoryList = new CategoryListWidget(this.client, leftWidth, listHeight, headerHeight, 25);
            categoryList.setLeftPos(0);
            
            // 2. 右侧：选项列表
            // 从 leftWidth (120) 开始并占用剩余宽度
            optionList = new ConfigOptionListWidget(this.client, this.width - leftWidth, listHeight, 0, 30);
            optionList.setLeftPos(leftWidth);
            
            initCategories();
            initOptionEntries();
            
            // 恢复滚动
            if (categoryList != null) categoryList.setScrollAmount(lastCategoryScroll);
            if (optionList != null) optionList.setScrollAmount(lastOptionScroll);
            
            this.addDrawableChild(categoryList);
            this.addDrawableChild(optionList);
            
            // 3. 页脚按钮
            int buttonWidth = 80;
            int buttonY = footerY + 5; // 在 30px 页脚中垂直居中
            
            // 保存
            ButtonWidget saveBtn = ButtonWidget.builder(Text.translatable("button.damage-engine.save_close").withColor(0xFFB7F3C8), b -> {
                config.save();
                this.client.setScreen(parent);
            }).dimensions(this.width - buttonWidth - 10, buttonY, buttonWidth, 20).build();
            footerButtons.add(saveBtn);
            this.addSelectableChild(saveBtn);
            
            // 取消
            ButtonWidget cancelBtn = ButtonWidget.builder(Text.translatable("gui.cancel"), b -> {
                config.load();
                this.client.setScreen(parent);
            }).dimensions(this.width - buttonWidth * 2 - 20, buttonY, buttonWidth, 20).build();
            footerButtons.add(cancelBtn);
            this.addSelectableChild(cancelBtn);
            
            // 预览切换
            int onColor = 0xFFB5F0C6;
            int offColor = 0xFFFC887E;
            ButtonWidget previewBtn = ButtonWidget.builder(Text.translatable("text.damage-engine.preview").append(": ").append(Text.literal(previewEnabled ? "ON" : "OFF").withColor(previewEnabled ? onColor : offColor)), b -> {
                previewEnabled = !previewEnabled;
                b.setMessage(Text.translatable("text.damage-engine.preview").append(": ").append(Text.literal(previewEnabled ? "ON" : "OFF").withColor(previewEnabled ? onColor : offColor)));
            }).dimensions(10, buttonY, 80, 20).build();
            footerButtons.add(previewBtn);
            this.addSelectableChild(previewBtn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void refreshOptions() {
        if (optionList == null) return;
        double scroll = optionList.getScrollAmount();
        optionList.clearEntriesPublic();
        initOptionEntries();
        optionList.setScrollAmount(scroll);
    }
    
    private void initCategories() {
        categories.clear();
        categoryList.clearEntriesPublic(); // 确保如果多次调用不会重复（虽然 init 会清除分类列表）
        
        addCategory("category.damage_engine.general", 0);
        addCategory("category.damage_engine.appearance", 1);
        addCategory("category.damage_engine.keybinds", 2);
        addCategory("category.damage_engine.other", 3);
    }

    private void initOptionEntries() {
        // --- General ---
        addHeader("category.damage_engine.general");
        // Removed "Show Damage" option as requested
        addOption(new BooleanOptionEntry("option.damage-engine.showProgressBar", config.showProgressBar, v -> config.showProgressBar = v));
        addOption(new BooleanOptionEntry("option.damage-engine.showDamageHistory", config.showDamageHistory, v -> config.showDamageHistory = v));
        
        // --- Appearance ---
        addHeader("category.damage_engine.appearance");
        // 更正了标签和按钮文本的键
        addOption(new ButtonActionEntry("option.damage-engine.edit_pos_label", "button.damage-engine.edit_pos", () -> this.client.setScreen(new HudEditorScreen(this))));
        
        addOption(new SliderEntry("option.damage-engine.scale", config.hudScale, 0.5f, 2.5f, v -> config.hudScale = v));
        addOption(new HexColorEntry("option.damage-engine.progressBarColor", config.progressBarColor, v -> config.progressBarColor = v));
        
        // 伤害颜色
        addOption(new ExpandableHeaderEntry("option.damage-engine.colors", expandDamageColors, v -> {
            expandDamageColors = v;
            refreshOptions();
        }));
        
        if (expandDamageColors) {
             // 排序以显示
             config.damageThresholds.sort((a, b) -> Float.compare(a.threshold, b.threshold));
             
             for (DamageEngineConfig.DamageThreshold dt : new ArrayList<>(config.damageThresholds)) {
                 addOption(new DamageThresholdEntry(dt, () -> {
                     config.damageThresholds.remove(dt);
                     refreshOptions();
                 }));
             }
             
             // 添加按钮
             addOption(new AddButtonEntry(() -> {
                 float nextVal = 0f;
                 if (!config.damageThresholds.isEmpty()) {
                     nextVal = config.damageThresholds.get(config.damageThresholds.size() - 1).threshold + 50f;
                 }
                 config.damageThresholds.add(new DamageEngineConfig.DamageThreshold(nextVal, 0xFFFFFFFF));
                 refreshOptions();
             }));
        }
        
        // --- Keybinds ---
        addHeader("category.damage_engine.keybinds");
        
        addOption(new KeybindEntry("key.damage_engine.config", DamageEngineClient.configKeyBinding));
        addOption(new KeybindEntry("key.damage_engine.toggle_hud", DamageEngineClient.toggleHudKeyBinding));
        
        
        // --- Other ---
        addHeader("category.damage_engine.other");
        addOption(new NumericEntry("option.damage-engine.resetTime", config.resetTime, v -> config.resetTime = v));
        addOption(new NumericEntry("option.damage-engine.historyDisappearanceTime", config.historyDisappearanceTime, v -> config.historyDisappearanceTime = v));
        addOption(new IntegerSliderEntry("option.damage-engine.historyLimit", config.historyLimit, 1, 50, v -> config.historyLimit = v));
        addOption(new BooleanOptionEntry(Text.translatable("option.damage-engine.debugMode").withColor(0xFFFBFB54), config.debugMode, v -> config.debugMode = v));
        
        // 重置按钮
        addOption(new ResetButtonEntry("option.damage-engine.reset", () -> {
            config.resetToDefaults();

            DamageEngineClient.configKeyBinding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_UNKNOWN)); // 默认为 UNKNOWN
            DamageEngineClient.toggleHudKeyBinding.setBoundKey(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_UNKNOWN)); // 默认为 UNKNOWN
            KeyBinding.updateKeysByCode();
            this.client.options.write();
            
            // 不要重新创建屏幕，只需刷新
            refreshOptions();
        }));
        
        // 底部滚动垫片
        addOption(new SpacerEntry(30));
        addOption(new SpacerEntry(30));
    }
    
    private void addCategory(String key, int id) {
        
        CategoryEntry cat = new CategoryEntry(this, Text.translatable(key), id, 0); // 暂时为索引 0
        categoryList.addEntryPublic(cat);
        categories.add(cat);
    }
    
    private void addHeader(String key) {
        // 找到此标题所属的分类（通过键匹配）并更新其 targetIndex
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
            // 粗略的滚动计算
            for (int i = 0; i < cat.targetIndex; i++) {
                y += 30; // 近似高度
            }
            // 使用平滑滚动到目标
            isNavigating = true;
            optionList.setTargetScroll(y);
        }
    }
    
    private void updateActiveCategory() {
        if (isNavigating) return;
        if (optionList == null || categories.isEmpty()) return;
        
        // 找到当前可见的分类
        // 滚动量对应 Y 偏移
        double scroll = optionList.getScrollAmount();
        
        // 我们需要知道选项列表中每个分类标题的 Y 位置
        // 因为所有项目高度为 30，所以是 index * 30。
        
        int currentBest = 0;
        for (int i = 0; i < categories.size(); i++) {
            CategoryEntry cat = categories.get(i);
            double catY = cat.targetIndex * 30; // 近似值
            
            // 如果滚动超过此分类标题，它就是一个候选者
            if (scroll >= catY - 10) { // -10 容差
                currentBest = i;
            } else {
                break; // 通过的标题太靠下
            }
        }
        
        selectedCategoryIndex = categories.get(currentBest).id;
    }

    public void setBinding(boolean binding) {
        this.isBinding = binding;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 首先检查绑定模式
        if (isBinding) {
            // 检查是否按下了 ESC - 如果是，在此处消耗它以防止关闭屏幕
            // 实际的绑定逻辑由按钮的 keyPressed 处理，
            // 但我们需要确保事件不会传播到 Screen.close()
            
            // 允许聚焦元素（绑定按钮）首先处理按键
            if (this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            
            // 如果子元素没有消耗它，但我们在绑定模式下，我们要必须消耗 ESC。
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                return true;
            }
            
            return true; // 在绑定模式下消耗所有按键？或者让子元素处理？
        }
        
        // 正常模式
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            config.save();
            this.client.setScreen(parent);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 首先将点击转发给按钮
        for (ButtonWidget btn : footerButtons) {
            if (btn.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        try {
            // 1. 绘制主要背景，如果在选项中启用了模糊
            // renderBackground 如果 world != null 则调用 renderInGameBackground
            this.renderBackground(context, mouseX, mouseY, delta);
            
            // 更新滚动监听
            if (optionList != null) {
                // 如果没有在导航（自动滚动），检查鼠标悬停
                // 只根据我在右侧悬停的设置项调整
                if (!isNavigating) {
                    int hoveredIndex = optionList.getHoveredEntryIndex(mouseX, mouseY);
                    if (hoveredIndex != -1) {
                         // 根据 hoveredIndex 找到对应分类
                         int bestCatId = -1;
                         // 遍历所有分类，找到 targetIndex <= hoveredIndex 的最后一个
                         for (CategoryEntry cat : categories) {
                             if (cat.targetIndex <= hoveredIndex) {
                                 bestCatId = cat.id;
                             } else {
                                 break; // 因为按顺序排列，一旦超过就停止
                             }
                         }
                         if (bestCatId != -1) {
                             selectedCategoryIndex = bestCatId;
                         }
                    }
                }
                
                // 不用根据我处在哪一页动态调整
                // 所以我们移除了 updateActiveCategory() 的后备调用
            }
            
            // 更新平滑滚动
            if (optionList != null) optionList.updateSmoothScroll(delta);
            
            // 2. 渲染列表
            super.render(context, mouseX, mouseY, delta);
            
            // 3. 绘制分隔线
            int leftWidth = 120;
            int footerY = this.height - 30;
            
            // 垂直线（确保它不会低于 footerY）
            context.fill(leftWidth - 1, 0, leftWidth, footerY, 0xFF555555);
            
            // 水平线（页脚上方）
            context.fill(0, footerY - 1, this.width, footerY, 0xFF555555);

            context.fill(0, 25, leftWidth, 26, 0xFF555555);
            
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200); // 提高 Z 轴索引
            
            for (ButtonWidget btn : footerButtons) {
                btn.render(context, mouseX, mouseY, delta);
            }
            context.getMatrices().pop();
            
            context.drawTextWithShadow(this.textRenderer, Text.translatable("category.damage_engine"), 10, 10, 0xFFFFFF);
            
            if (previewEnabled) {
                new DamageHud().renderPreview(context, this.width/2, this.height/2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {

            
            boolean isSelected = screen.selectedCategoryIndex == id;
            if (isSelected) {

                context.fill(x, y + 2, x + 2, y + entryHeight - 2, 0xFFFFFFFF);
            }
            int color = 0xFFFFFFFF;

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
        public CategoryListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, 25);
            this.centerListVertically = false;
        }
        public void clearEntriesPublic() { this.clearEntries(); }
        
        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {

             
             this.enableScissor(context);
             this.renderList(context, mouseX, mouseY, delta);
             context.disableScissor();
        }

        @Override public int getRowWidth() { return 100; }
        public void addEntryPublic(CategoryEntry entry) { this.addEntry(entry); }
        public void setLeftPos(int x) { }
        @Override public int getRowLeft() { return 0; }
        public int getScrollbarPositionX() { return -10; } 
    }
    
    private abstract static class OptionEntry extends ElementListWidget.Entry<OptionEntry> {
        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 手动检查悬停，以防万一
            boolean isHovered = hovered || (mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight);
            
            if (isHovered && shouldHighlight()) {

                context.fill(x - 200, y, x + entryWidth + 200, y + entryHeight - 2, 0x30FFFFFF);
            }
            renderContent(context, index, y, x, entryWidth, entryHeight, mouseX, mouseY, hovered, tickDelta);
        }
        
        public abstract void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta);
        
        protected boolean shouldHighlight() { return true; }
    }
    
    private class ConfigOptionListWidget extends ElementListWidget<OptionEntry> {
        private double targetScroll = 0;
        private boolean isSmoothScrolling = false;

        public ConfigOptionListWidget(MinecraftClient client, int width, int height, int y, int itemHeight) {
            super(client, width, height, y, 30);
            this.centerListVertically = false;
        }
        
        public int getHoveredEntryIndex(double mouseX, double mouseY) {

             if (mouseX < this.getX() || mouseX > this.getRight() || mouseY < this.getY() || mouseY > this.getBottom()) {
                 return -1;
             }
             
             int i = MathHelper.floor(mouseY - (double)this.getY() - this.headerHeight + this.getScrollAmount() - 4.0D);

             int index = i / 30;
             if (index >= 0 && index < this.getEntryCount()) {
                 return index;
             }
             return -1;
        }
        
        public void clearEntriesPublic() { this.clearEntries(); }
        @Override public int getRowWidth() { return this.width - 20; } 
        public void addEntryPublic(OptionEntry entry) { this.addEntry(entry); }
        public void setLeftPos(int x) { 
            this.setX(x);
        }
        @Override public int getRowLeft() { return this.getX() + 10; }
        public int getScrollbarPositionX() { return this.client.getWindow().getScaledWidth() - 6; }
        
        
        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
             this.enableScissor(context);
             this.renderList(context, mouseX, mouseY, delta);
             context.disableScissor();
             
             // 滚动条
             int scrollbarX = this.getScrollbarPositionX();
             int scrollbarY = this.getY();
             int scrollbarHeight = this.getHeight();
             int contentHeight = this.getMaxScroll() + this.getHeight();
             
             if (contentHeight > this.getHeight()) {
                 int barHeight = (int)((float)(this.getHeight() * this.getHeight()) / (float)contentHeight);
                 barHeight = Math.max(32, barHeight);
                 if (barHeight > this.getHeight()) barHeight = this.getHeight();
                 
                 int barTop = (int)this.getScrollAmount() * (this.getHeight() - barHeight) / (this.getMaxScroll()) + this.getY();
                 if (barTop < this.getY()) barTop = this.getY();
                 
                 // 绘制轨道（深色背景）
                context.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0x80000000);
                
                // 绘制滑块（按要求为淡白色）
                context.fill(scrollbarX, barTop, scrollbarX + 6, barTop + barHeight, 0xA0FFFFFF);
            }
       }
        
        @Override
        protected void drawHeaderAndFooterSeparators(DrawContext context) {}
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 检查是否点击滚动条区域
            int scrollbarX = this.getScrollbarPositionX();
            // 扩大的点击区域
            if (mouseX >= scrollbarX - 10) {
                DamageConfigScreen.this.isNavigating = false;
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            DamageConfigScreen.this.isNavigating = false;
            this.targetScroll = this.getScrollAmount() - verticalAmount * 80.0; // 增加速度 (幅度更大)
            this.targetScroll = Math.max(0, Math.min(this.targetScroll, this.getMaxScroll()));
            this.isSmoothScrolling = true;
            return true;
        }
        
        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            int contentHeight = this.getMaxScroll() + this.getHeight();
            if (contentHeight > this.getHeight()) {
                 int scrollbarX = this.getScrollbarPositionX();
                 if (draggingScrollbar || (mouseX >= scrollbarX - 10)) {
                     DamageConfigScreen.this.isNavigating = false;
                     draggingScrollbar = true;
                     // 根据鼠标移动计算新的滚动量
                     double scrollFactor = (double)this.getMaxScroll() / (double)(this.getHeight() - 30); // 近似滑块高度因子
                     
                     double barHeight = (double)(this.getHeight() * this.getHeight()) / (double)contentHeight;
                     barHeight = Math.max(32, barHeight);
                     double trackHeight = this.getHeight() - barHeight;
                     
                     if (trackHeight > 0) {
                         double scrollPerPixel = this.getMaxScroll() / trackHeight;
                         
                         double newScroll = this.getScrollAmount() + deltaY * scrollPerPixel;
                         this.setScrollAmount(newScroll);
                         this.targetScroll = newScroll; // 同步目标
                         this.isSmoothScrolling = false; // 禁用平滑
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
                    DamageConfigScreen.this.isNavigating = false;
                } else {
                    this.setScrollAmount(MathHelper.lerp(0.5f * delta, current, targetScroll));
                }
            } else {
                targetScroll = this.getScrollAmount();
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
            this(Text.translatable(key), initial, onToggle);
        }

        public BooleanOptionEntry(Text label, boolean initial, Consumer<Boolean> onToggle) {
            this.state = initial;
            this.label = label;
            int onColor = 0xFFB5F0C6;
            int offColor = 0xFFFC887E;
            
            this.button = ButtonWidget.builder(Text.translatable(state ? "options.on" : "options.off").withColor(state ? onColor : offColor), b -> {
                state = !state;
                b.setMessage(Text.translatable(state ? "options.on" : "options.off").withColor(state ? onColor : offColor));
                onToggle.accept(state);
            }).dimensions(0, 0, 100, 20).build();
        }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 调整位置：y+8 (文本), y+2 (按钮)
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFF);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
    
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
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFF);
            slider.setX(x + entryWidth - 110);
            slider.setY(y + 2);
            slider.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(slider); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(slider); }
    }

    private static class IntegerSliderEntry extends OptionEntry {
        private final SliderWidget slider;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        
        public IntegerSliderEntry(String key, int current, int min, int max, Consumer<Integer> onChange) {
            this.label = Text.translatable(key);
            float minF = (float)min;
            float maxF = (float)max;
            float currentF = (float)current;
            
            this.slider = new SliderWidget(0, 0, 100, 20, Text.literal(String.valueOf(current)), (currentF - minF) / (maxF - minF)) {
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
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFF);
            slider.setX(x + entryWidth - 110);
            slider.setY(y + 2);
            slider.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(slider); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(slider); }
    }
    
    private class ResetButtonEntry extends OptionEntry {
        private final ButtonWidget button;
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
                 color = 0xFFB5F0C6; // 绿色
                 btnText = Text.translatable("text.damage-engine.reset_done");
            }
            
            this.button = ButtonWidget.builder(btnText.copy().withColor(color), b -> handleClick())
                .dimensions(0, 0, 100, 20).build();
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
            // 检查超时以重置状态
            if (resetButtonState == 2) {
                if (System.currentTimeMillis() - resetButtonActionTime > 3000) {
                    resetButtonState = 0;
                    button.setMessage(Text.translatable("gui.reset").withColor(0xFFFC887E)); // 重置为 #FC887E
                }
            } else if (resetButtonState == 1) {
                 // 5秒后自动取消确认
                 if (System.currentTimeMillis() - resetButtonActionTime > 5000) {
                     resetButtonState = 0;
                     button.setMessage(Text.translatable("gui.reset").withColor(0xFFFC887E)); // 重置为 #FC887E
                 }
            }
            
            // 左侧标签文字也显示为 #FC887E
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFC887E);
            
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
    
    private static class ExpandableHeaderEntry extends OptionEntry {
        private final ButtonWidget button;
        private final Text label;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private boolean expanded;
        private final Consumer<Boolean> onToggle;
        
        public ExpandableHeaderEntry(String key, boolean expanded, Consumer<Boolean> onToggle) {
            this.label = Text.translatable(key);
            this.expanded = expanded;
            this.onToggle = onToggle;
            
            // 用于交互的不可见按钮，我们手动渲染符号
            this.button = ButtonWidget.builder(Text.empty(), b -> onToggle.accept(!expanded))
                .dimensions(0, 0, 20, 20).build();
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // 整行响应：只要悬停，文字和符号都变色
            // 使用更可靠的悬停检测
            boolean isHovered = hovered || (mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight);
            int color = isHovered ? 0xFFFBFB54 : 0xFFFFFFFF;
            
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, color);
            
            button.setX(x + entryWidth - 25);
            button.setY(y + 2);
            // 不渲染按钮背景
            
            // 手动渲染符号：如果展开则为 - (收起)，如果折叠则为 + (展开)
            String symbol = expanded ? "-" : "+";
            int symWidth = client.textRenderer.getWidth(symbol);
            
            // 确保符号也使用相同的悬停颜色
            context.drawTextWithShadow(client.textRenderer, symbol, button.getX() + 10 - symWidth/2, button.getY() + 6, color);
        }
        
        @Override 
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // 整行响应点击
            if (button == 0) {
                this.onToggle.accept(!expanded);
                client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
        private final ButtonWidget delBtn;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private int currentColor;
        
        public DamageThresholdEntry(DamageEngineConfig.DamageThreshold dt, Runnable onDelete) {
            this.currentColor = dt.color;
            
            this.valField = new TextFieldWidget(client.textRenderer, 0, 0, 40, 20, Text.empty());
            this.valField.setText(String.format("%.0f", dt.threshold));
            this.valField.setChangedListener(s -> {
                try { dt.threshold = Float.parseFloat(s); } catch(Exception ignored){}
            });
            
            this.colField = new TextFieldWidget(client.textRenderer, 0, 0, 55, 20, Text.empty());
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
            
            this.delBtn = new TextButtonWidget(0, 0, 20, 20, Text.literal("-"), b -> onDelete.run(), 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rightX = x + entryWidth;
            
            delBtn.setX(rightX - 25);
            delBtn.setY(y + 2);
            
            int previewSize = 18;
            int previewX = rightX - 25 - 5 - previewSize;
            int previewY = y + 2 + 1;
            
            colField.setX(previewX - 5 - 55);
            colField.setY(y + 2);
            colField.setWidth(55);
            
            valField.setX(colField.getX() - 5 - 40);
            valField.setY(y + 2);
            
            Text label = Text.translatable("text.damage-engine.damage_reach");
            
            // 整行响应：只要鼠标在这一行范围内 (hovered 为 true)，标签就变色
            int labelColor = hovered ? 0xFFFBFB54 : 0xFFFFFFFF; // Yellow if hovered, White otherwise
            
            context.drawTextWithShadow(client.textRenderer, label, x + 10, y + 8, labelColor); // 稍微缩进
            
            valField.render(context, mouseX, mouseY, tickDelta);
            colField.render(context, mouseX, mouseY, tickDelta);
            delBtn.render(context, mouseX, mouseY, tickDelta);
            
            context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            context.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF000000 | (currentColor & 0xFFFFFF));
        }
        @Override public List<? extends Element> children() { return List.of(valField, colField, delBtn); }
        @Override public List<? extends Selectable> selectableChildren() { return List.of(valField, colField, delBtn); }
    }

    private static class AddButtonEntry extends OptionEntry {
        private final ButtonWidget button;
        private final MinecraftClient client = MinecraftClient.getInstance();
        private final Runnable action;
        
        public AddButtonEntry(Runnable action) {
            this.action = action;
            // 按钮设为全宽，不可见（只用于处理点击）
            // 或者是保留小按钮，但 mouseClicked 覆盖全行
            this.button = new TextButtonWidget(0, 0, 20, 20, Text.literal("+"), b -> action.run(), 0xFFFFFFFF, 0xFFFBFB54);
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int rightX = x + entryWidth;
            button.setX(rightX - 25);
            button.setY(y + 2);
            
            // 整行响应：只要悬停在这一行，+ 号就变黄
            // 使用更可靠的悬停检测
            boolean isHovered = hovered || (mouseX >= x && mouseX <= x + entryWidth && mouseY >= y && mouseY <= y + entryHeight);
            int color = isHovered ? 0xFFFBFB54 : 0xFFFFFFFF;
            
            // 手动绘制 + 号
            String symbol = "+";
            int symWidth = client.textRenderer.getWidth(symbol);
            // 确保 + 号也使用悬停颜色
            context.drawTextWithShadow(client.textRenderer, symbol, button.getX() + 10 - symWidth/2, button.getY() + 6, color);
            
            // 不调用 button.render，完全接管绘制
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
             // 允许整行点击
             if (button == 0) { // 左键
                 this.action.run();
                 client.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                 return true;
             }
             return false;
        }
        
        @Override public List<? extends Element> children() { return Collections.emptyList(); } // 不返回 button，避免双重处理
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
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFF);
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
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFF);
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
            this.field.setMaxLength(7); // 限制为 7 个字符
            this.field.setText("#" + String.format("%06X", initial & 0xFFFFFF)); // 确保带有 # 的 6 位十六进制
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
            context.drawTextWithShadow(client.textRenderer, label, x, y + 8, 0xFFFFFF);
            
            // 布局：[输入框 (75)] [间距 (5)] [预览框 (17)]
            // 总宽度：100
            // 起始 X：x + entryWidth - 110
            
            int startX = x + entryWidth - 110;
            
            field.setX(startX);
            field.setY(y + 2);
            field.setWidth(75); // 调整宽度
            field.render(context, mouseX, mouseY, tickDelta);
            
            // Render Preview Box
            int previewSize = 17;
            int previewX = startX + 80; // 75 + 5
            
            // 垂直居中：(20 - 17) / 2 = 1.5 -> 1 或 2 偏移
            int previewY = y + 2 + 1; 
            
            // 外边框（白色）
            context.fill(previewX - 1, previewY - 1, previewX + previewSize + 1, previewY + previewSize + 1, 0xFFFFFFFF);
            
            // 内部颜色
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
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(client.textRenderer, text, x, y + 10, 0xFFFFFF);
        }
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }
    
    private static class SpacerEntry extends OptionEntry {
        private final int height;
        public SpacerEntry(int height) { this.height = height; }
        @Override
        protected boolean shouldHighlight() { return false; }
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            // Empty
        }
        @Override public List<? extends Element> children() { return Collections.emptyList(); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.emptyList(); }
    }
    
    private static class KeybindEntry extends OptionEntry {
        private final Text label;
        private final BindingButton button;
        private final KeyBinding keyBinding;
        
        // 在绑定时拦截输入的自定义 ButtonWidget
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
                    return true; // 消耗事件
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
            
            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (isBinding()) {
                    keyBinding.setBoundKey(InputUtil.Type.MOUSE.createFromCode(button));
                    finishBinding();
                    return true; // 消耗事件
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
            if (binding) return Text.literal("> <").withColor(0xFFFFFF55);
            // 用户请求英文 'Not Bound'（将使用可翻译键）和颜色 #FFFFFF
            if (keyBinding.isUnbound()) return Text.translatable("key.damage_engine.not_bound").withColor(0xFFFFFFFF);
            return keyBinding.getBoundKeyLocalizedText();
        }
        
        private void updateMessage() {
            button.setMessage(getBindingText());
        }
        
        @Override
        public void renderContent(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, label, x, y + 8, 0xFFFFFF);
            button.setX(x + entryWidth - 110);
            button.setY(y + 2);
            button.render(context, mouseX, mouseY, tickDelta);
        }
        
        @Override public List<? extends Element> children() { return Collections.singletonList(button); }
        @Override public List<? extends Selectable> selectableChildren() { return Collections.singletonList(button); }
    }
}
