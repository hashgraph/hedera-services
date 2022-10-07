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
package com.hedera.services.txns.submission;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static org.mockito.BDDMockito.given;

import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SemanticPrecheckTest {
    private final SignedTxnAccessor xferAccessor =
            SignedTxnAccessor.uncheckedFrom(
                    Transaction.newBuilder()
                            .setBodyBytes(
                                    TransactionBody.newBuilder()
                                            .setCryptoTransfer(
                                                    CryptoTransferTransactionBody
                                                            .getDefaultInstance())
                                            .build()
                                            .toByteString())
                            .build());

    @Mock private TransitionLogic transitionLogic;
    @Mock private TransitionLogicLookup transitionLogicLookup;

    private SemanticPrecheck subject;

    @BeforeEach
    void setUp() {
        subject = new SemanticPrecheck(transitionLogicLookup);
    }

    @Test
    void usesDiscoveredLogicCheck() {
        given(transitionLogicLookup.lookupFor(CryptoTransfer, xferAccessor.getTxn()))
                .willReturn(Optional.of(transitionLogic));
        given(transitionLogic.validateSemantics(xferAccessor))
                .willReturn(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);

        // when:
        var result = subject.validate(xferAccessor, xferAccessor.getFunction(), NOT_SUPPORTED);

        // then:
        Assertions.assertEquals(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS, result);
    }

    @Test
    void defaultsToGiven() {
        given(transitionLogicLookup.lookupFor(CryptoTransfer, xferAccessor.getTxn()))
                .willReturn(Optional.empty());

        // when:
        var result =
                subject.validate(xferAccessor, xferAccessor.getFunction(), INSUFFICIENT_TX_FEE);

        // then:
        Assertions.assertEquals(INSUFFICIENT_TX_FEE, result);
    }
}
