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

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.roster.ActiveRosters.Phase.HANDOFF;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.state.history.HistoryProof;
import com.hedera.hapi.node.state.history.HistoryProofVote;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fixtures.state.FakeServiceMigrator;
import com.hedera.node.app.fixtures.state.FakeServicesRegistry;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.history.HistoryLibrary;
import com.hedera.node.app.history.HistoryService;
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
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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

    private <T extends Record> void commitSingleton(@NonNull final String key, @NonNull final T value) {
        commit(writableStates -> {
            final WritableSingletonState<T> state = writableStates.getSingleton(key);
            state.put(value);
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
