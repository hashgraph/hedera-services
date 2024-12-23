/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.tss;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.node.app.tss.RosterToKey.ACTIVE_ROSTER;
import static com.hedera.node.app.tss.RosterToKey.CANDIDATE_ROSTER;
import static com.hedera.node.app.tss.RosterToKey.NONE;
import static com.hedera.node.app.tss.TssBaseService.Status.PENDING_LEDGER_ID;
import static com.hedera.node.app.tss.TssKeyingStatus.KEYING_COMPLETE;
import static com.hedera.node.app.tss.TssKeyingStatus.WAITING_FOR_ENCRYPTION_KEYS;
import static com.hedera.node.app.tss.TssKeyingStatus.WAITING_FOR_THRESHOLD_TSS_MESSAGES;
import static com.hedera.node.app.tss.TssKeyingStatus.WAITING_FOR_THRESHOLD_TSS_VOTES;
import static com.hedera.node.app.tss.handlers.TssUtils.SIGNATURE_SCHEMA;
import static com.hedera.node.app.tss.handlers.TssUtils.computeParticipantDirectory;
import static com.hedera.node.app.tss.handlers.TssUtils.hasMetThreshold;
import static com.hedera.node.app.tss.handlers.TssUtils.voteForValidMessages;
import static com.swirlds.platform.roster.RosterRetriever.getCandidateRosterHash;
import static com.swirlds.platform.roster.RosterRetriever.retrieveActiveOrGenesisRoster;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.tss.TssEncryptionKeys;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssShareSignatureTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.roster.schemas.V0540RosterSchema;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.api.FakeGroupElement;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.node.app.tss.handlers.TssSubmissions;
import com.hedera.node.app.tss.schemas.V0560TssBaseSchema;
import com.hedera.node.app.tss.schemas.V0580TssBaseSchema;
import com.hedera.node.app.tss.stores.ReadableTssStore;
import com.hedera.node.app.tss.stores.ReadableTssStoreImpl;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Default implementation of the {@link TssBaseService}.
 */
public class TssBaseServiceImpl implements TssBaseService {
    private static final Logger log = LogManager.getLogger(TssBaseServiceImpl.class);

    /**
     * Copy-on-write list to avoid concurrent modification exceptions if a consumer unregisters
     * itself in its callback.
     */
    private final List<BiConsumer<byte[], byte[]>> consumers = new CopyOnWriteArrayList<>();

    private final TssMetrics tssMetrics;
    private final TssLibrary tssLibrary;
    private final TssHandlers tssHandlers;
    private final TssSubmissions tssSubmissions;
    private final Executor tssLibraryExecutor;
    private final Executor signingExecutor;
    private final TssKeysAccessor tssKeysAccessor;
    private final TssDirectoryAccessor tssDirectoryAccessor;
    private final AppContext appContext;
    private final TssCryptographyManager tssCryptographyManager;
    // Indicates whether the current node has already submitted a tss message for the target roster.
    // This is false by default and will be set to true when the node submits a message for the target roster.
    // This is reset to false when we start keying a candidate roster
    private boolean haveSentMessageForTargetRoster;
    // Indicates whether the current node has already submitted a tss vote for the target roster.
    // This is false by default and will be set to true when the node submits a vote for the target roster.
    // This is reset to false when we start keying a candidate roster
    private boolean haveSentVoteForTargetRoster;
    // Indicates the current TssStatus of the network.
    // This is used to determine the next steps in the TSS lifecycle.
    // This is set to null by default and will be updated from state when each second is processed.
    // This is also null when the network restarts or reconnects.
    private TssStatus tssStatus;

