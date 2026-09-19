package damage.engine.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.AgeableWaterCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
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
 * 取景规则(尺寸以"模型静态几何"为准,不可测时才回退到碰撞箱):
 * <ul>
 *   <li>碰撞箱按 vanilla {@code InventoryScreen} / Damage-Indicators 的做法先归一到 scale=1,
 *       消除幼年、史莱姆体积等自身缩放对取景比例的干扰;</li>
 *   <li>幻翼、鱿鱼这类模型远宽于碰撞箱的生物,只用碰撞箱会导致横向被裁,因此量取模型真实几何;
 *       末影龙这类"碰撞箱远大于可见模型"的则相反,只用碰撞箱会让头像明显偏小;</li>
 *   <li>垂直方向固定按碰撞箱中心居中——模型原点在各模组间并不统一,按模型高度居中有可能把实体顶出框外。</li>
 * </ul>
 */
public final class EntityIconRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");

    /**
     * 实体在渲染框内占据的比例。
     * <p>
     * 渲染框已按 DamageHud 的 {@code ENTITY_ICON_OVERSIZE} 放大(离屏纹理按框尺寸生成,
     * 框比头像槽大是为了给模型留出溢出的余地、避免被裁边),这里按框的尺寸折算,
     * 保证实体在头像槽里的实际大小与其他版本一致。
     */
    private static final float FILL_RATIO = 0.525f;

    /** 垂直微调,与 Damage-Indicators 的 offsetY 取值一致(模型不可测时的回退分支使用)。 */
    private static final float CENTER_OFFSET_Y = 0.0625f;

    /** 原版实体渲染把模型沿 Y 下移 1.501 格(脚底对齐模型原点),用它作为垂直居中的基准。 */
    private static final float MODEL_VERTICAL_PIVOT = 1.501f;

    /** 模型几何相对碰撞箱的上限倍数:避免个别模型的异常值把头像压成看不见的一点。 */
    private static final float MODEL_EXTENT_CAP = 3.0f;

    /**
     * 取景时"模型前后长度"的折减系数。
     * 头像里前后方向是被透视压缩的,若把体长原样计入取景尺寸,长体型生物(末影龙)会被压得很小;
     * 折减后允许头尾略微溢出,换来主体(翼展)明显变大。常规生物体长远小于身高,不受影响。
     */
    private static final float DEPTH_SHRINK = 0.6f;

    /**
     * 末影龙这类"翼展/体长远大于身躯"的生物的额外放大系数。
     * <p>
     * 末影龙翼展(约 15.5 格)和体长(约 16 格)远大于身躯(约 2.4 格),按整体几何取景时
     * 取景尺寸被翼展/体长占满,画出来只剩一条十几像素高的细线,看上去"特别小"。
     * <p>
     * 但头像离屏纹理是按渲染框尺寸生成的,放得过大反而会被切边,
     * 所以这里只补到"翼展刚好填满渲染框"为止:FILL_RATIO × 该系数 ≈ 1。
     */
    private static final float WINGED_EXTRA_SCALE = 1.25f;

    /** 判定"翼展主导"的阈值:横向跨度超过竖向跨度的该倍数时,认为主体会被翼展压小。 */
    private static final float WING_SPAN_RATIO = 4.0f;

    /**
     * 动物类(牛/猪/羊/鸡/狼/鱿鱼等)的额外收缩系数。
     * <p>
     * 这些生物的模型相对碰撞箱偏"方",按统一取景比例画出来会比同尺寸的怪物更占地方,
     * 与怪物混在一起时显得过大,故单独把它们的取景尺寸放大一点(等价于整体画小一些)。
     */
    private static final float ANIMAL_SHRINK = 1.25f;

    /**
     * 模型几何缓存(按实体类型)。
     * 模型几何是静态的,缓存除了省开销,更重要的是避免逐帧读取"当前动画姿态"导致取景比例随行走摆动而抖动。
     */
    private static final Map<EntityType<?>, ModelExtents> MODEL_CACHE = new HashMap<>();

    /** 诊断用:上次打印取景信息的实体 id(避免刷屏) */
    private static int lastLogId = -1;

    private EntityIconRenderer() {
    }

    /**
     * 将实体渲染到 HUD 的指定方形区域内。必须在渲染状态提取阶段调用。
     *
     * @param x             区域左上角 x(屏幕坐标,不套用当前 pose)
     * @param y             区域左上角 y(屏幕坐标,不套用当前 pose)
     * @param size          区域边长(像素)
     * @param alpha         整体透明度(0~1;当前版本的 GUI 实体管线不支持整体透明度,仅用作可见性阈值)
     * @param followRotation true = 跟随实际朝向(以玩家视角为基准),false = 按自定义角度旋转
     * @param customAngle    自定义朝向角度(0~360,0 = 正面朝向观察者,顺时针增大;仅 followRotation=false 时生效)
     */
    public static void render(GuiGraphicsExtractor guiGraphics, LivingEntity entity, int x, int y, int size,
                              float alpha, boolean followRotation, int customAngle) {
        if (guiGraphics == null || entity == null || size <= 0 || alpha <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        try {
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            EntityRenderState state = dispatcher.extractEntity(entity, 1.0f);

            float bbW;
            float bbH;
            if (state instanceof LivingEntityRenderState livingState) {
                // 只改渲染状态里的朝向,不改世界中的实体:
                // 跟随模式以玩家视角为基准(生物正对你时显示正面,背对时显示背面);
                // 自定义模式按配置角度摆姿势:0° = 正面朝向观察者(等价原「固定」),角度增大为顺时针。
                livingState.bodyRot = followRotation ? (livingState.bodyRot - mc.player.getYRot()) : (180.0f + customAngle);

                // 与 vanilla InventoryScreen / Damage-Indicators 一致:碰撞箱归一到 scale=1,
                // 因为渲染管线会再乘一次实体自身缩放,不归一会让同一取景比例下的实际大小随实体缩放变化。
                float entityScale = livingState.scale > 0.01f ? livingState.scale : 1.0f;
                bbW = livingState.boundingBoxWidth / entityScale;
                bbH = livingState.boundingBoxHeight / entityScale;
                livingState.boundingBoxWidth = bbW;
                livingState.boundingBoxHeight = bbH;
                livingState.scale = 1.0f;
            } else {
                // 末影龙等使用专用渲染器的实体,渲染状态不是 LivingEntityRenderState,
                // 既没有 bodyRot 也没有 scale 可调,朝向由飞行轨迹决定,这里只按碰撞箱取景。
                bbW = state.boundingBoxWidth;
                bbH = state.boundingBoxHeight;
            }

            // 取景尺寸优先用模型真实几何:碰撞箱有时远大于可见模型
            // (末影龙碰撞箱 16x8,实际翼展只有几格),只按碰撞箱取景会让这类生物的头像明显偏小。
            // 模型不可测时才回退到碰撞箱公式;上限仍按碰撞箱收紧,避免异常模型把头像压成一点。
            ModelExtents model = modelExtents(entity, dispatcher);
            float dim;
            float translateY;
            float boost = 1.0f;
            if (model != null) {
                // 取景尺寸:横向取"模型宽度"与折减后的"前后长度"的较大者,纵向取模型高度。
                // 体长单独折减,避免末影龙这类长体型生物被体长撑小(见 DEPTH_SHRINK 说明)。
                float horizontal = Math.max(model.spanX(), model.spanZ() * DEPTH_SHRINK);
                dim = Math.max(horizontal, model.spanY());
                dim = Math.min(dim, Math.max(bbW, bbH) * MODEL_EXTENT_CAP);
                // 垂直居中按"模型自身的垂直中点"算,而不是碰撞箱中心。
                // 原版实体渲染会把模型沿 Y 下移 1.501 格(脚底对齐原点),故中点落在框心对应的位移是 1.501 - 中点;
                // 末影龙这类模型重心与碰撞箱差很多,用碰撞箱中心会把模型整个顶出框外。
                translateY = MODEL_VERTICAL_PIVOT - model.centerY();
                // 横向跨度被翼展/体长主导时(末影龙),按整体几何取景只剩一条细线,补到填满渲染框
                if (horizontal > model.spanY() * WING_SPAN_RATIO) boost = WINGED_EXTRA_SCALE;
            } else {
                dim = Math.max(bbW * 1.15f, bbH) * 1.1f;
                translateY = bbH / 2.0f + CENTER_OFFSET_Y;
            }
            if (dim <= 0.05f) dim = 1.0f;
            // 动物类整体再收一档,避免和怪物放在一起时显得过大
            if (isAnimal(entity)) dim *= ANIMAL_SHRINK;
            float scale = size * FILL_RATIO * boost / dim;

            Vector3f translate = new Vector3f(0.0f, translateY, 0.0f);

            // 与原版 InventoryScreen 一致:绕 Z 轴 180°(GUI 坐标翻转)
            Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
            // 覆盖相机朝向,使火焰等公告板元素朝向 GUI 观察者
            Quaternionf cameraAngle = new Quaternionf();

            if (entity.getId() != lastLogId) {
                lastLogId = entity.getId();
                LOGGER.info("[DE] icon: 取景 id={} 类型={} follow={} 归一包围箱={}x{} 模型XYZ={}x{}x{} 中点Y={} dim={} 位移Y={} scale={}",
                    entity.getId(), entity.getType().getDescription().getString(), followRotation,
                    String.format("%.2f", bbW),
                    String.format("%.2f", bbH),
                    model != null ? String.format("%.2f", model.spanX()) : "n/a",
                    model != null ? String.format("%.2f", model.spanY()) : "n/a",
                    model != null ? String.format("%.2f", model.spanZ()) : "n/a",
                    model != null ? String.format("%.2f", model.centerY()) : "n/a",
                    String.format("%.2f", dim),
                    String.format("%.2f", translateY),
                    String.format("%.2f", scale));
            }

            guiGraphics.entity(state, scale, translate, rotation, cameraAngle, x, y, x + size, y + size);
        } catch (Throwable t) {
            LOGGER.warn("[DE] 实体头像渲染失败: {}", t.toString());
        }
    }

    /** 动物类(含鱿鱼/海豚/鱼等水生动物)判定,用于取景时再收一档。 */
    private static boolean isAnimal(LivingEntity entity) {
        return entity instanceof Animal
            || entity instanceof AgeableWaterCreature
            || entity instanceof WaterAnimal;
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
