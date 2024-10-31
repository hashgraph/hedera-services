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
import static com.hedera.node.app.tss.handlers.TssUtils.getTssMessages;
import static com.hedera.node.app.tss.handlers.TssUtils.validateTssMessages;
import static com.hedera.node.app.tss.handlers.TssVoteHandler.hasMetThreshold;
import static com.swirlds.platform.roster.RosterRetriever.retrieve;
import static com.swirlds.platform.system.InitTrigger.GENESIS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.tss.TssVoteMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.services.ServiceMigrator;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPrivateShare;
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
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.ReadableRosterStore;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.state.State;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;
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

    private final TssHandlers tssHandlers;
    private final TssSubmissions tssSubmissions;
    private final ExecutorService signingExecutor;
    private final TssLibrary tssLibrary;
    private final Executor tssLibraryExecutor;

    public TssBaseServiceImpl(
            @NonNull final AppContext appContext,
            @NonNull final ExecutorService signingExecutor,
            @NonNull final Executor submissionExecutor,
            @NonNull final TssLibrary tssLibrary,
            @NonNull final Executor tssLibraryExecutor) {
        requireNonNull(appContext);
        this.signingExecutor = requireNonNull(signingExecutor);
        final var component = DaggerTssBaseServiceComponent.factory()
                .create(appContext.gossip(), submissionExecutor, tssLibraryExecutor);
        tssHandlers = new TssHandlers(component.tssMessageHandler(), component.tssVoteHandler());
        tssSubmissions = component.tssSubmissions();
        this.tssLibrary = requireNonNull(tssLibrary);
        this.tssLibraryExecutor = requireNonNull(tssLibraryExecutor);
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

        // (TSS-FUTURE) Implement `keyActiveRoster`
        // https://github.com/hashgraph/hedera-services/issues/16166
        final var storeFactory = context.storeFactory();
        // generate TSS messages based on the active roster and the candidate roster
        final var tssStore = storeFactory.readableStore(ReadableTssStore.class);
        final var maxSharesPerNode =
                context.configuration().getConfigData(TssConfig.class).maxSharesPerNode();
        final var selfId = (int) context.networkInfo().selfNodeInfo().nodeId();

        final var activeRoster =
                storeFactory.readableStore(ReadableRosterStore.class).getActiveRoster();
        final var activeRosterHash = RosterUtils.hash(activeRoster).getBytes();

        final var activeDirectory = computeParticipantDirectory(activeRoster, maxSharesPerNode, selfId);
        final var candidateDirectory = computeParticipantDirectory(candidateRoster, maxSharesPerNode, selfId);
        final var candidateRosterHash = RosterUtils.hash(candidateRoster).getBytes();

        final var tssPrivateShares = getTssPrivateShares(activeDirectory, tssStore, activeRosterHash);
        final AtomicInteger shareIndex = new AtomicInteger(0);
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
                                // FUTURE : Remove handleContext and provide configuration and networkInfo
                                tssSubmissions.submitTssMessage(tssMessage, context);
                            },
                            tssLibraryExecutor)
                    .exceptionally(e -> {
                        log.error("Error generating tssMessage", e);
                        return null;
                    });
        }
    }

    @NonNull
    private List<TssPrivateShare> getTssPrivateShares(
            @NonNull final TssParticipantDirectory activeRosterParticipantDirectory,
            @NonNull final ReadableTssStore tssStore,
            @NonNull final Bytes activeRosterHash) {
        final var validTssOps = validateTssMessages(
                tssStore.getTssMessageBodies(activeRosterHash), activeRosterParticipantDirectory, tssLibrary);
        final var validTssMessages = getTssMessages(validTssOps);
        return tssLibrary.decryptPrivateShares(activeRosterParticipantDirectory, validTssMessages);
    }

    @Override
    public void requestLedgerSignature(@NonNull final byte[] messageHash) {
        requireNonNull(messageHash);
        // (TSS-FUTURE) Initiate asynchronous process of creating a ledger signature
        final var mockSignature = noThrowSha384HashOf(messageHash);
        CompletableFuture.runAsync(
                () -> consumers.forEach(consumer -> {
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
                }),
                signingExecutor);
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
            @NonNull ServicesSoftwareVersion version) {
        final Roster currentActiveRoster = new Roster(retrieve(state).rosterEntries());
        if (trigger != GENESIS) {
            final var creatorVersion = serviceMigrator.creationVersionOf(state);
            if (creatorVersion != null) {
                final var isUpgrade = version.compareTo(new ServicesSoftwareVersion(creatorVersion)) > 0;
                // If we are not at genesis and the software version is newer than the state version, then we need to
                // pick the active roster or candidate roster based on votes in the state
                if (isUpgrade) {
                    final var candidateRosterHash = requireNonNull(state.getReadableStates(RosterStateId.NAME)
                                    .<RosterState>getSingleton(RosterStateId.ROSTER_STATES_KEY)
                                    .get())
                            .candidateRosterHash();
                    final var tssStore = new ReadableStoreFactory(state).getStore(ReadableTssStore.class);
                    if (hasEnoughWeight(currentActiveRoster, candidateRosterHash, tssStore)) {
                        final ReadableKVState<ProtoBytes, Roster> rosters = requireNonNull(
                                state.getReadableStates(RosterStateId.NAME).get(RosterStateId.ROSTER_KEY));
                        return requireNonNull(rosters.get(new ProtoBytes(candidateRosterHash)));
                    }
                }
            }
        }
        return currentActiveRoster;
    }

    private static boolean hasEnoughWeight(
            @NonNull Roster activeRoster, @NonNull Bytes rosterHash, @NonNull ReadableTssStore tssBaseStore) {
        // Also get the total active roster weight
        long activeRosterTotalWeight = 0;
        final var voteWeightMap = new LinkedHashMap<Bytes, Long>();
        for (final RosterEntry rosterEntry : activeRoster.rosterEntries()) {
            activeRosterTotalWeight += rosterEntry.weight();
            final var tssVoteMapKey = new TssVoteMapKey(rosterHash, rosterEntry.nodeId());
            if (tssBaseStore.exists(tssVoteMapKey)) {
                final TssVoteTransactionBody voteBody = tssBaseStore.getVote(tssVoteMapKey);
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
}
