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
package com.hedera.services.fees.calculation.consensus.txns;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeleteMerkleTopicResourceUsageTest extends TopicResourceUsageTestBase {
    private DeleteTopicResourceUsage subject;

    @BeforeEach
    void setup() throws Throwable {
        super.setup();
        subject = new DeleteTopicResourceUsage();
    }

    @Test
    void recognizesApplicableQuery() {
        final var deleteTopicTx = makeTransactionBody(topicId);
        final var nonDeleteTopicTx = TransactionBody.getDefaultInstance();

        assertTrue(subject.applicableTo(deleteTopicTx));
        assertFalse(subject.applicableTo(nonDeleteTopicTx));
    }

    @Test
    void getFeeThrowsExceptionForBadTxBody() {
        final var nonDeleteTopicTx = TransactionBody.getDefaultInstance();

        assertThrows(
                InvalidTxBodyException.class, () -> subject.usageGiven(null, sigValueObj, view));
        assertThrows(
                InvalidTxBodyException.class,
                () -> subject.usageGiven(nonDeleteTopicTx, sigValueObj, view));
    }

    @Test
    void feeDataAsExpected() throws InvalidTxBodyException {
        final var txBody = makeTransactionBody(topicId);

        final var feeData = subject.usageGiven(txBody, sigValueObj, view);

        final int expectedExtraBpt = 24; // + 24 for topicId
        checkServicesFee(feeData, 0);
        checkNetworkFee(feeData, expectedExtraBpt, 0);
        checkNodeFee(feeData, expectedExtraBpt);
    }

    private TransactionBody makeTransactionBody(final TopicID topicId) {
        final var deleteTopicTxBody =
                ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId);
        return TransactionBody.newBuilder().setConsensusDeleteTopic(deleteTopicTxBody).build();
    }
}
