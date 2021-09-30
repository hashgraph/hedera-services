package com.hedera.services.sigs;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.swirlds.common.SwirldTransaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignatureExpanderTest {
    @Mock SigRequirements keyOrderer;
    @Mock PlatformTxnAccessor accessor;
    @Mock SwirldTransaction transaction;

    private SignatureExpander expander;

    @Test
    void successfulExpansion() {
        Expansion expansion = Mockito.mock(Expansion.class);
        expander = new SignatureExpander(keyOrderer) {
            Expansion createExpansion(
                    PlatformTxnAccessor txnAccessor,
                    SigRequirements keyOrderer,
                    PubKeyToSigBytes pkToSigFn,
                    TxnScopedPlatformSigFactory sigFactory
            ) {
                return expansion;
            }
        };

        given(accessor.getPlatformTxn()).willReturn(transaction);

        // when:
        expander.accept(accessor);

        // then:
        verify(transaction).clear();
        verify(expansion).execute();
    }

    @Test
    void failedExpansion() {
        Expansion expansion = Mockito.mock(Expansion.class);
        expander = new SignatureExpander(keyOrderer) {
            Expansion createExpansion(
                    PlatformTxnAccessor txnAccessor,
                    SigRequirements keyOrderer,
                    PubKeyToSigBytes pkToSigFn,
                    TxnScopedPlatformSigFactory sigFactory
            ) {
                return expansion;
            }
        };

        given(accessor.getPlatformTxn()).willReturn(transaction);
        given(expansion.execute()).willThrow(new RuntimeException("bad"));

        // when:
        expander.accept(accessor);

        // then:
        verify(transaction).clear();
        verify(expansion).execute();
    }
}

