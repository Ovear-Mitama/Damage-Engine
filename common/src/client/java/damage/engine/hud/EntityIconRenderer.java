package damage.engine.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体头像渲染器:把 3D 实体渲染到 HUD 的信息面板上。
 * <p>
 * 26.1+ 的 GUI 渲染已改为"渲染状态提取"模型:{@link GuiGraphicsExtractor} 不再直接绘制,
 * 而是收集渲染状态后由 {@code GuiEntityRenderer} 画到离屏纹理再合成,因此这里不手动摆 PoseStack,
 * 而是取渲染状态 → 改朝向 → 交给 {@link GuiGraphicsExtractor#entity} 渲染。
 * <p>
 * 取景规则:
 * <ul>
 *   <li>按"1 格 = 头像槽边长 / 1.8(玩家身高)"的固定比例绘制,实体多大就画多大,
 *       不再把每个实体缩放去填满头像槽,也不对模型缩放做归一化或上限限制;</li>
 *   <li>渲染框按实体实际尺寸(碰撞箱与模型几何取大者)放大留余量,只作为画布与裁剪边界,
 *       目的是让模型有溢出的余地、不被切边,不参与大小计算;</li>
 *   <li>垂直方向按模型自身的垂直中点居中(模型几何不可测时回退到碰撞箱中心)。</li>
 * </ul>
 */
public final class EntityIconRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");

    /**
     * 换算基准:多少格高度对应"刚好填满头像槽"。
     * <p>
     * 以玩家身高(1.8 格)为基准,即 1 格 = 头像槽边长 / 1.8 像素。实体按这个比例画出来就是它的
     * 真实体型——大史莱姆是大的、小史莱姆是小的,不再把每个实体都缩放去"填满头像槽"。
     */
    private static final float PLAYER_HEIGHT_BLOCKS = 1.8f;

    /** 垂直微调,与 Damage-Indicators 的 offsetY 取值一致(模型不可测时的回退分支使用)。 */
    private static final float CENTER_OFFSET_Y = 0.0625f;

    /** 原版实体渲染把模型沿 Y 下移 1.501 格(脚底对齐模型原点),用它作为垂直居中的基准。 */
    private static final float MODEL_VERTICAL_PIVOT = 1.501f;

    /**
     * 渲染框相对实体实际尺寸的余量。
     * <p>
     * 渲染框只是交给 GUI 实体管线的画布与裁剪边界,放大它会让模型有溢出的余地、不会被切边,
     * 但不影响实体本身的像素大小(大小只由 {@code scale} 决定)。
     */
    private static final float PICTURE_BOX_MARGIN = 2.0f;

    /**
     * 模型几何缓存(按实体类型)。
     * 模型几何是静态的,缓存除了省开销,更重要的是避免逐帧读取"当前动画姿态"导致居中基准随行走摆动而抖动。
     */
    private static final Map<EntityType<?>, ModelExtents> MODEL_CACHE = new HashMap<>();

    /** 诊断用:上次打印取景信息的实体 id(避免刷屏) */
    private static int lastLogId = -1;

    private EntityIconRenderer() {
    }

    /**
     * 将实体按它的真实体型渲染到 HUD 的头像槽里。必须在渲染状态提取阶段调用。
     *
     * @param slotX         头像槽左上角 x(屏幕坐标,不套用当前 pose)
     * @param slotY         头像槽左上角 y(屏幕坐标,不套用当前 pose)
     * @param slotSize      头像槽边长(像素)
     * @param alpha         整体透明度(0~1;当前版本的 GUI 实体管线不支持整体透明度,仅用作可见性阈值)
     * @param followRotation true = 跟随实际朝向(以玩家视角为基准),false = 按自定义角度旋转
     * @param customAngle    自定义朝向角度(0~360,0 = 正面朝向观察者,顺时针增大;仅 followRotation=false 时生效)
     */
    public static void render(GuiGraphicsExtractor guiGraphics, LivingEntity entity, int slotX, int slotY, int slotSize,
                              float alpha, boolean followRotation, int customAngle) {
        if (guiGraphics == null || entity == null || slotSize <= 0 || alpha <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        try {
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderState state = dispatcher.extractEntity(entity, 1.0f);

            float bbW = state.boundingBoxWidth;
            float bbH = state.boundingBoxHeight;
            if (state instanceof LivingEntityRenderState livingState) {
                // 只改渲染状态里的朝向,不改世界中的实体:
                // 跟随模式以玩家视角为基准(生物正对你时显示正面,背对时显示背面);
                // 自定义模式按配置角度摆姿势:0° = 正面朝向观察者(等价原「固定」),角度增大为顺时针。
                // 注:这里用减号——渲染状态的 yaw 与旧版管线(1.20/1.21 直接设 entity yaw)手性相反,
                // 用减号才能让"角度增大"在两个管线家族里呈现同一方向。
                livingState.bodyRot = followRotation ? (livingState.bodyRot - mc.player.getYRot()) : (180.0f - customAngle);
            }
            // 注意:既不再把实体缩放归一化,也不再按"填满头像槽"反推缩放。
            // 体型差异(史莱姆大小、幼年体缩放、模组自定义缩放)交给渲染管线按实体状态自己应用,
            // 这里只给一个固定的"1 格 = 多少像素",画出来就是它的真实体型。

            ModelExtents model = modelExtents(entity, dispatcher);
            float pixelsPerBlock = slotSize / PLAYER_HEIGHT_BLOCKS;
            // 渲染器内部施加的体型缩放(史莱姆/岩浆怪按大小属性放大、模组自定义缩放)。
            // 它不在渲染状态里,而且只作用在模型本体上、不作用于下面那个固定的 1.501 位移,
            // 所以算居中时必须让模型中点乘上它,否则个体越大越往上飘。
            float bodyScale = rendererBodyScale(dispatcher, entity, state);

            // 垂直居中按"模型自身的垂直中点"算,而不是碰撞箱中心。
            // 原版实体渲染会把模型沿 Y 下移 1.501 格(脚底对齐原点),故中点落在槽心对应的位移是 1.501 - 中点×体型缩放;
            // 模型不可测时回退到碰撞箱中心。
            float translateY = model != null
                ? (MODEL_VERTICAL_PIVOT - model.centerY() * bodyScale)
                : (bbH / 2.0f + CENTER_OFFSET_Y);

            // 渲染框按实体实际尺寸放大:它只是画布与裁剪边界,放大只为留出溢出的余地、不切边,
            // 实体本身的像素大小只由上面那个固定比例决定。
            float spanBlocks = model != null
                ? Math.max(Math.max(model.spanX(), model.spanZ()), model.spanY())
                : Math.max(bbW, bbH);
            float boundBlocks = Math.max(spanBlocks, Math.max(bbW, bbH));
            int boxSize = Math.max(slotSize, (int) Math.ceil(boundBlocks * pixelsPerBlock * PICTURE_BOX_MARGIN));
            int boxX = slotX + slotSize / 2 - boxSize / 2;
            int boxY = slotY + slotSize / 2 - boxSize / 2;

            Vector3f translate = new Vector3f(0.0f, translateY, 0.0f);

            // 与原版 InventoryScreen 一致:绕 Z 轴 180°(GUI 坐标翻转)
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
            // 覆盖相机朝向,使火焰等公告板元素朝向 GUI 观察者
            Quaternionf cameraAngle = new Quaternionf();

            if (entity.getId() != lastLogId) {
                lastLogId = entity.getId();
                LOGGER.info("[DE] icon: id={} 类型={} follow={} 包围箱={}x{} 模型XYZ={}x{}x{} 中点Y={} 体型缩放={} 每格像素={} 位移Y={} 渲染框={}",
                    entity.getId(), entity.getType().getDescription().getString(), followRotation,
                    String.format("%.2f", bbW),
                    String.format("%.2f", bbH),
                    model != null ? String.format("%.2f", model.spanX()) : "n/a",
                    model != null ? String.format("%.2f", model.spanY()) : "n/a",
                    model != null ? String.format("%.2f", model.spanZ()) : "n/a",
                    model != null ? String.format("%.2f", model.centerY()) : "n/a",
                    String.format("%.2f", bodyScale),
                    String.format("%.2f", pixelsPerBlock),
                    String.format("%.2f", translateY),
                    boxSize);
            }

            guiGraphics.entity(state, pixelsPerBlock, translate, rotation, cameraAngle,
                boxX, boxY, boxX + boxSize, boxY + boxSize);
        } catch (Throwable t) {
            LOGGER.warn("[DE] 实体头像渲染失败: {}", t.toString());
        }
    }

    /**
     * 读出渲染器内部施加的体型缩放。
     * <p>
     * 史莱姆/岩浆怪这类"同一个实体按大小属性放大"的缩放是在 {@code LivingEntityRenderer#scale} 里做的:
     * 既不在 {@link EntityRenderState} 里,也不改变碰撞箱与模型几何各自的比例。这里给它一个空
     * {@link PoseStack} 当探针,从返回的矩阵里读出缩放值,用于修正垂直居中。取不到就按 1 处理。
     */
    private static float rendererBodyScale(EntityRenderDispatcher dispatcher, LivingEntity entity, EntityRenderState state) {
        if (!(state instanceof LivingEntityRenderState livingState)) return 1.0f;
        try {
            EntityRenderer<?, ?> renderer = dispatcher.getRenderer(entity);
            if (!(renderer instanceof LivingEntityRenderer)) return 1.0f;
            PoseStack probe = new PoseStack();
            ((LivingEntityRenderer) renderer).scale(livingState, probe);
            Vector3f scale = probe.last().pose().getScale(new Vector3f());
            float s = Math.abs(scale.y);
            return s > 0.001f ? s : 1.0f;
        } catch (Throwable t) {
            return 1.0f;
        }
    }

    /** 按实体类型取模型几何(带缓存);测量失败返回 null,由调用方回退到碰撞箱。 */
    private static ModelExtents modelExtents(LivingEntity entity, EntityRenderDispatcher dispatcher) {
        EntityType<?> type = entity.getType();
        if (MODEL_CACHE.containsKey(type)) return MODEL_CACHE.get(type);
        ModelExtents measured = measureModel(dispatcher, entity);
        if (measured != null) MODEL_CACHE.put(type, measured);
        return measured;
    }

    /**
     * 用模型的初始(未动画)姿态测量几何包围盒尺寸,单位:格
     * ({@link ModelPart#getExtentsForGui} 内部已按 1/16 归一到方块单位)。
     * <p>
     * 先快照全部部位的姿态,复位到初始姿态测量,最后原样还原,避免影响正常渲染;
     * 这样得到的结果与当前动画进度无关,取景比例不会随行走/摆尾抖动。
     */
    private static ModelExtents measureModel(EntityRenderDispatcher dispatcher, LivingEntity entity) {
        try {
            EntityRenderer<?, ?> renderer = dispatcher.getRenderer(entity);
            EntityModel<?> model = resolveModel(renderer);
            if (model == null) return null;
            ModelPart root = model.root();
            if (root == null) return null;

            List<ModelPart> parts = model.allParts();
            PartPose[] saved = new PartPose[parts.size()];
            for (int i = 0; i < parts.size(); i++) {
                saved[i] = parts.get(i).storePose();
            }

            float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
            float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            int[] count = {0};
            try {
                for (ModelPart part : parts) {
                    part.resetPose();
                }
                root.getExtentsForGui(new PoseStack(), v -> {
                    count[0]++;
                    min[0] = Math.min(min[0], v.x());
                    min[1] = Math.min(min[1], v.y());
                    min[2] = Math.min(min[2], v.z());
                    max[0] = Math.max(max[0], v.x());
                    max[1] = Math.max(max[1], v.y());
                    max[2] = Math.max(max[2], v.z());
                });
            } finally {
                for (int i = 0; i < parts.size(); i++) {
                    parts.get(i).loadPose(saved[i]);
                }
            }

            if (count[0] == 0) return null;
            float spanX = max[0] - min[0];
            float spanY = max[1] - min[1];
            float spanZ = max[2] - min[2];
            if (spanX <= 0.01f && spanY <= 0.01f && spanZ <= 0.01f) return null;
            return new ModelExtents(spanX, spanY, spanZ, (min[1] + max[1]) / 2.0f);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 取渲染器使用的实体模型。
     * <p>
     * 常规生物走 {@code LivingEntityRenderer},直接通过 {@link RenderLayerParent#getModel()} 取;
     * 末影龙这类自定义渲染器不实现该接口,模型是私有字段,这里反射兜底。
     * 结果按实体类型缓存,只在首次测量时做一次反射。
     */
    private static EntityModel<?> resolveModel(EntityRenderer<?, ?> renderer) {
        if (renderer instanceof RenderLayerParent<?, ?> parent) {
            return parent.getModel();
        }
        for (Class<?> c = renderer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!EntityModel.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    if (field.get(renderer) instanceof EntityModel<?> model) {
                        return model;
                    }
                } catch (Throwable ignored) {
                    // 反射不可用时退回碰撞箱取景,不影响功能
                }
            }
        }
        return null;
    }

    /** 模型几何包围盒尺寸(单位:格);centerY 为垂直中点。 */
    private record ModelExtents(float spanX, float spanY, float spanZ, float centerY) {
    }
}
