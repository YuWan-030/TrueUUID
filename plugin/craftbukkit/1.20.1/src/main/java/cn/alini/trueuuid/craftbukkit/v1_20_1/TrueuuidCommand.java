package cn.alini.trueuuid.craftbukkit.v1_20_1;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.bukkit.MessageStyle;
import cn.alini.trueuuid.bukkit.Tone;
import cn.alini.trueuuid.bukkit.TrueuuidBukkitApi;
import cn.alini.trueuuid.protocol.OfflineIdentity;
import cn.alini.trueuuid.server.AdmissionMode;
import cn.alini.trueuuid.server.AuthenticatedIdentity;
import cn.alini.trueuuid.server.CollisionApprovalService;
import cn.alini.trueuuid.server.OfflineAliasAllocator;
import cn.alini.trueuuid.server.PersistentIdentityRepository;
import cn.alini.trueuuid.server.ReleaseConfirmationService;
import cn.alini.trueuuid.server.ServerConfiguration;
import cn.alini.trueuuid.server.TrueuuidPermissions;
import cn.alini.trueuuid.server.UnifiedAdmissionPolicy;
import org.bukkit.Bukkit;
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
import java.util.logging.Logger;

/** Thin Bukkit command renderer over shared repository and permission contracts. */
final class TrueuuidCommand implements TabExecutor {
    record Feedback(Tone tone, String text) {
        Feedback {
            Objects.requireNonNull(tone, "tone");
            Objects.requireNonNull(text, "text");
        }
    }

    interface ReloadAction { Feedback reload(); }

    private final PersistentIdentityRepository identities;
    private final TrueuuidBukkitApi statuses;
    private final CollisionApprovalService collisionApprovals;
    private final Supplier<ServerConfiguration> configuration;
    private final ReloadAction reloadAction;
    private final MessageStyle style;
    private final Supplier<String> runtimeSummary;
    private final Logger logger;
    private final ReleaseConfirmationService confirmations = new ReleaseConfirmationService();
    private final OfflineAliasAllocator aliases = new OfflineAliasAllocator();

