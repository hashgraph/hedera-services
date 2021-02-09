package com.hedera.services.fees.calculation.consensus.txns;

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

import com.hedera.test.utils.*;
import com.hederahashgraph.api.proto.java.*;
import com.hederahashgraph.exception.InvalidTxBodyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CreateMerkleTopicResourceUsageTest extends TopicResourceUsageTestBase {
    CreateTopicResourceUsage subject;

    @BeforeEach
    void setup() throws Throwable {
        super.setup();
        subject = new CreateTopicResourceUsage();
    }

    @Test
    public void recognizesApplicableQuery() {
        // setup:
        TransactionBody createTopicTx = TransactionBody.newBuilder()
                .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder().build())
                .build();
        TransactionBody nonCreateTopicTx = TransactionBody.newBuilder().build();

        // expect:
        assertTrue(subject.applicableTo(createTopicTx));
        assertFalse(subject.applicableTo(nonCreateTopicTx));
    }

    @Test
    public void getFeeThrowsExceptionForBadTxBody() {
        // setup:
        TransactionBody nonCreateTopicTx = TransactionBody.newBuilder().build();

        // expect:
        assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(null, sigValueObj, view));
        assertThrows(InvalidTxBodyException.class, () -> subject.usageGiven(nonCreateTopicTx, sigValueObj, view));
    }


    @ParameterizedTest
    @CsvSource({
            ", , , , 0, 8, 0", // base values for bpt and rbh
            ", , , , 3600, 8, 100", // 3600 is chosen as duration so that each extra byte increases rbh by 1
            "12345678, , , , 3600, 16, 108", // + 8 (memo size)
            "12345678, 0000000000000000000000000000000000000000000000000000000000000000, , , 3600, 48, 140", //  +32 (admin key)
            "12345678, 0000000000000000000000000000000000000000000000000000000000000000, 1111111111111111111111111111111111111111111111111111111111111111, , 3600, 80, 172", // +32 (submit key)
            "12345678, 0000000000000000000000000000000000000000000000000000000000000000, 1111111111111111111111111111111111111111111111111111111111111111, 0.1.2, 3600, 104, 196", // +24 (auto renew account)
            "12345678, 0000000000000000000000000000000000000000000000000000000000000000, 1111111111111111111111111111111111111111111111111111111111111111, 0.1.2, 7200, 104, 392" // increase duration => increase rbh
    })
    public void feeDataAsExpected(
            String memo,
            @ConvertWith(Ed25519KeyConverter.class) Key adminJKey,
            @ConvertWith(Ed25519KeyConverter.class) Key submitJKey,
            @ConvertWith(AccountIDConverter.class) AccountID autoRenewAccountId,
            @ConvertWith(DurationConverter.class) Duration autoRenewPeriod,
            int expectedExtraBpt,
            int expectedExtraServicesRbh
    ) throws Exception {
        // setup:
        TransactionBody txBody = makeTransactionBody(memo, adminJKey, submitJKey, autoRenewAccountId, autoRenewPeriod);

        // when:
        FeeData feeData = subject.usageGiven(txBody, sigValueObj, view);

        // expect:
        int expectedNetworkRbh = 2;  // Addition rbh for topicId
        checkServicesFee(feeData, expectedExtraServicesRbh);
        checkNetworkFee(feeData, expectedExtraBpt, expectedNetworkRbh);
        checkNodeFee(feeData, expectedExtraBpt);
    }

    private TransactionBody makeTransactionBody(
            String memo, Key adminKey, Key submitKey, AccountID autoRenewAccountId, Duration autoRenewPeriod) {
        ConsensusCreateTopicTransactionBody.Builder createTopicTxBodyBuilder =
                ConsensusCreateTopicTransactionBody.newBuilder()
                        .mergeAdminKey(adminKey)
                        .mergeSubmitKey(submitKey)
                        .mergeAutoRenewAccount(autoRenewAccountId);
        if (memo != null) {
            createTopicTxBodyBuilder.setMemo(memo);
        }
        if (autoRenewPeriod != null) {
            createTopicTxBodyBuilder.setAutoRenewPeriod(autoRenewPeriod);
        }
        return TransactionBody.newBuilder()
                .setConsensusCreateTopic(createTopicTxBodyBuilder)
                .build();
    }
}
