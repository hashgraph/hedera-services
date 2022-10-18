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

public class FcAssessedCustomFeeSerdeTest extends SelfSerializableDataTest<FcAssessedCustomFee> {
    @Override
    protected Class<FcAssessedCustomFee> getType() {
        return FcAssessedCustomFee.class;
    }

    @Override
    protected FcAssessedCustomFee getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextAssessedFee();
    }

    @Override
    protected FcAssessedCustomFee getExpectedObject(final int version, final int testCaseNo) {
        final var result = super.getExpectedObject(version, testCaseNo);
        if (version < FcAssessedCustomFee.RELEASE_0171_VERSION) {
            // Need to drop the last field.
            return new FcAssessedCustomFee(
                    result.account(),
                    result.token(),
                    result.units(),
                    FcAssessedCustomFee.UNKNOWN_EFFECTIVE_PAYER_ACCOUNT_NUMS);
        }
        return result;
    }
}
