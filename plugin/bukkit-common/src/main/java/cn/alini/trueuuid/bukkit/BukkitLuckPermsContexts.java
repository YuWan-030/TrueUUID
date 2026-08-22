package cn.alini.trueuuid.bukkit;

import cn.alini.trueuuid.server.AuthenticatedIdentity;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.context.ContextSet;
import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Objects;

/** Optional, unshaded LuckPerms contexts. It is loaded only when LuckPerms is present. */
public final class BukkitLuckPermsContexts implements AutoCloseable {
    private final LuckPerms luckPerms;
    private final ContextCalculator<Player> calculator;

    private BukkitLuckPermsContexts(LuckPerms luckPerms, ContextCalculator<Player> calculator) {
        this.luckPerms = luckPerms;
        this.calculator = calculator;
    }

    public static BukkitLuckPermsContexts install(TrueuuidBukkitApi api) {
        Objects.requireNonNull(api, "api");
        LuckPerms luckPerms = LuckPermsProvider.get();
        ContextCalculator<Player> calculator = new ContextCalculator<>() {
            @Override public void calculate(Player target, ContextConsumer consumer) {
                api.identityOf(target.getUniqueId()).ifPresent(identity -> publish(identity, consumer));
            }

            @Override public ContextSet estimatePotentialContexts() {
                MutableContextSet contexts = MutableContextSet.create();
                for (String status : new String[]{"premium", "offline"}) {
                    contexts.add("trueuuid-status", status);
                }
                for (String aliased : new String[]{"true", "false"}) {
                    contexts.add("trueuuid-aliased", aliased);
                }
                for (String authority : new String[]{"mojang", "yggdrasil", "offline"}) {
                    contexts.add("trueuuid-authority", authority);
                }
                return contexts;
            }
        };
        luckPerms.getContextManager().registerCalculator(calculator);
        return new BukkitLuckPermsContexts(luckPerms, calculator);
    }

    private static void publish(AuthenticatedIdentity identity, ContextConsumer consumer) {
        consumer.accept("trueuuid-status",
                identity.kind() == AuthenticatedIdentity.Kind.PREMIUM ? "premium" : "offline");
        consumer.accept("trueuuid-aliased", Boolean.toString(identity.aliased()));
        String authority = switch (identity.authority()) {
            case MOJANG -> "mojang";
            case ALLOWLISTED_YGGDRASIL -> "yggdrasil";
            case OFFLINE -> "offline";
        };
        consumer.accept("trueuuid-authority", authority.toLowerCase(Locale.ROOT));
    }

    @Override public void close() {
        luckPerms.getContextManager().unregisterCalculator(calculator);
    }
}
