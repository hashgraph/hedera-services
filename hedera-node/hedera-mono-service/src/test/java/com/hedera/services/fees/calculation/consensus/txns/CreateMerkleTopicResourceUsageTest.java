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

import com.hedera.test.utils.AccountIDConverter;
import com.hedera.test.utils.DurationConverter;
import com.hedera.test.utils.Ed25519KeyConverter;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import javax.annotation.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.CsvSource;

class CreateMerkleTopicResourceUsageTest extends TopicResourceUsageTestBase {
    private CreateTopicResourceUsage subject;

    @BeforeEach
    void setup() throws Throwable {
        super.setup();
        subject = new CreateTopicResourceUsage();
    }

    @Test
    void recognizesApplicableQuery() {
        final var createTopicTx =
                TransactionBody.newBuilder()
                        .setConsensusCreateTopic(
                                ConsensusCreateTopicTransactionBody.getDefaultInstance())
                        .build();
        final var nonCreateTopicTx = TransactionBody.getDefaultInstance();

        assertTrue(subject.applicableTo(createTopicTx));
        assertFalse(subject.applicableTo(nonCreateTopicTx));
    }

    @Test
    void getFeeThrowsExceptionForBadTxBody() {
        final var nonCreateTopicTx = TransactionBody.getDefaultInstance();

        assertThrows(
                InvalidTxBodyException.class, () -> subject.usageGiven(null, sigValueObj, view));
        assertThrows(
                InvalidTxBodyException.class,
                () -> subject.usageGiven(nonCreateTopicTx, sigValueObj, view));
    }

    @ParameterizedTest
    @CsvSource({
        ", , , , 0, 8, 0", // base values for bpt and rbh
        ", , , , 3600, 8, 100", // 3600 is chosen as duration so that each extra byte increases rbh
        // by 1
        "12345678, , , , 3600, 16, 108", // + 8 (memo size)
        "12345678, 0000000000000000000000000000000000000000000000000000000000000000, , , 3600, 48,"
                + " 140",
        //  +32 (admin key)
        "12345678, 0000000000000000000000000000000000000000000000000000000000000000,"
            + " 1111111111111111111111111111111111111111111111111111111111111111, , 3600, 80, 172",
        // +32 (submit key)
        "12345678, 0000000000000000000000000000000000000000000000000000000000000000,"
            + " 1111111111111111111111111111111111111111111111111111111111111111, 0.1.2, 3600, 104,"
            + " 196",
        // +24 (auto renew account)
        "12345678, 0000000000000000000000000000000000000000000000000000000000000000,"
            + " 1111111111111111111111111111111111111111111111111111111111111111, 0.1.2, 7200, 104,"
            + " 392"
        // increase duration => increase rbh
    })
    void feeDataAsExpected(
            final String memo,
            @ConvertWith(Ed25519KeyConverter.class) final Key adminJKey,
            @ConvertWith(Ed25519KeyConverter.class) final Key submitJKey,
            @ConvertWith(AccountIDConverter.class) final AccountID autoRenewAccountId,
            @ConvertWith(DurationConverter.class) final Duration autoRenewPeriod,
            final int expectedExtraBpt,
            final int expectedExtraServicesRbh)
            throws InvalidTxBodyException {
        final var txBody =
                makeTransactionBody(
                        memo, adminJKey, submitJKey, autoRenewAccountId, autoRenewPeriod);

        final var feeData = subject.usageGiven(txBody, sigValueObj, view);

        final int expectedNetworkRbh = 2; // Addition rbh for topicId
        checkServicesFee(feeData, expectedExtraServicesRbh);
        checkNetworkFee(feeData, expectedExtraBpt, expectedNetworkRbh);
        checkNodeFee(feeData, expectedExtraBpt);
    }

    private TransactionBody makeTransactionBody(
            @Nullable final String memo,
            final Key adminKey,
            final Key submitKey,
            final AccountID autoRenewAccountId,
            @Nullable final Duration autoRenewPeriod) {
        final var createTopicTxBodyBuilder =
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
