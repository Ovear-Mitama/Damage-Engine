package damage.engine.compat.tacz;

/**
 * Fabric-side hook for TaCZ.
 *
 * <p>这里原本订阅 TaCZ 官方 {@code EntityHurtByGunEvent.PRE} Fabric 事件,拿到 TaCZ 自己的
 * 爆头判定({@code isHeadShot()})并把受击者标记给共享的伤害统计,使 TaCZ 爆头算作暴击。</p>
 *
 * <p>但 TaCZ 本体只有 Forge 版;Fabric 端的 "TaCZ: Refabricated" 目前只有 1.20.1 / 1.21.1
 * 构建,<b>没有 1.19.2</b>(见 Modrinth 项目 tacz-refabricated)。所以 1.19 的 Fabric 端无法
 * 编译这个事件类型,这里只保留 {@link #init()} 入口;爆头→暴击仅 Forge 端可用。</p>
 *
 * <p>不依赖该事件的兼容(把 TaCZ 子弹识别为远程伤害、从子弹反查射手的)走
 * {@link TaczCompat} 的反射路径,Fabric / Forge 都生效。</p>
 */
public final class TaczFabricCompat {
    private TaczFabricCompat() {}

    public static void init() {
        // TaCZ: Refabricated 没有 1.19.2 构建,无法订阅其 Fabric 事件。
    }
}
