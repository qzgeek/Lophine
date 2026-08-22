package fun.bm.lophine.config.modules.optimizations;

import me.earthme.luminol.config.IConfigModule;
import me.earthme.luminol.config.flags.ConfigClassInfo;
import me.earthme.luminol.config.flags.ConfigInfo;
import me.earthme.luminol.enums.EnumConfigCategory;

@ConfigClassInfo(category = EnumConfigCategory.OPTIMIZATIONS, name = "player_data")
public class PlayerDataConfig implements IConfigModule {
    @ConfigInfo(name = "async_save_enabled", comments =
            """
                    异步保存玩家数据：序列化在区域线程完成，压缩+写盘+rename 在后台线程执行，
                    消除自动保存/退服时的同步磁盘卡顿（HDD 上尤其明显）。
                    关闭后回退到原版同步保存。""")
    public static boolean asyncSaveEnabled = true;
}
