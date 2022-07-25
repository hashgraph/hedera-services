/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.ExpirableTxnRecord.RELEASE_0260_VERSION;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.RELEASE_0270_VERSION;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.RELEASE_0280_VERSION;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.serde.SerializedForms;
import com.hedera.test.utils.SeededPropertySource;

public class ExpirableTxnRecordSerdeTest extends SelfSerializableDataTest<ExpirableTxnRecord> {
    public static final int NUM_TEST_CASES = 4 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected Class<ExpirableTxnRecord> getType() {
        return ExpirableTxnRecord.class;
    }

    @Override
    protected int getNumTestCasesFor(int version) {
        return NUM_TEST_CASES;
    }

    @Override
    protected byte[] getSerializedForm(final int version, final int testCaseNo) {
        return SerializedForms.loadForm(ExpirableTxnRecord.class, version, testCaseNo);
    }

    @Override
    protected ExpirableTxnRecord getExpectedObject(final int version, final int testCaseNo) {
        final var seeded = SeededPropertySource.forSerdeTest(version, testCaseNo).nextRecord();
        if (version < RELEASE_0260_VERSION) {
            // Ethereum hash added in release 0.26
            seeded.setEthereumHash(ExpirableTxnRecord.MISSING_ETHEREUM_HASH);
            // sender ID add in release 0.26
            if (seeded.getContractCallResult() != null) {
                seeded.getContractCallResult().setSenderId(null);
            }
            if (seeded.getContractCreateResult() != null) {
                seeded.getContractCreateResult().setSenderId(null);
            }
        }
        if (version < RELEASE_0270_VERSION) {
            seeded.clearStakingRewardsPaid();
        }
        if (version < RELEASE_0280_VERSION) {
            seeded.clearPrngData();
        }
        return seeded;
    }

    @Override
    protected ExpirableTxnRecord getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextRecord();
    }
}
