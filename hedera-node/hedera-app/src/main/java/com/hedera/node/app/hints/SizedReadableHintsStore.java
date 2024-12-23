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

package com.hedera.node.app.hints;

import com.hedera.hapi.node.state.hints.HintsKeySet;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.OptionalLong;

/**
 * Encapsulates information about the hints in state for a certain maximum universe size.
 */
public interface SizedReadableHintsStore {
    /**
     * Returns the next party id, if one exists.
     * @return the next party id, or empty
     */
    OptionalLong nextPartyId();

    /**
     * Returns the party id of the given node id in this universe size, if it has one.
     * @param nodeId the node id
     * @return the party id, or empty
     */
    OptionalLong partyIdOf(long nodeId);

    /**
     * Returns the timestamped hints for the given party id in this universe size, if
     * any exist.
     * @param partyId the party id
     * @return the timestamped hints, or null
     */
    @Nullable
    HintsKeySet hintsForParty(long partyId);

    /**
     * Returns the timestamped hints for the given node id in this universe size, if
     * any exist.
     * @param nodeId the node id
     * @return the timestamped hints, or null
     */
    @Nullable
    default HintsKeySet hintsForNode(long nodeId) {
        final var partyId = partyIdOf(nodeId);
        return partyId.isPresent() ? hintsForParty(partyId.getAsLong()) : null;
    }
}
