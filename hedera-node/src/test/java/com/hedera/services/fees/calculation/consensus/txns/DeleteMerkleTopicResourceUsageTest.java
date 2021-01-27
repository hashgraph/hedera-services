package com.hedera.services.fees.calculation.consensus.txns;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.exception.InvalidTxBodyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeleteMerkleTopicResourceUsageTest extends TopicResourceUsageTestBase {

    DeleteTopicResourceUsage subject;

    @BeforeEach
    void setup() throws Throwable {
        super.setup();
        subject = new DeleteTopicResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // setup:
        TransactionBody deleteTopicTx = TransactionBody.newBuilder()
                .setConsensusDeleteTopic(ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId).build())
                .build();
        TransactionBody nonDeleteTopicTx = TransactionBody.newBuilder().build();

        // expect:
        assertTrue(subject.applicableTo(deleteTopicTx));
        assertFalse(subject.applicableTo(nonDeleteTopicTx));
    }

    @Test
    public void getFeeThrowsExceptionForBadTxBody() {
        // setup:
        TransactionBody nonDeleteTopicTx = TransactionBody.newBuilder().build();

        // expect:
        assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(null, sigValueObj, view));
        assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(nonDeleteTopicTx, sigValueObj, view));
    }


    @Test
    public void feeDataAsExpected() throws Exception {
        // setup:
        TransactionBody txBody = makeTransactionBody(topicId);

        // when:
        FeeData feeData = subject.usageGiven(txBody, sigValueObj, view);

        // expect:
        int expectedExtraBpt = 24; // + 24 for topicId
        checkServicesFee(feeData, 0);
        checkNetworkFee(feeData, expectedExtraBpt, 0);
        checkNodeFee(feeData, expectedExtraBpt);
    }

    private TransactionBody makeTransactionBody(TopicID topicId) {
        ConsensusDeleteTopicTransactionBody deleteTopicTxBody =
                ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(topicId).build();
        return TransactionBody.newBuilder()
                .setConsensusDeleteTopic(deleteTopicTxBody)
                .build();
    }}
