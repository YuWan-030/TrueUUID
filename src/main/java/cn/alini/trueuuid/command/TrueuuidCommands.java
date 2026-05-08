package cn.alini.trueuuid.command;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.config.TrueuuidConfig;
import cn.alini.trueuuid.server.NameRegistry;
import cn.alini.trueuuid.server.TrueuuidRuntime;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;

import com.google.gson.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = Trueuuid.MODID)
public class TrueuuidCommands {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("trueuuid")
                .requires(src -> src.hasPermission(3))
                // 新增：/trueuuid mojang status
                .then(Commands.literal("config")
                        .requires(src -> src.hasPermission(3))
                        .then(Commands.literal("nomojang")
                                .then(Commands.literal("status")
                                        .executes(ctx -> cmdNomojangStatus(ctx.getSource()))
                                )
                                .then(Commands.literal("on")
                                        .executes(ctx -> cmdNomojangSet(ctx.getSource(), true))
                                )
                                .then(Commands.literal("off")
                                        .executes(ctx -> cmdNomojangSet(ctx.getSource(), false))
                                )
                                .then(Commands.literal("toggle")
                                        .executes(ctx -> cmdNomojangToggle(ctx.getSource()))
                                )
                        )
                )
                .then(Commands.literal("mojang")
                        .then(Commands.literal("status")
                                .executes(ctx -> mojangStatus(ctx.getSource()))
                        )
                )
                .then(Commands.literal("link")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> run(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        true, true, true, true, true)))
                        .then(Commands.literal("run")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> run(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                false, true, true, true, true))))
                        .then(Commands.literal("dryrun")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> run(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                true, true, true, true, true))))
                )
                .then(Commands.literal("reload")
                        .executes(ctx -> cmdConfigReload(ctx.getSource()))
                )
        );



    }

    // 新增方法：runtime 从磁盘重载配置并将值写入 TrueuuidConfig.COMMON
    private static int cmdConfigReload(CommandSourceStack src) {
        try {
            Path cfgPath = FMLPaths.CONFIGDIR.get().resolve("trueuuid-common.toml");
            CommentedFileConfig cfg = CommentedFileConfig.builder(cfgPath)
                    .sync() // 与磁盘保持同步
                    .autosave()
                    .build();
            cfg.load();

            // 辅助读取函数：优先读取 auth.xxx，其次尝试不带 auth 前缀的样式（兼容不同定义位置）
            java.util.function.BiFunction<String, String, Object> getVal = (authKey, altKey) -> {
                if (cfg.contains(authKey)) return cfg.get(authKey);
                if (altKey != null && cfg.contains(altKey)) return cfg.get(altKey);
                return null;
            };

            // 布尔项
            Object v;
            v = getVal.apply("auth.nomojang.enabled", "nomojang.enabled");
            if (v instanceof Boolean) TrueuuidConfig.COMMON.nomojangEnabled.set((Boolean) v);

            v = getVal.apply("auth.debug", "debug");
            if (v instanceof Boolean) TrueuuidConfig.COMMON.debug.set((Boolean) v);

            v = getVal.apply("auth.recentIpGrace.enabled", "recentIpGrace.enabled");
            if (v instanceof Boolean) TrueuuidConfig.COMMON.recentIpGraceEnabled.set((Boolean) v);

            v = getVal.apply("auth.knownPremiumDenyOffline", "knownPremiumDenyOffline");
            if (v instanceof Boolean) TrueuuidConfig.COMMON.knownPremiumDenyOffline.set((Boolean) v);

            v = getVal.apply("auth.allowOfflineForUnknownOnly", "allowOfflineForUnknownOnly");
            if (v instanceof Boolean) TrueuuidConfig.COMMON.allowOfflineForUnknownOnly.set((Boolean) v);

            v = getVal.apply("auth.allowOfflineOnTimeout", "allowOfflineOnTimeout");
            if (v instanceof Boolean) TrueuuidConfig.COMMON.allowOfflineOnTimeout.set((Boolean) v);

            v = getVal.apply("auth.allowOfflineOnFailure", "allowOfflineOnFailure");
            if (v instanceof Boolean) TrueuuidConfig.COMMON.allowOfflineOnFailure.set((Boolean) v);

            // 数值项
            v = getVal.apply("auth.timeoutMs", "timeoutMs");
            if (v instanceof Number) TrueuuidConfig.COMMON.timeoutMs.set(((Number) v).longValue());

            v = getVal.apply("auth.recentIpGrace.ttlSeconds", "recentIpGrace.ttlSeconds");
            if (v instanceof Number) TrueuuidConfig.COMMON.recentIpGraceTtlSeconds.set(((Number) v).intValue());

            // 字符串项
            v = getVal.apply("auth.timeoutKickMessage", "timeoutKickMessage");
            if (v != null) TrueuuidConfig.COMMON.timeoutKickMessage.set(String.valueOf(v));

            v = getVal.apply("auth.offlineFallbackMessage", "offlineFallbackMessage");
            if (v != null) TrueuuidConfig.COMMON.offlineFallbackMessage.set(String.valueOf(v));

            v = getVal.apply("auth.offlineShortSubtitle", "offlineShortSubtitle");
            if (v != null) TrueuuidConfig.COMMON.offlineShortSubtitle.set(String.valueOf(v));

            v = getVal.apply("auth.onlineShortSubtitle", "onlineShortSubtitle");
            if (v != null) TrueuuidConfig.COMMON.onlineShortSubtitle.set(String.valueOf(v));

            // 完成反馈
            src.sendSuccess(() -> Component.literal("[TrueUUID] 配置已从磁盘重载").withStyle(net.minecraft.ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("[TrueUUID] 重载配置失败: " + ex.getMessage()).withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
    }

    // 以下方法加入到 `TrueuuidCommands` 类中（同一文件）
    private static int cmdNomojangStatus(CommandSourceStack src) {
        boolean enabled = TrueuuidConfig.nomojangEnabled();
        if (enabled) {
            src.sendSuccess(() -> Component.literal("[TrueUUID] NoMojang: 已启用").withStyle(net.minecraft.ChatFormatting.GREEN), false);
        } else {
            src.sendSuccess(() -> Component.literal("[TrueUUID] NoMojang: 已禁用").withStyle(net.minecraft.ChatFormatting.RED), false);
        }
        return 1;
    }

    private static int cmdNomojangSet(CommandSourceStack src, boolean value) {
        try {
            TrueuuidConfig.COMMON.nomojangEnabled.set(value);
            // 运行时也可记录日志
            src.sendSuccess(() -> Component.literal("[TrueUUID] NoMojang 已" + (value ? "启用" : "禁用"))
                    .withStyle(value ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED), false);
            return 1;
        } catch (Throwable t) {
            src.sendFailure(Component.literal("[TrueUUID] 无法设置 NoMojang: " + t.getMessage()).withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
    }

    private static int cmdNomojangToggle(CommandSourceStack src) {
        boolean current = TrueuuidConfig.nomojangEnabled();
        return cmdNomojangSet(src, !current);
    }

    private static int mojangStatus(CommandSourceStack src) {
        try {
            String testUrl = TrueuuidConfig.COMMON.mojangReverseProxy.get()+"/session/minecraft/hasJoined?username=Mojang&serverId=test";
            java.net.URL url = new java.net.URL(testUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200 || responseCode == 204 || responseCode == 403) {
                src.sendSuccess(() -> Component.literal("[TrueUUID] Mojang 会话服务器可访问，响应码: " + responseCode)
                        .withStyle(net.minecraft.ChatFormatting.GREEN), false);
            } else {
                src.sendFailure(Component.literal("[TrueUUID] Mojang 会话服务器响应异常，响应码: " + responseCode)
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            return 1;
        } catch (Exception e) {
            src.sendFailure(Component.literal("[TrueUUID] 无法连接到 Mojang 会话服务器: " + e.getMessage())
                    .withStyle(net.minecraft.ChatFormatting.RED));
            return 0;
        }
    }

    private static int run(CommandSourceStack src, String name,
                           boolean dryRun, boolean backup,
                           boolean mergeInv, boolean mergeEnder, boolean mergeStats) {
        MinecraftServer server = src.getServer();

        Optional<NameRegistry.Entry> reg = getEntry(name);
        if (reg.isEmpty()) {
            src.sendFailure(Component.literal("未在注册表中找到该名字的正版记录：" + name));
            return 0;
        }
        UUID premium = reg.get().premiumUuid;
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));

        Path worldRoot = server.getWorldPath(LevelResource.ROOT);
        Path playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Path adv = worldRoot.resolve("advancements");
        Path stats = worldRoot.resolve("stats");

        Path premDat = playerData.resolve(premium + ".dat");
        Path offDat = playerData.resolve(offline + ".dat");
        Path premAdv = adv.resolve(premium + ".json");
        Path offAdv = adv.resolve(offline + ".json");
        Path premStats = stats.resolve(premium + ".json");
        Path offStats = stats.resolve(offline + ".json");

        src.sendSuccess(() -> Component.literal(
                "[TrueUUID] link " + (dryRun ? "(dry-run)" : "(run)") + " name=" + name +
                        "\n premium=" + premium + "\n offline=" + offline +
                        "\n files:\n  " + offDat + " -> " + premDat +
                        "\n  " + offAdv + " -> " + premAdv +
                        "\n  " + offStats + " -> " + premStats
        ), false);

        if (dryRun) return 1;

        try {
            if (backup) {
                String ts = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
                Path backupDir = worldRoot.resolve("backups/trueuuid/" + ts + "/" + name.toLowerCase(Locale.ROOT));
                Files.createDirectories(backupDir);
                copyIfExists(premDat, backupDir.resolve("premium.dat"));
                copyIfExists(offDat, backupDir.resolve("offline.dat"));
                copyIfExists(premAdv, backupDir.resolve("premium.adv.json"));
                copyIfExists(offAdv, backupDir.resolve("offline.adv.json"));
                copyIfExists(premStats, backupDir.resolve("premium.stats.json"));
                copyIfExists(offStats, backupDir.resolve("offline.stats.json"));
            }

            // ==== NBT 合并实现 ====
            if (Files.exists(offDat)) {
                if (!Files.exists(premDat)) {
                    Files.move(offDat, premDat, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // 合并 NBT（背包/末影箱）
                    mergePlayerDatNBT(premDat, offDat, mergeInv, mergeEnder);
                }
            }
            // ==== advancements 合并实现 ====
            if (Files.exists(offAdv)) {
                if (!Files.exists(premAdv)) {
                    Files.move(offAdv, premAdv, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    mergeAdvancementsJson(premAdv, offAdv);
                }
            }
            // ==== stats 合并实现 ====
            if (Files.exists(offStats)) {
                if (!Files.exists(premStats)) {
                    Files.move(offStats, premStats, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    mergeStatsJson(premStats, offStats);
                }
            }

            src.sendSuccess(() -> Component.literal("完成。建议玩家下次以正版登录确认数据。"), false);
            return 1;
        } catch (Exception ex) {
            src.sendFailure(Component.literal("失败：" + ex.getMessage()));
            ex.printStackTrace();
            return 0;
        }
    }

    private static Optional<NameRegistry.Entry> getEntry(String name) {
        // 仅供命令打印 premium，用不到其它字段时可改为直接 getPremiumUuid
        try {
            var f = NameRegistry.class.getDeclaredField("map");
            f.setAccessible(true);
        } catch (Throwable ignored) {
        }
        // 简化：复用 getPremiumUuid，并构造一个 Entry
        return TrueuuidRuntime.NAME_REGISTRY.getPremiumUuid(name).map(u -> {
            NameRegistry.Entry e = new NameRegistry.Entry();
            e.premiumUuid = u;
            return e;
        });
    }

    private static void copyIfExists(Path from, Path to) throws Exception {
        if (Files.exists(from)) {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // --- NBT合并：背包/末影箱 ---
    private static void mergePlayerDatNBT(Path premDat, Path offDat, boolean mergeInv, boolean mergeEnder) throws Exception {
        CompoundTag prem, off;
        try (InputStream is = Files.newInputStream(premDat)) {
            prem = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
        }
        try (InputStream is = Files.newInputStream(offDat)) {
            off = NbtIo.readCompressed(is, NbtAccounter.unlimitedHeap());
        }

        boolean changed = false;
        if (mergeInv) {
            changed |= mergeItemListTag(prem, off, "Inventory");
        }
        if (mergeEnder) {
            changed |= mergeItemListTag(prem, off, "EnderItems");
        }

        if (changed) {
            try (OutputStream os = Files.newOutputStream(premDat, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                NbtIo.writeCompressed(prem, os);
            }
        }
    }

    // 合并规则：以 premium 为主，offline中未出现的item补到后面（不会覆盖原物品槽号）
    private static boolean mergeItemListTag(CompoundTag prem, CompoundTag off, String key) {
        if (!prem.contains(key) || !off.contains(key)) return false;
        ListTag premList = prem.getList(key, 10); // 10: CompoundTag
        ListTag offList = off.getList(key, 10);
        // 以槽号为主键
        Set<Integer> premSlots = new HashSet<>();
        for (int i = 0; i < premList.size(); ++i) {
            CompoundTag tag = premList.getCompound(i);
            if (tag.contains("Slot")) premSlots.add((int) tag.getByte("Slot"));
        }
        boolean changed = false;
        for (int i = 0; i < offList.size(); ++i) {
            CompoundTag tag = offList.getCompound(i);
            if (tag.contains("Slot")) {
                int slot = tag.getByte("Slot");
                if (!premSlots.contains(slot)) {
                    premList.add(tag.copy());
                    changed = true;
                }
            }
        }
        if (changed) {
            prem.put(key, premList);
        }
        return changed;
    }

    // --- advancements 合并：并集 ---
    private static void mergeAdvancementsJson(Path premAdv, Path offAdv) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject prem, off;
        try (Reader r = Files.newBufferedReader(premAdv)) {
            prem = gson.fromJson(r, JsonObject.class);
        }
        try (Reader r = Files.newBufferedReader(offAdv)) {
            off = gson.fromJson(r, JsonObject.class);
        }
        boolean changed = false;
        for (String key : off.keySet()) {
            if (!prem.has(key)) {
                prem.add(key, off.get(key));
                changed = true;
            }
        }
        if (changed) {
            try (Writer w = Files.newBufferedWriter(premAdv, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(prem, w);
            }
        }
    }

    // 更健壮的 mergeStatsJson 实现，处理 JsonElement 类型差异，避免 ClassCastException
    private static void mergeStatsJson(Path premStats, Path offStats) throws Exception {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        JsonObject prem, off;
        try (Reader r = Files.newBufferedReader(premStats)) {
            prem = gson.fromJson(r, JsonObject.class);
        }
        try (Reader r = Files.newBufferedReader(offStats)) {
            off = gson.fromJson(r, JsonObject.class);
        }
        boolean changed = false;

        for (String cat : off.keySet()) {
            JsonElement offElem = off.get(cat);
            // 如果 premium 中没有该分类，直接拷贝整个元素（无论类型）
            if (!prem.has(cat)) {
                prem.add(cat, offElem);
                changed = true;
                continue;
            }

            JsonElement premElem = prem.get(cat);

            // 两边都是对象 -> 逐条合并（数值累加，非数值保留 premium）
            if (offElem.isJsonObject() && premElem.isJsonObject()) {
                JsonObject offCat = offElem.getAsJsonObject();
                JsonObject premCat = premElem.getAsJsonObject();
                for (String key : offCat.keySet()) {
                    JsonElement offVal = offCat.get(key);
                    if (!premCat.has(key)) {
                        premCat.add(key, offVal);
                        changed = true;
                    } else {
                        JsonElement premVal = premCat.get(key);
                        // 尝试对原语数值做累加
                        if (premVal.isJsonPrimitive() && offVal.isJsonPrimitive()) {
                            JsonPrimitive pPri = premVal.getAsJsonPrimitive();
                            JsonPrimitive oPri = offVal.getAsJsonPrimitive();
                            if (pPri.isNumber() && oPri.isNumber()) {
                                try {
                                    long a = pPri.getAsLong();
                                    long b = oPri.getAsLong();
                                    premCat.addProperty(key, a + b);
                                    changed = true;
                                } catch (Exception ignored) {
                                    // 若不能以 long 累加则保持 premium 原值
                                }
                            }
                        }
                        // 其他类型（数组/对象/非数值原语）优先保留 prem，不覆盖
                    }
                }
                prem.add(cat, premCat);
            } else {
                // 类型不一致或都不是对象：
                // 若两边都是原语且为数字，则尝试累加（例如少见的数值统计）
                if (premElem.isJsonPrimitive() && offElem.isJsonPrimitive()) {
                    JsonPrimitive pPri = premElem.getAsJsonPrimitive();
                    JsonPrimitive oPri = offElem.getAsJsonPrimitive();
                    if (pPri.isNumber() && oPri.isNumber()) {
                        try {
                            long a = pPri.getAsLong();
                            long b = oPri.getAsLong();
                            prem.addProperty(cat, a + b);
                            changed = true;
                        } catch (Exception ignored) {
                            // 不可累加则保留 prem
                        }
                    }
                }
                // 其余情况（类型不一致且 prem 已存在）保持 prem，不覆盖
            }
        }

        if (changed) {
            try (Writer w = Files.newBufferedWriter(premStats, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(prem, w);
            }
        }

    }
}