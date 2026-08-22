package cn.alini.trueuuid.spigot.v1_20_1;

import cn.alini.trueuuid.api.AccountStatus;
import cn.alini.trueuuid.bukkit.HybridJoinFeedback;
import cn.alini.trueuuid.bukkit.BukkitLoginStatusService;
import cn.alini.trueuuid.bukkit.HybridPluginSettings;
import cn.alini.trueuuid.bukkit.TrueuuidBukkitApi;
import cn.alini.trueuuid.bukkit.BukkitLuckPermsContexts;
import cn.alini.trueuuid.protocol.AuthMessages;
import cn.alini.trueuuid.protocol.AuthWireCodec;
import cn.alini.trueuuid.protocol.BoundedRequestCoordinator;
import cn.alini.trueuuid.protocol.EndpointPolicy;
import cn.alini.trueuuid.protocol.HasJoinedProfileParser;
import cn.alini.trueuuid.protocol.HybridIdentityPolicy;
import cn.alini.trueuuid.protocol.HybridLoginCoordinator;
import cn.alini.trueuuid.protocol.OfflineAuthPort;
import cn.alini.trueuuid.protocol.OfflineIdentity;
import cn.alini.trueuuid.protocol.OfflineClientResponse;
import cn.alini.trueuuid.protocol.PersistentHybridIdentityStore;
import cn.alini.trueuuid.protocol.PremiumVerificationResult;
import cn.alini.trueuuid.protocol.SafeSessionVerifier;
import cn.alini.trueuuid.protocol.SessionVerifier;
import cn.alini.trueuuid.protocol.VerifiedProfile;
import cn.alini.trueuuid.server.AdmissionMode;
import cn.alini.trueuuid.server.AuthenticatedIdentity;
import cn.alini.trueuuid.server.AuthorityResult;
import cn.alini.trueuuid.server.CollisionApprovalService;
import cn.alini.trueuuid.server.MinecraftNames;
import cn.alini.trueuuid.server.OfflineAliasAllocator;
import cn.alini.trueuuid.server.PersistentIdentityRepository;
import cn.alini.trueuuid.server.ServerConfiguration;
import cn.alini.trueuuid.server.UnifiedAdmissionPolicy;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.injector.temporary.MinimalInjector;
import com.comphenix.protocol.injector.temporary.TemporaryPlayerFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Unsupported production-shaped Spigot 1.20.1 candidate. */
public final class TrueuuidSpigotPlugin extends JavaPlugin implements Listener {
    static final String PROTOCOLLIB_VERSION = StartupFingerprint.PROTOCOLLIB_VERSION;
    static final String PROTOCOLLIB_SHA256 = StartupFingerprint.PROTOCOLLIB_SHA256;
    private static final AtomicInteger NEXT_TRANSACTION = new AtomicInteger(0x4f000000);
    private static final AtomicLong NEXT_GENERATION = new AtomicLong();
    private static final String DENIAL_MESSAGE = LoginDenialMessages.STARTUP_FAILURE;

    private final Map<String, PendingAttempt> attemptsByConnection = new ConcurrentHashMap<>();
    private final Map<String, PendingAttempt> attemptsByName = new ConcurrentHashMap<>();
    private final Set<UUID> statusChannelClients = ConcurrentHashMap.newKeySet();
    private ExactSpigot1201Bridge bridge;
    private HybridPluginSettings settings;
    private PersistentIdentityRepository identities;
    private final UnifiedAdmissionPolicy admissionPolicy = new UnifiedAdmissionPolicy();
    private final OfflineAliasAllocator aliasAllocator = new OfflineAliasAllocator();
    private final CollisionApprovalService collisionApprovals = new CollisionApprovalService();
    private BoundedRequestCoordinator requests;
    private SafeSessionVerifier sessionVerifier;
    private BukkitLoginStatusService statusService;
    private Semaphore pendingSlots;
    private AutoCloseable luckPermsContexts;

