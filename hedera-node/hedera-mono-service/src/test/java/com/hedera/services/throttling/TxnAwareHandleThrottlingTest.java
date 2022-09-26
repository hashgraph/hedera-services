/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.throttling;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Transaction;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TxnAwareHandleThrottlingTest {
    private Instant consensusTime = Instant.ofEpochSecond(1_234_567L, 123);

    @Mock private TimedFunctionalityThrottling delegate;
    @Mock private TransactionContext txnCtx;
    @Mock private Query query;

    private TxnAwareHandleThrottling subject;

    @BeforeEach
    void setUp() {
        subject = new TxnAwareHandleThrottling(txnCtx, delegate);
    }

    @Test
    void txnHandlingDoesntSupportQueries() {
        // expect:
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.shouldThrottleQuery(CryptoGetAccountBalance, query));
    }

    @Test
    void delegatesWasLastTxnGasThrottled() {
        given(delegate.wasLastTxnGasThrottled()).willReturn(true);

        assertTrue(subject.wasLastTxnGasThrottled());
    }

    @Test
    void delegatesThrottlingDecisionsWithConsensusTime() {
        // setup:
        final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

        given(txnCtx.consensusTime()).willReturn(consensusTime);
        given(delegate.shouldThrottleTxn(accessor, consensusTime)).willReturn(true);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor));
        // and:
        verify(delegate).shouldThrottleTxn(accessor, consensusTime);
    }

    @Test
    void delegatesThrottlingConsensusTxnDecisionsWithConsensusTime() {
        // setup:
        final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

        given(txnCtx.consensusTime()).willReturn(consensusTime);
        given(delegate.shouldThrottleTxn(accessor, consensusTime)).willReturn(true);

        // expect:
        assertTrue(subject.shouldThrottleTxn(accessor));

        // and:
        verify(delegate).shouldThrottleTxn(accessor, consensusTime);
    }

    @Test
    void otherMethodsPassThrough() {
        // setup:
        ThrottleDefinitions defs = new ThrottleDefinitions();
        List<DeterministicThrottle> whatever = List.of(DeterministicThrottle.withTps(1));

        given(delegate.allActiveThrottles()).willReturn(whatever);
        given(delegate.activeThrottlesFor(HederaFunctionality.CryptoTransfer)).willReturn(whatever);

        // when:
        var all = subject.allActiveThrottles();
        var onlyXfer = subject.activeThrottlesFor(HederaFunctionality.CryptoTransfer);
        subject.rebuildFor(defs);
        subject.resetUsage();

        // then:
        verify(delegate).rebuildFor(defs);
        verify(delegate).resetUsage();
        assertSame(whatever, all);
        assertSame(whatever, onlyXfer);
    }

    @Test
    void leakUnusedGasCallsDelegateLeakMethod() {
        // setup:
        final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

        // when:
        subject.leakUnusedGasPreviouslyReserved(accessor, 12345L);

        // then:
        verify(delegate).leakUnusedGasPreviouslyReserved(accessor, 12345L);
    }

    @Test
    void gasLimitThrottleWorks() {
        GasLimitDeterministicThrottle gasLimitDeterministicThrottle =
                new GasLimitDeterministicThrottle(1234);
        given(delegate.gasLimitThrottle()).willReturn(gasLimitDeterministicThrottle);
        GasLimitDeterministicThrottle gasLimitDeterministicThrottle1 = subject.gasLimitThrottle();
        assertEquals(gasLimitDeterministicThrottle, gasLimitDeterministicThrottle1);
    }

    @Test
    void applyGasConfig() {
        subject.applyGasConfig();
        verify(delegate).applyGasConfig();
    }
}
