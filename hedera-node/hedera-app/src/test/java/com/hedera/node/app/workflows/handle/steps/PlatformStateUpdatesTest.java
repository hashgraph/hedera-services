/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.FREEZE_TIME_KEY;
import static com.swirlds.platform.state.service.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.swirlds.platform.state.service.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ServiceEndpoint;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.node.state.roster.RosterState;
import com.hedera.hapi.node.state.roster.RoundRosterPair;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.state.PlatformState;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.app.roster.RosterService;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.service.PlatformStateService;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformStateUpdatesTest implements TransactionFactory {
    private static final RosterState ROSTER_STATE = RosterState.newBuilder()
            .roundRosterPairs(List.of(RoundRosterPair.DEFAULT))
            .build();
    private FakeState state;

    private PlatformStateUpdates subject;
    private AtomicReference<Timestamp> freezeTimeBackingStore;
    private AtomicReference<PlatformState> platformStateBackingStore;
    private AtomicReference<RosterState> rosterStateBackingStore = new AtomicReference<>(ROSTER_STATE);
    private ConcurrentHashMap<ProtoBytes, Roster> rosters = new ConcurrentHashMap<>();
    private ConcurrentHashMap<EntityNumber, Node> nodes = new ConcurrentHashMap<>();

    @Mock(strictness = LENIENT)
    protected WritableStates writableStates;

    @BeforeEach
    void setUp() {
        freezeTimeBackingStore = new AtomicReference<>(null);
        platformStateBackingStore = new AtomicReference<>(V0540PlatformStateSchema.GENESIS_PLATFORM_STATE);
        when(writableStates.getSingleton(FREEZE_TIME_KEY))
                .then(invocation -> new WritableSingletonStateBase<>(
                        FREEZE_TIME_KEY, freezeTimeBackingStore::get, freezeTimeBackingStore::set));

        state = new FakeState()
                .addService(FreezeService.NAME, Map.of(FREEZE_TIME_KEY, freezeTimeBackingStore))
                .addService(
                        RosterService.NAME,
                        Map.of(
                                ROSTER_STATES_KEY, rosterStateBackingStore,
                                ROSTER_KEY, rosters))
                .addService(AddressBookService.NAME, Map.of(NODES_KEY, nodes))
                .addService(
                        PlatformStateService.NAME,
                        Map.of(V0540PlatformStateSchema.PLATFORM_STATE_KEY, platformStateBackingStore));

        subject = new PlatformStateUpdates();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testMethodsWithInvalidArguments() {
        // given
        final var txBody = simpleCryptoTransfer().body();

        // then
        assertThatThrownBy(() -> subject.handleTxBody(null, txBody, DEFAULT_CONFIG))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.handleTxBody(state, txBody, DEFAULT_CONFIG))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.handleTxBody(state, null, DEFAULT_CONFIG))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCryptoTransferShouldBeNoOp() {
        // given
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();

        // then
        Assertions.assertThatCode(() -> subject.handleTxBody(state, txBody, DEFAULT_CONFIG))
                .doesNotThrowAnyException();
    }

    @Test
    void testFreezeUpgradeWhenKeying() {
        // given
        final var freezeTime = Timestamp.newBuilder().seconds(123L).nanos(456).build();
        freezeTimeBackingStore.set(freezeTime);
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(FREEZE_UPGRADE));

        // when
        subject.handleTxBody(state, txBody.build(), configWith(true, true));

        // then
        final var platformState = platformStateBackingStore.get();
        assertEquals(freezeTime.seconds(), platformState.freezeTimeOrThrow().seconds());
        assertEquals(freezeTime.nanos(), platformState.freezeTimeOrThrow().nanos());
    }

    @Test
    void testFreezeUpgradeWhenKeyingButNotUsingRosterLifecycle() {
        // given
        final var freezeTime = Timestamp.newBuilder().seconds(123L).nanos(456).build();
        freezeTimeBackingStore.set(freezeTime);
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(FREEZE_UPGRADE));

        // when
        subject.handleTxBody(state, txBody.build(), configWith(true, false));

        // then
        final var platformState = platformStateBackingStore.get();
        assertEquals(freezeTime.seconds(), platformState.freezeTimeOrThrow().seconds());
        assertEquals(freezeTime.nanos(), platformState.freezeTimeOrThrow().nanos());
    }

    @Test
    void putsCandidateRosterWhenNotKeyingButUsingRosterLifecycle() {
        // given
        final var freezeTime = Timestamp.newBuilder().seconds(123L).nanos(456).build();
        freezeTimeBackingStore.set(freezeTime);
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(FREEZE_UPGRADE));
        nodes.put(
                new EntityNumber(0L),
                Node.newBuilder()
                        .weight(1)
                        .gossipCaCertificate(Bytes.fromHex("0123"))
                        .gossipEndpoint(new ServiceEndpoint(Bytes.EMPTY, 50211, "test.org"))
                        .build());

        // when
        subject.handleTxBody(state, txBody.build(), configWith(false, true));

        // then
        final var platformState = platformStateBackingStore.get();
        assertEquals(freezeTime.seconds(), platformState.freezeTimeOrThrow().seconds());
        assertEquals(freezeTime.nanos(), platformState.freezeTimeOrThrow().nanos());
    }

    private Configuration configWith(final boolean keyCandidateRoster, final boolean useRosterLifecycle) {
        return HederaTestConfigBuilder.create()
                .withValue("tss.keyCandidateRoster", "" + keyCandidateRoster)
                .withValue("addressBook.useRosterLifecycle", "" + useRosterLifecycle)
                .getOrCreateConfig();
    }
}
