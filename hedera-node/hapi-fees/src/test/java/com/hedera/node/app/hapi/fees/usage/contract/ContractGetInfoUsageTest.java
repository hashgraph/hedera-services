/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.fees.usage.contract;

import static com.hedera.node.app.hapi.fees.usage.SingletonEstimatorUtils.ESTIMATOR_UTILS;
import static com.hedera.node.app.hapi.fees.usage.contract.entities.ContractEntitySizes.CONTRACT_ENTITY_SIZES;
import static com.hedera.node.app.hapi.fees.usage.crypto.entities.CryptoEntitySizes.CRYPTO_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.getAccountKeyStorageSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.hapi.fees.test.KeyUtils;
import com.hederahashgraph.api.proto.java.ContractGetInfoQuery;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import org.junit.jupiter.api.Test;

class ContractGetInfoUsageTest {
    private final Query query =
            Query.newBuilder()
                    .setContractGetInfo(ContractGetInfoQuery.getDefaultInstance())
                    .build();

    private static final int NUM_TOKEN_ASSOCS = 3;
    private static final Key KEY = KeyUtils.A_CONTRACT_KEY;
    private static final String MEMO = "Hey there!";

    private ContractGetInfoUsage subject;

    @Test
    void getsExpectedUsage() {
        final var expectedTb = BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE;
        final var expectedRb =
                BASIC_QUERY_RES_HEADER
                        + NUM_TOKEN_ASSOCS * CRYPTO_ENTITY_SIZES.bytesInTokenAssocRepr()
                        + CONTRACT_ENTITY_SIZES.fixedBytesInContractRepr()
                        + getAccountKeyStorageSize(KEY)
                        + MEMO.length();
        final var usage = FeeComponents.newBuilder().setBpt(expectedTb).setBpr(expectedRb).build();
        final var expected = ESTIMATOR_UTILS.withDefaultQueryPartitioning(usage);

        subject = ContractGetInfoUsage.newEstimate(query);

        final var actual =
                subject.givenCurrentKey(KEY)
                        .givenCurrentMemo(MEMO)
                        .givenCurrentTokenAssocs(NUM_TOKEN_ASSOCS)
                        .get();

        assertEquals(expected, actual);
    }
}
