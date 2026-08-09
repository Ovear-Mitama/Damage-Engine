package damage.engine;

import damage.engine.hud.DamageSessionManager;
import damage.engine.hud.DamageIndicator;
import damage.engine.hud.RatingManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.phys.EntityHitResult;

import io.netty.buffer.Unpooled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DamageEngineClient implements ClientModInitializer {
    public static KeyMapping configKeyBinding;
    public static KeyMapping toggleHudKeyBinding;
    public static KeyMapping clearDamageKeyBinding;
    
    public static volatile boolean serverHasMod = false;
    public static volatile boolean serverModChecked = false;
    public static int joinCheckTicks = -1;
    /** Platform-specific handshake sender, called by ClientTickMixin. */
    public static Runnable handshakeSender = null;
    
    public static final Logger LOGGER = LoggerFactory.getLogger("damage-engine");

    @Override
    public void onInitializeClient() {
        configKeyBinding = ClientKeybindings.configKeyBinding;
        toggleHudKeyBinding = ClientKeybindings.toggleHudKeyBinding;
        clearDamageKeyBinding = ClientKeybindings.clearDamageKeyBinding;
        KeyMapping.resetMapping();

        // Set up handshake sender for multiplayer server mod detection
        handshakeSender = () -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            ClientPlayNetworking.send(DamageEngine.HANDSHAKE_PACKET_ID, buf);
        };

        // Capture the game's real view+projection matrices right before entities are
        // drawn (BEFORE_ENTITIES). The fabric API guarantees matrixStack holds the
        // camera view matrix at this point, so the damage floats project exactly like
        // the world. Earlier capture points (e.g. inside renderLevel) grab a stale
        // modelview which collapses all floats to one screen position.
        net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents.BEFORE_ENTITIES.register(ctx -> {
            org.joml.Matrix4f proj = ctx.projectionMatrix();
            org.joml.Matrix4f view = new org.joml.Matrix4f(ctx.matrixStack().last().pose());
            damage.engine.hud.DamageIndicator.captureMatrices(
                proj != null ? new org.joml.Matrix4f(proj) : null, view);
        });

        // Register S2C damage packet handler
        ClientPlayNetworking.registerGlobalReceiver(DamageEngine.DAMAGE_PACKET_ID,
            (client, handler, buf, responseSender) -> {
                int entityId = buf.readVarInt();
                float amount = buf.readFloat();
                boolean isCrit = buf.readBoolean();
                int attackerId = buf.readVarInt();
                String debugInfo = buf.readUtf();
                double posX = buf.readDouble();
                double posY = buf.readDouble();
                double posZ = buf.readDouble();
                boolean isProjectile = buf.readBoolean();
                boolean killed = buf.readBoolean();

                if (!serverHasMod) {
                    serverHasMod = true;
                    serverModChecked = true;
                }

                client.execute(() -> {
                    if (client.level == null || client.player == null) return;

                    DamageEngineConfig config = DamageEngineConfig.getInstance();

                    if (config.debugShowDamageInfo) {
                        if (debugInfo != null && !debugInfo.isEmpty()) {
                            client.player.displayClientMessage(
                                Component.literal("[DE Debug] ").withStyle(s -> s.withColor(TextColor.fromRgb(0xB3EDC4)))
                                    .append(Component.literal(debugInfo).withStyle(s -> s.withColor(TextColor.fromRgb(0xFFFFFF)))),
                                false
                            );
                        }
                        client.player.displayClientMessage(
                            Component.literal("[DE Debug] ").withStyle(s -> s.withColor(TextColor.fromRgb(0xB3EDC4)))
                                .append(Component.literal("Dmg: " + String.format("%.1f", amount)
                                + (isCrit ? " Crit" : "") + " | Entity: " + entityId
                                + " | Projectile: " + (isProjectile ? "Yes" : "No")).withStyle(s -> s.withColor(TextColor.fromRgb(0xFFFFFF)))),
                            false
                        );
                    }

                    boolean isSelfDamage = attackerId == client.player.getId();

                    if (!isSelfDamage && config.showGlobalDamageIndicator) {
                        // Damage floats on other entities (victim isn't me) follow the "record other players" toggle.
                        // Only filter when an attacker was actually resolved (attackerId > 0): unresolved
                        // attackers (e.g. TACZ bullets whose owner can't be resolved) may still be our own damage.
                        if (attackerId > 0 && entityId != client.player.getId() && !config.recordOtherPlayers) return;
                        if (attackerId > 0 && client.level != null) {
                            net.minecraft.world.entity.Entity ae = client.level.getEntity(attackerId);
                            if (ae instanceof net.minecraft.world.entity.LivingEntity le && le.isInvisible()) {
                                return;
                            }
                        }

                        double dx = posX - client.player.getX();
                        double dy = posY - client.player.getY();
                        double dz = posZ - client.player.getZ();
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                        if (distance <= config.globalIndicatorMaxDistance) {
                            if (config.showDamageIndicator && amount > 0) {
                                DamageIndicator.addIndicator(entityId, posX, posY, posZ, amount, isCrit, killed);
                            }
                            if (config.showKillIndicator && killed) {
                                DamageIndicator.addIndicator(entityId, posX, posY, posZ, amount, false, true);
                            }
                        }
                    }

                    if (!isSelfDamage) {
                        if (config.recordOtherPlayers) {
                            DamageSessionManager.getInstance().addOtherPlayerDamage(amount, isCrit, attackerId);
                        }
                        return;
                    }

                    boolean preferSwitchTarget = false;
                    try {
                        if (client.hitResult instanceof EntityHitResult ehr) {
                            preferSwitchTarget = ehr.getEntity() != null && ehr.getEntity().getId() == entityId;
                        }
                    } catch (Exception ignored) {}

                    DamageSessionManager.getInstance().addDamage(amount, isCrit, entityId, preferSwitchTarget);

                    if (config.showDamageIndicator && amount > 0) {
                        DamageIndicator.addIndicator(entityId, posX, posY, posZ, amount, isCrit, killed);
                    }
                    if (config.showKillIndicator && killed) {
                        DamageIndicator.addIndicator(entityId, posX, posY, posZ, amount, false, true);
                    }

                    if (config.debugShowRating && client.player != null) {
                        RatingManager rm = RatingManager.getInstance();
                        if (rm.isVisible()) {
                            client.player.displayClientMessage(
                                Component.literal("[DE Debug] ").withStyle(s -> s.withColor(TextColor.fromRgb(0xB3EDC4)))
                                    .append(Component.literal("Rating: " + rm.getGrade() + " | Score: " + String.format("%.1f", rm.getScore())).withStyle(s -> s.withColor(TextColor.fromRgb(0xFFFFFF)))),
                                true
                            );
                        }
                    }
                });
            });
    }
    
}
