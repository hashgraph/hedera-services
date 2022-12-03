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
package com.hedera.node.app.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * This test extends the {@link MutableStateBaseTest}, getting all the test methods used there, but
 * this time executed on a {@link WrappedMutableState} rather than on the {@link DummyMutableState}
 * directly.
 */
class WrappedMutableStateTest extends MutableStateBaseTest {
    protected MutableStateBase<String, String> createState(
            @NonNull final Map<String, DummyMerkleNode> merkleMap) {
        final var dummy = new DummyMutableState(STATE_KEY, merkleMap);
        return new WrappedMutableState<>(dummy);
    }

    // Write a few tests to verify that when we commit on the WrappedMutableState, the commit goes
    // into
    // the DummyMutableState, but doesn't yet get into the underlying merkleMap guy
}