    public TssBaseServiceImpl(
            @NonNull final AppContext appContext,
            @NonNull final Executor signingExecutor,
            @NonNull final Executor submissionExecutor,
            @NonNull final TssLibrary tssLibrary,
            @NonNull final Executor tssLibraryExecutor,
            @NonNull final Metrics metrics) {
        requireNonNull(appContext);
        this.tssLibrary = requireNonNull(tssLibrary);
        this.signingExecutor = requireNonNull(signingExecutor);
        this.tssLibraryExecutor = requireNonNull(tssLibraryExecutor);
        this.appContext = requireNonNull(appContext);
        final var component = DaggerTssBaseServiceComponent.factory()
                .create(
                        tssLibrary,
                        appContext.instantSource(),
                        appContext,
                        submissionExecutor,
                        tssLibraryExecutor,
                        metrics,
                        this);
        this.tssKeysAccessor = component.tssKeysAccessor();
        this.tssDirectoryAccessor = component.tssDirectoryAccessor();
        this.tssMetrics = component.tssMetrics();
        this.tssHandlers = new TssHandlers(
                component.tssMessageHandler(), component.tssVoteHandler(), component.tssShareSignatureHandler());
        this.tssSubmissions = component.tssSubmissions();
        this.tssCryptographyManager = component.tssCryptographyManager();
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V0560TssBaseSchema());
        registry.register(new V0580TssBaseSchema());
    }

    @Override
    public Status getStatus(
            @NonNull final Roster roster,
            @NonNull final Bytes ledgerId,
            @NonNull final ReadableTssStoreImpl tssBaseStore) {
        requireNonNull(roster);
        requireNonNull(ledgerId);
        requireNonNull(tssBaseStore);
        // (TSS-FUTURE) Determine if the given ledger id can be recovered from the key material for the given roster
        return PENDING_LEDGER_ID;
    }

    @Override
    public void bootstrapLedgerId(
            @NonNull final Roster roster,
            @NonNull final HandleContext context,
            @NonNull final Consumer<Bytes> ledgerIdConsumer) {
        requireNonNull(roster);
        requireNonNull(context);
        requireNonNull(ledgerIdConsumer);
        // (TSS-FUTURE) Create a real ledger id
        ledgerIdConsumer.accept(Bytes.EMPTY);
    }

    @Override
    public void setCandidateRoster(@NonNull final Roster candidateRoster, @NonNull final HandleContext context) {
        requireNonNull(candidateRoster);

        // we keep track of the starting point of the candidate roster's lifecycle
        final Instant candidateRosterLifecycleStart = InstantSource.system().instant();
        tssMetrics.trackCandidateRosterLifecycleStart(candidateRosterLifecycleStart);
        // (TSS-FUTURE) Implement `keyActiveRoster`
        // https://github.com/hashgraph/hedera-services/issues/16166

        final var maxSharesPerNode =
                context.configuration().getConfigData(TssConfig.class).maxSharesPerNode();

        // TODO - use the real encryption keys from state
        final LongFunction<BlsPublicKey> encryptionKeyFn =
                nodeId -> new BlsPublicKey(new FakeGroupElement(BigInteger.valueOf(nodeId)), SIGNATURE_SCHEMA);
        final var candidateDirectory = computeParticipantDirectory(candidateRoster, maxSharesPerNode, encryptionKeyFn);
        final var activeRoster = requireNonNull(
                context.storeFactory().readableStore(ReadableRosterStore.class).getActiveRoster());
        final var sourceRosterHash = RosterUtils.hash(activeRoster).getBytes();

        final var tssPrivateShares = tssKeysAccessor.accessTssKeys().activeRosterShares();

        final var candidateRosterHash = RosterUtils.hash(candidateRoster).getBytes();
        // FUTURE - instead of an arbitrary counter here, use the share index from the private share
        final var shareIndex = new AtomicInteger(0);
        for (final var tssPrivateShare : tssPrivateShares) {
            CompletableFuture.runAsync(
                            () -> {
                                final var msg = tssLibrary.generateTssMessage(candidateDirectory, tssPrivateShare);
                                final var tssMessage = TssMessageTransactionBody.newBuilder()
                                        .sourceRosterHash(sourceRosterHash)
                                        .targetRosterHash(candidateRosterHash)
                                        .shareIndex(shareIndex.getAndAdd(1))
                                        .tssMessage(Bytes.wrap(msg.toBytes()))
                                        .build();
                                tssSubmissions.submitTssMessage(tssMessage, context);
                            },
                            tssLibraryExecutor)
                    .exceptionally(e -> {
                        log.error("Error generating tssMessage", e);
                        return null;
                    });
        }
    }

    @Override
    public void requestLedgerSignature(
            @NonNull final byte[] messageHash, @NonNull final Instant lastUsedConsensusTime) {
        requireNonNull(messageHash);
        requireNonNull(lastUsedConsensusTime);
        final var mockSignature = noThrowSha384HashOf(messageHash);
        CompletableFuture.runAsync(
                () -> {
                    if (appContext
                            .configSupplier()
                            .get()
                            .getConfigData(TssConfig.class)
                            .signWithLedgerId()) {
                        submitShareSignatures(messageHash, lastUsedConsensusTime);
                    } else {
                        // This is only for testing purposes when the candidate roster is
                        // not enabled
                        consumers.forEach(consumer -> {
                            try {
                                consumer.accept(messageHash, mockSignature);
                            } catch (Exception e) {
                                log.error(
                                        "Failed to provide signature {} on message {} to consumer {}",
                                        CommonUtils.hex(mockSignature),
                                        CommonUtils.hex(messageHash),
                                        consumer,
                                        e);
                            }
                        });
                    }
                },
                signingExecutor);
    }

    private void submitShareSignatures(final byte[] messageHash, final Instant lastUsedConsensusTime) {
        final var tssPrivateShares = tssKeysAccessor.accessTssKeys().activeRosterShares();
        final var activeRoster = tssKeysAccessor.accessTssKeys().activeRosterHash();
        long nanosOffset = 1;
        for (final var privateShare : tssPrivateShares) {
            final var signature = tssLibrary.sign(privateShare, messageHash);
            final var tssShareSignatureBody = TssShareSignatureTransactionBody.newBuilder()
                    .messageHash(Bytes.wrap(messageHash))
                    .shareSignature(Bytes.wrap(signature.signature().toBytes()))
                    .shareIndex(privateShare.shareId())
                    .rosterHash(activeRoster)
                    .build();
            tssSubmissions.submitTssShareSignature(
                    tssShareSignatureBody, lastUsedConsensusTime.plusNanos(nanosOffset++));
        }
    }

    @Override
    public void registerLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {
        requireNonNull(consumer);
        consumers.add(consumer);
    }

    @Override
    public void unregisterLedgerSignatureConsumer(@NonNull final BiConsumer<byte[], byte[]> consumer) {
        requireNonNull(consumer);
        consumers.remove(consumer);
    }

    @Override
    public TssHandlers tssHandlers() {
        return tssHandlers;
    }

    @Override
    @NonNull
    public Roster chooseRosterForNetwork(
            @NonNull final State state,
            @NonNull final InitTrigger trigger,
            @NonNull final ServiceMigrator serviceMigrator,
            @NonNull final ServicesSoftwareVersion version,
            @NonNull final Configuration configuration,
            @NonNull final Roster overrideRoster) {
        if (!configuration.getConfigData(TssConfig.class).keyCandidateRoster()) {
            return overrideRoster;
        }
        final var activeRoster = retrieveActiveOrGenesisRoster(state);
        if (trigger != GENESIS) {
            final var creatorVersion = requireNonNull(serviceMigrator.creationVersionOf(state));
            final var isUpgrade = version.compareTo(new ServicesSoftwareVersion(creatorVersion)) > 0;
            // If we are not at genesis and the software version is newer than the state version, then we need to
            // pick the active roster or candidate roster based on votes in the state
            if (isUpgrade) {
                final var candidateRosterHash = getCandidateRosterHash(state);
                final var tssStore = new ReadableStoreFactory(state).getStore(ReadableTssStore.class);
                if (hasEnoughWeight(activeRoster, candidateRosterHash, tssStore)) {
                    final ReadableKVState<ProtoBytes, Roster> rosters = requireNonNull(
                            state.getReadableStates(RosterService.NAME).get(V0540RosterSchema.ROSTER_KEY));
                    // It should be impossible to set a candidate roster hash that doesn't exist
                    return requireNonNull(rosters.get(new ProtoBytes(candidateRosterHash)));
                }
            }
        }
        return activeRoster;
    }

    @Override
    public void regenerateKeyMaterial(@NonNull final State state) {
        tssKeysAccessor.generateKeyMaterialForActiveRoster(state);
    }

    @Override
    public void ensureParticipantDirectoryKnown(@NonNull final State state) {
        tssDirectoryAccessor.generateTssParticipantDirectory(state);
    }

    @Override
    public Bytes ledgerIdFrom(
            @NonNull final TssParticipantDirectory directory, @NonNull final List<TssMessage> tssMessages) {
        requireNonNull(directory);
        requireNonNull(tssMessages);
        final var publicShares = tssLibrary.computePublicShares(directory, tssMessages);
        final var publicKey = tssLibrary.aggregatePublicShares(publicShares);
        return Bytes.wrap(publicKey.toBytes());
    }

    /**
     * Notifies the consumers that a signature has been received for the message hash.
     *
     * @param messageHash the message hash
     * @param signature   the signature
     */
    public void notifySignature(@NonNull final byte[] messageHash, @NonNull final byte[] signature) {
        requireNonNull(messageHash);
        requireNonNull(signature);
        consumers.forEach(consumer -> {
            try {
                consumer.accept(messageHash, signature);
            } catch (Exception e) {
                log.error(
                        "Failed to provide signature {} on message {} to consumer {}",
                        CommonUtils.hex(signature),
                        CommonUtils.hex(messageHash),
                        consumer,
                        e);
            }
        });
    }

    /**
     * Returns true if there exists a vote bitset for the given candidate roster hash whose received weight
     * is at least 1/3 of the total weight of the active roster.
     *
     * @param activeRoster the active roster
     * @param rosterHash   the candidate roster hash
     * @param tssBaseStore the TSS store
     * @return true if the threshold has been reached, false otherwise
     */
    private static boolean hasEnoughWeight(
            @NonNull final Roster activeRoster,
            @NonNull final Bytes rosterHash,
            @NonNull final ReadableTssStore tssBaseStore) {
        // Also get the total active roster weight
        long activeRosterTotalWeight = 0;
        final var voteWeightMap = new LinkedHashMap<Bytes, Long>();
        for (final var rosterEntry : activeRoster.rosterEntries()) {
            activeRosterTotalWeight += rosterEntry.weight();
            final var tssVoteMapKey = new TssVoteMapKey(rosterHash, rosterEntry.nodeId());
            if (tssBaseStore.exists(tssVoteMapKey)) {
                final var voteBody = tssBaseStore.getVote(tssVoteMapKey);
                voteWeightMap.merge(voteBody.tssVote(), rosterEntry.weight(), Long::sum);
            }
        }
        // Use hasMetThreshold to check if any of the votes have met the threshold
        for (final var voteWeight : voteWeightMap.values()) {
            if (hasMetThreshold(voteWeight, activeRosterTotalWeight)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TssMessage getTssMessageFromBytes(Bytes wrap, TssParticipantDirectory directory) {
        return tssLibrary.getTssMessageFromBytes(wrap, directory);
    }

    @Override
    public void manageTssStatus(
            final State state,
            final boolean isStakePeriodBoundary,
            final Instant consensusNow,
            final StoreMetricsService storeMetricsService) {
        if (!appContext.configSupplier().get().getConfigData(TssConfig.class).keyCandidateRoster()) {
            return;
        }
        final var readableStoreFactory = new ReadableStoreFactory(state);
        final var tssStore = readableStoreFactory.getStore(ReadableTssStore.class);
        final var rosterStore = readableStoreFactory.getStore(ReadableRosterStore.class);
        // If the Tss Status is not computed yet during restart or reconnect, compute it from state.
        if (tssStatus == null) {
            tssStatus = computeInitialTssStatus(tssStore, rosterStore);
        }

        // In order for the TSS state machine to run asynchronously in a separate thread, all the necessary
        // information is collected and passed to the manageTssStatus method.
        final var targetRosterHash = getTargetRosterHash(
                requireNonNull(rosterStore.getActiveRoster()), rosterStore.getCandidateRoster(), tssStatus);

        // collect tss encryption keys for all nodes in the active roster that are not null
        final var targetRoster = rosterStore.get(targetRosterHash);
        final List<TssEncryptionKeys> targetRosterEncryptionKeys = targetRoster == null
                ? List.of()
                : targetRoster.rosterEntries().stream()
                        .map(entry -> tssStore.getTssEncryptionKeys(entry.nodeId()))
                        .filter(Objects::nonNull)
                        .filter(k -> k.currentEncryptionKey().equals(Bytes.EMPTY))
                        .toList();

        final var voteKey = new TssVoteMapKey(
                targetRosterHash, appContext.selfNodeInfoSupplier().get().nodeId());
        final var info = new RosterAndTssInfo(
                rosterStore.getActiveRoster(),
                requireNonNull(rosterStore.getCurrentRosterHash()),
                rosterStore.getCandidateRoster(),
                targetRosterHash,
                tssStore.getMessagesForTarget(targetRosterHash),
                tssStore.anyWinningVoteFrom(rosterStore.getCurrentRosterHash(), targetRosterHash, rosterStore),
                targetRosterEncryptionKeys,
                tssStore.getVote(voteKey));
        CompletableFuture.runAsync(
                () -> updateTssStatus(isStakePeriodBoundary, consensusNow, info), tssLibraryExecutor);
    }

    /**
     * Computes the initial TssStatus when the network restarts or reconnects or on genesis.
     * This is called only once when JVM restarts.
     *
     * @param tssStore    the TSS store
     * @param rosterStore the roster store
     * @return the initial TssStatus
     */
    TssStatus computeInitialTssStatus(final ReadableTssStore tssStore, final ReadableRosterStore rosterStore) {
        final var activeRosterHash = requireNonNull(rosterStore.getCurrentRosterHash());
        final var candidateRoster = rosterStore.getCandidateRoster();
        final var candidateRosterHash =
                candidateRoster != null ? RosterUtils.hash(candidateRoster).getBytes() : null;

        final var winningVoteActive = tssStore.anyWinningVoteFor(activeRosterHash, rosterStore);
        if (winningVoteActive.isEmpty()) {
            final var keyingStatus = getTssKeyingStatus(tssStore, activeRosterHash, rosterStore.getActiveRoster());
            return new TssStatus(keyingStatus, ACTIVE_ROSTER, Bytes.EMPTY);
        }

        final var activeRosterLedgerId = winningVoteActive.get().ledgerId();
        if (candidateRosterHash != null) {
            final var winningVoteCandidate =
                    tssStore.anyWinningVoteFrom(activeRosterHash, candidateRosterHash, rosterStore);
            return winningVoteCandidate
                    .map(voteBody -> new TssStatus(KEYING_COMPLETE, NONE, voteBody.ledgerId()))
                    .orElseGet(() -> {
                        final var keyingStatus =
                                getTssKeyingStatus(tssStore, candidateRosterHash, rosterStore.getCandidateRoster());
                        return new TssStatus(keyingStatus, CANDIDATE_ROSTER, activeRosterLedgerId);
                    });
        }

        return new TssStatus(KEYING_COMPLETE, NONE, activeRosterLedgerId);
    }

    /**
     * Verifies the current TSS status when computing initial status.
     *
     * @param tssStore         the TSS store
     * @param targetRosterHash the target roster hash
     * @param targetRoster     the target roster
     * @return the TSS keying status
     */
    private TssKeyingStatus getTssKeyingStatus(
            final ReadableTssStore tssStore, final Bytes targetRosterHash, final Roster targetRoster) {
        final var numEncryptionKeys = requireNonNull(targetRoster).rosterEntries().stream()
                .map(entry -> tssStore.getTssEncryptionKeys(entry.nodeId()))
                .filter(Objects::nonNull)
                .filter(k -> !k.currentEncryptionKey().equals(Bytes.EMPTY))
                .count();
        if (numEncryptionKeys != targetRoster.rosterEntries().size()) {
            return WAITING_FOR_ENCRYPTION_KEYS;
        }
        // Since this is called only once when JVM restarts, it is okay to do these synchronously.
        final var activeDirectory = tssDirectoryAccessor.activeParticipantDirectory();
        final var tssMessages = tssStore.getMessagesForTarget(targetRosterHash);
        final var result = voteForValidMessages(tssMessages, activeDirectory, tssLibrary);
        if (result.isEmpty()) {
            return WAITING_FOR_THRESHOLD_TSS_MESSAGES;
        } else {
            return WAITING_FOR_THRESHOLD_TSS_VOTES;
        }
    }

    /**
     * Computes the next TSS status from the state.
     *
     * @param isStakePeriodBoundary whether the current consensus round is a stake period boundary
     * @param consensusNow          the current consensus time
     * @param info                  the roster and TSS information
     */
    void updateTssStatus(final boolean isStakePeriodBoundary, final Instant consensusNow, final RosterAndTssInfo info) {
        final var statusChange = new StatusChange(isStakePeriodBoundary, consensusNow, info);
        this.tssStatus = statusChange.computeNewStatus();
    }

    @VisibleForTesting
    public TssKeysAccessor getTssKeysAccessor() {
        return tssKeysAccessor;
    }

    /**
     * A class to manage the status change of the TSS.
     * It computes the new status based on the old status and the current state of the system.
     * If needed, it schedules work to generate TSS messages and votes.
     */
    public class StatusChange {
        private TssKeyingStatus newKeyingStatus;
        private RosterToKey newRosterToKey;
        private Bytes newLedgerId;
        private final boolean isStakePeriodBoundary;
        private final Instant consensusNow;
        private final RosterAndTssInfo info;

        public StatusChange(
                final boolean isStakePeriodBoundary, final Instant consensusNow, final RosterAndTssInfo info) {
            this.isStakePeriodBoundary = isStakePeriodBoundary;
            this.info = info;
            this.newKeyingStatus = tssStatus.tssKeyingStatus();
            this.newRosterToKey = tssStatus.rosterToKey();
            this.newLedgerId = tssStatus.ledgerId();
            this.consensusNow = consensusNow;
        }

        /**
         * Computes the new status based on the old status and the current state of the system.
         * If needed, it schedules work to generate TSS messages and votes.
         *
         * @return the new status
         */
        public TssStatus computeNewStatus() {
            switch (tssStatus.rosterToKey()) {
                case NONE -> {
                    if (isStakePeriodBoundary) {
                        newRosterToKey = CANDIDATE_ROSTER;
                        newKeyingStatus = WAITING_FOR_ENCRYPTION_KEYS;
                        haveSentMessageForTargetRoster = false;
                        haveSentVoteForTargetRoster = false;
                    }
                }
                case CANDIDATE_ROSTER -> {
                    final var activeRosterHash = requireNonNull(info.activeRosterHash());
                    final var candidateRosterHash = RosterUtils.hash(requireNonNull(info.candidateRoster()))
                            .getBytes();

                    switch (tssStatus.tssKeyingStatus()) {
                        case KEYING_COMPLETE -> newRosterToKey = NONE;
                        case WAITING_FOR_THRESHOLD_TSS_MESSAGES -> validateMessagesAndSubmitIfNeeded(
                                activeRosterHash, candidateRosterHash);
                        case WAITING_FOR_THRESHOLD_TSS_VOTES -> validateVotesAndSubmitIfNeeded(
                                activeRosterHash, candidateRosterHash);
                        case WAITING_FOR_ENCRYPTION_KEYS -> validateThresholdEncryptionKeysReached(
                                info.candidateRoster());
                    }
                }
                case ACTIVE_ROSTER -> {
                    requireNonNull(info.activeRosterHash());
                    switch (tssStatus.tssKeyingStatus()) {
                        case KEYING_COMPLETE -> newRosterToKey = NONE;
                        case WAITING_FOR_THRESHOLD_TSS_MESSAGES -> validateMessagesAndSubmitIfNeeded(
                                Bytes.EMPTY, info.activeRosterHash());
                        case WAITING_FOR_THRESHOLD_TSS_VOTES -> validateVotesAndSubmitIfNeeded(
                                Bytes.EMPTY, info.activeRosterHash());
                        case WAITING_FOR_ENCRYPTION_KEYS -> validateThresholdEncryptionKeysReached(info.activeRoster());
                    }
                }
            }
            return new TssStatus(newKeyingStatus, newRosterToKey, newLedgerId);
        }

        /**
         * Validates the votes and submits a vote for the current node if needed to reach the threshold.
         *
         * @param targetRosterHash the target roster hash
         * @param sourceRosterHash the source roster hash
         */
        private void validateVotesAndSubmitIfNeeded(final Bytes sourceRosterHash, final Bytes targetRosterHash) {
            final var voteBodies = info.winningVote();
            if (voteBodies.isPresent()) {
                newKeyingStatus = KEYING_COMPLETE;
                newLedgerId = voteBodies.get().ledgerId();
            } else if (!haveSentVoteForTargetRoster && info.selfVote() == null) {
                // Obtain the directory of participants for the source roster
                final var directory = tssDirectoryAccessor.activeParticipantDirectory();
                final var vote = tssCryptographyManager.getVote(info.tssMessages(), directory);
                if (vote != null) {
                    final var tssVote = TssVoteTransactionBody.newBuilder()
                            .tssVote(vote.bitSet())
                            .sourceRosterHash(sourceRosterHash)
                            .targetRosterHash(targetRosterHash)
                            .ledgerId(vote.ledgerId())
                            .nodeSignature(vote.signature().getBytes())
                            .build();
                    tssSubmissions.submitTssVote(tssVote, consensusNow);
                    haveSentVoteForTargetRoster = true;
                }
            }
        }

        /**
         * Validates the messages and submits a message for the current node if needed to reach the threshold.
         *
         * @param sourceRosterHash the source roster hash
         * @param targetRosterHash the target roster hash
         */
        private void validateMessagesAndSubmitIfNeeded(final Bytes sourceRosterHash, final Bytes targetRosterHash) {
            final var thresholdReached = validateThresholdTssMessages();
            if (thresholdReached) {
                newKeyingStatus = WAITING_FOR_THRESHOLD_TSS_VOTES;
            } else if (!haveSentMessageForTargetRoster) {
                if (tssStatus.rosterToKey() == ACTIVE_ROSTER) {
                    final var msg = tssLibrary.generateTssMessage(tssDirectoryAccessor.activeParticipantDirectory());
                    final var tssMessage = TssMessageTransactionBody.newBuilder()
                            .sourceRosterHash(sourceRosterHash)
                            .targetRosterHash(targetRosterHash)
                            .shareIndex(appContext.selfNodeInfoSupplier().get().nodeId() + 1)
                            .tssMessage(Bytes.wrap(msg.toBytes()))
                            .build();
                    // need to use consensusNow here
                    tssSubmissions.submitTssMessage(tssMessage, consensusNow);
                    haveSentMessageForTargetRoster = true;
                } else if (tssStatus.rosterToKey() == CANDIDATE_ROSTER) {
                    // Obtain the directory of participants for the target roster
                    // submit ours and set haveSentMessageForTargetRoster to true
                    final var tssPrivateShares = tssKeysAccessor.accessTssKeys().activeRosterShares();
                    for (final var tssPrivateShare : tssPrivateShares) {
                        final var msg = tssLibrary.generateTssMessage(
                                tssDirectoryAccessor.generateTssParticipantDirectoryFor(info.candidateRoster()),
                                tssPrivateShare);
                        final var tssMessage = TssMessageTransactionBody.newBuilder()
                                .sourceRosterHash(sourceRosterHash)
                                .targetRosterHash(targetRosterHash)
                                .shareIndex(tssPrivateShare.shareId())
                                .tssMessage(Bytes.wrap(msg.toBytes()))
                                .build();
                        // need to use consensusNow here
                        tssSubmissions.submitTssMessage(tssMessage, consensusNow);
                        haveSentMessageForTargetRoster = true;
                    }
                }
            }
        }

        /**
         * Validates the threshold of TSS messages.
         *
         * @return true if the threshold is met, false otherwise
         */
        private boolean validateThresholdTssMessages() {
            final var participantDirectory = tssDirectoryAccessor.activeParticipantDirectory();
            final var tssMessageBodies = info.tssMessages();
            return voteForValidMessages(tssMessageBodies, participantDirectory, tssLibrary)
                    .isPresent();
        }

        /**
         * Validates the threshold of encryption keys and creates the encryption key for self if not present.
         */
        private void validateThresholdEncryptionKeysReached(final Roster roster) {
            var numTssEncryptionKeys = info.targetRosterEncryptionKeys().size();
            final var thresholdReached = numTssEncryptionKeys
                    >= (2 * requireNonNull(roster).rosterEntries().size()) / 3;
            if (thresholdReached) {
                newKeyingStatus = TssKeyingStatus.WAITING_FOR_THRESHOLD_TSS_MESSAGES;
            } else {
                // TODO: Create the encryption key for self if not present
            }
        }
    }

    /**
     * A record to hold the roster and TSS information that is needed to compute new TSS status.
     *
     * @param activeRoster               the active roster
     * @param activeRosterHash           the active roster hash
     * @param candidateRoster            the candidate roster
     * @param targetRosterHash           the target roster hash
     * @param tssMessages                the TSS messages for the target roster
     * @param winningVote                the winning vote for the target roster
     * @param targetRosterEncryptionKeys the encryption keys for the active roster
     * @param selfVote                   the self vote for the current node
     */
    public record RosterAndTssInfo(
            @NonNull Roster activeRoster,
            @NonNull Bytes activeRosterHash,
            @Nullable Roster candidateRoster,
            @NonNull Bytes targetRosterHash,
            @NonNull List<TssMessageTransactionBody> tssMessages,
            @NonNull Optional<TssVoteTransactionBody> winningVote,
            @NonNull List<TssEncryptionKeys> targetRosterEncryptionKeys,
            TssVoteTransactionBody selfVote) {}

    /**
     * Returns the target roster hash based on the current TSS status roster to key.
     *
     * @param sourceRoster    the active roster
     * @param candidateRoster the candidate roster
     * @param tssStatus       the TSS status
     * @return the target roster hash
     */
    @NonNull
    private Bytes getTargetRosterHash(
            @NonNull final Roster sourceRoster,
            @Nullable final Roster candidateRoster,
            @NonNull final TssStatus tssStatus) {
        final var rosterToKey = tssStatus.rosterToKey();
        return switch (rosterToKey) {
            case ACTIVE_ROSTER -> RosterUtils.hash(requireNonNull(sourceRoster)).getBytes();
            case CANDIDATE_ROSTER -> RosterUtils.hash(requireNonNull(candidateRoster))
                    .getBytes();
            case NONE -> Bytes.EMPTY;
        };
    }

    @VisibleForTesting
    public TssStatus getTssStatus() {
        return tssStatus;
    }

    @VisibleForTesting
    public void setTssStatus(final TssStatus tssStatus) {
        this.tssStatus = tssStatus;
    }

    @VisibleForTesting
    public boolean haveSentVoteForTargetRoster() {
        return haveSentVoteForTargetRoster;
    }

    @VisibleForTesting
    public boolean haveSentMessageForTargetRoster() {
        return haveSentMessageForTargetRoster;
    }
}
