package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.bukkit.TrueuuidBukkitApi;
import cn.alini.trueuuid.server.AuthenticatedIdentity;
import cn.alini.trueuuid.server.CollisionApprovalService;
import cn.alini.trueuuid.server.OfflineAliasAllocator;
import cn.alini.trueuuid.server.PersistentIdentityRepository;
import cn.alini.trueuuid.server.ReleaseConfirmationService;
import cn.alini.trueuuid.server.ServerConfiguration;
import cn.alini.trueuuid.server.TrueuuidPermissions;
import cn.alini.trueuuid.server.UnifiedAdmissionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Thin Bukkit command renderer over shared repository and permission contracts. */
final class TrueuuidSpigotCommand implements TabExecutor {
    interface ReloadAction { String reload(); }

    private final PersistentIdentityRepository identities;
    private final TrueuuidBukkitApi statuses;
    private final CollisionApprovalService collisionApprovals;
    private final Supplier<ServerConfiguration> configuration;
    private final ReloadAction reloadAction;
    private final ReleaseConfirmationService confirmations = new ReleaseConfirmationService();
    private final OfflineAliasAllocator aliases = new OfflineAliasAllocator();

    TrueuuidSpigotCommand(PersistentIdentityRepository identities, TrueuuidBukkitApi statuses,
                          CollisionApprovalService collisionApprovals,
                          Supplier<ServerConfiguration> configuration, ReloadAction reloadAction) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.collisionApprovals = Objects.requireNonNull(collisionApprovals, "collisionApprovals");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0 || equals(args[0], "status")) return status(sender, args);
            if (equals(args[0], "health")) return health(sender);
            if (equals(args[0], "policy")) return policy(sender, args);
            if (equals(args[0], "identity")) return identity(sender, args);
            if (equals(args[0], "config")) return config(sender, args);
            if (equals(args[0], "reload")) return reload(sender);
            sender.sendMessage(ChatColor.RED + "Unknown or unavailable TrueUUID command.");
        } catch (IllegalArgumentException invalid) {
            sender.sendMessage(ChatColor.RED + "Invalid input: " + invalid.getMessage());
        } catch (IOException failure) {
            sender.sendMessage(ChatColor.RED + "Identity update failed closed; see the server log.");
            Bukkit.getLogger().warning("TrueUUID command storage failure: " + failure.getMessage());
        }
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        Player target;
        if (args.length <= 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console must use /trueuuid status <player>.");
                return true;
            }
            target = player;
        } else {
            if (!allowed(sender, TrueuuidPermissions.STATUS_OTHER)) return denied(sender);
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "That player is not online.");
                return true;
            }
        }
        AccountStatus status = statuses.statusOf(target.getUniqueId());
        Optional<AuthenticatedIdentity> identity = statuses.identityOf(target.getUniqueId());
        sender.sendMessage(ChatColor.AQUA + "TrueUUID " + target.getName() + ": " + status);
        identity.ifPresent(value -> sender.sendMessage(ChatColor.GRAY + describe(value)));
        return true;
    }

    private boolean health(CommandSender sender) {
        if (!allowed(sender, TrueuuidPermissions.HEALTH)) return denied(sender);
        ServerConfiguration value = configuration.get();
        sender.sendMessage(ChatColor.GREEN + "TrueUUID candidate is active and fail-closed.");
        sender.sendMessage(ChatColor.GRAY + "admission=" + value.admission().mode()
                + ", transport=" + value.authentication().transport()
                + ", offline-client=" + value.admission().offlineClient()
                + ", repository-generation=" + identities.generation());
        return true;
    }

    private boolean policy(CommandSender sender, String[] args) {
        if (!allowed(sender, TrueuuidPermissions.POLICY)) return denied(sender);
        if (args.length < 3 || !equals(args[1], "explain")) {
            sender.sendMessage(ChatColor.RED + "Usage: /trueuuid policy explain <name> [profile-uuid]");
            return true;
        }
        String name = args[2];
        Optional<UUID> hint = args.length >= 4 ? Optional.of(UUID.fromString(args[3])) : Optional.empty();
        PersistentIdentityRepository.Record stored = identities.findByBaseName(name).orElse(null);
        sender.sendMessage(ChatColor.AQUA + "Policy for " + name + ": mode="
                + configuration.get().admission().mode() + ", hint=" + hint.map(UUID::toString).orElse("missing"));
        sender.sendMessage(ChatColor.GRAY + (stored == null ? "No stored bindings."
                : "premium=" + (stored.premium() != null) + ", offline=" + (stored.offline() != null)
                + ", blocked=" + stored.blocked()));
        sender.sendMessage(ChatColor.YELLOW
                + "Final routing still requires a live authoritative Mojang lookup; unavailable lookup denies.");
        return true;
    }

    private boolean identity(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2) return identityUsage(sender);
        if (equals(args[1], "inspect")) {
            if (!allowed(sender, TrueuuidPermissions.IDENTITY_INSPECT)) return denied(sender);
            if (args.length != 3) return identityUsage(sender);
            Optional<AuthenticatedIdentity> identity;
            try {
                identity = identities.identityOf(UUID.fromString(args[2]));
            } catch (IllegalArgumentException notUuid) {
                PersistentIdentityRepository.Record record = identities.findByBaseName(args[2]).orElse(null);
                if (record == null) identity = Optional.empty();
                else {
                    sender.sendMessage(ChatColor.GRAY + "base=" + record.baseName()
                            + ", premium=" + binding(record.premium())
                            + ", offline=" + binding(record.offline()) + ", blocked=" + record.blocked());
                    return true;
                }
            }
            sender.sendMessage(identity.map(value -> ChatColor.GRAY + describe(value))
                    .orElse(ChatColor.RED + "Identity not found."));
            return true;
        }
        if (equals(args[1], "alias")) return alias(sender, args);
        if (equals(args[1], "collision")) return collision(sender, args);
        if (equals(args[1], "block") || equals(args[1], "unblock")) {
            if (!allowed(sender, TrueuuidPermissions.IDENTITY_BLOCK)) return denied(sender);
            if (args.length != 3) return identityUsage(sender);
            boolean block = equals(args[1], "block");
            identities.block(args[2], block);
            sender.sendMessage(ChatColor.GREEN + (block ? "Blocked " : "Unblocked ") + args[2] + ".");
            return true;
        }
        if (equals(args[1], "release")) return release(sender, args);
        return identityUsage(sender);
    }

    private boolean collision(CommandSender sender, String[] args) {
        if (!allowed(sender, TrueuuidPermissions.IDENTITY_COLLISION)) return denied(sender);
        if (args.length != 5 || !equals(args[2], "allow")) return identityUsage(sender);
        if (configuration.get().admission().mode()
                != cn.alini.trueuuid.server.AdmissionMode.CONSENT_REQUIRED) {
            throw new IllegalArgumentException(
                    "collision approval requires admission.mode=CONSENT_REQUIRED");
        }

        String name = args[3];
        PersistentIdentityRepository.Record record = identities.findByBaseName(name).orElseThrow(
                () -> new IllegalArgumentException("no stored claimant exists for that name"));
        UnifiedAdmissionPolicy.CollisionResolution resolution;
        UUID offlineUuid;
        if (equals(args[4], "offline")) {
            if (record.premium() == null || record.offline() != null) {
                throw new IllegalArgumentException(
                        "offline approval requires one premium binding and no offline binding");
            }
            resolution = UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE;
            offlineUuid = cn.alini.trueuuid.protocol.OfflineIdentity.profile(name).uuid();
        } else if (equals(args[4], "premium")) {
            if (record.offline() == null || record.premium() != null
                    || !record.offline().effectiveName().equalsIgnoreCase(record.baseName())) {
                throw new IllegalArgumentException(
                        "premium approval requires one unaliased offline binding and no premium binding");
            }
            resolution = UnifiedAdmissionPolicy.CollisionResolution.MOVE_EXISTING_OFFLINE;
            offlineUuid = record.offline().uuid();
        } else {
            throw new IllegalArgumentException("identity type must be premium or offline");
        }

        String alias = aliases.allocate(record.baseName(), offlineUuid,
                configuration.get().aliases().prefix(), identities::effectiveNameUnavailable);
        collisionApprovals.issue(record.baseName(), resolution, identities.generation());
        sender.sendMessage(ChatColor.YELLOW + "One matching " + args[4].toLowerCase(Locale.ROOT)
                + " login may reconnect within 60 seconds. The offline identity will use " + alias + ".");
        sender.sendMessage(ChatColor.GRAY
                + "This approval is one-use and repository-generation-bound; it grants no premium proof.");
        return true;
    }

    private boolean alias(CommandSender sender, String[] args) throws IOException {
        if (!allowed(sender, TrueuuidPermissions.IDENTITY_ALIAS)) return denied(sender);
        if (args.length < 4) return identityUsage(sender);
        UUID uuid = UUID.fromString(args[3]);
        String value;
        if (equals(args[2], "set") && args.length == 5) {
            throw new IllegalArgumentException(
                    "manual alias overrides require an authoritative name-availability check; use reset for now");
        } else if (equals(args[2], "reset") && args.length == 4) {
            AuthenticatedIdentity identity = identities.identityOf(uuid).orElseThrow(
                    () -> new IllegalArgumentException("offline identity not found"));
            if (identity.kind() != AuthenticatedIdentity.Kind.OFFLINE) {
                throw new IllegalArgumentException("aliases are offline-only");
            }
            value = aliases.allocate(identity.requestedName(), uuid, configuration.get().aliases().prefix(),
                    identities::effectiveNameUnavailable);
        } else return identityUsage(sender);
        AuthenticatedIdentity updated = identities.setOfflineAlias(uuid, value);
        sender.sendMessage(ChatColor.GREEN + "Offline alias is now " + updated.effectiveName() + ".");
        return true;
    }

    private boolean release(CommandSender sender, String[] args) throws IOException {
        if (!allowed(sender, TrueuuidPermissions.IDENTITY_RELEASE)) return denied(sender);
        if (args.length < 3) return identityUsage(sender);
        UUID uuid = UUID.fromString(args[2]);
        String actor = actor(sender);
        long generation = identities.generation();
        if (args.length == 5 && equals(args[3], "confirm")) {
            if (!confirmations.consume(actor, uuid, generation, args[4])) {
                sender.sendMessage(ChatColor.RED + "Confirmation is invalid, expired, used, or the repository changed.");
                return true;
            }
            if (!identities.release(uuid, generation)) {
                sender.sendMessage(ChatColor.RED + "Identity was not released because repository state changed.");
                return true;
            }
            sender.sendMessage(ChatColor.GREEN + "Released only the TrueUUID binding. Player data was not deleted.");
            return true;
        }
        String token = confirmations.issue(actor, uuid, generation);
        sender.sendMessage(ChatColor.YELLOW + "Confirm within 60 seconds: /trueuuid identity release "
                + uuid + " confirm " + token);
        sender.sendMessage(ChatColor.GRAY + "This releases identity metadata only; it never deletes player data.");
        return true;
    }

    private boolean config(CommandSender sender, String[] args) {
        if (!allowed(sender, TrueuuidPermissions.RELOAD)) return denied(sender);
        if (args.length == 2 && equals(args[1], "validate")) {
            sender.sendMessage(ChatColor.GREEN + "Active configuration snapshot is valid: " + configuration.get());
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /trueuuid config validate");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!allowed(sender, TrueuuidPermissions.RELOAD)) return denied(sender);
        sender.sendMessage(reloadAction.reload());
        return true;
    }

    private static boolean identityUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED
                + "Usage: /trueuuid identity inspect|alias|collision|block|unblock|release ...");
        return true;
    }

    private static boolean allowed(CommandSender sender, String permission) {
        return !(sender instanceof Player) || sender.hasPermission(permission);
    }

    private static boolean denied(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "You do not have permission.");
        return true;
    }

    private static String actor(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console";
    }

    private static boolean equals(String value, String expected) {
        return expected.equalsIgnoreCase(value);
    }

    private static String binding(PersistentIdentityRepository.Binding value) {
        return value == null ? "none" : value.uuid() + "/" + value.effectiveName();
    }

    private static String describe(AuthenticatedIdentity value) {
        return "uuid=" + value.uuid() + ", kind=" + value.kind() + ", requested="
                + value.requestedName() + ", effective=" + value.effectiveName()
                + ", authority=" + value.authority() + ", aliased=" + value.aliased();
    }

    @Override public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args
    ) {
        List<String> choices = new ArrayList<>();
        if (args.length == 1) {
            if (sender instanceof Player) choices.add("status");
            addIf(sender, choices, "health", TrueuuidPermissions.HEALTH);
            addIf(sender, choices, "policy", TrueuuidPermissions.POLICY);
            if (allowed(sender, TrueuuidPermissions.IDENTITY_INSPECT)
                    || allowed(sender, TrueuuidPermissions.IDENTITY_ALIAS)
                    || allowed(sender, TrueuuidPermissions.IDENTITY_COLLISION)
                    || allowed(sender, TrueuuidPermissions.IDENTITY_BLOCK)
                    || allowed(sender, TrueuuidPermissions.IDENTITY_RELEASE)) choices.add("identity");
            addIf(sender, choices, "config", TrueuuidPermissions.RELOAD);
            addIf(sender, choices, "reload", TrueuuidPermissions.RELOAD);
        } else if (args.length == 2 && equals(args[0], "identity")) {
            addIf(sender, choices, "inspect", TrueuuidPermissions.IDENTITY_INSPECT);
            addIf(sender, choices, "alias", TrueuuidPermissions.IDENTITY_ALIAS);
            addIf(sender, choices, "collision", TrueuuidPermissions.IDENTITY_COLLISION);
            addIf(sender, choices, "block", TrueuuidPermissions.IDENTITY_BLOCK);
            addIf(sender, choices, "unblock", TrueuuidPermissions.IDENTITY_BLOCK);
            addIf(sender, choices, "release", TrueuuidPermissions.IDENTITY_RELEASE);
        } else if (args.length == 3 && equals(args[0], "identity")
                && equals(args[1], "collision")) {
            addIf(sender, choices, "allow", TrueuuidPermissions.IDENTITY_COLLISION);
        } else if (args.length == 5 && equals(args[0], "identity")
                && equals(args[1], "collision") && equals(args[2], "allow")) {
            addIf(sender, choices, "premium", TrueuuidPermissions.IDENTITY_COLLISION);
            addIf(sender, choices, "offline", TrueuuidPermissions.IDENTITY_COLLISION);
        } else if (args.length == 3 && equals(args[0], "identity")
                && equals(args[1], "alias")) {
            addIf(sender, choices, "reset", TrueuuidPermissions.IDENTITY_ALIAS);
        } else if (args.length == 2 && equals(args[0], "policy")) {
            addIf(sender, choices, "explain", TrueuuidPermissions.POLICY);
        } else if (args.length == 2 && equals(args[0], "config")) {
            addIf(sender, choices, "validate", TrueuuidPermissions.RELOAD);
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }

    private static void addIf(CommandSender sender, List<String> values, String value, String permission) {
        if (allowed(sender, permission)) values.add(value);
    }
}
