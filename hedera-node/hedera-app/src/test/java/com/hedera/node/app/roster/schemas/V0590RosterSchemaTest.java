/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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
import static com.hedera.node.app.roster.schemas.V0590RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0590RosterSchema.ROSTER_STATES_KEY;
import static com.swirlds.platform.roster.RosterRetriever.buildRoster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.swirlds.common.RosterStateId;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.PbjConverter;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.state.service.schemas.V0590PlatformStateSchema;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.State;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.ReadableSingletonState;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link V0590RosterSchema}.
 */
@ExtendWith(MockitoExtension.class)
class V0590RosterSchemaTest {
    private static final long ROUND_NO = 666L;
    private static final Network NETWORK = Network.newBuilder()
            .nodeMetadata(NodeMetadata.newBuilder()
                    .rosterEntry(RosterEntry.newBuilder().nodeId(2L).build())
                    .build())
            .build();
    private static final Roster ROSTER = new Roster(NETWORK.nodeMetadata().stream()
            .map(NodeMetadata::rosterEntryOrThrow)
            .toList());
    private static final AddressBook ADDRESS_BOOK = RosterUtils.buildAddressBook(ROSTER);

    private static final Network PREVIOUS_NETWORK = Network.newBuilder()
            .nodeMetadata(NodeMetadata.newBuilder()
                    .rosterEntry(RosterEntry.newBuilder().nodeId(1L).build())
                    .build())
            .build();
    private static final Roster PREVIOUS_ROSTER = new Roster(PREVIOUS_NETWORK.nodeMetadata().stream()
            .map(NodeMetadata::rosterEntryOrThrow)
            .toList());
    private static final AddressBook PREVIOUS_ADDRESS_BOOK = RosterUtils.buildAddressBook(PREVIOUS_ROSTER);

    private static final Network DISK_NETWORK = Network.newBuilder()
            .nodeMetadata(NodeMetadata.newBuilder()
                    .rosterEntry(RosterEntry.newBuilder().nodeId(3L).build())
                    .build())
            .build();
    private static final Roster DISK_ROSTER = new Roster(DISK_NETWORK.nodeMetadata().stream()
            .map(NodeMetadata::rosterEntryOrThrow)
            .toList());
    private static final AddressBook DISK_ADDRESS_BOOK = RosterUtils.buildAddressBook(DISK_ROSTER);

    @Mock
    private MigrationContext ctx;

    @Mock
    private ReadableStates readableStates;

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
    private State state;

    private State getState() {
        return state;
    }

    @Mock
    private ReadablePlatformStateStore platformStateStore;

    @Mock
    private ReadableSingletonState<PlatformState> platformStateSingleton;

    @Mock
    private PlatformState platformState;

    @Mock
    private WritableSingletonState<RosterState> rosterState;

    private V0590RosterSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0590RosterSchema(canAdopt, rosterStoreFactory, this::getState, () -> DISK_ADDRESS_BOOK);
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
    void migratesSingleCurrentPlatformStateAddressBookToRosterState() {
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        setupPlatformState(ADDRESS_BOOK, null);
        setupEmptyReadableRosterStates();

        subject.migrate(ctx);

        verify(rosterStore, times(1)).putActiveRoster(ROSTER, ROUND_NO);
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void migratesNoPlatformStateAddressBooksToRosterStateBecauseNonePresent() {
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        setupPlatformState(null, null);
        setupEmptyReadableRosterStates();

        subject.migrate(ctx);

        verifyNoInteractions(rosterStore);
    }

    @Test
    void migratesAllPlatformStateAddressBooksToRosterState() {
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<RosterState>getSingleton(ROSTER_STATES_KEY)).willReturn(rosterState);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        setupPlatformState(ADDRESS_BOOK, PREVIOUS_ADDRESS_BOOK);
        setupEmptyReadableRosterStates();

        subject.migrate(ctx);

        verify(rosterStore, times(1)).putActiveRoster(PREVIOUS_ROSTER, 0L);
        verify(rosterStore, times(1)).putActiveRoster(ROSTER, ROUND_NO);
        verifyNoMoreInteractions(rosterStore);
    }

    private void setupPlatformState(
            @Nullable final AddressBook addressBook, @Nullable final AddressBook previousAddressBook) {
        doReturn(readableStates).when(state).getReadableStates(PlatformStateService.NAME);
        doReturn(platformStateSingleton).when(readableStates).getSingleton(V0590PlatformStateSchema.PLATFORM_STATE_KEY);
        doReturn(platformState).when(platformStateSingleton).get();
        doReturn(addressBook == null ? null : PbjConverter.toPbjAddressBook(addressBook))
                .when(platformState)
                .addressBook();
        lenient()
                .doReturn(previousAddressBook == null ? null : PbjConverter.toPbjAddressBook(previousAddressBook))
                .when(platformState)
                .previousAddressBook();
    }

