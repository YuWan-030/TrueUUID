package cn.alini.trueuuid.command;

import cn.alini.trueuuid.Trueuuid;
import cn.alini.trueuuid.server.NameRegistry;
import cn.alini.trueuuid.server.TrueuuidRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

@Mod.EventBusSubscriber(modid = Trueuuid.MODID)
public class TrueuuidCommands {

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent e) {
        CommandDispatcher<CommandSourceStack> d = e.getDispatcher();
        d.register(Commands.literal("trueuuid")
                .requires(src -> src.hasPermission(3))
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
                ));
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
        } catch (Throwable ignored) {}
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
            prem = NbtIo.readCompressed(is);
        }
        try (InputStream is = Files.newInputStream(offDat)) {
            off = NbtIo.readCompressed(is);
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
            if (tag.contains("Slot")) premSlots.add((int)tag.getByte("Slot"));
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

    // --- stats 合并：数值累加，字符串类以 premium 为准 ---
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
            JsonObject offCat = off.getAsJsonObject(cat);
            JsonObject premCat = prem.has(cat) ? prem.getAsJsonObject(cat) : new JsonObject();
            for (String key : offCat.keySet()) {
                if (!premCat.has(key)) {
                    premCat.add(key, offCat.get(key));
                    changed = true;
                } else {
                    // 累加数值
                    try {
                        long a = premCat.get(key).getAsLong();
                        long b = offCat.get(key).getAsLong();
                        long sum = a + b;
                        premCat.addProperty(key, sum);
                        changed = true;
                    } catch (Exception ex) {
                        // 非数值则优先保留 premium
                    }
                }
            }
            prem.add(cat, premCat);
        }
        if (changed) {
            try (Writer w = Files.newBufferedWriter(premStats, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(prem, w);
            }
        }
    }
}