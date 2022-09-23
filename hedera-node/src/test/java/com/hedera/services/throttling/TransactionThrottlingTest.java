/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransactionThrottlingTest {
    FunctionalityThrottling functionalThrottling;

    TransactionThrottling subject;

    @BeforeEach
    void setup() {
        functionalThrottling = mock(FunctionalityThrottling.class);

        subject = new TransactionThrottling(functionalThrottling);
    }

    @Test
    void delegatesExpectedFunction() {
        // setup:
        TransactionBody createTxn =
                TransactionBody.newBuilder()
                        .setConsensusCreateTopic(
                                ConsensusCreateTopicTransactionBody.newBuilder().setMemo("Hi!"))
                        .build();
        final var accessor =
                SignedTxnAccessor.uncheckedFrom(
                        Transaction.newBuilder()
                                .setSignedTransactionBytes(
                                        SignedTransaction.newBuilder()
                                                .setBodyBytes(createTxn.toByteString())
                                                .build()
                                                .toByteString())
                                .build());

        given(functionalThrottling.shouldThrottleTxn(accessor)).willReturn(true);

        // when:
        boolean should = subject.shouldThrottle(accessor);

        // then:
        assertTrue(should);
        verify(functionalThrottling).shouldThrottleTxn(accessor);
    }
}