    TrueuuidCommand(PersistentIdentityRepository identities, TrueuuidBukkitApi statuses,
                    CollisionApprovalService collisionApprovals,
                    Supplier<ServerConfiguration> configuration, ReloadAction reloadAction,
                    MessageStyle style, Supplier<String> runtimeSummary, Logger logger) {
        this.identities = Objects.requireNonNull(identities, "identities");
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.collisionApprovals = Objects.requireNonNull(collisionApprovals, "collisionApprovals");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.style = Objects.requireNonNull(style, "style");
        this.runtimeSummary = Objects.requireNonNull(runtimeSummary, "runtimeSummary");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0 || equals(args[0], "status")) return status(sender, args);
            if (equals(args[0], "health")) return health(sender);
            if (equals(args[0], "policy")) return policy(sender, args);
            if (equals(args[0], "identity")) return identity(sender, args);
            if (equals(args[0], "config")) return config(sender, args);
            if (equals(args[0], "reload")) return reload(sender);
            error(sender, "Unknown or unavailable TrueUUID command.");
        } catch (IllegalArgumentException invalid) {
            error(sender, "Invalid input: " + invalid.getMessage());
        } catch (IOException failure) {
            error(sender, "Identity update failed closed; see the server log.");
            logger.warning("TrueUUID command storage failure: " + failure.getMessage());
        }
        return true;
    }

    private boolean status(CommandSender sender, String[] args) {
        Player target;
        if (args.length <= 1) {
            if (!(sender instanceof Player player)) {
                error(sender, "Console must use /trueuuid status <player>.");
                return true;
            }
            target = player;
        } else {
            if (!allowed(sender, TrueuuidPermissions.STATUS_OTHER)) return denied(sender);
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                error(sender, "That player is not online.");
                return true;
            }
        }
        AccountStatus status = statuses.statusOf(target.getUniqueId());
        Optional<AuthenticatedIdentity> identity = statuses.identityOf(target.getUniqueId());
        Tone tone = switch (status) {
            case PREMIUM_VERIFIED -> Tone.SUCCESS;
            case OFFLINE_FALLBACK -> Tone.ERROR;
            default -> Tone.INFO;
        };
        style.send(sender, tone, target.getName() + ": " + status);
        identity.ifPresent(value -> detail(sender, describe(value)));
        return true;
    }

    private boolean health(CommandSender sender) {
        if (!allowed(sender, TrueuuidPermissions.HEALTH)) return denied(sender);
        ServerConfiguration value = configuration.get();
        style.send(sender, Tone.SUCCESS, "Login verification is active and fails closed.");
        detail(sender, runtimeSummary.get());
        detail(sender, "admission=" + value.admission().mode()
                + ", transport=" + value.authentication().transport()
                + ", offline-client=" + value.admission().offlineClient()
                + ", repository-generation=" + identities.generation());
        return true;
    }

    private boolean policy(CommandSender sender, String[] args) {
        if (!allowed(sender, TrueuuidPermissions.POLICY)) return denied(sender);
        if (args.length < 3 || !equals(args[1], "explain")) {
            error(sender, "Usage: /trueuuid policy explain <name> [profile-uuid]");
            return true;
        }
        String name = args[2];
        Optional<UUID> hint = args.length >= 4 ? Optional.of(UUID.fromString(args[3])) : Optional.empty();
        PersistentIdentityRepository.Record stored = identities.findByBaseName(name).orElse(null);
        style.send(sender, Tone.INFO, "Policy for " + name + ": mode="
                + configuration.get().admission().mode() + ", hint=" + hint.map(UUID::toString).orElse("missing"));
        detail(sender, stored == null ? "No stored bindings."
                : "premium=" + (stored.premium() != null) + ", offline=" + (stored.offline() != null)
                + ", blocked=" + stored.blocked());
        style.send(sender, Tone.WARNING,
                "Final routing still requires a live authoritative Mojang lookup; unavailable lookup denies.");
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
                    detail(sender, "base=" + record.baseName()
                            + ", premium=" + binding(record.premium())
                            + ", offline=" + binding(record.offline()) + ", blocked=" + record.blocked());
                    return true;
                }
            }
            if (identity.isPresent()) detail(sender, describe(identity.orElseThrow()));
            else error(sender, "Identity not found.");
            return true;
        }
        if (equals(args[1], "alias")) return alias(sender, args);
        if (equals(args[1], "collision")) return collision(sender, args);
        if (equals(args[1], "block") || equals(args[1], "unblock")) {
            if (!allowed(sender, TrueuuidPermissions.IDENTITY_BLOCK)) return denied(sender);
            if (args.length != 3) return identityUsage(sender);
            boolean block = equals(args[1], "block");
            identities.block(args[2], block);
            style.send(sender, Tone.SUCCESS, (block ? "Blocked " : "Unblocked ") + args[2] + ".");
            return true;
        }
        if (equals(args[1], "release")) return release(sender, args);
        return identityUsage(sender);
    }

    private boolean collision(CommandSender sender, String[] args) {
        if (!allowed(sender, TrueuuidPermissions.IDENTITY_COLLISION)) return denied(sender);
        if (args.length != 5 || !equals(args[2], "allow")) return identityUsage(sender);
        if (configuration.get().admission().mode() != AdmissionMode.CONSENT_REQUIRED) {
            throw new IllegalArgumentException("collision approval requires admission.mode=CONSENT_REQUIRED");
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
            offlineUuid = OfflineIdentity.profile(name).uuid();
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
        style.send(sender, Tone.WARNING, "One matching " + args[4].toLowerCase(Locale.ROOT)
                + " login may reconnect within 60 seconds. The offline identity will use " + alias + ".");
        detail(sender, "This approval is one-use and repository-generation-bound; it grants no premium proof.");
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
        style.send(sender, Tone.SUCCESS, "Offline alias is now " + updated.effectiveName() + ".");
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
                error(sender, "Confirmation is invalid, expired, used, or the repository changed.");
                return true;
            }
            if (!identities.release(uuid, generation)) {
                error(sender, "Identity was not released because repository state changed.");
                return true;
            }
            style.send(sender, Tone.SUCCESS, "Released only the TrueUUID binding. Player data was not deleted.");
            return true;
        }
        String token = confirmations.issue(actor, uuid, generation);
        style.send(sender, Tone.WARNING, "Confirm within 60 seconds: /trueuuid identity release "
                + uuid + " confirm " + token);
        detail(sender, "This releases identity metadata only; it never deletes player data.");
        return true;
    }

    private boolean config(CommandSender sender, String[] args) {
        if (!allowed(sender, TrueuuidPermissions.RELOAD)) return denied(sender);
        if (args.length == 2 && equals(args[1], "validate")) {
            style.send(sender, Tone.SUCCESS, "Active configuration snapshot is valid: " + configuration.get());
            return true;
        }
        error(sender, "Usage: /trueuuid config validate");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!allowed(sender, TrueuuidPermissions.RELOAD)) return denied(sender);
        Feedback feedback = reloadAction.reload();
        style.send(sender, feedback.tone(), feedback.text());
        return true;
    }

    private boolean identityUsage(CommandSender sender) {
        error(sender, "Usage: /trueuuid identity inspect|alias|collision|block|unblock|release ...");
        return true;
    }

    private boolean denied(CommandSender sender) {
        error(sender, "You do not have permission.");
        return true;
    }

    private void error(CommandSender sender, String text) {
        style.send(sender, Tone.ERROR, text);
    }

    private void detail(CommandSender sender, String text) {
        style.send(sender, Tone.DETAIL, text);
    }

    private static boolean allowed(CommandSender sender, String permission) {
        return !(sender instanceof Player) || sender.hasPermission(permission);
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
