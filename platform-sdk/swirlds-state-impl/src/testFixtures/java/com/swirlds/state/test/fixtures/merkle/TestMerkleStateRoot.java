/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.test.fixtures.merkle;

import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.spi.ReadableStates;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

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

    // methods below will be removed -- added for helping to solve compile issues

    @NonNull
    @Override
    public ReadableStates getReadableStates(@NonNull String serviceName) {
        return null;
    }

    @NonNull
    @Override
    public WritableStates getWritableStates(@NonNull String serviceName) {
        return null;
    }
}
