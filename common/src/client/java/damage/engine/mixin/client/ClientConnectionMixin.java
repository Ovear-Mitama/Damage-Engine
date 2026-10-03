package damage.engine.mixin.client;

import damage.engine.DamageEngineClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientConnectionMixin {

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void damageEngine$onGameJoin(CallbackInfo ci) {
        DamageEngineClient.serverHasMod = false;
        DamageEngineClient.serverModChecked = false;
        // 单机世界服务端一定有 mod，直接标记
        if (net.minecraft.client.Minecraft.getInstance().getSingleplayerServer() != null) {
            DamageEngineClient.serverHasMod = true;
            DamageEngineClient.serverModChecked = true;
            DamageEngineClient.joinCheckTicks = -1;
        } else {
            DamageEngineClient.joinCheckTicks = 40;
        }
    }
}
