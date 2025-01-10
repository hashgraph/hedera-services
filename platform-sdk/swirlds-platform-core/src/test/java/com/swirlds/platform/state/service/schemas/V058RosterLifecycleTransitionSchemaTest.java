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

package com.swirlds.platform.state.service.schemas;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.AddressBookConfig;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.service.WritablePlatformStateStore;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.Address;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.spi.WritableSingletonState;
import com.swirlds.state.spi.WritableStates;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V058RosterLifecycleTransitionSchemaTest {
    @Mock
    private AddressBook addressBook;

    @Mock
    private Configuration config;

    @Mock
    private AddressBookConfig addressBookConfig;

    @Mock
    private MigrationContext ctx;

    @Mock
    private WritableStates writableStates;

    @Mock
    private WritablePlatformStateStore platformStateStore;

    @Mock
    private WritableSingletonState<PlatformState> stateSingleton;

    @Mock
    private Function<Configuration, SoftwareVersion> appVersionFn;

    @Mock
    private Function<WritableStates, WritablePlatformStateStore> platformStateStoreFn;

    private V058RosterLifecycleTransitionSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V058RosterLifecycleTransitionSchema(() -> addressBook, appVersionFn, platformStateStoreFn);
    }

    @Test
    void migrateNullsOutAddressBooksAtBoundary() {
        final var oldState = PlatformState.newBuilder()
                .previousAddressBook(com.hedera.hapi.platform.state.AddressBook.DEFAULT)
                .addressBook(com.hedera.hapi.platform.state.AddressBook.DEFAULT)
                .build();
        given(ctx.newStates()).willReturn(writableStates);
        given(writableStates.<PlatformState>getSingleton("PLATFORM_STATE")).willReturn(stateSingleton);
        given(stateSingleton.get()).willReturn(oldState);
        given(ctx.appConfig()).willReturn(config);
        given(config.getConfigData(AddressBookConfig.class)).willReturn(addressBookConfig);
        given(addressBookConfig.useRosterLifecycle()).willReturn(true);
        subject.migrate(ctx);
        verify(stateSingleton).put(PlatformState.DEFAULT);
    }

    @Test
    void doesNotApplyAtGenesis() {
        given(ctx.appConfig()).willReturn(config);
        given(config.getConfigData(AddressBookConfig.class)).willReturn(addressBookConfig);
        given(ctx.isGenesis()).willReturn(true);
        subject.restart(ctx);
        verifyNoInteractions(platformStateStoreFn);
    }

    @Test
    void noOpIfAddressBookNotChanged() {
        given(ctx.appConfig()).willReturn(config);
        given(config.getConfigData(AddressBookConfig.class)).willReturn(addressBookConfig);
        subject.restart(ctx);
        verifyNoInteractions(platformStateStoreFn);
    }

    @Test
    @SuppressWarnings("unchecked")
    void changesAtUpgradeBoundary() {
        final ArgumentCaptor<Consumer<PlatformStateModifier>> captor = ArgumentCaptor.forClass(Consumer.class);
        given(ctx.appConfig()).willReturn(config);
        given(ctx.isUpgrade(any(), any())).willReturn(true);
        given(config.getConfigData(AddressBookConfig.class)).willReturn(addressBookConfig);
        given(addressBook.copy()).willReturn(addressBook);
        given(ctx.newStates()).willReturn(writableStates);
        given(platformStateStoreFn.apply(writableStates)).willReturn(platformStateStore);
        given(platformStateStore.getAddressBook()).willReturn(addressBook);
        subject.restart(ctx);
        verify(platformStateStore).bulkUpdate(captor.capture());
        captor.getValue().accept(platformStateStore);
        verify(platformStateStore).setPreviousAddressBook(addressBook);
        verify(platformStateStore).setAddressBook(addressBook);
    }

    @Test
    @SuppressWarnings("unchecked")
    void changesWhenForcedWithoutExtantBook() {
        final ArgumentCaptor<Consumer<PlatformStateModifier>> captor = ArgumentCaptor.forClass(Consumer.class);
        given(ctx.appConfig()).willReturn(config);
        given(config.getConfigData(AddressBookConfig.class)).willReturn(addressBookConfig);
        given(addressBookConfig.forceUseOfConfigAddressBook()).willReturn(true);
        given(addressBook.copy()).willReturn(addressBook);
        given(ctx.newStates()).willReturn(writableStates);
        given(platformStateStoreFn.apply(writableStates)).willReturn(platformStateStore);
        subject.restart(ctx);
        verify(platformStateStore).bulkUpdate(captor.capture());
        captor.getValue().accept(platformStateStore);
        verify(platformStateStore).setPreviousAddressBook(null);
        verify(platformStateStore).setAddressBook(addressBook);
    }

    @Test
    @SuppressWarnings("unchecked")
    void changesWhenForcedWithExtantBook() {
        final ArgumentCaptor<Consumer<PlatformStateModifier>> captor = ArgumentCaptor.forClass(Consumer.class);
        given(ctx.appConfig()).willReturn(config);
        given(config.getConfigData(AddressBookConfig.class)).willReturn(addressBookConfig);
        given(addressBookConfig.forceUseOfConfigAddressBook()).willReturn(true);
        given(addressBook.copy()).willReturn(addressBook);
        final var matchingAddress = mock(Address.class);
        final var matchingNodeId = NodeId.of(42L);
        final var matchingWeight = 42L;
        given(matchingAddress.getNodeId()).willReturn(matchingNodeId);
        final var newAddress = mock(Address.class);
        given(newAddress.getNodeId()).willReturn(NodeId.of(43L));
        given(addressBook.iterator())
                .willReturn(List.of(matchingAddress, newAddress).iterator());
        given(ctx.newStates()).willReturn(writableStates);
        given(platformStateStoreFn.apply(writableStates)).willReturn(platformStateStore);
        final var currentBook = mock(AddressBook.class);
        given(currentBook.copy()).willReturn(currentBook);
        given(currentBook.contains(matchingNodeId)).willReturn(true);
        given(currentBook.getAddress(matchingNodeId)).willReturn(matchingAddress);
        given(matchingAddress.getWeight()).willReturn(matchingWeight);
        given(matchingAddress.copySetWeight(matchingWeight)).willReturn(matchingAddress);
        given(platformStateStore.getAddressBook()).willReturn(currentBook);
        subject.restart(ctx);
        verify(platformStateStore).bulkUpdate(captor.capture());
        captor.getValue().accept(platformStateStore);
        verify(platformStateStore).setPreviousAddressBook(currentBook);
        verify(platformStateStore).setAddressBook(argThat(book -> book.getSize() == 2));
    }
}
