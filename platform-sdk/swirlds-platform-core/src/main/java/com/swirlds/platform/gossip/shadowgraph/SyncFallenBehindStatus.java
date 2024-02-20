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

package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.platform.consensus.NonAncientEventWindow;
import edu.umd.cs.findbugs.annotations.NonNull;

public enum SyncFallenBehindStatus {
    NONE_FALLEN_BEHIND,
    SELF_FALLEN_BEHIND,
    OTHER_FALLEN_BEHIND;

    /**
     * Compute the fallen behind status between ourselves and a peer.
     *
     * @param self  our event window
     * @param other the peer's event window
     * @return the status
     */
    @NonNull
    public static SyncFallenBehindStatus getStatus(
            @NonNull final NonAncientEventWindow self, @NonNull final NonAncientEventWindow other) {
        if (other.getAncientThreshold() < self.getExpiredThreshold()) {
            return OTHER_FALLEN_BEHIND;
        }
        if (self.getAncientThreshold() < other.getExpiredThreshold()) {
            return SELF_FALLEN_BEHIND;
        }
        return NONE_FALLEN_BEHIND;
    }
}
