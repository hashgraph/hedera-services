/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.history.schemas.V059HistorySchema.ACTIVE_PROOF_CONSTRUCTION_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.NEXT_PROOF_CONSTRUCTION_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.PROOF_KEY_SETS_KEY;
import static com.hedera.node.app.history.schemas.V059HistorySchema.PROOF_VOTES_KEY;
import static com.hedera.node.app.roster.ActiveRosters.Phase.BOOTSTRAP;
import static com.hedera.node.app.roster.ActiveRosters.Phase.HANDOFF;
import static com.hedera.node.app.roster.ActiveRosters.Phase.TRANSITION;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.history.ConstructionNodeId;
import com.hedera.hapi.node.state.history.History;
import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofConstruction;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.history.HistorySignature;
import com.hedera.hapi.node.state.history.ProofKey;
import com.hedera.hapi.node.state.history.ProofKeySet;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.platform.state.NodeId;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.HistoryService;
import com.hedera.node.app.history.schemas.V059HistorySchema;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.hedera.node.config.data.TssConfig;
import com.hedera.node.config.data.VersionConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.info.NetworkInfo;
import com.swirlds.state.spi.CommittableWritableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableHistoryStoreImplTest {
    private static final HistoryProofVote DEFAULT_VOTE =
            HistoryProofVote.newBuilder().proof(HistoryProof.DEFAULT).build();
    private static final Metrics NO_OP_METRICS = new NoOpMetrics();
    private static final Bytes LEDGER_ID = Bytes.wrap("123");
    private static final Roster A_ROSTER = new Roster(List.of(RosterEntry.DEFAULT));
    private static final Bytes A_ROSTER_HASH = Bytes.wrap("A");
    private static final Bytes B_ROSTER_HASH = Bytes.wrap("B");
    private static final Roster C_ROSTER = new Roster(List.of(
            RosterEntry.newBuilder().nodeId(1L).build(),
            RosterEntry.newBuilder().nodeId(2L).build()));
    private static final Bytes C_ROSTER_HASH = Bytes.wrap("C");
    private static final TssConfig TSS_CONFIG = DEFAULT_CONFIG.getConfigData(TssConfig.class);
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final HistorySignature DEFAULT_SIGNATURE = HistorySignature.newBuilder()
            .history(History.DEFAULT)
            .signature(Bytes.wrap("X"))
            .build();

    @Mock
    private AppContext appContext;

    @Mock
    private ActiveRosters activeRosters;

    @Mock
    private HistoryLibrary library;

    @Mock
    private HistoryLibraryCodec codec;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private StartupNetworks startupNetworks;

    private State state;

    private WritableHistoryStoreImpl subject;

    @BeforeEach
    void setUp() {
        state = emptyState();
        subject = new WritableHistoryStoreImpl(state.getWritableStates(HistoryService.NAME));
    }

    @Test
    void ledgerIdIsNullUntilNonEmpty() {
        assertNull(subject.getLedgerId());

        subject.setLedgerId(LEDGER_ID);

        assertEquals(LEDGER_ID, subject.getLedgerId());
    }

    @Test
    void refusesToGetOrCreateForHandoff() {
        given(activeRosters.phase()).willReturn(HANDOFF);

        assertNull(subject.getConstructionFor(activeRosters));
        assertThrows(
                IllegalArgumentException.class,
                () -> subject.getOrCreateConstruction(activeRosters, CONSENSUS_NOW, TSS_CONFIG));
    }

    @Test
    void findsMatchingTransitionConstructionInActiveConstructionIfThere() {
        given(activeRosters.phase()).willReturn(TRANSITION);
        given(activeRosters.sourceRosterHash()).willReturn(A_ROSTER_HASH);
        given(activeRosters.targetRosterHash()).willReturn(B_ROSTER_HASH);
        final var active = HistoryProofConstruction.newBuilder()
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(B_ROSTER_HASH)
                .build();
        setConstructions(active, HistoryProofConstruction.DEFAULT);

        assertSame(active, subject.getConstructionFor(activeRosters));
        assertSame(active, subject.getOrCreateConstruction(activeRosters, CONSENSUS_NOW, TSS_CONFIG));
    }

    @Test
    void findsMatchingTransitionConstructionInNextConstructionIfThere() {
        given(activeRosters.phase()).willReturn(TRANSITION);
        given(activeRosters.sourceRosterHash()).willReturn(B_ROSTER_HASH);
        given(activeRosters.targetRosterHash()).willReturn(C_ROSTER_HASH);
        final var active = HistoryProofConstruction.newBuilder()
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(B_ROSTER_HASH)
                .build();
        final var next = HistoryProofConstruction.newBuilder()
                .sourceRosterHash(B_ROSTER_HASH)
                .targetRosterHash(C_ROSTER_HASH)
                .build();
        setConstructions(active, next);

        final var construction = subject.getConstructionFor(activeRosters);

        assertSame(next, construction);
    }

    @Test
    void createsBootstrapConstructionIfNotPresent() {
        givenARosterLookup();
        given(activeRosters.phase()).willReturn(BOOTSTRAP);
        given(activeRosters.sourceRosterHash()).willReturn(A_ROSTER_HASH);
        given(activeRosters.targetRosterHash()).willReturn(A_ROSTER_HASH);

        final var construction = subject.getOrCreateConstruction(activeRosters, CONSENSUS_NOW, TSS_CONFIG);

        assertEquals(1L, construction.constructionId());
        final var expectedGracePeriodEndTime =
                asTimestamp(CONSENSUS_NOW.plus(TSS_CONFIG.bootstrapProofKeyGracePeriod()));
        assertEquals(expectedGracePeriodEndTime, construction.gracePeriodEndTimeOrThrow());
        assertEquals(A_ROSTER_HASH, construction.sourceRosterHash());
        assertEquals(A_ROSTER_HASH, construction.targetRosterHash());

        assertSame(construction, getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY));
    }

    @Test
    void setsAsNextConstructionAndRotatesKeysDuringTransition() {
        givenCRosterLookup();
        given(activeRosters.phase()).willReturn(TRANSITION);
        given(activeRosters.sourceRosterHash()).willReturn(B_ROSTER_HASH);
        given(activeRosters.targetRosterHash()).willReturn(C_ROSTER_HASH);
        final var active = HistoryProofConstruction.newBuilder()
                .constructionId(2L)
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(B_ROSTER_HASH)
                .build();
        setConstructions(active, HistoryProofConstruction.DEFAULT);
        assertSame(active, subject.getActiveConstruction());
        final var key = Bytes.wrap("ONE");
        final var nextKey = Bytes.wrap("TWO");
        final long rotatingKeyNodeId = C_ROSTER.rosterEntries().getFirst().nodeId();
        subject.setProofKey(rotatingKeyNodeId, key, CONSENSUS_NOW.minusSeconds(1440));
        subject.setProofKey(rotatingKeyNodeId, nextKey, CONSENSUS_NOW.minusSeconds(1439));
        final long newKeyNodeId = C_ROSTER.rosterEntries().getLast().nodeId();
        final var newKey = Bytes.wrap("THREE");
        assertTrue(subject.setProofKey(newKeyNodeId, newKey, CONSENSUS_NOW.minusSeconds(1L)));

        final var construction = subject.getOrCreateConstruction(activeRosters, CONSENSUS_NOW, TSS_CONFIG);

        assertEquals(3L, construction.constructionId());
        final var expectedGracePeriodEndTime =
                asTimestamp(CONSENSUS_NOW.plus(TSS_CONFIG.transitionProofKeyGracePeriod()));
        assertEquals(expectedGracePeriodEndTime, construction.gracePeriodEndTimeOrThrow());
        assertEquals(B_ROSTER_HASH, construction.sourceRosterHash());
        assertEquals(C_ROSTER_HASH, construction.targetRosterHash());

        assertSame(construction, getSingleton(NEXT_PROOF_CONSTRUCTION_KEY));

        final var updatedKeySet = state.getWritableStates(HistoryService.NAME)
                .<NodeId, ProofKeySet>get(V059HistorySchema.PROOF_KEY_SETS_KEY)
                .get(new NodeId(rotatingKeyNodeId));
        requireNonNull(updatedKeySet);
        assertEquals(nextKey, updatedKeySet.key());
        assertEquals(asTimestamp(CONSENSUS_NOW), updatedKeySet.adoptionTime());
        assertEquals(Bytes.EMPTY, updatedKeySet.nextKey());

        final var newKeySet = state.getWritableStates(HistoryService.NAME)
                .<NodeId, ProofKeySet>get(V059HistorySchema.PROOF_KEY_SETS_KEY)
                .get(new NodeId(newKeyNodeId));
        requireNonNull(newKeySet);
        assertEquals(newKey, newKeySet.key());
        assertEquals(asTimestamp(CONSENSUS_NOW.minusSeconds(1L)), newKeySet.adoptionTime());
    }

    @Test
    void canSetAssemblyStartTimeIfConstructionIdExists() {
        setConstructions(
                HistoryProofConstruction.newBuilder().constructionId(123L).build(),
                HistoryProofConstruction.newBuilder().constructionId(456L).build());

        assertThrows(IllegalArgumentException.class, () -> subject.setAssemblyTime(0L, CONSENSUS_NOW));
        subject.setAssemblyTime(123L, CONSENSUS_NOW);
        assertEquals(
                asTimestamp(CONSENSUS_NOW),
                this.<HistoryProofConstruction>getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY)
                        .assemblyStartTimeOrThrow());
        assertFalse(this.<HistoryProofConstruction>getSingleton(NEXT_PROOF_CONSTRUCTION_KEY)
                .hasAssemblyStartTime());

        subject.setAssemblyTime(123L, CONSENSUS_NOW);
        assertEquals(
                asTimestamp(CONSENSUS_NOW),
                this.<HistoryProofConstruction>getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY)
                        .assemblyStartTimeOrThrow());

        final var then = CONSENSUS_NOW.plusSeconds(1L);
        subject.setAssemblyTime(456L, then);
        assertEquals(
                asTimestamp(then),
                this.<HistoryProofConstruction>getSingleton(NEXT_PROOF_CONSTRUCTION_KEY)
                        .assemblyStartTimeOrThrow());
    }

    @Test
    void canSetTargetProof() {
        setConstructions(
                HistoryProofConstruction.newBuilder().constructionId(123L).build(),
                HistoryProofConstruction.newBuilder().constructionId(456L).build());

        final var bookHash = Bytes.wrap("DOODLE");
        final var proof = new HistoryProof(bookHash, List.of(ProofKey.DEFAULT), History.DEFAULT, Bytes.EMPTY);
        subject.completeProof(456L, proof);

        final var construction = this.<HistoryProofConstruction>getSingleton(NEXT_PROOF_CONSTRUCTION_KEY);
        assertEquals(bookHash, construction.targetProofOrThrow().sourceAddressBookHash());
    }

    @Test
    void purgingStateAfterExactlyHandoffIsFalseIfNothingHappened() {
        given(activeRosters.phase()).willReturn(TRANSITION);
        assertThrows(IllegalArgumentException.class, () -> subject.purgeStateAfterHandoff(activeRosters));
        given(activeRosters.phase()).willReturn(BOOTSTRAP);
        assertThrows(IllegalArgumentException.class, () -> subject.purgeStateAfterHandoff(activeRosters));
        given(activeRosters.phase()).willReturn(HANDOFF);
        given(activeRosters.currentRosterHash()).willReturn(Bytes.wrap("NA"));

        assertFalse(subject.purgeStateAfterHandoff(activeRosters));
    }

    @Test
    void purgingStateAfterHandoffHasTrueExpectedEffectIfSomethingHappened() {
        given(activeRosters.phase()).willReturn(HANDOFF);
        given(activeRosters.currentRosterHash()).willReturn(C_ROSTER_HASH);
        final SortedSet<Long> removedNodeIds = new TreeSet<>(List.of(0L));
        given(activeRosters.removedNodeIds()).willReturn(removedNodeIds);
        givenARosterLookup();
        final var activeConstruction = HistoryProofConstruction.newBuilder()
                .constructionId(123L)
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(A_ROSTER_HASH)
                .build();
        final var nextConstruction = HistoryProofConstruction.newBuilder()
                .constructionId(456L)
                .targetRosterHash(C_ROSTER_HASH)
                .build();
        setConstructions(activeConstruction, nextConstruction);
        A_ROSTER.rosterEntries().forEach(entry -> subject.addProofVote(entry.nodeId(), 123L, DEFAULT_VOTE));
        addSomeProofKeySetsFor(A_ROSTER);
        commit(states -> {
            states.<ConstructionNodeId, HistoryProofVote>get(PROOF_VOTES_KEY)
                    .put(new ConstructionNodeId(123L, 0L), DEFAULT_VOTE);
        });
        subject.addSignature(0L, 123L, DEFAULT_SIGNATURE, CONSENSUS_NOW);
        final var votesBefore = subject.getVotes(123L, Set.of(0L, 1L));
        assertEquals(1, votesBefore.size());
        assertEquals(DEFAULT_VOTE, votesBefore.get(0L));
        final var publicationsBefore = subject.getProofKeyPublications(Set.of(0L));
        assertEquals(1, publicationsBefore.size());
        final var signaturesBefore = subject.getSignaturePublications(123L, Set.of(0L));
        assertEquals(1, signaturesBefore.size());

        subject.purgeStateAfterHandoff(activeRosters);

        assertSame(nextConstruction, this.<HistoryProofConstruction>getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY));

        assertEquals(
                0L,
                state.getWritableStates(HistoryService.NAME)
                        .get(PROOF_VOTES_KEY)
                        .size());
        assertEquals(
                0L,
                state.getWritableStates(HistoryService.NAME)
                        .get(PROOF_KEY_SETS_KEY)
                        .size());
    }

    private void givenARosterLookup() {
        given(activeRosters.findRelatedRoster(A_ROSTER_HASH)).willReturn(A_ROSTER);
    }

    private void givenCRosterLookup() {
        given(activeRosters.findRelatedRoster(C_ROSTER_HASH)).willReturn(C_ROSTER);
    }

    private void addSomeProofKeySetsFor(@NonNull final Roster roster) {
        commit(states -> {
            final var keySets = states.<NodeId, ProofKeySet>get(V059HistorySchema.PROOF_KEY_SETS_KEY);
            roster.rosterEntries().forEach(entry -> {
                final var keySet = ProofKeySet.newBuilder()
                        .key(Bytes.wrap("KEY" + entry.nodeId()))
                        .adoptionTime(asTimestamp(CONSENSUS_NOW.minusSeconds(entry.nodeId())))
                        .build();
                keySets.put(new NodeId(entry.nodeId()), keySet);
            });
        });
    }

    @SuppressWarnings("unchecked")
    private <T extends Record> @NonNull T getSingleton(@NonNull final String key) {
        return requireNonNull((T)
                state.getWritableStates(HistoryService.NAME).getSingleton(key).get());
    }

    private void setConstructions(
            @NonNull final HistoryProofConstruction active, @NonNull final HistoryProofConstruction next) {
        commit(states -> {
            states.getSingleton(ACTIVE_PROOF_CONSTRUCTION_KEY).put(active);
            states.getSingleton(NEXT_PROOF_CONSTRUCTION_KEY).put(next);
        });
    }

    private void commit(@NonNull final Consumer<WritableStates> mutation) {
        final var writableStates = state.getWritableStates(HistoryService.NAME);
        mutation.accept(writableStates);
        ((CommittableWritableStates) writableStates).commit();
    }

    private State emptyState() {
        final var state = new FakeState();
        final var servicesRegistry = new FakeServicesRegistry();
        Set.of(
                        new EntityIdService(),
                        new HistoryServiceImpl(NO_OP_METRICS, ForkJoinPool.commonPool(), appContext, library, codec))
                .forEach(servicesRegistry::register);
        final var migrator = new FakeServiceMigrator();
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        migrator.doMigrations(
                state,
                servicesRegistry,
                null,
                new ServicesSoftwareVersion(
                        bootstrapConfig.getConfigData(VersionConfig.class).servicesVersion()),
                new ConfigProviderImpl().getConfiguration(),
                DEFAULT_CONFIG,
                networkInfo,
                NO_OP_METRICS,
                startupNetworks);
        return state;
    }
}
