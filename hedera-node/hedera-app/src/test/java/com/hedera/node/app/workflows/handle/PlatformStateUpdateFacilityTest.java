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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.freeze.FreezeType.FREEZE_UPGRADE;
import static com.hedera.node.app.service.networkadmin.impl.FreezeServiceImpl.FREEZE_TIME_KEY;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.spi.fixtures.TransactionFactory;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import com.swirlds.state.spi.WritableStates;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformStateUpdateFacilityTest implements TransactionFactory {
    private FakeHederaState state;

    private PlatformStateUpdateFacility subject;
    private AtomicReference<Timestamp> freezeTimeBackingStore;

    @Mock(strictness = Strictness.LENIENT)
    private PlatformState platformState;

    @Mock(strictness = LENIENT)
    protected WritableStates writableStates;

    @BeforeEach
    void setUp() {
        freezeTimeBackingStore = new AtomicReference<>(null);
        when(writableStates.getSingleton(FREEZE_TIME_KEY))
                .then(invocation -> new WritableSingletonStateBase<>(
                        FREEZE_TIME_KEY, freezeTimeBackingStore::get, freezeTimeBackingStore::set));

        state = new FakeHederaState().addService(FreezeService.NAME, Map.of(FREEZE_TIME_KEY, freezeTimeBackingStore));

        doAnswer(answer -> when(platformState.getFreezeTime()).thenReturn(answer.getArgument(0)))
                .when(platformState)
                .setFreezeTime(any(Instant.class));

        subject = new PlatformStateUpdateFacility();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testMethodsWithInvalidArguments() {
        // given
        final var txBody = simpleCryptoTransfer().body();

        // then
        assertThatThrownBy(() -> subject.handleTxBody(null, platformState, txBody))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.handleTxBody(state, null, txBody)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> subject.handleTxBody(state, platformState, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCryptoTransferShouldBeNoOp() {
        // given
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.DEFAULT)
                .build();

        // then
        Assertions.assertThatCode(() -> subject.handleTxBody(state, platformState, txBody))
                .doesNotThrowAnyException();
    }

    @Test
    void testFreezeUpgrade() {
        // given
        final var freezeTime = Timestamp.newBuilder().seconds(123L).nanos(456).build();
        freezeTimeBackingStore.set(freezeTime);
        final var txBody = TransactionBody.newBuilder()
                .freeze(FreezeTransactionBody.newBuilder().freezeType(FREEZE_UPGRADE));

        // when
        subject.handleTxBody(state, platformState, txBody.build());

        // then
        assertEquals(freezeTime.seconds(), platformState.getFreezeTime().getEpochSecond());
        assertEquals(freezeTime.nanos(), platformState.getFreezeTime().getNano());
    }
}
