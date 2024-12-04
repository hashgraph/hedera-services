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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterEntry;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.internal.network.Network;
import com.hedera.node.internal.network.NodeMetadata;
import com.swirlds.platform.roster.RosterUtils;
import com.swirlds.platform.state.service.ReadablePlatformStateStore;
import com.swirlds.platform.state.service.WritableRosterStore;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.StartupNetworks;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V057RosterSchemaTest {
    private static final long ROUND_NO = 666L;
    private static final SemanticVersion THEN =
            SemanticVersion.newBuilder().minor(7).build();
    private static final Network NETWORK = Network.newBuilder()
            .nodeMetadata(NodeMetadata.newBuilder()
                    .rosterEntry(RosterEntry.newBuilder().nodeId(1L).build())
                    .build())
            .build();
    private static final Roster ROSTER = RosterUtils.fromNetwork(NETWORK);
    private static final AddressBook ADDRESS_BOOK = new AddressBook(List.of());

    @Mock
    private Predicate<Roster> canAdopt;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritableRosterStore rosterStore;

    @Mock
    private MigrationContext context;

    @Mock
    private StartupNetworks startupNetworks;

    @Mock
    private Function<WritableStates, WritableRosterStore> rosterStoreFactory;

    @Mock
    private Function<WritableStates, ReadablePlatformStateStore> platformStateStoreFactory;

    @Mock
    private ReadablePlatformStateStore platformStateStore;

    private V057RosterSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V057RosterSchema(canAdopt, rosterStoreFactory, platformStateStoreFactory);
    }

    @Test
    void noOpIfNotUsingRosterLifecycle() {
        givenContextWith(CurrentVersion.NA, RosterLifecycle.OFF, AvailableNetwork.NONE);

        subject.restart(context);

        verify(context, never()).newStates();
    }

    @Test
    void setsActiveFromStartupNetworksAtGenesis() {
        givenContextWith(CurrentVersion.NA, RosterLifecycle.ON, AvailableNetwork.GENESIS);
        given(context.isGenesis()).willReturn(true);

        subject.restart(context);

        verify(rosterStore).putActiveRoster(ROSTER, 0L);
    }

    @Test
    void doesNotAdoptCandidateIfNotUpgradeBoundary() {
        givenContextWith(CurrentVersion.OLD, RosterLifecycle.ON, AvailableNetwork.NONE);
        given(context.previousVersion()).willReturn(THEN);

        subject.restart(context);

        verifyNoInteractions(canAdopt);
        verify(rosterStore, never()).putActiveRoster(ROSTER, ROUND_NO);
    }

    @Test
    void doesNotAdoptCandidateIfTestFails() {
        givenContextWith(CurrentVersion.NEW, RosterLifecycle.ON, AvailableNetwork.NONE);
        given(context.previousVersion()).willReturn(THEN);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(rosterStore.getCandidateRoster()).willReturn(ROSTER);
        given(canAdopt.test(ROSTER)).willReturn(false);

        subject.restart(context);

        verify(rosterStore, never()).adoptCandidateRoster(ROUND_NO);
    }

    @Test
    void forcesActiveFromMigrationAtUpgradeBoundaryIfNonePresent() {
        givenContextWith(CurrentVersion.NEW, RosterLifecycle.ON, AvailableNetwork.MIGRATION);
        given(context.previousVersion()).willReturn(THEN);
        given(context.roundNumber()).willReturn(ROUND_NO);
        given(platformStateStoreFactory.apply(writableStates)).willReturn(platformStateStore);
        given(platformStateStore.getAddressBook()).willReturn(ADDRESS_BOOK);

        subject.restart(context);

        verify(rosterStore).putActiveRoster(ROSTER, ROUND_NO + 1);
    }

    @Test
    void adoptsCandidateAtUpgradeBoundaryIfTestPasses() {
        givenContextWith(CurrentVersion.NEW, RosterLifecycle.ON, AvailableNetwork.NONE);
        given(context.previousVersion()).willReturn(THEN);
        given(rosterStore.getActiveRoster()).willReturn(ROSTER);
        given(rosterStore.getCandidateRoster()).willReturn(ROSTER);
        given(canAdopt.test(ROSTER)).willReturn(true);
        given(context.roundNumber()).willReturn(ROUND_NO);

        subject.restart(context);

        verify(rosterStore).adoptCandidateRoster(ROUND_NO + 1);
    }

    @Test
    void usesOverrideNetworkIfPresent() {
        given(context.roundNumber()).willReturn(ROUND_NO);
        givenContextWith(CurrentVersion.OLD, RosterLifecycle.ON, AvailableNetwork.OVERRIDE);
        given(startupNetworks.overrideNetworkFor(ROUND_NO)).willReturn(Optional.of(NETWORK));
        given(platformStateStoreFactory.apply(writableStates)).willReturn(platformStateStore);
        given(platformStateStore.getAddressBook()).willReturn(ADDRESS_BOOK);

        subject.restart(context);

        verify(rosterStore).putActiveRoster(ROSTER, ROUND_NO + 1);
        verify(startupNetworks).setOverrideRound(ROUND_NO);
    }

    private enum CurrentVersion {
        NA,
        OLD,
        NEW,
    }

    private enum RosterLifecycle {
        ON,
        OFF
    }

    private enum AvailableNetwork {
        GENESIS,
        OVERRIDE,
        MIGRATION,
        NONE
    }

    private void givenContextWith(
            @NonNull final CurrentVersion currentVersion,
            @NonNull final RosterLifecycle rosterLifecycle,
            @NonNull final AvailableNetwork availableNetwork) {
        final var configBuilder = HederaTestConfigBuilder.create()
                .withValue(
                        "addressBook.useRosterLifecycle",
                        switch (rosterLifecycle) {
                            case ON -> "true";
                            case OFF -> "false";
                        });
        switch (currentVersion) {
            case NA -> {
                // No-op
            }
            case OLD -> configBuilder.withValue("hedera.services.version", "0.7.0");
            case NEW -> configBuilder.withValue("hedera.services.version", "0.42.0");
        }
        given(context.configuration()).willReturn(configBuilder.getOrCreateConfig());
        if (rosterLifecycle == RosterLifecycle.ON) {
            given(context.newStates()).willReturn(writableStates);
            given(context.startupNetworks()).willReturn(startupNetworks);
            given(rosterStoreFactory.apply(writableStates)).willReturn(rosterStore);
        }
        switch (availableNetwork) {
            case GENESIS -> given(startupNetworks.genesisNetworkOrThrow()).willReturn(NETWORK);
            case OVERRIDE -> {
                given(context.roundNumber()).willReturn(ROUND_NO);
                given(startupNetworks.overrideNetworkFor(ROUND_NO)).willReturn(Optional.of(NETWORK));
            }
            case MIGRATION -> given(startupNetworks.migrationNetworkOrThrow()).willReturn(NETWORK);
            case NONE -> {
                // No-op
            }
        }
    }
}
