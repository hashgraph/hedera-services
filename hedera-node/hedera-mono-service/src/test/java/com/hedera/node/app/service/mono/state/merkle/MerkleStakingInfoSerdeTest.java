/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.merkle;

import com.hedera.node.app.service.mono.state.migration.StakingNodeInfoStateTranslator;
import com.hedera.test.serde.EqualityType;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;
import edu.umd.cs.findbugs.annotations.NonNull;

public class MerkleStakingInfoSerdeTest extends SelfSerializableDataTest<MerkleStakingInfo> {
    public static final int NUM_TEST_CASES = 2 * MIN_TEST_CASES_PER_VERSION;

    @Override
    protected Class<MerkleStakingInfo> getType() {
        return MerkleStakingInfo.class;
    }

    @Override
    protected int getNumTestCasesFor(final int version) {
        return NUM_TEST_CASES;
    }

    @Override
    protected MerkleStakingInfo getExpectedObject(SeededPropertySource propertySource) {
        return propertySource.next0371StakingInfo();
    }

    @Override
    protected MerkleStakingInfo getExpectedObject(
            final int version, final int testCaseNo, @NonNull final EqualityType equalityType) {
        final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
        final var seededObject = (version < MerkleStakingInfo.RELEASE_0371_VERSION
                ? propertySource.next0370StakingInfo()
                : propertySource.next0371StakingInfo());
        final var pbjStaking = StakingNodeInfoStateTranslator.stakingInfoFromMerkleStakingInfo(seededObject);
        final var merkleStakingInfo = StakingNodeInfoStateTranslator.merkleStakingInfoFromStakingNodeInfo(pbjStaking);
        return merkleStakingInfo;
    }
}
