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
package com.hedera.services.state.merkle;

import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleTokenRelStatusSerdeTest extends SelfSerializableDataTest<MerkleTokenRelStatus> {
    @Override
    protected Class<MerkleTokenRelStatus> getType() {
        return MerkleTokenRelStatus.class;
    }

    @Override
    protected MerkleTokenRelStatus getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.nextMerkleTokenRelStatus();
    }

    @Override
    protected MerkleTokenRelStatus getExpectedObject(final int version, final int testCaseNo) {
        var expected = super.getExpectedObject(version, testCaseNo);
        if (version < MerkleTokenRelStatus.RELEASE_0250_VERSION) {
            expected.setNext(0);
            expected.setPrev(0);
        }
        return expected;
    }
}
