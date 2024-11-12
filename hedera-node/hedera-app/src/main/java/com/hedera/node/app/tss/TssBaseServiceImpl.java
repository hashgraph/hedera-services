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
import static com.hedera.node.app.tss.TssBaseService.Status.PENDING_LEDGER_ID;
import static com.hedera.node.app.tss.handlers.TssUtils.computeParticipantDirectory;
import static com.hedera.node.app.tss.handlers.TssVoteHandler.hasMetThreshold;
import static com.swirlds.platform.roster.RosterRetriever.getCandidateRosterHash;
import static com.swirlds.platform.roster.RosterRetriever.retrieveActiveOrGenesisRoster;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssShareSignatureTransactionBody;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.handlers.TssHandlers;
import com.hedera.node.app.tss.handlers.TssSubmissions;
import com.hedera.node.app.tss.schemas.V0560TssBaseSchema;
import com.hedera.node.app.tss.stores.ReadableTssStore;
import com.hedera.node.app.tss.stores.ReadableTssStoreImpl;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.RosterStateId;
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
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
    private final ExecutorService signingExecutor;
    private final TssKeysAccessor tssKeysAccessor;
    private final AppContext appContext;

    public TssBaseServiceImpl(
            @NonNull final AppContext appContext,
            @NonNull final ExecutorService signingExecutor,
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
        this.tssMetrics = component.tssMetrics();
        this.tssHandlers = new TssHandlers(
                component.tssMessageHandler(), component.tssVoteHandler(), component.tssShareSignatureHandler());
        this.tssSubmissions = component.tssSubmissions();
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V0560TssBaseSchema());
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
        final var selfId = (int) context.networkInfo().selfNodeInfo().nodeId();

        final var candidateDirectory = computeParticipantDirectory(candidateRoster, maxSharesPerNode, selfId);
        final var activeRoster = requireNonNull(
                context.storeFactory().readableStore(ReadableRosterStore.class).getActiveRoster());
        final var activeRosterHash = RosterUtils.hash(activeRoster).getBytes();
        final var tssPrivateShares = tssKeysAccessor.accessTssKeys().activeRosterShares();

        final var candidateRosterHash = RosterUtils.hash(candidateRoster).getBytes();
        // FUTURE - instead of an arbitrary counter here, use the share index from the private share
        final var shareIndex = new AtomicInteger(0);
        for (final var tssPrivateShare : tssPrivateShares) {
            CompletableFuture.runAsync(
                            () -> {
                                final var msg = tssLibrary.generateTssMessage(candidateDirectory, tssPrivateShare);
                                final var tssMessage = TssMessageTransactionBody.newBuilder()
                                        .sourceRosterHash(activeRosterHash)
                                        .targetRosterHash(candidateRosterHash)
                                        .shareIndex(shareIndex.getAndAdd(1))
                                        .tssMessage(Bytes.wrap(msg.bytes()))
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
    public void requestLedgerSignature(final byte[] messageHash, final Instant lastUsedConsensusTime) {
        requireNonNull(messageHash);
        // (TSS-FUTURE) Initiate an asynchronous process of creating a ledger signature
        final var mockSignature = noThrowSha384HashOf(messageHash);
        CompletableFuture.runAsync(
                () -> {
                    if (appContext
                            .configSupplier()
                            .get()
                            .getConfigData(TssConfig.class)
                            .keyCandidateRoster()) {
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
                    .shareSignature(Bytes.wrap(signature.signature().signature().toBytes()))
                    .shareIndex(privateShare.shareId().idElement())
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
            @NonNull State state,
            @NonNull InitTrigger trigger,
            @NonNull ServiceMigrator serviceMigrator,
            @NonNull ServicesSoftwareVersion version,
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
                            state.getReadableStates(RosterStateId.NAME).get(RosterStateId.ROSTER_KEY));
                    // It should be impossible to set a candidate roster hash that doesn't exist
                    return requireNonNull(rosters.get(new ProtoBytes(candidateRosterHash)));
                }
            }
        }
        return activeRoster;
    }

    @Override
    public void regenerateKeyMaterial(@NonNull final State state) {
        tssKeysAccessor.generateKeyMaterialForActiveRoster(
                state,
                appContext.configSupplier().get(),
                appContext.selfNodeInfoSupplier().get().nodeId());
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

    @VisibleForTesting
    public TssKeysAccessor getTssKeysAccessor() {
        return tssKeysAccessor;
    }
}
