/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.iss;

import com.swirlds.platform.scratchpad.ScratchpadType;

/**
 * Describes the data held in the ISS scratchpad.
 */
public enum IssScratchpad implements ScratchpadType {
    /**
     * The round number of the most recently observed ISS, or null if this node has never observed an ISS.
     */
    LAST_ISS_ROUND(0);

    // FUTURE WORK: store data that allows us to detect when we have attempted to restart from the same state snapshot
    // multiple times without resolving the ISS, and potentially allow nodes to delete some of their states in order
    // to resolve the ISS.

    private final int fieldId;

    /**
     * Constructor.
     *
     * @param fieldId the field ID
     */
    IssScratchpad(final int fieldId) {
        this.fieldId = fieldId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFieldId() {
        return fieldId;
    }
}
