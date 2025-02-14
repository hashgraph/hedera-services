// SPDX-License-Identifier: Apache-2.0
package com.swirlds.state.test.fixtures.merkle;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.state.merkle.MerkleStateRoot;

@ConstructableIgnored
public class TestMerkleStateRoot extends MerkleStateRoot<TestMerkleStateRoot> {

    @Override
    protected TestMerkleStateRoot copyingConstructor() {
        return new TestMerkleStateRoot();
    }

    @Override
    public long getCurrentRound() {
        return 0;
    }
}
