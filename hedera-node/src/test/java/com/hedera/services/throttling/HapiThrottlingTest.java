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

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions;
import com.hedera.services.throttles.GasLimitDeterministicThrottle;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HapiThrottlingTest {
    @Mock private TimedFunctionalityThrottling delegate;
    @Mock private Query query;

    private HapiThrottling subject;

    @BeforeEach
    void setUp() {
        subject = new HapiThrottling(delegate);
    }

    @Test
    void delegatesQueryWithSomeInstant() {
        given(delegate.shouldThrottleQuery(any(), any(), any())).willReturn(true);

        // when:
        var ans = subject.shouldThrottleQuery(ContractCallLocal, query);

        // then:
        assertTrue(ans);
        // and:
        verify(delegate).shouldThrottleQuery(eq(ContractCallLocal), any(), any());
    }

    @Test
    void delegatesTxnWithSomeInstant() {
        // setup:
        final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

        given(delegate.shouldThrottleTxn(any(), any())).willReturn(true);

        // when:
        var ans = subject.shouldThrottleTxn(accessor);

        // then:
        assertTrue(ans);
        // and:
        verify(delegate).shouldThrottleTxn(eq(accessor), any());
    }

    @Test
    void delegatesConsensusTxnWithSomeInstant() {
        // setup:
        final var accessor = SignedTxnAccessor.uncheckedFrom(Transaction.getDefaultInstance());

        given(delegate.shouldThrottleTxn(any(), any())).willReturn(true);

        // when:
        var ans = subject.shouldThrottleTxn(accessor);

        // then:
        assertTrue(ans);
        // and:
        verify(delegate).shouldThrottleTxn(eq(accessor), any());
    }

    @Test
    void unsupportedMethodsThrow() {
        // expect:
        assertThrows(UnsupportedOperationException.class, () -> subject.activeThrottlesFor(null));
        assertThrows(UnsupportedOperationException.class, () -> subject.wasLastTxnGasThrottled());
    }

    @Test
    void delegatesRebuild() {
        // setup:
        ThrottleDefinitions defs = new ThrottleDefinitions();

        // when:
        subject.rebuildFor(defs);

        // then:
        verify(delegate).rebuildFor(defs);
    }

    @Test
    void delegatesResetUsage() {
        // setup:
        ThrottleDefinitions defs = new ThrottleDefinitions();

        // when:
        subject.resetUsage();

        // then:
        verify(delegate).resetUsage();
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
    void throttlesAvailableToUsageStats() {
        given(delegate.allActiveThrottles()).willReturn(List.of());
        given(delegate.gasLimitThrottle()).willReturn(new GasLimitDeterministicThrottle(123));
        assertTrue(subject.allActiveThrottles().isEmpty());
        assertEquals(123, subject.gasLimitThrottle().getCapacity());
    }

    @Test
    void applyGasConfigTest() {
        subject.applyGasConfig();
        verify(delegate).applyGasConfig();
    }
}
