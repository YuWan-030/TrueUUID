package cn.alini.trueuuid;

import com.mojang.logging.LogUtils;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.server.TrueuuidRuntime;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Trueuuid.MODID)
public class Trueuuid {
    public static final String MODID = "trueuuid";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Trueuuid() {
        // 注册并生成 config/trueuuid-common.toml (Register and generate config/trueuuid-common.toml)
        TrueuuidConfig.register();

        // 初始化运行时单例（注册表、最近 IP 容错缓存等） (Initialize runtime singleton (registry, recent IP grace cache, etc.))
        TrueuuidRuntime.init();

        LOGGER.info("TrueUUID 已注册配置");
        LOGGER.info("TrueUUID 已经加载");
    }
}
