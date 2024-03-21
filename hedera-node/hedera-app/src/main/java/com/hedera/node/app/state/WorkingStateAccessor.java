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

import com.swirlds.platform.state.HederaState;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A singleton class that provides access to the working {@link HederaState}.
 */
@Singleton
public class WorkingStateAccessor {
    private HederaState hederaState = null;

    @Inject
    public WorkingStateAccessor() {
        // Default constructor
    }

    /**
     * Returns the working {@link HederaState}.
     * @return the working {@link HederaState}.
     */
    @Nullable
    public HederaState getHederaState() {
        return hederaState;
    }

    /**
     * Sets the working {@link HederaState}.
     * @param hederaState the working {@link HederaState}.
     */
    public void setHederaState(HederaState hederaState) {
        requireNonNull(hederaState);
        this.hederaState = hederaState;
    }
}
