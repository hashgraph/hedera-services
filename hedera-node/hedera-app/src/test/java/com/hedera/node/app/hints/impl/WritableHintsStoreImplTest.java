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

package com.hedera.node.app.hints.impl;

import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.hints.HintsService.partySizeForRoster;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.ACTIVE_CONSTRUCTION_KEY;
import static com.hedera.node.app.hints.schemas.V059HintsSchema.NEXT_CONSTRUCTION_KEY;
import static com.hedera.node.app.roster.ActiveRosters.Phase.BOOTSTRAP;
import static com.hedera.node.app.roster.ActiveRosters.Phase.HANDOFF;
import static com.hedera.node.app.roster.ActiveRosters.Phase.TRANSITION;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.hints.HintsConstruction;
import com.hedera.hapi.node.state.hints.HintsKeySet;
import com.hedera.hapi.node.state.hints.HintsPartyId;
import com.hedera.hapi.node.state.hints.NodePartyId;
import com.hedera.hapi.node.state.hints.PreprocessedKeys;
import com.hedera.hapi.node.state.hints.PreprocessingVote;
import com.hedera.hapi.node.state.hints.PreprocessingVoteId;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.hints.HintsLibrary;
import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.schemas.V059HintsSchema;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.metrics.StoreMetricsServiceImpl;
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
import com.swirlds.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WritableHintsStoreImplTest {
    private static final Metrics NO_OP_METRICS = new NoOpMetrics();
    private static final PreprocessingVote DEFAULT_VOTE = PreprocessingVote.newBuilder()
            .preprocessedKeys(PreprocessedKeys.DEFAULT)
            .build();
    private static final Roster A_ROSTER = new Roster(List.of(RosterEntry.DEFAULT));
    private static final Bytes A_ROSTER_HASH = Bytes.wrap("A");
    private static final Bytes B_ROSTER_HASH = Bytes.wrap("B");
    private static final Roster C_ROSTER = new Roster(List.of(
            RosterEntry.newBuilder().nodeId(1L).build(),
            RosterEntry.newBuilder().nodeId(2L).build()));
    private static final Bytes C_ROSTER_HASH = Bytes.wrap("C");
    private static final TssConfig TSS_CONFIG = DEFAULT_CONFIG.getConfigData(TssConfig.class);
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private AppContext appContext;

    @Mock
    private ActiveRosters activeRosters;

    @Mock
    private HintsLibrary library;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private ConfigProviderImpl configProvider;

    @Mock
    private StoreMetricsServiceImpl storeMetricsService;

    private State state;

    private WritableHintsStoreImpl subject;

    @BeforeEach
    void setUp() {
        state = emptyState();
        subject = new WritableHintsStoreImpl(state.getWritableStates(HintsService.NAME));
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
        final var active = HintsConstruction.newBuilder()
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(B_ROSTER_HASH)
                .build();
        setConstructions(active, HintsConstruction.DEFAULT);

        assertSame(active, subject.getConstructionFor(activeRosters));
        assertSame(active, subject.getOrCreateConstruction(activeRosters, CONSENSUS_NOW, TSS_CONFIG));
    }

    @Test
    void findsMatchingTransitionConstructionInNextConstructionIfThere() {
        given(activeRosters.phase()).willReturn(TRANSITION);
        given(activeRosters.sourceRosterHash()).willReturn(B_ROSTER_HASH);
        given(activeRosters.targetRosterHash()).willReturn(C_ROSTER_HASH);
        final var active = HintsConstruction.newBuilder()
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(B_ROSTER_HASH)
                .build();
        final var next = HintsConstruction.newBuilder()
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
                asTimestamp(CONSENSUS_NOW.plus(TSS_CONFIG.bootstrapHintsKeyGracePeriod()));
        assertEquals(expectedGracePeriodEndTime, construction.gracePeriodEndTimeOrThrow());
        assertEquals(A_ROSTER_HASH, construction.sourceRosterHash());
        assertEquals(A_ROSTER_HASH, construction.targetRosterHash());

        final var activeConstruction = state.getWritableStates(HintsService.NAME)
                .<HintsConstruction>getSingleton(ACTIVE_CONSTRUCTION_KEY)
                .get();
        requireNonNull(activeConstruction);
        assertSame(construction, activeConstruction);
    }

    @Test
    void setsAsNextConstructionAndRotatesKeysDuringTransition() {
        givenCRosterLookup();
        given(activeRosters.phase()).willReturn(TRANSITION);
        given(activeRosters.sourceRosterHash()).willReturn(B_ROSTER_HASH);
        given(activeRosters.targetRosterHash()).willReturn(C_ROSTER_HASH);
        final var active = HintsConstruction.newBuilder()
                .constructionId(2L)
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(B_ROSTER_HASH)
                .build();
        setConstructions(active, HintsConstruction.DEFAULT);
        assertSame(active, subject.getActiveConstruction());
        final var key = Bytes.wrap("ONE");
        final var nextKey = Bytes.wrap("TWO");
        final long rotatingKeyNodeId = 666L;
        final int numParties = HintsService.partySizeForRoster(C_ROSTER);
        subject.setHintsKey(rotatingKeyNodeId, 0, numParties, key, CONSENSUS_NOW.minusSeconds(1440));
        subject.setHintsKey(rotatingKeyNodeId, 0, numParties, nextKey, CONSENSUS_NOW.minusSeconds(1439));
        final long newKeyNodeId = 42L;
        final var newKey = Bytes.wrap("THREE");
        assertTrue(subject.setHintsKey(newKeyNodeId, 1, numParties, newKey, CONSENSUS_NOW.minusSeconds(1L)));

        final var construction = subject.getOrCreateConstruction(activeRosters, CONSENSUS_NOW, TSS_CONFIG);

        assertEquals(3L, construction.constructionId());
        final var expectedGracePeriodEndTime =
                asTimestamp(CONSENSUS_NOW.plus(TSS_CONFIG.transitionHintsKeyGracePeriod()));
        assertEquals(expectedGracePeriodEndTime, construction.gracePeriodEndTimeOrThrow());
        assertEquals(B_ROSTER_HASH, construction.sourceRosterHash());
        assertEquals(C_ROSTER_HASH, construction.targetRosterHash());

        final var nextConstruction = state.getWritableStates(HintsService.NAME)
                .<HintsConstruction>getSingleton(NEXT_CONSTRUCTION_KEY)
                .get();
        requireNonNull(nextConstruction);
        assertSame(construction, nextConstruction);

        final var rotatedPartyId = new HintsPartyId(0, numParties);
        final var updatedKeySet = state.getWritableStates(HintsService.NAME)
                .<HintsPartyId, HintsKeySet>get(V059HintsSchema.HINTS_KEY_SETS_KEY)
                .get(rotatedPartyId);
        requireNonNull(updatedKeySet);
        assertEquals(666L, updatedKeySet.nodeId());
        assertEquals(nextKey, updatedKeySet.key());
        assertEquals(asTimestamp(CONSENSUS_NOW), updatedKeySet.adoptionTime());
        assertEquals(0, updatedKeySet.nextKey().length());

        final var newPartyId = new HintsPartyId(1, numParties);
        final var newKeySet = state.getWritableStates(HintsService.NAME)
                .<HintsPartyId, HintsKeySet>get(V059HintsSchema.HINTS_KEY_SETS_KEY)
                .get(newPartyId);
        requireNonNull(newKeySet);
        assertEquals(newKeyNodeId, newKeySet.nodeId());
        assertEquals(newKey, newKeySet.key());
        assertEquals(asTimestamp(CONSENSUS_NOW.minusSeconds(1L)), newKeySet.adoptionTime());
    }

    @Test
    void canSetPreprocessingStartTimeIfConstructionIdExists() {
        setConstructions(
                HintsConstruction.newBuilder().constructionId(123L).build(),
                HintsConstruction.newBuilder().constructionId(456L).build());

        assertThrows(IllegalArgumentException.class, () -> subject.setPreprocessingStartTime(0L, CONSENSUS_NOW));
        subject.setPreprocessingStartTime(123L, CONSENSUS_NOW);
        assertEquals(
                asTimestamp(CONSENSUS_NOW),
                constructionNow(ACTIVE_CONSTRUCTION_KEY).preprocessingStartTimeOrThrow());
        assertFalse(constructionNow(NEXT_CONSTRUCTION_KEY).hasPreprocessingStartTime());

        subject.setPreprocessingStartTime(123L, CONSENSUS_NOW);
        assertEquals(
                asTimestamp(CONSENSUS_NOW),
                constructionNow(ACTIVE_CONSTRUCTION_KEY).preprocessingStartTimeOrThrow());

        final var then = CONSENSUS_NOW.plusSeconds(1L);
        subject.setPreprocessingStartTime(456L, then);
        assertEquals(asTimestamp(then), constructionNow(NEXT_CONSTRUCTION_KEY).preprocessingStartTimeOrThrow());
    }

    @Test
    void canSetHintsScheme() {
        setConstructions(
                HintsConstruction.newBuilder().constructionId(123L).build(),
                HintsConstruction.newBuilder().constructionId(456L).build());
        final var verificationKey = Bytes.wrap("VK");
        final var keys = new PreprocessedKeys(Bytes.EMPTY, verificationKey);
        final var nodePartyIds = Map.of(1L, 2, 3L, 6);
        assertNull(subject.getActiveVerificationKey());

        subject.setHintsScheme(456L, keys, nodePartyIds);

        final var construction = constructionNow(NEXT_CONSTRUCTION_KEY);
        assertEquals(keys, construction.hintsSchemeOrThrow().preprocessedKeysOrThrow());
        assertEquals(
                List.of(new NodePartyId(1L, 2), new NodePartyId(3L, 6)),
                construction.hintsSchemeOrThrow().nodePartyIds());
        assertNull(subject.getActiveVerificationKey());

        subject.setHintsScheme(123L, keys, nodePartyIds);
        assertEquals(verificationKey, subject.getActiveVerificationKey());
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
        given(activeRosters.currentRoster()).willReturn(C_ROSTER);
        given(activeRosters.currentRosterHash()).willReturn(C_ROSTER_HASH);
        givenARosterLookup();
        final var activeConstruction = HintsConstruction.newBuilder()
                .constructionId(123L)
                .sourceRosterHash(A_ROSTER_HASH)
                .targetRosterHash(A_ROSTER_HASH)
                .build();
        final var nextConstruction = HintsConstruction.newBuilder()
                .constructionId(456L)
                .targetRosterHash(C_ROSTER_HASH)
                .build();
        setConstructions(activeConstruction, nextConstruction);
        addSomeVotesFor(123L, A_ROSTER);
        addSomeHintsKeySetsFor(A_ROSTER);
        final var votesBefore = subject.getVotes(123L, Set.of(0L, 1L));
        assertEquals(1, votesBefore.size());
        assertEquals(DEFAULT_VOTE, votesBefore.get(0L));
        final var publicationsBefore = subject.getHintsKeyPublications(Set.of(0L), partySizeForRoster(A_ROSTER));
        assertEquals(1, publicationsBefore.size());

        subject.purgeStateAfterHandoff(activeRosters);

        assertSame(nextConstruction, constructionNow(ACTIVE_CONSTRUCTION_KEY));

        assertEquals(0L, votesNow().size());
        assertEquals(0L, keySetsNow().size());
    }

    private ReadableKVState<PreprocessingVoteId, PreprocessingVote> votesNow() {
        return state.getWritableStates(HintsService.NAME).get(V059HintsSchema.PREPROCESSING_VOTES_KEY);
    }

    private ReadableKVState<HintsPartyId, HintsKeySet> keySetsNow() {
        return state.getWritableStates(HintsService.NAME).get(V059HintsSchema.HINTS_KEY_SETS_KEY);
    }

    private HintsConstruction constructionNow(@NonNull final String key) {
        final var construction = state.getWritableStates(HintsService.NAME)
                .<HintsConstruction>getSingleton(key)
                .get();
        return requireNonNull(construction);
    }

    private void setConstructions(@NonNull final HintsConstruction active, @NonNull final HintsConstruction next) {
        final var writableStates = state.getWritableStates(HintsService.NAME);
        state.getWritableStates(HintsService.NAME)
                .<HintsConstruction>getSingleton(ACTIVE_CONSTRUCTION_KEY)
                .put(active);
        state.getWritableStates(HintsService.NAME)
                .<HintsConstruction>getSingleton(V059HintsSchema.NEXT_CONSTRUCTION_KEY)
                .put(next);
        ((CommittableWritableStates) writableStates).commit();
    }

    private void addSomeVotesFor(final long constructionId, @NonNull final Roster roster) {
        roster.rosterEntries()
                .forEach(entry -> subject.addPreprocessingVote(entry.nodeId(), constructionId, DEFAULT_VOTE));
    }

    private void addSomeHintsKeySetsFor(@NonNull final Roster roster) {
        final var writableStates = state.getWritableStates(HintsService.NAME);
        final var keySets = state.getWritableStates(HintsService.NAME)
                .<HintsPartyId, HintsKeySet>get(V059HintsSchema.HINTS_KEY_SETS_KEY);
        final int numParties = partySizeForRoster(roster);
        for (int i = 0; i < numParties; i++) {
            final var partyId = new HintsPartyId(i, numParties);
            final var keySet = HintsKeySet.newBuilder()
                    .nodeId(i)
                    .key(Bytes.wrap("KEY" + i))
                    .adoptionTime(asTimestamp(CONSENSUS_NOW.minusSeconds(i)))
                    .build();
            keySets.put(partyId, keySet);
        }
        ((CommittableWritableStates) writableStates).commit();
    }

    private void givenARosterLookup() {
        given(activeRosters.findRelatedRoster(A_ROSTER_HASH)).willReturn(A_ROSTER);
    }

    private void givenCRosterLookup() {
        given(activeRosters.findRelatedRoster(C_ROSTER_HASH)).willReturn(C_ROSTER);
    }

    private State emptyState() {
        final var state = new FakeState();
        final var servicesRegistry = new FakeServicesRegistry();
        Set.of(
                        new EntityIdService(),
                        new HintsServiceImpl(NO_OP_METRICS, ForkJoinPool.commonPool(), appContext, library))
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
                startupNetworks,
                storeMetricsService,
                configProvider);
        return state;
    }
}
