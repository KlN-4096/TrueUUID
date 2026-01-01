package cn.alini.trueuuid;

import com.mojang.logging.LogUtils;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.server.TrueuuidRuntime;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Arrays;

@Mod(Trueuuid.MODID)
public class Trueuuid {
    public static final String MODID = "trueuuid";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static boolean isDebug() {
        try {
            return TrueuuidConfig.debug();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void debug(String message, Object... args) {
        if (!isDebug()) return;
        LOGGER.debug(prefixDebug(message), args);
    }

    public static void debug(Throwable t, String message, Object... args) {
        if (!isDebug()) return;
        LOGGER.debug(prefixDebug(message), appendArg(args, t));
    }

    public static void info(String message, Object... args) {
        LOGGER.info(prefix(message), args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(prefix(message), args);
    }

    public static void warn(Throwable t, String message, Object... args) {
        // 只在 debug 模式输出堆栈，避免污染 latest.log；想看堆栈请看 debug.log
        if (isDebug()) {
            LOGGER.debug(prefixDebug(message), appendArg(args, t));
        }
        LOGGER.warn(prefix(message) + " ({})", appendArg(args, brief(t)));
    }

    public static void error(String message, Object... args) {
        LOGGER.error(prefix(message), args);
    }

    public static void error(Throwable t, String message, Object... args) {
        // 只在 debug 模式输出堆栈，避免污染 latest.log；想看堆栈请看 debug.log
        if (isDebug()) {
            LOGGER.debug(prefixDebug(message), appendArg(args, t));
        }
        LOGGER.error(prefix(message) + " ({})", appendArg(args, brief(t)));
    }

    public Trueuuid() {
        // 注册并生成 config/trueuuid-common.toml
        TrueuuidConfig.register();

        // 初始化运行时单例（注册表、最近 IP 容错缓存等）
        TrueuuidRuntime.init();

        // =====MoJang网络连通性测试=====
        // 若开启 nomojang，则跳过启动时的 Mojang 网络连通性检测
        if (TrueuuidConfig.nomojangEnabled()) {
            info("nomojang 已启用，跳过 Mojang 会话服务器连通性检测");
        } else {
            // =====MoJang网络连通性测试=====
            try {
                String testUrl = TrueuuidConfig.COMMON.mojangReverseProxy.get()+"/session/minecraft/hasJoined?username=Mojang&serverId=test";
                java.net.URL url = new java.net.URL(testUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000); // 3秒超时
                conn.setReadTimeout(3000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == 200 || responseCode == 204 || responseCode == 403) {
                    info("成功连接到 Mojang 会话服务器，响应码: {}", responseCode);
                } else {
                    warn("Mojang 会话服务器响应异常，响应码: {}", responseCode);
                }
            } catch (Exception e) {
                error(e, "无法连接到 Mojang 会话服务器，请检查网络连接或防火墙设置");
            }
        }

        info("TrueUUID 已经加载");
    }

    private static String prefix(String message) {
        return "[TrueUUID] " + message;
    }

    private static String prefixDebug(String message) {
        return "[TrueUUID][DEBUG] " + message;
    }

    private static Object[] appendArg(Object[] args, Object extra) {
        if (args == null || args.length == 0) {
            return new Object[]{extra};
        }
        Object[] copy = Arrays.copyOf(args, args.length + 1);
        copy[args.length] = extra;
        return copy;
    }

    private static String brief(Throwable t) {
        if (t == null) return "null";
        String msg = t.getMessage();
        if (msg == null || msg.isBlank()) return t.getClass().getSimpleName();
        return t.getClass().getSimpleName() + ": " + msg;
    }
}
