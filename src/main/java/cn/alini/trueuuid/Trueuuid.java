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
        // 注册并生成 config/trueuuid-common.toml
        TrueuuidConfig.register();

        // 初始化运行时单例（注册表、最近 IP 容错缓存等）
        TrueuuidRuntime.init();

        // =====MoJang网络连通性测试=====
        try{
            java.net.URL url = new java.net.URL("https://sessionserver.mojang.com");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(3000); // 3秒超时
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                LOGGER.warn("无法连接到 Mojang 会话服务器 (sessionserver.mojang.com)，请检查网络连接。");
            } else {
                LOGGER.info("成功连接到 Mojang 会话服务器 (sessionserver.mojang.com)。");
            }
        } catch (Exception e) {
            LOGGER.error("连接 Mojang 会话服务器 (sessionserver.mojang.com) 时发生异常，请检查网络连接。", e);
        }

        LOGGER.info("TrueUUID 已经加载");
    }
}