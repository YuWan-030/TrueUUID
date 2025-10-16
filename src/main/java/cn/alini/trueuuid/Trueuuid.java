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
        // 若开启 nomojang，则跳过启动时的 Mojang 网络连通性检测
        if (TrueuuidConfig.nomojangEnabled()) {
            LOGGER.info("nomojang 已启用，跳过 Mojang 会话服务器连通性检测");
        } else {
            // =====MoJang网络连通性测试=====
            try {
                String testUrl = "https://sessionserver.mojang.com/session/minecraft/hasJoined?username=Mojang&serverId=test";
                java.net.URL url = new java.net.URL(testUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000); // 3秒超时
                conn.setReadTimeout(3000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 204 || responseCode == 403) {
                    LOGGER.info("成功连接到 Mojang 会话服务器 (sessionserver.mojang.com)，响应码: {}", responseCode);
                } else {
                    LOGGER.warn("Mojang 会话服务器响应异常，响应码: {}", responseCode);
                }
            } catch (Exception e) {
                LOGGER.error("无法连接到 Mojang 会话服务器 (sessionserver.mojang.com)，请检查网络连接或防火墙设置。", e);
            }
        }

        LOGGER.info("TrueUUID 已经加载");
    }
}