    @Override public void onEnable() {
        try {
            verifyServerModeAndDependencies();
            boolean freshConfiguration = !Files.exists(getDataFolder().toPath().resolve("config.yml"));
            saveDefaultConfig();
            settings = HybridPluginSettings.load(getConfig(), freshConfiguration);
            pendingSlots = new Semaphore(settings.maximumPendingLogins());
            bridge = ExactSpigot1201Bridge.open();
            StartupFingerprint.requireImplementationVersion(bridge.implementationVersion());
            identities = new PersistentIdentityRepository(
                    getDataFolder().toPath().resolve("identity-repository-v2.json"));
            int imported = identities.importLegacyIfEmpty(
                    getDataFolder().toPath().resolve("hybrid-identities.json"));
            if (imported == 0) {
                imported = identities.importVerifiedRegistryIfEmpty(
                        getDataFolder().toPath().resolve("trueuuid-registry.json"));
            }
            if (imported > 0) {
                getLogger().info("Imported " + imported
                        + " legacy identity bindings; the original store was preserved.");
            }
            requests = new BoundedRequestCoordinator();
            sessionVerifier = new SafeSessionVerifier(requests,
                    () -> new EndpointPolicy(settings.customEndpointAllowlist()), HasJoinedProfileParser::parse);
            statusService = new BukkitLoginStatusService((player, failure) -> getLogger().log(
                    java.util.logging.Level.WARNING, "TrueUUID addon callback failed", failure),
                    identities::identityOf);
            Bukkit.getServicesManager().register(TrueuuidBukkitApi.class, statusService, this, ServicePriority.Normal);
            PluginCommand trueuuidCommand = Objects.requireNonNull(getCommand("trueuuid"),
                    "plugin.yml did not declare /trueuuid");
            TrueuuidSpigotCommand commands = new TrueuuidSpigotCommand(
                    identities, statusService, collisionApprovals,
                    () -> settings.core(), this::reloadSettings);
            trueuuidCommand.setExecutor(commands);
            trueuuidCommand.setTabCompleter(commands);
            luckPermsContexts = createLuckPermsContexts(settings.core().permissions().provider());
            if (luckPermsContexts != null) {
                getLogger().info("LuckPerms dynamic TrueUUID contexts registered without modifying groups.");
            }
            Bukkit.getMessenger().registerOutgoingPluginChannel(this, Fabric1201StatusPayload.CHANNEL);
            Bukkit.getPluginManager().registerEvents(this, this);
            installPacketHooks();
            Bukkit.getScheduler().runTaskTimer(this, this::sweepPending, 10L, 10L);
            getLogger().warning("Spigot 1.20.1 is an UNSUPPORTED candidate; release publication remains vetoed.");
            if (settings.admissionMode() == AdmissionMode.FIRST_CLAIM) {
                getLogger().warning("FIRST_CLAIM is enabled: offline users can locally squat Mojang account names.");
            }
            getLogger().info("Secure login bridge enabled: mode=" + settings.mode()
                    + ", offline=" + settings.offlineAdmissionMode()
                    + ", admission=" + settings.admissionMode()
                    + ", Spigot=" + bridge.implementationVersion() + ", ProtocolLib=" + PROTOCOLLIB_VERSION);
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "TrueUUID refused startup because an exact security seam failed", failure);
            Bukkit.getPluginManager().disablePlugin(this);
            // This plugin is the authentication boundary for an offline-mode
            // server. Continuing after it refuses startup would turn a safe
            // plugin failure into an unauthenticated, fail-open server.
            Bukkit.shutdown();
        }
    }

    @Override public void onDisable() {
        attemptsByConnection.values().forEach(attempt -> {
            attempt.coordinator().cancel().ifPresent(
                    ignored -> disconnectDuringShutdown(attempt));
        });
        attemptsByConnection.clear();
        attemptsByName.clear();
        statusChannelClients.clear();
        if (requests != null) requests.close();
        if (statusService != null) statusService.clearAll();
        closeLuckPermsContexts();
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
        Bukkit.getServicesManager().unregisterAll(this);
    }

    private void verifyServerModeAndDependencies() throws Exception {
        if (Bukkit.getOnlineMode()) throw new IllegalStateException("Spigot candidate requires online-mode=false");
        Plugin protocolLib = Bukkit.getPluginManager().getPlugin("ProtocolLib");
        if (protocolLib == null || !protocolLib.isEnabled()) throw new IllegalStateException("ProtocolLib is unavailable");
        Path protocolLibJar = Path.of(protocolLib.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(protocolLibJar)));
        StartupFingerprint.requireProtocolLib(protocolLib.getDescription().getVersion(), actual);
        StartupFingerprint.requireBukkitVersion(Bukkit.getVersion());
    }

    private String reloadSettings() {
        if (!attemptsByConnection.isEmpty()) {
            return ChatColor.RED + "Reload denied while logins are pending; try again shortly.";
        }
        try {
            reloadConfig();
            HybridPluginSettings reloaded = HybridPluginSettings.load(getConfig(), false);
            AutoCloseable replacementContexts = createLuckPermsContexts(
                    reloaded.core().permissions().provider());
            closeLuckPermsContexts();
            luckPermsContexts = replacementContexts;
            settings = reloaded;
            pendingSlots = new Semaphore(reloaded.maximumPendingLogins());
            if (reloaded.admissionMode() == AdmissionMode.FIRST_CLAIM) {
                getLogger().warning("FIRST_CLAIM is enabled after reload: offline users can locally squat Mojang names.");
            }
            return ChatColor.GREEN + "TrueUUID configuration reloaded and validated.";
        } catch (RuntimeException failure) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "TrueUUID configuration reload failed; the previous snapshot remains active", failure);
            return ChatColor.RED + "Reload failed validation; the previous configuration remains active.";
        }
    }

    private AutoCloseable createLuckPermsContexts(ServerConfiguration.Provider provider) {
        boolean installed = Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
        return switch (provider) {
            case PLATFORM -> null;
            case AUTO -> installed ? BukkitLuckPermsContexts.install(statusService) : null;
            case LUCKPERMS -> {
                if (!installed) {
                    throw new IllegalStateException(
                            "permissions.provider=LUCKPERMS requires LuckPerms on this backend");
                }
                yield BukkitLuckPermsContexts.install(statusService);
            }
        };
    }

    private void closeLuckPermsContexts() {
        if (luckPermsContexts == null) return;
        try {
            luckPermsContexts.close();
        } catch (Exception failure) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Could not unregister LuckPerms contexts", failure);
        } finally {
            luckPermsContexts = null;
        }
    }

    private void installPacketHooks() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this,
                PacketType.Login.Client.START,
                PacketType.Login.Client.CUSTOM_PAYLOAD,
                PacketType.Login.Client.ENCRYPTION_BEGIN) {
            @Override public void onPacketReceiving(PacketEvent event) {
                try {
                    if (event.getPacketType() == PacketType.Login.Client.START) onLoginStart(event);
                    else if (event.getPacketType() == PacketType.Login.Client.CUSTOM_PAYLOAD) onQueryAnswer(event);
                    else onEncryptionResponse(event);
                } catch (Throwable failure) {
                    event.setCancelled(true);
                    failClosed(event, failure);
                }
            }
        });
    }

    private void onLoginStart(PacketEvent event) throws Throwable {
        event.setCancelled(true);
        MinimalInjector injector = TemporaryPlayerFactory.getInjectorFromPlayer(event.getPlayer());
        if (injector == null) throw new IllegalStateException("ProtocolLib did not expose a temporary login injector");
        ExactSpigot1201Bridge.Connection connection = bridge.resolve(injector);
        if (!pendingSlots.tryAcquire()) {
            disconnectEarly(connection, LoginDenialMessages.SERVER_BUSY);
            return;
        }
        boolean slotTransferred = false;
        try {
            if (!"HELLO".equals(bridge.stateName(connection))) {
                throw new IllegalStateException("LOGIN_START was not intercepted before native profile commitment");
            }
            String name = bridge.startName(event.getPacket().getHandle());
            Optional<UUID> uuidHint = bridge.startUuidHint(event.getPacket().getHandle());
            String connectionKey = connectionKey(event);
            String nameKey = normalizeName(name);
            long now = Clock.systemUTC().millis();
            int transactionId = nextTransactionId();
            String nonce = UUID.randomUUID().toString().replace("-", "");
            Optional<PersistentIdentityRepository.Record> storedEntry = identities.findByBaseName(name);
            HybridIdentityPolicy.StoredIdentity stored = coordinatorStoredIdentity(
                    storedEntry.orElse(null), uuidHint);
            HybridLoginCoordinator.AttemptToken token = new HybridLoginCoordinator.AttemptToken(
                    connectionKey, NEXT_GENERATION.incrementAndGet(), transactionId, nonce,
                    now + settings.loginTimeout().toMillis());
            HybridLoginCoordinator coordinator = new HybridLoginCoordinator(token, stored, settings.mode());
            PendingAttempt attempt = new PendingAttempt(connectionKey, nameKey, name, remoteIp(event),
                    event.getPacket().getHandle(), connection, coordinator, token,
                    storedEntry.orElse(null), uuidHint);
            if (attemptsByConnection.putIfAbsent(connectionKey, attempt) != null
                    || attemptsByName.putIfAbsent(nameKey, attempt) != null) {
                attemptsByConnection.remove(connectionKey, attempt);
                attemptsByName.remove(nameKey, attempt);
                throw new IllegalStateException("duplicate or replayed LOGIN_START");
            }
            slotTransferred = true;
            dispatch(attempt, coordinator.begin().orElseThrow());
        } finally {
            if (!slotTransferred) pendingSlots.release();
        }
    }

    private void onQueryAnswer(PacketEvent event) throws Throwable {
        PendingAttempt attempt = attemptsByConnection.get(connectionKey(event));
        if (attempt == null) return;
        event.setCancelled(true);
        int transactionId = bridge.queryTransaction(event.getPacket().getHandle());
        byte[] payload = bridge.queryPayload(event.getPacket().getHandle());
        if (attempt.offlineClientGate()) {
            handleOfflineClientGateAnswer(attempt, transactionId, payload);
            return;
        }
        if (payload == null) {
            dispatchOptional(attempt, attempt.coordinator().clientQueryUnsupported(transactionId));
            return;
        }

        AuthMessages.Answer answer;
        try {
            answer = AuthWireCodec.decodeAnswer(payload);
        } catch (RuntimeException malformed) {
            attempt.premiumFailure(HybridIdentityPolicy.PremiumProof.MALFORMED_RESPONSE);
            dispatchOptional(attempt, attempt.coordinator().clientProof(transactionId,
                    token(attempt).nonce(),
                    PremiumVerificationResult.failed(HybridIdentityPolicy.PremiumProof.MALFORMED_RESPONSE)));
            return;
        }
        if ((settings.admissionMode() == AdmissionMode.SAFE_PARALLEL
                || settings.admissionMode() == AdmissionMode.CONSENT_REQUIRED)
                && isExplicitOfflineTrueuuidAnswer(answer)) {
            if (settings.admissionMode() == AdmissionMode.CONSENT_REQUIRED
                    && attempt.storedRecord() != null
                    && attempt.storedRecord().premium() != null
                    && attempt.storedRecord().offline() == null
                    && !collisionApprovals.consume(attempt.requestedName(),
                    UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE,
                    identities.generation())) {
                attempt.denialMessage(collisionDenial(attempt,
                        UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE));
                attempt.premiumFailure(HybridIdentityPolicy.PremiumProof.CLIENT_ABORTED);
                dispatchOptional(attempt, attempt.coordinator().clientProof(transactionId,
                        token(attempt).nonce(), PremiumVerificationResult.failed(
                                HybridIdentityPolicy.PremiumProof.CLIENT_ABORTED)));
                return;
            }
            attempt.aliasRequired(attempt.authorityResult() instanceof AuthorityResult.PremiumProfile
                    || (attempt.storedRecord() != null && attempt.storedRecord().premium() != null));
            OfflineAuthPort.Operation operation = attempt.storedRecord() != null
                    && attempt.storedRecord().offline() != null
                    ? OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED
                    : OfflineAuthPort.Operation.ENROLL_NEW;
            dispatchOptional(attempt, attempt.coordinator().clientSelectOffline(transactionId, operation));
            return;
        }
        if (answer.migrationConfirmed() || answer.missingSessionToken() || !answer.joined()) {
            HybridIdentityPolicy.PremiumProof reason = answer.missingSessionToken()
                    ? HybridIdentityPolicy.PremiumProof.CLIENT_ABORTED
                    : HybridIdentityPolicy.PremiumProof.NOT_JOINED;
            attempt.premiumFailure(reason);
            dispatchOptional(attempt, attempt.coordinator().clientProof(transactionId,
                    token(attempt).nonce(), PremiumVerificationResult.failed(reason)));
            return;
        }

        attempt.authority(answer.customEndpoint().isBlank()
                ? PersistentHybridIdentityStore.Authority.MOJANG
                : PersistentHybridIdentityStore.Authority.ALLOWLISTED_YGGDRASIL);

        CompletableFuture<PremiumVerificationResult> verification = sessionVerifier.verifyPremium(
                new SessionVerifier.Request(attempt.requestedName(), token(attempt).nonce(),
                        attempt.remoteIp(), answer.customEndpoint())).toCompletableFuture();
        attempt.coordinator().own(verification);
        verification.whenComplete((result, failure) -> {
            if (failure == null && result instanceof PremiumVerificationResult.Failed failed) {
                attempt.premiumFailure(failed.reason());
            }
            Optional<HybridLoginCoordinator.Effect> next = failure == null
                    ? attempt.coordinator().clientProof(transactionId, token(attempt).nonce(), result)
                    : attempt.coordinator().internalError();
            dispatchOptional(attempt, next);
        });
    }

    private void onEncryptionResponse(PacketEvent event) throws Throwable {
        PendingAttempt attempt = attemptsByConnection.get(connectionKey(event));
        if (attempt == null) return;
        if (!attempt.nativeProofStarted() || !"KEY".equals(bridge.stateName(attempt.connection()))) {
            event.setCancelled(true);
            dispatchOptional(attempt, attempt.coordinator().internalError());
        }
        // Otherwise leave the packet untouched. Spigot owns verify-token
        // validation, RSA, digest, encryption, and authlib session proof.
    }

    private void dispatch(PendingAttempt attempt, HybridLoginCoordinator.Effect effect) {
        try {
            if (effect instanceof HybridLoginCoordinator.LookupAuthority) {
                CompletableFuture<AuthorityResult> lookup = requests.submit(
                        attempt.requestedName(), attempt.remoteIp(),
                        "mojang-profile-existence-" + token(attempt).generation(),
                        () -> lookupMojangProfile(attempt));
                attempt.coordinator().own(lookup);
                lookup.whenComplete((result, failure) -> {
                    AuthorityResult detailed = failure == null && result != null
                            ? result
                            : new AuthorityResult.Unavailable(AuthorityResult.Failure.INTERNAL_ERROR);
                    UnifiedAdmissionPolicy.Decision decision = admissionPolicy.decide(
                            settings.core(), attempt.requestedName(), attempt.uuidHint(), detailed,
                            attempt.storedRecord() == null
                                    ? new UnifiedAdmissionPolicy.StoredBindings(false, false, false, false)
                                    : attempt.storedRecord().policyBindings());
                    HybridIdentityPolicy.AuthorityLookup classified;
                    Optional<HybridLoginCoordinator.Effect> next;
                    if (decision instanceof UnifiedAdmissionPolicy.RequirePremiumProof) {
                        classified = HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS;
                        next = attempt.coordinator().authorityLookup(classified);
                    } else if (decision instanceof UnifiedAdmissionPolicy.AllowOffline offline) {
                        attempt.aliasRequired(offline.aliasRequired());
                        classified = HybridIdentityPolicy.AuthorityLookup.DEFINITELY_ABSENT;
                        OfflineAuthPort.Operation operation = attempt.storedRecord() != null
                                && attempt.storedRecord().offline() != null
                                ? OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED
                                : OfflineAuthPort.Operation.ENROLL_NEW;
                        next = attempt.coordinator().authoritySelectOffline(operation);
                    } else if (decision instanceof UnifiedAdmissionPolicy.RequireCollisionConsent collision) {
                        classified = HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS;
                        next = resolveCollision(attempt, collision.resolution());
                    } else {
                        classified = HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE;
                        next = attempt.coordinator().authorityLookup(classified);
                    }
                    attempt.authorityResult(detailed);
                    attempt.authorityLookup(classified);
                    dispatchOptional(attempt, next);
                });
            } else if (effect instanceof HybridLoginCoordinator.SendClientQuery) {
                attempt.transport("CLIENT_ASSISTED");
                sendClientQuery(attempt);
            } else if (effect instanceof HybridLoginCoordinator.StartVanillaPremiumProof) {
                attempt.nativeProofStarted(true);
                attempt.transport("VANILLA_HYBRID");
                bridge.runOnEventLoop(attempt.connection(), () -> {
                    try {
                        bridge.startNativePremium(attempt.connection(), attempt.requestedName());
                    } catch (Throwable failure) {
                        dispatchOptional(attempt, attempt.coordinator().internalError());
                    }
                });
            } else if (effect instanceof HybridLoginCoordinator.RequireOfflineCredential offline) {
                handleOfflineRequirement(attempt, offline.operation());
            } else if (effect instanceof HybridLoginCoordinator.ApplyAuthenticatedProfile apply) {
                persistAndApply(attempt, apply.profile(), apply.status());
            } else if (effect instanceof HybridLoginCoordinator.Deny deny) {
                denyAttempt(attempt, deny);
            }
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.WARNING, "Login effect failed closed for " + attempt.requestedName(), failure);
            denyAttempt(attempt, new HybridLoginCoordinator.Deny(
                    token(attempt), HybridLoginCoordinator.DenialReason.INTERNAL_ERROR));
        }
    }

    private Optional<HybridLoginCoordinator.Effect> resolveCollision(
            PendingAttempt attempt,
            UnifiedAdmissionPolicy.CollisionResolution resolution
    ) {
        try {
            if (!collisionApprovals.consume(attempt.requestedName(), resolution, identities.generation())) {
                attempt.denialMessage(collisionDenial(attempt, resolution));
                // The platform-neutral coordinator has no mutation effect. This
                // transitions it to its terminal fail-closed policy path; the
                // explicit message and audit classification retain the real cause.
                return attempt.coordinator().authorityLookup(
                        HybridIdentityPolicy.AuthorityLookup.UNAVAILABLE);
            }

            if (resolution == UnifiedAdmissionPolicy.CollisionResolution.ALIAS_INCOMING_OFFLINE) {
                attempt.aliasRequired(true);
                OfflineAuthPort.Operation operation = attempt.storedRecord() != null
                        && attempt.storedRecord().offline() != null
                        ? OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED
                        : OfflineAuthPort.Operation.ENROLL_NEW;
                return attempt.coordinator().authoritySelectOffline(operation);
            }

            // Do not mutate stored identity state on an untrusted UUID hint.
            // The transition is applied only after the premium profile has
            // been verified, immediately before that profile is persisted.
            attempt.collisionMoveApproved(true);
            return attempt.coordinator().authorityLookup(
                    HybridIdentityPolicy.AuthorityLookup.PREMIUM_EXISTS);
        } catch (Exception failure) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Approved collision transition failed closed for " + attempt.requestedName(), failure);
            return attempt.coordinator().internalError();
        }
    }

    private String collisionDenial(
            PendingAttempt attempt,
            UnifiedAdmissionPolicy.CollisionResolution resolution
    ) {
        PersistentIdentityRepository.Record record = Objects.requireNonNull(
                attempt.storedRecord(), "collision requires stored identity state");
        UUID offlineUuid = resolution == UnifiedAdmissionPolicy.CollisionResolution.MOVE_EXISTING_OFFLINE
                ? Objects.requireNonNull(record.offline(), "offline collision binding").uuid()
                : OfflineIdentity.profile(attempt.requestedName()).uuid();
        String alias = aliasAllocator.allocate(attempt.requestedName(), offlineUuid,
                settings.aliasPrefix(), identities::effectiveNameUnavailable);
        return LoginDenialMessages.collision(attempt.requestedName(), alias, resolution);
    }

    private void moveExistingOfflineToAlias(PendingAttempt attempt) throws Exception {
        PersistentIdentityRepository.Record record = identities.findByBaseName(
                attempt.requestedName()).orElseThrow();
        PersistentIdentityRepository.Binding offline = Objects.requireNonNull(
                record.offline(), "approved premium collision has no offline binding");
        if (!offline.effectiveName().equalsIgnoreCase(record.baseName())) return;

        String alias = aliasAllocator.allocate(record.baseName(), offline.uuid(),
                settings.aliasPrefix(), identities::effectiveNameUnavailable);
        identities.setOfflineAlias(offline.uuid(), alias);
        org.bukkit.entity.Player liveOffline = Bukkit.getPlayer(offline.uuid());
        if (liveOffline != null && liveOffline.isOnline()) {
            Bukkit.getScheduler().runTask(this, () -> liveOffline.kickPlayer(
                    "An administrator approved this identity collision. Your offline identity now uses "
                            + alias + ". Reconnect; your UUID and player data were not changed."));
        }
    }

    private void denyAttempt(PendingAttempt attempt, HybridLoginCoordinator.Deny deny) {
        String message;
        try {
            message = LoginDenialMessages.forAttempt(deny.reason(), attempt.authorityLookup(),
                    attempt.premiumFailure(), attempt.offlineFailure(), attempt.denialMessage());
        } catch (RuntimeException invalidMessage) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Invalid TrueUUID denial message; using the safe generic message", invalidMessage);
            message = DENIAL_MESSAGE;
        }

        String delivery = "not_sent_connection_closed";
        try {
            if (attempt.connection().injector().isConnected()) {
                String clientMessage = message;
                bridge.runOnEventLoop(attempt.connection(), () -> {
                    try {
                        bridge.disconnect(attempt.connection(), clientMessage);
                    } catch (Throwable failure) {
                        getLogger().log(java.util.logging.Level.WARNING,
                                "Failed to send native TrueUUID denial to " + attempt.requestedName(), failure);
                    }
                });
                delivery = "requested";
            }
        } catch (Throwable disconnectFailure) {
            delivery = "failed";
            getLogger().log(java.util.logging.Level.WARNING,
                    "Failed to deliver TrueUUID denial to " + attempt.requestedName(), disconnectFailure);
        } finally {
            getLogger().warning("Denied TrueUUID login for " + attempt.requestedName()
                    + ": " + deny.reason()
                    + " authority=" + attempt.authorityLookup()
                    + " premium_failure=" + attempt.premiumFailure()
                    + " offline_failure=" + attempt.offlineFailure()
                    + " client_message=" + delivery);
            cleanup(attempt);
        }
    }

    private void disconnectDuringShutdown(PendingAttempt attempt) {
        try {
            if (attempt.connection().injector().isConnected()) {
                bridge.runOnEventLoop(attempt.connection(), () -> {
                    try {
                        bridge.disconnect(attempt.connection(), LoginDenialMessages.SERVER_SHUTTING_DOWN);
                    } catch (Throwable failure) {
                        getLogger().log(java.util.logging.Level.FINE,
                                "Could not send the shutdown denial packet", failure);
                    }
                });
            }
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.FINE,
                    "Could not notify a closing login connection during shutdown", failure);
        }
    }

    private void disconnectEarly(ExactSpigot1201Bridge.Connection connection, String message) throws Throwable {
        bridge.runOnEventLoop(connection, () -> {
            try {
                bridge.disconnect(connection, message);
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.WARNING,
                        "Failed to send native TrueUUID login denial", failure);
                connection.injector().disconnect(message);
            }
        });
    }

    private AuthorityResult lookupMojangProfile(PendingAttempt attempt) throws Exception {
        try {
            return bridge.lookupMojangProfile(attempt.connection(), attempt.requestedName());
        } catch (Throwable failure) {
            throw new Exception("exact authlib profile lookup failed", failure);
        }
    }

    private void sendClientQuery(PendingAttempt attempt) throws Throwable {
        byte[] payload = AuthWireCodec.encodeQuery(new AuthMessages.Query(
                token(attempt).nonce(), false, "", ""));
        bridge.runOnEventLoop(attempt.connection(), () -> {
            try {
                attempt.connection().injector().sendServerPacket(
                        bridge.customQuery(token(attempt).transactionId(), payload), null, false);
            } catch (Throwable failure) {
                dispatchOptional(attempt, attempt.coordinator().internalError());
            }
        });
    }

    private void handleOfflineRequirement(PendingAttempt attempt, OfflineAuthPort.Operation operation) throws Throwable {
        PersistentIdentityRepository.Binding stored = attempt.storedRecord() == null
                ? null : attempt.storedRecord().offline();
        boolean existing = stored != null;
        if ((operation == OfflineAuthPort.Operation.AUTHENTICATE_ENROLLED) != existing) {
            throw new IllegalStateException("offline identity state changed during login");
        }

        switch (settings.offlineAdmissionMode()) {
            case DENY -> {
                attempt.offlineFailure(OfflineAuthPort.Failure.UNAVAILABLE);
                attempt.denialMessage("Offline accounts are disabled on this server. Sign in with a premium Minecraft account.");
                dispatchOptional(attempt, attempt.coordinator().offlineProof(
                        new OfflineAuthPort.Denied(OfflineAuthPort.Failure.UNAVAILABLE)));
            }
            case ALLOW_VANILLA -> {
                attempt.offlineAuthority(PersistentHybridIdentityStore.Authority.OFFLINE_NAME_ONLY);
                attempt.transport("OFFLINE_VANILLA");
                acceptOffline(attempt);
            }
            case REQUIRE_TRUEUUID_CLIENT -> {
                attempt.offlineAuthority(PersistentHybridIdentityStore.Authority.TRUEUUID_CLIENT_GATE);
                attempt.transport("OFFLINE_TRUEUUID_CLIENT");
                attempt.offlineClientGate(true);
                attempt.denialMessage("Offline login requires the matching TrueUUID client mod. Install it or ask the administrator to allow vanilla offline clients.");
                sendClientQuery(attempt);
            }
        }
    }

    private void handleOfflineClientGateAnswer(PendingAttempt attempt, int transactionId, byte[] payload) {
        attempt.offlineClientGate(false);
        if (transactionId != token(attempt).transactionId() || payload == null) {
            denyOfflineGate(attempt);
            return;
        }
        try {
            AuthMessages.Answer answer = AuthWireCodec.decodeAnswer(payload);
            if (!isExplicitOfflineTrueuuidAnswer(answer)) {
                denyOfflineGate(attempt);
                return;
            }
            acceptOffline(attempt);
        } catch (RuntimeException malformed) {
            denyOfflineGate(attempt);
        }
    }

    private void denyOfflineGate(PendingAttempt attempt) {
        attempt.offlineFailure(OfflineAuthPort.Failure.INVALID_CREDENTIAL);
        dispatchOptional(attempt, attempt.coordinator().offlineProof(
                new OfflineAuthPort.Denied(OfflineAuthPort.Failure.INVALID_CREDENTIAL)));
    }

    static boolean isExplicitOfflineTrueuuidAnswer(AuthMessages.Answer answer) {
        return OfflineClientResponse.isExplicit(answer);
    }

    private void acceptOffline(PendingAttempt attempt) {
        VerifiedProfile derived = OfflineIdentity.profile(attempt.requestedName());
        PersistentIdentityRepository.Binding stored = attempt.storedRecord() == null
                ? null : attempt.storedRecord().offline();
        if (stored != null && !stored.uuid().equals(derived.uuid())) {
            dispatchOptional(attempt, attempt.coordinator().internalError());
            return;
        }
        String effectiveName;
        if (stored != null) {
            effectiveName = stored.effectiveName();
        } else if (attempt.aliasRequired()) {
            effectiveName = aliasAllocator.allocate(attempt.requestedName(), derived.uuid(),
                    settings.aliasPrefix(), identities::effectiveNameUnavailable);
        } else {
            effectiveName = attempt.requestedName();
        }
        if (!effectiveName.equalsIgnoreCase(attempt.requestedName())) {
            verifyOfflineAlias(attempt, derived.uuid(), effectiveName, new HashSet<>(), 0);
            return;
        }
        finishOffline(attempt, derived.uuid(), effectiveName);
    }

    private void verifyOfflineAlias(
            PendingAttempt attempt,
            UUID offlineUuid,
            String candidate,
            Set<String> rejected,
            int verificationCount
    ) {
        // A leading '.', '+' or '-' cannot be a canonical Mojang Java name.
        // It therefore needs no remote availability lookup; repository indexes
        // have already excluded local case-insensitive collisions.
        if (!MinecraftNames.isCanonical(candidate)) {
            finishOffline(attempt, offlineUuid, candidate);
            return;
        }
        if (verificationCount >= 32) {
            attempt.offlineFailure(OfflineAuthPort.Failure.INTERNAL_ERROR);
            attempt.denialMessage("A safe offline alias could not be allocated. Contact the administrator.");
            dispatchOptional(attempt, attempt.coordinator().offlineProof(
                    new OfflineAuthPort.Denied(OfflineAuthPort.Failure.INTERNAL_ERROR)));
            return;
        }
        CompletableFuture<AuthorityResult> lookup = requests.submit(
                candidate, attempt.remoteIp(),
                "mojang-alias-existence-" + token(attempt).generation() + "-" + verificationCount,
                () -> lookupMojangProfileName(attempt, candidate));
        attempt.coordinator().own(lookup);
        lookup.whenComplete((result, failure) -> {
            AuthorityResult classified = failure == null && result != null
                    ? result : new AuthorityResult.Unavailable(AuthorityResult.Failure.INTERNAL_ERROR);
            if (classified instanceof AuthorityResult.DefinitelyAbsent) {
                finishOffline(attempt, offlineUuid, candidate);
                return;
            }
            if (classified instanceof AuthorityResult.PremiumProfile) {
                rejected.add(candidate.toLowerCase(Locale.ROOT));
                String next = aliasAllocator.allocate(attempt.requestedName(), offlineUuid,
                        settings.aliasPrefix(), normalized -> identities.effectiveNameUnavailable(normalized)
                                || rejected.contains(normalized));
                verifyOfflineAlias(attempt, offlineUuid, next, rejected, verificationCount + 1);
                return;
            }
            attempt.offlineFailure(OfflineAuthPort.Failure.UNAVAILABLE);
            attempt.denialMessage("Minecraft account lookup is unavailable, so a collision-safe offline alias could not be verified. Try again later.");
            dispatchOptional(attempt, attempt.coordinator().offlineProof(
                    new OfflineAuthPort.Denied(OfflineAuthPort.Failure.UNAVAILABLE)));
        });
    }

    private void finishOffline(PendingAttempt attempt, UUID offlineUuid, String effectiveName) {
        VerifiedProfile profile = new VerifiedProfile(offlineUuid, effectiveName, java.util.List.of());
        dispatchOptional(attempt, attempt.coordinator().offlineProof(
                new OfflineAuthPort.Accepted(profile.uuid(), profile.name())));
    }

    private AuthorityResult lookupMojangProfileName(PendingAttempt attempt, String name) throws Exception {
        try {
            return bridge.lookupMojangProfile(attempt.connection(), name);
        } catch (Throwable failure) {
            throw new Exception("exact authlib alias lookup failed", failure);
        }
    }

    private void persistAndApply(PendingAttempt attempt, VerifiedProfile profile, AccountStatus status) {
        try {
            AccountStatus alreadyOnline = statusService.statusOf(profile.uuid());
            if (alreadyOnline == AccountStatus.PREMIUM_VERIFIED
                    || alreadyOnline == AccountStatus.OFFLINE_FALLBACK) {
                attempt.denialMessage("This account is already connected.");
                dispatchOptional(attempt, attempt.coordinator().cancel());
                return;
            }
            if (status == AccountStatus.PREMIUM_VERIFIED) {
                preparePremiumCanonicalName(attempt, profile.name());
                identities.recordPremium(profile.uuid(), attempt.requestedName(), profile.name(),
                        attempt.authority() == PersistentHybridIdentityStore.Authority.MOJANG
                                ? AuthenticatedIdentity.Authority.MOJANG
                                : AuthenticatedIdentity.Authority.ALLOWLISTED_YGGDRASIL);
            } else if (status == AccountStatus.OFFLINE_FALLBACK) {
                identities.recordOffline(profile.uuid(), attempt.requestedName(), profile.name());
            } else {
                throw new IllegalArgumentException("unexpected hybrid profile status " + status);
            }
            setExpectedProfile(attempt, profile);
            bridge.runOnEventLoop(attempt.connection(), () -> {
                try {
                    bridge.continueAssisted(attempt.connection(), attempt.originalStart(), profile);
                } catch (Throwable failure) {
                    dispatchOptional(attempt, attempt.coordinator().profileApplicationFailed());
                }
            });
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.WARNING, "Authenticated identity persistence failed closed", failure);
            dispatchOptional(attempt, attempt.coordinator().profileApplicationFailed());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void verifyAppliedProfile(AsyncPlayerPreLoginEvent event) {
        PendingAttempt attempt = attemptsByName.get(normalizeName(event.getName()));
        if (attempt == null) return;
        try {
            VerifiedProfile nativeProfile = bridge.verifiedProfile(attempt.connection());
            if (!nativeProfile.uuid().equals(event.getUniqueId())
                    || !nativeProfile.name().equals(event.getName())) {
                throw new IllegalStateException("pre-login profile differs from the native verified profile");
            }
            if (attempt.nativeProofStarted()) {
                preparePremiumCanonicalName(attempt, nativeProfile.name());
                identities.recordPremium(nativeProfile.uuid(), attempt.requestedName(), nativeProfile.name(),
                        AuthenticatedIdentity.Authority.MOJANG);
                setExpectedProfile(attempt, nativeProfile);
                HybridLoginCoordinator.Effect apply = attempt.coordinator()
                        .vanillaProof(PremiumVerificationResult.verified(nativeProfile)).orElseThrow();
                if (!(apply instanceof HybridLoginCoordinator.ApplyAuthenticatedProfile expected)
                        || !sameIdentity(expected.profile(), nativeProfile)) {
                    throw new IllegalStateException("native proof did not produce the expected profile effect");
                }
            } else if (!sameIdentity(Objects.requireNonNull(attempt.expectedProfile(), "expectedProfile"), nativeProfile)) {
                throw new IllegalStateException("client-assisted profile changed before pre-login");
            }
            HybridLoginCoordinator.Effect awaiting = attempt.coordinator().profileApplied().orElseThrow();
            if (!(awaiting instanceof HybridLoginCoordinator.AwaitAcceptance)) {
                throw new IllegalStateException("profile application did not reach acceptance wait");
            }
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.WARNING, "Pre-login profile verification failed closed", failure);
            attempt.denialMessage(LoginDenialMessages.PROFILE_APPLICATION_FAILED);
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    LoginDenialMessages.PROFILE_APPLICATION_FAILED);
            dispatchOptional(attempt, attempt.coordinator().profileApplicationFailed());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void observePreLoginResult(AsyncPlayerPreLoginEvent event) {
        PendingAttempt attempt = attemptsByName.get(normalizeName(event.getName()));
        if (attempt != null && event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            dispatchOptional(attempt, attempt.coordinator().cancel());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void publishAcceptedLogin(PlayerLoginEvent event) {
        PendingAttempt attempt = attemptsByName.get(normalizeName(event.getPlayer().getName()));
        if (attempt == null) return;
        VerifiedProfile expectedProfile = attempt.expectedProfile();
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED
                || expectedProfile == null
                || !event.getPlayer().getUniqueId().equals(expectedProfile.uuid())
                || !event.getPlayer().getName().equals(expectedProfile.name())) {
            dispatchOptional(attempt, attempt.coordinator().cancel());
            return;
        }
        Optional<HybridLoginCoordinator.Effect> committed = attempt.coordinator().commitAcceptance();
        if (committed.isEmpty() || !(committed.orElseThrow() instanceof HybridLoginCoordinator.PublishStatus publish)) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    LoginDenialMessages.PROFILE_APPLICATION_FAILED);
            cleanup(attempt);
            return;
        }
        statusService.publish(event.getPlayer(), publish.status());
        getLogger().info("TrueUUID login_complete outcome=" + publish.status()
                + " transport=" + attempt.transport()
                + " player=" + event.getPlayer().getName()
                + " uuid=" + event.getPlayer().getUniqueId());
        cleanup(attempt);
    }

    /** Sends only the final server-owned status after a Fabric client registers its play channel. */
    @EventHandler
    public void publishFabricClientStatus(PlayerRegisterChannelEvent event) {
        if (!Fabric1201StatusPayload.CHANNEL.equals(event.getChannel())) return;
        AccountStatus status = statusService.statusOf(event.getPlayer().getUniqueId());
        if (status != AccountStatus.PREMIUM_VERIFIED && status != AccountStatus.OFFLINE_FALLBACK) return;
        statusChannelClients.add(event.getPlayer().getUniqueId());
        event.getPlayer().sendPluginMessage(this, Fabric1201StatusPayload.CHANNEL,
                Fabric1201StatusPayload.encode(status));
    }

    /** Private feedback only; the token-free login_complete line remains console-only audit output. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void publishJoinFeedback(PlayerJoinEvent event) {
        AccountStatus status = statusService.statusOf(event.getPlayer().getUniqueId());
        if (status != AccountStatus.PREMIUM_VERIFIED && status != AccountStatus.OFFLINE_FALLBACK) return;
        HybridJoinFeedback.Messages messages = HybridJoinFeedback.messages(status,
                event.getPlayer().getLocale(), statusService.identityOf(event.getPlayer().getUniqueId()));
        ChatColor color = status == AccountStatus.PREMIUM_VERIFIED ? ChatColor.GREEN : ChatColor.RED;
        if (settings.showPlayerChat()) event.getPlayer().sendMessage(color + messages.chat());
        if (!settings.showVanillaActionBar()) return;

        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || statusChannelClients.contains(playerId)
                    || statusService.statusOf(playerId) != status) return;
            HybridJoinFeedback.Messages current = HybridJoinFeedback.messages(status, player.getLocale(),
                    statusService.identityOf(playerId));
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    TextComponent.fromLegacyText(color + current.actionBar()));
        }, settings.vanillaActionBarDelayTicks());
    }

    @EventHandler public void clearStatus(PlayerQuitEvent event) {
        statusChannelClients.remove(event.getPlayer().getUniqueId());
        statusService.clear(event.getPlayer().getUniqueId());
    }

    private void sweepPending() {
        long now = System.currentTimeMillis();
        attemptsByConnection.values().forEach(attempt -> {
            if (!attempt.connection().injector().isConnected()) {
                dispatchOptional(attempt, attempt.coordinator().disconnected());
            } else if (now >= token(attempt).deadlineEpochMilli()) {
                dispatchOptional(attempt, attempt.coordinator().timeout());
            }
        });
    }

    private void failClosed(PacketEvent event, Throwable failure) {
        getLogger().log(java.util.logging.Level.WARNING, "Login packet failed closed", failure);
        PendingAttempt attempt;
        try {
            attempt = attemptsByConnection.get(connectionKey(event));
        } catch (RuntimeException ignored) {
            attempt = null;
        }
        if (attempt != null) {
            dispatchOptional(attempt, attempt.coordinator().internalError());
            return;
        }
        try {
            MinimalInjector injector = TemporaryPlayerFactory.getInjectorFromPlayer(event.getPlayer());
            if (injector == null) throw new IllegalStateException("missing temporary login injector");
            disconnectEarly(bridge.resolve(injector), LoginDenialMessages.STARTUP_FAILURE);
        } catch (Throwable disconnectFailure) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "Could not send the native startup denial; closing the login channel", disconnectFailure);
            event.getPlayer().kickPlayer(LoginDenialMessages.STARTUP_FAILURE);
        }
    }

    private void dispatchOptional(PendingAttempt attempt, Optional<HybridLoginCoordinator.Effect> effect) {
        effect.ifPresent(next -> dispatch(attempt, next));
    }

    private void cleanup(PendingAttempt attempt) {
        attemptsByConnection.remove(attempt.connectionKey(), attempt);
        attemptsByName.remove(attempt.nameKey(), attempt);
        if (attempt.effectiveNameKey() != null) {
            attemptsByName.remove(attempt.effectiveNameKey(), attempt);
        }
        if (attempt.releaseSlot()) pendingSlots.release();
    }

    private HybridIdentityPolicy.StoredIdentity coordinatorStoredIdentity(
            PersistentIdentityRepository.Record record, Optional<UUID> uuidHint
    ) {
        if (record == null) return HybridIdentityPolicy.StoredIdentity.UNKNOWN;
        if (record.premium() != null
                && (settings.admissionMode() != AdmissionMode.SAFE_PARALLEL
                && settings.admissionMode() != AdmissionMode.CONSENT_REQUIRED
                || uuidHint.map(record.premium().uuid()::equals).orElse(false))) {
            return HybridIdentityPolicy.StoredIdentity.PREMIUM_LOCKED;
        }
        // Every offline route is reclassified by the authoritative name lookup.
        // The repository is consulted afterward to choose authenticate/enroll;
        // the untrusted Login Start UUID hint never chooses an offline account.
        return HybridIdentityPolicy.StoredIdentity.UNKNOWN;
    }

    private void setExpectedProfile(PendingAttempt attempt, VerifiedProfile profile) {
        attempt.expectedProfile(profile);
        String effectiveKey = normalizeName(profile.name());
        if (!effectiveKey.equals(attempt.nameKey())) {
            PendingAttempt collision = attemptsByName.putIfAbsent(effectiveKey, attempt);
            if (collision != null && collision != attempt) {
                throw new IllegalStateException("effective identity name is already authenticating");
            }
            attempt.effectiveNameKey(effectiveKey);
        }
    }

    private void preparePremiumCanonicalName(PendingAttempt attempt, String canonicalName) throws Exception {
        PersistentIdentityRepository.Record record = identities.findByBaseName(attempt.requestedName()).orElse(null);
        if (record == null || record.offline() == null
                || !record.offline().effectiveName().equalsIgnoreCase(canonicalName)) return;
        if (settings.admissionMode() == AdmissionMode.CONSENT_REQUIRED) {
            if (!attempt.collisionMoveApproved()) {
                throw new IllegalStateException(
                        "collision reached profile application without explicit administrator approval");
            }
            moveExistingOfflineToAlias(attempt);
            return;
        }
        String alias = aliasAllocator.allocate(attempt.requestedName(), record.offline().uuid(),
                settings.aliasPrefix(), identities::effectiveNameUnavailable);
        if (MinecraftNames.isCanonical(alias)) {
            throw new IllegalStateException(
                    "cannot promote a live legacy identity with a canonical-compatible alias prefix; "
                            + "configure aliases.prefix to '.', '+', or '-'");
        }
        identities.setOfflineAlias(record.offline().uuid(), alias);
        org.bukkit.entity.Player liveOffline = Bukkit.getPlayer(record.offline().uuid());
        if (liveOffline != null && liveOffline.isOnline()) {
            Bukkit.getScheduler().runTask(this, () -> liveOffline.kickPlayer(
                    "Your offline identity now uses " + alias
                            + " because the verified premium account joined. Reconnect with the new alias."));
        }
    }

    private static HybridLoginCoordinator.AttemptToken token(PendingAttempt attempt) {
        return attempt.token();
    }

    private static boolean sameIdentity(VerifiedProfile expected, VerifiedProfile actual) {
        return expected.uuid().equals(actual.uuid()) && expected.name().equals(actual.name())
                && expected.properties().equals(actual.properties());
    }

    private static int nextTransactionId() {
        int value = NEXT_TRANSACTION.getAndIncrement();
        if (value < 0) throw new IllegalStateException("TrueUUID transaction id space exhausted");
        return value;
    }

    private static String connectionKey(PacketEvent event) {
        InetSocketAddress address = event.getPlayer().getAddress();
        if (address == null || address.getAddress() == null) throw new IllegalStateException("login has no remote address");
        return address.toString();
    }

    private static String remoteIp(PacketEvent event) {
        InetSocketAddress address = event.getPlayer().getAddress();
        if (address == null || address.getAddress() == null) throw new IllegalStateException("login has no remote IP");
        return address.getAddress().getHostAddress();
    }

    private static String normalizeName(String name) {
        return Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
    }

    private static final class PendingAttempt {
        private final String connectionKey;
        private final String nameKey;
        private final String requestedName;
        private final String remoteIp;
        private final Object originalStart;
        private final ExactSpigot1201Bridge.Connection connection;
        private final HybridLoginCoordinator coordinator;
        private final HybridLoginCoordinator.AttemptToken token;
        private final PersistentIdentityRepository.Record storedRecord;
        private final Optional<UUID> uuidHint;
        private final java.util.concurrent.atomic.AtomicBoolean slotReleased = new java.util.concurrent.atomic.AtomicBoolean();
        private volatile boolean nativeProofStarted;
        private volatile VerifiedProfile expectedProfile;
        private volatile PersistentHybridIdentityStore.Authority authority = PersistentHybridIdentityStore.Authority.MOJANG;
        private volatile PersistentHybridIdentityStore.Authority offlineAuthority;
        private volatile HybridIdentityPolicy.AuthorityLookup authorityLookup;
        private volatile HybridIdentityPolicy.PremiumProof premiumFailure;
        private volatile OfflineAuthPort.Failure offlineFailure;
        private volatile boolean offlineClientGate;
        private volatile String transport = "CLIENT_ASSISTED";
        private volatile String denialMessage;
        private volatile String effectiveNameKey;
        private volatile boolean aliasRequired;
        private volatile AuthorityResult authorityResult;
        private volatile boolean collisionMoveApproved;

        private PendingAttempt(String connectionKey, String nameKey, String requestedName, String remoteIp,
                               Object originalStart, ExactSpigot1201Bridge.Connection connection,
                               HybridLoginCoordinator coordinator, HybridLoginCoordinator.AttemptToken token,
                               PersistentIdentityRepository.Record storedRecord, Optional<UUID> uuidHint) {
            this.connectionKey = connectionKey;
            this.nameKey = nameKey;
            this.requestedName = requestedName;
            this.remoteIp = remoteIp;
            this.originalStart = originalStart;
            this.connection = connection;
            this.coordinator = coordinator;
            this.token = token;
            this.storedRecord = storedRecord;
            this.uuidHint = Objects.requireNonNull(uuidHint, "uuidHint");
        }

        String connectionKey() { return connectionKey; }
        String nameKey() { return nameKey; }
        String requestedName() { return requestedName; }
        String remoteIp() { return remoteIp; }
        Object originalStart() { return originalStart; }
        ExactSpigot1201Bridge.Connection connection() { return connection; }
        HybridLoginCoordinator coordinator() { return coordinator; }
        HybridLoginCoordinator.AttemptToken token() { return token; }
        PersistentIdentityRepository.Record storedRecord() { return storedRecord; }
        Optional<UUID> uuidHint() { return uuidHint; }
        boolean nativeProofStarted() { return nativeProofStarted; }
        void nativeProofStarted(boolean value) { nativeProofStarted = value; }
        VerifiedProfile expectedProfile() { return expectedProfile; }
        void expectedProfile(VerifiedProfile value) { expectedProfile = value; }
        PersistentHybridIdentityStore.Authority authority() { return authority; }
        void authority(PersistentHybridIdentityStore.Authority value) { authority = value; }
        PersistentHybridIdentityStore.Authority offlineAuthority() { return offlineAuthority; }
        void offlineAuthority(PersistentHybridIdentityStore.Authority value) { offlineAuthority = value; }
        HybridIdentityPolicy.AuthorityLookup authorityLookup() { return authorityLookup; }
        void authorityLookup(HybridIdentityPolicy.AuthorityLookup value) { authorityLookup = value; }
        HybridIdentityPolicy.PremiumProof premiumFailure() { return premiumFailure; }
        void premiumFailure(HybridIdentityPolicy.PremiumProof value) { premiumFailure = value; }
        OfflineAuthPort.Failure offlineFailure() { return offlineFailure; }
        void offlineFailure(OfflineAuthPort.Failure value) { offlineFailure = value; }
        boolean offlineClientGate() { return offlineClientGate; }
        void offlineClientGate(boolean value) { offlineClientGate = value; }
        String transport() { return transport; }
        void transport(String value) { transport = value; }
        String denialMessage() { return denialMessage; }
        void denialMessage(String value) { denialMessage = value; }
        String effectiveNameKey() { return effectiveNameKey; }
        void effectiveNameKey(String value) { effectiveNameKey = value; }
        boolean aliasRequired() { return aliasRequired; }
        void aliasRequired(boolean value) { aliasRequired = value; }
        AuthorityResult authorityResult() { return authorityResult; }
        void authorityResult(AuthorityResult value) { authorityResult = value; }
        boolean collisionMoveApproved() { return collisionMoveApproved; }
        void collisionMoveApproved(boolean value) { collisionMoveApproved = value; }
        boolean releaseSlot() { return slotReleased.compareAndSet(false, true); }
    }
}