    private void setupEmptyReadableRosterStates() {
        final ReadableStates rosterReadableStates = mock(ReadableStates.class);
        doReturn(rosterReadableStates).when(state).getReadableStates(RosterStateId.NAME);
        final ReadableSingletonState<RosterState> rosterStateSingleton = mock(ReadableSingletonState.class);
        doReturn(rosterStateSingleton).when(rosterReadableStates).getSingleton(RosterStateId.ROSTER_STATES_KEY);
        final RosterState rosterState = mock(RosterState.class);
        doReturn(rosterState).when(rosterStateSingleton).get();
        doReturn(List.of()).when(rosterState).roundRosterPairs();
    }

    @Test
    void usesGenesisRosterIfLifecycleEnabledAndApropros() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(ctx.isGenesis()).willReturn(true);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(ctx.platformConfig()).willReturn(DEFAULT_CONFIG);
        given(startupNetworks.genesisNetworkOrThrow(DEFAULT_CONFIG)).willReturn(NETWORK);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.restart(ctx);

        verify(rosterStore).putActiveRoster(ROSTER, 0L);
    }

    @Test
    void usesAdaptedAddressBookAndMigrationRosterIfLifecycleEnabledIfApropos() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(startupNetworks.migrationNetworkOrThrow()).willReturn(NETWORK);

        setupPlatformState(ADDRESS_BOOK, null);
        setupEmptyReadableRosterStates();

        // This is the rosterStore for when the code updates it and writes to it upon the migration:
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.restart(ctx);

        verify(rosterStore).putActiveRoster(buildRoster(ADDRESS_BOOK), 0L);
        verify(rosterStore).putActiveRoster(ROSTER, ROUND_NO + 1L);
    }

    @Test
    void noOpIfNotUpgradeAndActiveRosterPresent() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);

        subject.restart(ctx);

        verify(rosterStore).getActiveRoster();
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void doesNotAdoptNullCandidateRoster() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(ctx.isUpgrade(any(), any())).willReturn(true);

        subject.restart(ctx);

        verify(rosterStore).getActiveRoster();
        verify(rosterStore).getCandidateRoster();
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void doesNotAdoptCandidateRosterIfNotSpecified() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(ctx.isUpgrade(any(), any())).willReturn(true);
        given(rosterStore.getCandidateRoster()).willReturn(ROSTER);
        given(canAdopt.test(ROSTER)).willReturn(false);

        subject.restart(ctx);

        verify(rosterStore).getActiveRoster();
        verify(rosterStore).getCandidateRoster();
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void adoptsCandidateRosterIfTestPasses() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.newStates()).willReturn(writableStates);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(ctx.isUpgrade(any(), any())).willReturn(true);
        given(rosterStore.getCandidateRoster()).willReturn(ROSTER);
        given(canAdopt.test(ROSTER)).willReturn(true);
        given(ctx.roundNumber()).willReturn(ROUND_NO);

        subject.restart(ctx);

        verify(rosterStore).getActiveRoster();
        verify(rosterStore).getCandidateRoster();
        verify(rosterStore).adoptCandidateRoster(ROUND_NO + 1L);
    }

    @Test
    void restartInstallsDiskAddressBookOnGenesisIfNotUsingLifecycle() {
        given(ctx.appConfig()).willReturn(DEFAULT_CONFIG);
        given(ctx.isGenesis()).willReturn(true);
        given(ctx.newStates()).willReturn(writableStates);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.restart(ctx);

        verify(rosterStore).putActiveRoster(DISK_ROSTER, 0L);
        verifyNoMoreInteractions(rosterStore);
    }

    @Test
    void restartIsNoOpWhenAddressBookDoesNotChangeIfNotUsingLifecycle() {
        given(ctx.appConfig()).willReturn(DEFAULT_CONFIG);
        given(ctx.newStates()).willReturn(writableStates);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.restart(ctx);

        verifyNoInteractions(rosterStore);
    }

    @Test
    void restartInstallsDiskAddressBookOnUpgradeIfNotUsingLifecycle() {
        given(ctx.appConfig()).willReturn(DEFAULT_CONFIG);
        given(ctx.isUpgrade(any(), any())).willReturn(true);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.newStates()).willReturn(writableStates);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);

        subject.restart(ctx);

        verify(rosterStore).putActiveRoster(DISK_ROSTER, ROUND_NO + 1L);
        verifyNoMoreInteractions(rosterStore);
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

    @Test
    void restartSetsActiveRosterFromOverrideWithPreservedWeightsIfPresent() {
        given(ctx.appConfig()).willReturn(WITH_ROSTER_LIFECYCLE);
        given(ctx.startupNetworks()).willReturn(startupNetworks);
        given(ctx.roundNumber()).willReturn(ROUND_NO);
        given(ctx.newStates()).willReturn(writableStates);
        given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        given(rosterStore.getActiveRoster())
                .willReturn(new Roster(
                        List.of(RosterEntry.newBuilder().nodeId(2L).weight(42L).build())));
        given(startupNetworks.overrideNetworkFor(ROUND_NO)).willReturn(Optional.of(NETWORK));
        final var adaptedRoster = new Roster(
                List.of(RosterEntry.newBuilder().nodeId(2L).weight(42L).build()));

        subject.restart(ctx);

        verify(rosterStore).putActiveRoster(adaptedRoster, ROUND_NO + 1L);
        verify(startupNetworks).setOverrideRound(ROUND_NO);
    }
}
