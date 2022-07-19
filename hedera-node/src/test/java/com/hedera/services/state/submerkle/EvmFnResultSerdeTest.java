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

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class EvmFnResultSerdeTest extends SelfSerializableDataTest<EvmFnResult> {
    public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected Class<EvmFnResult> getType() {
        return EvmFnResult.class;
    }

    @Override
    protected int getNumTestCasesFor(final int version) {
        return version == EvmFnResult.RELEASE_0240_VERSION
                ? MIN_TEST_CASES_PER_VERSION
                : NUM_TEST_CASES;
    }

    @Override
    protected EvmFnResult getExpectedObject(final int version, final int testCaseNo) {
        final var seeded = SeededPropertySource.forSerdeTest(version, testCaseNo).nextEvmResult();
        if (version < EvmFnResult.RELEASE_0250_VERSION) {
            // Always empty before 0.25
            seeded.setGas(0);
            seeded.setAmount(0);
            seeded.setFunctionParameters(EvmFnResult.EMPTY);
        }
        if (version < EvmFnResult.RELEASE_0260_VERSION) {
            // Always empty before 0.26
            seeded.setSenderId(null);
        }
        return seeded;
    }

    @Override
    protected EvmFnResult getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextEvmResult();
    }
}
