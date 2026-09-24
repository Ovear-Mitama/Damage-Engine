package damage.engine.hud;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import damage.engine.compat.GuiGraphics;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Slime;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 实体头像渲染器:把 3D 实体直接渲染到 HUD 的信息面板上。
 * <p>
 * 采用与原版 {@code InventoryScreen.renderEntityInInventory} 相同的做法:
 * 复用 GUI 的投影与 {@link GuiGraphics#pose()},把实体模型直接画进 GUI。
 * <p>
 * 取景不依赖碰撞箱,而是遍历模型的 {@link ModelPart} 求出真实几何范围,
 * 这样幼年生物(幼年变换发生在 {@code renderToBuffer} 内部)、幻翼等
 * "模型尺寸与碰撞箱差异较大"的情况都能正确取景。
 * <p>
 * 关键点:几何范围按<b>初始(未动画)姿态</b>测量并按实体类型缓存,
 * 否则走路时四肢摆动会让取景尺寸逐帧变化,头像就会跟着一胀一缩地抖动。
 */
public final class EntityIconRenderer {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");

    /** 原版 GUI 深度:实体画在 z=50(比面板背景 z=0 更靠近观察者,因此覆盖在面板之上)。 */
    private static final double GUI_Z = 50.0;
    /** 实体在头像框内占据的比例(超出部分由上层文本/血条盖住,外层 scissor 只做兜底限制)。 */
    private static final float FILL_RATIO = 0.98f;

    /**
     * 幼年补偿系数:{@link AgeableListModel} 的幼年缩放不在 ModelPart 树内,
     * 其合成效果为"以脚底为基准整体收缩"(头部 1.5/babyHeadScale、身体 1/babyBodyScale,
     * 默认参数下约 0.56),这里按其等效比例做补偿。
     */
    private static final float BABY_EXTENT_FACTOR = 0.5625f;

    /**
     * 动物类(牛/猪/羊/鸡/狼/鱿鱼等)的额外收缩系数。
     * <p>
     * 这些生物的模型相对碰撞箱偏"方",按统一取景比例画出来会比同尺寸的怪物更占地方,
     * 与怪物混在一起时显得过大,故单独把它们的取景尺寸放大一点(等价于整体画小一些)。
     */
    private static final float ANIMAL_SHRINK = 1.25f;

    /**
     * 史莱姆 / 岩浆怪的额外收缩系数。
     * <p>
     * 它们是实心方块 —— 模型在三个方向都填满取景框,而人形、动物都是细长或凹凸的轮廓,
     * 按同一个取景比例画出来会比同尺寸的其它生物更"占地",所以单独再收一档。
     */
    private static final float SLIME_SHRINK = 1.3f;

    /** 模型几何缓存(按实体类型 + 是否幼年);几何是静态的,缓存后取景比例不随行走摆动而抖动。 */
    private static final Map<CacheKey, float[]> MODEL_CACHE = new HashMap<>();

    /** 诊断用:上次打印取景信息的实体 id(避免刷屏) */
    private static int lastLogId = -1;

    private EntityIconRenderer() {
    }

    private record CacheKey(EntityType<?> type, boolean baby) {
    }

    /**
     * 将实体渲染到 HUD 的指定方形区域内。必须在渲染线程的 HUD 阶段调用。
     *
     * @param x             区域左上角 x
     * @param y             区域左上角 y
     * @param size          区域边长(像素)
     * @param alpha         整体透明度(0~1)
     * @param followRotation true = 跟随实际朝向(以玩家视角为基准),false = 按自定义角度旋转
     * @param customAngle    自定义朝向角度(0~360,0 = 正面朝向观察者,顺时针增大;仅 followRotation=false 时生效)
     */
    public static void render(PoseStack pose, LivingEntity entity, int x, int y, int size,
                              float alpha, boolean followRotation, int customAngle) {
        GuiGraphics guiGraphics = GuiGraphics.of(pose);
        if (guiGraphics == null || entity == null || alpha <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        try {
            EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
            var camera = mc.gameRenderer.getMainCamera();
            dispatcher.prepare(mc.level, camera, mc.getCameraEntity());
            // 碰撞箱开关是渲染器上的全局状态:按 F3+B 打开后,若这里只置 false 而不还原,
            // 之后每帧的世界渲染都会读到 false,碰撞箱会被"自动关闭",故先记录原值以便还原。
            boolean savedRenderHitBoxes = dispatcher.shouldRenderHitBoxes();
            dispatcher.setRenderShadow(false);
            dispatcher.setRenderHitBoxes(false);

            // 保存实体朝向,渲染时临时覆盖(渲染完还原,避免影响世界渲染)
            float savedBodyRot = entity.yBodyRot;
            float savedBodyRotO = entity.yBodyRotO;
            float savedYRot = entity.getYRot();
            float savedYRotO = entity.yRotO;
            float savedXRot = entity.getXRot();
            float savedXRotO = entity.xRotO;
            float savedYHeadRot = entity.yHeadRot;
            float savedYHeadRotO = entity.yHeadRotO;

            // 跟随模式以玩家视角为基准:生物正对你时图标里是正面,转身背对时显示背面;
            // 自定义模式按配置角度摆姿势:0° = 正面朝向观察者(等价原「固定」),角度增大为顺时针。
            // 注:这里用减号——GUI 里实体被 z 轴负缩放镜像过,角度按"加"会看到逆时针,减号才是顺时针。
            float yawOffset = followRotation ? -mc.player.getYRot() : ((180.0f - customAngle) - savedBodyRot);
            entity.yBodyRot = savedBodyRot + yawOffset;
            entity.yBodyRotO = savedBodyRotO + yawOffset;
            entity.setYRot(savedYRot + yawOffset);
            entity.yRotO = savedYRotO + yawOffset;
            entity.yHeadRot = savedYHeadRot + yawOffset;
            entity.yHeadRotO = savedYHeadRotO + yawOffset;
            entity.setXRot(savedXRot);
            entity.xRotO = savedXRotO;

            // 用模型真实几何取景(比碰撞箱更贴合实际绘制尺寸)
            float[] bounds = computeModelBounds(entity, savedBodyRot + yawOffset);
            // 动物类、史莱姆 / 岩浆怪整体再收一档,避免和怪物放在一起时显得过大
            float sizeFactor = entity instanceof Slime ? SLIME_SHRINK
                : (isAnimal(entity) ? ANIMAL_SHRINK : 1.0f);
            float focusX;
            float focusY;
            float scale;
            if (bounds != null) {
                focusX = bounds[0];
                focusY = bounds[1];
                // computeModelBounds 返回 {centerX, centerY, centerZ, extent},取景尺寸必须用 extent(下标 3)。
                // 曾误用下标 2(centerZ):僵尸这类 Z 中心接近 0 的模型取到 0,除法被钳到 scale 上限,
                // 模型因此被放大约 5 倍、撑满面板。
                scale = Mth.clamp(size * FILL_RATIO / (Math.max(bounds[3], 0.05f) * sizeFactor), 0.3f, 64.0f);
            } else {
                // 退化:按碰撞箱取景
                float height = Math.max(entity.getBbHeight(), 0.1f);
                float width = Math.max(entity.getBbWidth(), 0.05f);
                focusX = 0.0f;
                focusY = height * 0.5f;
                scale = Mth.clamp(size * FILL_RATIO / (Math.max(height, width) * sizeFactor), 0.3f, 64.0f);
            }

            if (entity.getId() != lastLogId) {
                lastLogId = entity.getId();
                LOGGER.info("[DE] icon: 取景 id={} 类型={} yaw={} 模型={} 碰撞箱高={} scale={}",
                    entity.getId(), entity.getType().getDescription().getString(),
                    String.format("%.1f", savedBodyRot + yawOffset),
                    bounds == null ? "无(退化)"
                        : String.format("center=(%.3f,%.3f) extent=%.3f", bounds[0], bounds[1], bounds[3]),
                    String.format("%.3f", entity.getBbHeight()),
                    String.format("%.2f", scale));
            }

            // 与原版一致:基础角度为绕 Z 轴 180°,配合 z 轴负缩放修正朝向
            Quaternionf angle = new Quaternionf().rotateZ((float) Math.PI);

            pose.pushPose();
            pose.translate(x + size / 2.0f, y + size / 2.0f, GUI_Z);
            pose.scale(scale, scale, -scale);
            pose.translate(focusX, focusY, 0.0f);
            pose.mulPose(angle);

            Lighting.setupForEntityInInventory();
            // 覆盖相机朝向:1.20.1 的 renderFlame 直接把它当作公告板旋转(mulPose)。
            // 不能传 pose 的逆——那是绕 Z 轴 180°,会把火焰面片翻到背面(被面剔除)导致完全不显示;
            // 这里改传一个合法的 Y 轴 180°:水平旋转不歪斜,且让面片朝向 GUI 观察者。
            dispatcher.overrideCameraOrientation(new Quaternionf().rotateY((float) Math.PI));

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            try {
                RenderSystem.runAsFancy(() -> dispatcher.render(
                    entity, 0.0, 0.0, 0.0, 0.0f, 1.0f, pose, guiGraphics.bufferSource(), 0xF000F0));
                guiGraphics.flush();
            } finally {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                pose.popPose();
                dispatcher.setRenderShadow(true);
                // 还原 F3+B 的碰撞箱开关:否则头像渲染会把原版碰撞箱显示永久关掉
                dispatcher.setRenderHitBoxes(savedRenderHitBoxes);

                entity.yBodyRot = savedBodyRot;
                entity.yBodyRotO = savedBodyRotO;
                entity.setYRot(savedYRot);
                entity.yRotO = savedYRotO;
                entity.setXRot(savedXRot);
                entity.xRotO = savedXRotO;
                entity.yHeadRot = savedYHeadRot;
                entity.yHeadRotO = savedYHeadRotO;

                // 还原渲染器相机朝向,避免影响后续世界渲染
                dispatcher.prepare(mc.level, camera, mc.getCameraEntity());
            }
        } catch (Throwable t) {
            LOGGER.warn("[DE] 实体头像渲染失败: {}", t.toString());
        }
    }

    /**
     * 由模型几何求取景参数(模型几何按实体类型缓存,只与朝向/缩放相关地换算)。
     *
     * @return {centerX, centerY, extent},单位:块,位于实体本地空间(脚底约 y=0、+Y 向上);
     *         无法取到几何时返回 null
     * @param renderedBodyRot 渲染时使用的身体朝向,用于把模型内的偏移一起旋转
     */
    private static float[] computeModelBounds(LivingEntity entity, float renderedBodyRot) {
        float[] raw = rawBounds(entity);
        if (raw == null) return null;

        float px = raw[0];
        float py = raw[1];
        float pz = raw[2];
        float extent = raw[3];

        // 模型渲染空间 -> 实体本地空间:
        // 原版 LivingEntityRenderer 依次执行 scale(-1,-1,1) / scale(entityScale) / translate(0,-1.501,0)
        // / 按身体朝向绕 Y 旋转,PoseStack 后置相乘,因此点先被旋转、再平移、最后缩放:
        //   x' = -s * xr,  y' = 1.501 * s - s * y,  z' = s * zr
        // 总缩放 s 同时乘在几何和那 1.501 的位移上(平移先施加到顶点、缩放后施加),所以取景尺寸
        // extent 也必须一起乘 s:否则"渲染器按体积放大"的生物(史莱姆 / 岩浆怪)会按放大倍数撑爆头像
        // (体积 4 的史莱姆被放大 4 倍,这就是之前史莱姆过大的原因)。
        float s = renderScale(entity);
        float rotDeg = 180.0f - renderedBodyRot;
        Vector3f rotated = new Vector3f(px, py, pz)
            .rotate(new Quaternionf().rotateY((float) Math.toRadians(rotDeg)));
        float cx = -s * rotated.x();
        float cy = 1.501f * s - s * rotated.y();
        float cz = s * rotated.z();
        if (entity.isBaby()) {
            // 幼年:measureModel 里那套补偿只收缩了"取景尺寸"(让幼体在头像里显得小),
            // 而渲染器实际是按 getScale 把模型整体缩小、并把脚底锚定在本地 y=0 的,
            // 所以真实几何的竖直中心只有收缩后中心的一半高度 —— 沿用收缩后的中心
            // 会让幼体在头像框里整体偏上(用户反馈:僵尸幼体位置偏高)。
            // 这里改用真实渲染高度的一半(以碰撞箱高度近似)。
            cy = entity.getBbHeight() * 0.5f;
        }
        return new float[]{cx, cy, cz, extent * s};
    }

    /**
     * 渲染器施加在模型上的总缩放。
     * <p>
     * 取 {@code max(getScale, 包围箱比值)} 且不低于 1:趴下、睡觉、幼年等姿态会让包围箱变小,
     * 那并不代表渲染器把模型缩小了,跟着缩小取景只会让头像忽大忽小,所以这里只放大、不缩小。
     */
    private static float renderScale(LivingEntity entity) {
        float selfScale = Math.max(entity.getScale(), 0.01f);
        if (entity instanceof Slime slime) {
            // 1.19 里史莱姆的包围箱是 type 基准(2.04) × 0.255 × 体积,和渲染器实际施加的
            // 体积缩放(× 体积)不成比例,靠上面的比值只能反推出约 1.02,量不出真正的放大倍数;
            // 渲染器用的就是这个体积值,直接取它。
            return Math.max(1f, Math.max(selfScale, slime.getSize()));
        }
        try {
            EntityDimensions base = entity.getType().getDimensions();
            float ratioH = entity.getBbHeight() / Math.max(base.height, 0.01f);
            float ratioW = entity.getBbWidth() / Math.max(base.width, 0.01f);
            return Math.max(1f, Math.max(selfScale, Math.max(ratioH, ratioW)));
        } catch (Throwable t) {
            return Math.max(1f, selfScale);
        }
    }

    /** 取模型的几何包围盒 {centerX, centerY, centerZ, extent}(模型空间,单位块,已含幼年补偿)。 */
    private static float[] rawBounds(LivingEntity entity) {
        CacheKey key = new CacheKey(entity.getType(), entity.isBaby());
        float[] cached = MODEL_CACHE.get(key);
        if (cached != null) return cached;
        float[] measured = measureModel(entity);
        if (measured != null) MODEL_CACHE.put(key, measured);
        return measured;
    }

    /**
     * 用模型的初始(未动画)姿态测量几何包围盒。
     * <p>
     * 先快照全部部位的姿态,复位到初始姿态测量,最后原样还原,避免影响正常渲染;
     * 这样取景尺寸与当前动画进度无关,头像大小不会随行走摆动一胀一缩。
     */
    private static float[] measureModel(LivingEntity entity) {
        try {
            EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
            EntityModel<?> model = resolveModel(renderer);
            if (model == null) return null;

            List<ModelPart> roots = visitRoots(model);
            List<ModelPart> all = collectParts(model);
            if (roots.isEmpty() || all.isEmpty()) return null;

            PartPose[] saved = new PartPose[all.size()];
            for (int i = 0; i < all.size(); i++) {
                saved[i] = all.get(i).storePose();
            }

            float[] mm = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                          -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            int[] count = {0};
            try {
                for (ModelPart part : all) {
                    // 只归零动画旋转,保留部位自身的默认偏移。
                    // 模型的骨架布局靠部位偏移承载(例如躯干 offset(0,12,0)、四肢同理),
                    // 用 resetPose() 会把偏移和旋转一起清零,整个模型塌到原点,
                    // 量出来的取景尺寸被严重低估(约 0.7 格,真实约 2 格),
                    // 结果就是头像里的僵尸/骷髅被放大 2 倍多、撑满整个面板。
                    part.xRot = 0.0f;
                    part.yRot = 0.0f;
                    part.zRot = 0.0f;
                }
                for (ModelPart part : roots) {
                    part.visit(new PoseStack(), (pose, path, index, cube) -> {
                        count[0]++;
                        accumulateCube(mm, pose.pose(), cube);
                    });
                }
            } finally {
                for (int i = 0; i < all.size(); i++) {
                    all.get(i).loadPose(saved[i]);
                }
            }

            if (count[0] == 0 || mm[0] > mm[3]) return null;

            float x0 = mm[0], y0 = mm[1], z0 = mm[2];
            float x1 = mm[3], y1 = mm[4], z1 = mm[5];

            // 幼年补偿:AgeableListModel 的幼年缩放发生在 renderToBuffer 内部(不在 ModelPart 树里),
            // 等效于"以脚底(=模型空间中 Y 最大处)为基准收缩",故按下端固定做缩放。
            if (entity.isBaby() && model instanceof AgeableListModel) {
                float feet = y1;
                y0 = feet - (feet - y0) * BABY_EXTENT_FACTOR;
                x0 *= BABY_EXTENT_FACTOR;
                x1 *= BABY_EXTENT_FACTOR;
                z0 *= BABY_EXTENT_FACTOR;
                z1 *= BABY_EXTENT_FACTOR;
            }

            float px = (x0 + x1) * 0.5f;
            float py = (y0 + y1) * 0.5f;
            float pz = (z0 + z1) * 0.5f;
            float extent = Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0));
            if (extent <= 0.0f) return null;
            return new float[]{px, py, pz, extent};
        } catch (Throwable t) {
            return null;
        }
    }

    /** 需要遍历的根部件(层级模型只有根,人形模型为各公开部件)。 */
    private static List<ModelPart> visitRoots(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchical) {
            ModelPart root = hierarchical.root();
            return root == null ? List.of() : List.of(root);
        }
        if (model instanceof HumanoidModel<?> humanoid) {
            List<ModelPart> roots = new ArrayList<>(7);
            for (ModelPart part : humanoidParts(humanoid)) {
                if (part != null) roots.add(part);
            }
            return roots;
        }
        return List.of();
    }

    /** 全部部件(含子部件),用于姿态快照/复位。 */
    private static List<ModelPart> collectParts(EntityModel<?> model) {
        List<ModelPart> all = new ArrayList<>();
        for (ModelPart root : visitRoots(model)) {
            root.getAllParts().forEach(all::add);
        }
        return all;
    }

    private static ModelPart[] humanoidParts(HumanoidModel<?> humanoid) {
        return new ModelPart[]{humanoid.head, humanoid.hat, humanoid.body,
            humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg};
    }

    /** 动物类(含鱿鱼/鱼/海豚等水生动物)判定,用于取景时再收一档。 */
    private static boolean isAnimal(LivingEntity entity) {
        return entity instanceof Animal || entity instanceof WaterAnimal;
    }

    /**
     * 取渲染器持有的实体模型。
     * <p>
     * 常规生物走 {@link LivingEntityRenderer#getModel()};末影龙这类专用渲染器
     * (模型字段为私有且不暴露 getter)通过反射扫描字段获取,避免退化成按碰撞箱取景
     * ——龙的碰撞箱远大于实际模型,会导致图标里的龙特别小。
     */
    private static EntityModel<?> resolveModel(EntityRenderer<?> renderer) {
        if (renderer instanceof LivingEntityRenderer<?, ?> living) return living.getModel();
        for (Class<?> c = renderer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                if (!EntityModel.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    if (field.get(renderer) instanceof EntityModel<?> model) return model;
                } catch (Throwable ignored) {
                    // 反射不可用时退回碰撞箱取景,不影响功能
                }
            }
        }
        return null;
    }

    /** 把立方体的 8 个角变换到渲染空间并并入包围盒(Cube 坐标单位为 1/16 块)。 */
    private static void accumulateCube(float[] mm, Matrix4f matrix, ModelPart.Cube cube) {
        float[] xs = {cube.minX / 16.0f, cube.maxX / 16.0f};
        float[] ys = {cube.minY / 16.0f, cube.maxY / 16.0f};
        float[] zs = {cube.minZ / 16.0f, cube.maxZ / 16.0f};
        for (float cx : xs) {
            for (float cy : ys) {
                for (float cz : zs) {
                    Vector4f v = new Vector4f(cx, cy, cz, 1.0f).mul(matrix);
                    mm[0] = Math.min(mm[0], v.x());
                    mm[1] = Math.min(mm[1], v.y());
                    mm[2] = Math.min(mm[2], v.z());
                    mm[3] = Math.max(mm[3], v.x());
                    mm[4] = Math.max(mm[4], v.y());
                    mm[5] = Math.max(mm[5], v.z());
                }
            }
        }
    }
}
