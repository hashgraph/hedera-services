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

import static com.hedera.services.state.merkle.MerkleUniqueToken.RELEASE_0180_VERSION;
import static com.hedera.services.state.merkle.MerkleUniqueToken.RELEASE_0250_VERSION;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.NftNumPair;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;

public class MerkleUniqueTokenSerdeTest extends SelfSerializableDataTest<MerkleUniqueToken> {
    public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected Class<MerkleUniqueToken> getType() {
        return MerkleUniqueToken.class;
    }

    @Override
    protected MerkleUniqueToken getExpectedObject(final SeededPropertySource propertySource) {
        return propertySource.next0260UniqueToken();
    }

    @Override
    protected MerkleUniqueToken getExpectedObject(final int version, final int testCaseNo) {
        final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
        final var seededObject = getExpectedObject(propertySource);
        if (version <= RELEASE_0180_VERSION) {
            seededObject.setSpender(EntityId.MISSING_ENTITY_ID);
        }
        if (version <= RELEASE_0250_VERSION) {
            seededObject.setPrev(NftNumPair.MISSING_NFT_NUM_PAIR);
            seededObject.setNext(NftNumPair.MISSING_NFT_NUM_PAIR);
            return seededObject;
        } else {
            return seededObject;
        }
    }
}
