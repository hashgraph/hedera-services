// SPDX-License-Identifier: Apache-2.0
package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_ABORT;
import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.PREPARE_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.TELEMETRY_UPGRADE;
import static com.hedera.hapi.node.freeze.FreezeType.UNKNOWN_FREEZE_TYPE;
import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_KEY;
import static com.hedera.node.app.roster.schemas.V0540RosterSchema.ROSTER_STATES_KEY;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.node.app.service.networkadmin.impl.schemas.V0490FreezeSchema.FREEZE_TIME_KEY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PlatformStateUpdatesTest implements TransactionFactory {
    public static final RosterState ROSTER_STATE = RosterState.newBuilder()
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

    @Mock
    private BiConsumer<Roster, Path> rosterExportHelper;

    @BeforeEach
    void setUp() {
        freezeTimeBackingStore = new AtomicReference<>(null);
        platformStateBackingStore = new AtomicReference<>(V0540PlatformStateSchema.UNINITIALIZED_PLATFORM_STATE);
        when(writableStates.getSingleton(FREEZE_TIME_KEY))
                .then(invocation -> new WritableSingletonStateBase<Timestamp>(
                        FreezeService.NAME, FREEZE_TIME_KEY) {
                    @Override
                    protected Timestamp readFromDataSource() {
                        return freezeTimeBackingStore.get();
                    }

                    @Override
                    protected void putIntoDataSource(@NotNull Timestamp value) {
                        freezeTimeBackingStore.set(value);
                    }

                    @Override
                    protected void removeFromDataSource() {
                        freezeTimeBackingStore.set(null);
                    }
                });

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

        subject = new PlatformStateUpdates(rosterExportHelper);
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
    void freezeAbortNullsOutFreezeTime() {
        final var freezeTime = Timestamp.newBuilder().seconds(123L).nanos(456).build();
        freezeTimeBackingStore.set(freezeTime);
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(FREEZE_ABORT));

        subject.handleTxBody(state, txBody.build(), DEFAULT_CONFIG);

        assertThat(platformStateBackingStore.get().freezeTime()).isNull();
    }

    @Test
    void unknownFreezeIsNoop() {
        platformStateBackingStore.set(
                PlatformState.newBuilder().freezeTime(Timestamp.DEFAULT).build());
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(UNKNOWN_FREEZE_TYPE));

        subject.handleTxBody(state, txBody.build(), DEFAULT_CONFIG);

        assertThat(platformStateBackingStore.get().freezeTime()).isEqualTo(Timestamp.DEFAULT);
    }

    @Test
    void telemetryUpgradeIsNoop() {
        platformStateBackingStore.set(
                PlatformState.newBuilder().freezeTime(Timestamp.DEFAULT).build());
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(TELEMETRY_UPGRADE));

        subject.handleTxBody(state, txBody.build(), DEFAULT_CONFIG);

        assertThat(platformStateBackingStore.get().freezeTime()).isEqualTo(Timestamp.DEFAULT);
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
                .freeze(FreezeTransactionBody.newBuilder().freezeType(PREPARE_UPGRADE));
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
        final var captor = ArgumentCaptor.forClass(Path.class);
        verify(rosterExportHelper).accept(any(), captor.capture());
        final var path = captor.getValue();
        assertEquals("candidate-network.json", path.getFileName().toString());
    }

    @Test
    void worksAroundFailureToPutCandidateRoster() {
        final var freezeTime = Timestamp.newBuilder().seconds(123L).nanos(456).build();
        freezeTimeBackingStore.set(freezeTime);
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(PREPARE_UPGRADE));
        nodes.put(
                new EntityNumber(0L),
                Node.newBuilder()
                        .weight(0)
                        .gossipCaCertificate(Bytes.fromHex("0123"))
                        .gossipEndpoint(new ServiceEndpoint(Bytes.EMPTY, 50211, "test.org"))
                        .build());

        subject.handleTxBody(state, txBody.build(), configWith(false, true));

        verify(rosterExportHelper, never()).accept(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportsCandidateRosterIfRequestedEvenWhenNotUsingRosterLifecycle() {
        final var freezeTime = Timestamp.newBuilder().seconds(123L).nanos(456).build();
        freezeTimeBackingStore.set(freezeTime);
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(PREPARE_UPGRADE));
        nodes.put(
                new EntityNumber(0L),
                Node.newBuilder()
                        .weight(1)
                        .gossipCaCertificate(Bytes.fromHex("0123"))
                        .gossipEndpoint(new ServiceEndpoint(Bytes.EMPTY, 50211, "test.org"))
                        .build());

        // when
        subject.handleTxBody(state, txBody.build(), configWith(false, false));

        // then
        final var captor = ArgumentCaptor.forClass(Path.class);
        verify(rosterExportHelper).accept(any(), captor.capture());
        final var path = captor.getValue();
        assertEquals("candidate-network.json", path.getFileName().toString());
    }

    private Configuration configWith(final boolean keyCandidateRoster, final boolean useRosterLifecycle) {
        return HederaTestConfigBuilder.create()
                .withValue("tss.keyCandidateRoster", "" + keyCandidateRoster)
                .withValue("addressBook.useRosterLifecycle", "" + useRosterLifecycle)
                .withValue("networkAdmin.exportCandidateRoster", "true")
                .withValue("networkAdmin.candidateRosterExportFile", "candidate-network.json")
                .getOrCreateConfig();
    }
}
