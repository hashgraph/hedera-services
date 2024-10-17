/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import com.hedera.hapi.node.state.roster.Roster;

/**
 * Contains information about a signed state. A SignedStateInfo object is still ok to read after the parent SignedState
 * object has been deleted.
 */
public interface SignedStateInfo {

    /**
     * The round of the state.
     *
     * @return the round number
     */
    long getRound();

    /**
     * Return the set of signatures collected so far for the hash of this SignedState. This includes the signature by
     * self.
     *
     * @return the set of signatures
     */
    SigSet getSigSet();

    /**
     * Check if this object contains a complete set of signatures with respect to an address book.
     * <p>
     * Note that there is a special edge case during emergency state recovery. A state with a root hash that matches the
     * current epoch hash is considered to be complete regardless of the signatures it has collected.
     *
     * @return does this contain signatures from members with greater than 2/3 of the total weight?
     */
    boolean isComplete();

    Roster getRoster();
}
