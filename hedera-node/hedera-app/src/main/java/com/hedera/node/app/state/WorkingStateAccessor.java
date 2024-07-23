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

package com.hedera.node.app.state;

import static java.util.Objects.requireNonNull;

import com.swirlds.state.MerkleState;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A singleton class that provides access to the working {@link MerkleState}.
 */
@Singleton
public class WorkingStateAccessor {
    private MerkleState merkleState = null;

    @Inject
    public WorkingStateAccessor() {
        // Default constructor
    }

    /**
     * Returns the working {@link MerkleState}.
     * @return the working {@link MerkleState}.
     */
    @Nullable
    public MerkleState getHederaState() {
        return merkleState;
    }

    /**
     * Sets the working {@link MerkleState}.
     * @param merkleState the working {@link MerkleState}.
     */
    public void setHederaState(MerkleState merkleState) {
        requireNonNull(merkleState);
        this.merkleState = merkleState;
    }
}
