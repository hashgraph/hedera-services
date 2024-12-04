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

package com.hedera.node.app.roster.schemas;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.fixtures.AppTestBase.WITH_ROSTER_LIFECYCLE;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link V0540RosterSchema}.
 */
@ExtendWith(MockitoExtension.class)
class V0540RosterSchemaTest {
    private static final long ROUND_NO = 666L;
    private static final Network NETWORK = Network.newBuilder()
            .nodeMetadata(NodeMetadata.newBuilder()
                    .rosterEntry(RosterEntry.newBuilder().nodeId(1L).build())
                    .build())
            .build();
    private static final Roster ROSTER = new Roster(NETWORK.nodeMetadata().stream()
            .map(NodeMetadata::rosterEntryOrThrow)
            .toList());
    private static final AddressBook ADDRESS_BOOK = new AddressBook(List.of());

    @Mock
    private MigrationContext ctx;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableRosterStore rosterStore;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private Function<WritableStates, WritableRosterStore> rosterStoreFactory;

    @Mock
    private Predicate<Roster> canAdopt;

    @Mock
    private Supplier<ReadablePlatformStateStore> platformStateStoreFactory;

    @Mock
    private ReadablePlatformStateStore platformStateStore;

    @Mock
    private WritableSingletonState<RosterState> rosterState;

    private V0540RosterSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0540RosterSchema(canAdopt, rosterStoreFactory, platformStateStoreFactory);
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate).hasSize(2);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        assertEquals(ROSTER_KEY, iter.next(), "Unexpected Roster key!");
        assertEquals(ROSTER_STATES_KEY, iter.next(), "Unexpected RosterState key!");
    }

    @Test
    void usesDefaultRosterStateIfLifecycleNotEnabled() {
        given(ctx.appConfig()).willReturn(DEFAULT_CONFIG);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);

        subject.migrate(ctx);

        verify(rosterState, times(1)).put(RosterState.DEFAULT);
    }

    @Test
    void usesGenesisRosterIfLifecycleEnabledAndApropros() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(ctx.isGenesis()).willReturn(true);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(startupNetworks.genesisNetworkOrThrow()).willReturn(NETWORK);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.migrate(ctx);

        verify(rosterStore).putActiveRoster(ROSTER, 0L);
    }

    @Test
    void usesAdaptedAddressBookAndMigrationRosterIfLifecycleEnabledIfApropos() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(startupNetworks.migrationNetworkOrThrow()).willReturn(NETWORK);
        given(platformStateStoreFactory.get()).willReturn(platformStateStore);
        given(platformStateStore.getAddressBook()).willReturn(ADDRESS_BOOK);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.migrate(ctx);

        verify(rosterStore).putActiveRoster(buildRoster(ADDRESS_BOOK), 0L);
        verify(rosterStore).putActiveRoster(ROSTER, ROUND_NO + 1L);
    }

    @Test
    void noOpIfNotUpgradeAndActiveRosterPresent() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);

        subject.migrate(ctx);

        verify(rosterStore).getActiveRoster();
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void doesNotAdoptNullCandidateRoster() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(ctx.isUpgrade(any(), any())).willReturn(true);

        subject.migrate(ctx);

        verify(rosterStore).getActiveRoster();
        verify(rosterStore).getCandidateRoster();
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void doesNotAdoptCandidateRosterIfNotSpecified() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(ctx.isUpgrade(any(), any())).willReturn(true);
        given(rosterStore.getCandidateRoster()).willReturn(ROSTER);
        given(canAdopt.test(ROSTER)).willReturn(false);

        subject.migrate(ctx);

        verify(rosterStore).getActiveRoster();
        verify(rosterStore).getCandidateRoster();
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void adoptsCandidateRosterIfTestPasses() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(ctx.isUpgrade(any(), any())).willReturn(true);
        given(rosterStore.getCandidateRoster()).willReturn(ROSTER);
        given(canAdopt.test(ROSTER)).willReturn(true);
        given(ctx.roundNumber()).willReturn(ROUND_NO);

        subject.migrate(ctx);

        verify(rosterStore).getActiveRoster();
        verify(rosterStore).getCandidateRoster();
        verify(rosterStore).adoptCandidateRoster(ROUND_NO + 1L);
    }

    @Test
    void restartIsNoOpIfNotUsingLifecycle() {
        given(ctx.appConfig()).willReturn(DEFAULT_CONFIG);

        subject.restart(ctx);

        verifyNoMoreInteractions(ctx);
    }

    @Test
    void restartIsNoOpIfNoOverridePresent() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.startupNetworks()).willReturn(startupNetworks);

        subject.restart(ctx);

        verifyNoInteractions(rosterStoreFactory);
    }

    @Test
    void restartSetsActiveRosterFromOverrideIfPresent() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.newStates()).willReturn(writableStates);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(startupNetworks.overrideNetworkFor(ROUND_NO)).willReturn(Optional.of(NETWORK));

        subject.restart(ctx);

        verify(rosterStore).putActiveRoster(ROSTER, ROUND_NO + 1L);
        verify(startupNetworks).setOverrideRound(ROUND_NO);
    }
}
