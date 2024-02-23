/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.dispatch.triggers.flow;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.dispatch.types.TriggerTwo;

/**
 * Dispatches when a state has been loaded from disk.
 */
@FunctionalInterface
public interface DiskStateLoadedTrigger extends TriggerTwo<Long, Hash> {

    /**
     * Signal that a state has been loaded from disk.
     *
     * @param round
     * 		the round of the state that was loaded
     * @param stateHash
     * 		the hash of the state that was loaded
     */
    @Override
    void dispatch(Long round, Hash stateHash);
}
