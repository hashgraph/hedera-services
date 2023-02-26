/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.notifications;

import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.state.signed.SignedState;

/**
 * A {@link com.swirlds.common.notification.Notification Notification} that a signed state fails to collect sufficient
 * signatures before being ejected from memory. State is guaranteed to hold a reservation until callback is finished.
 */
public class StateLacksSignaturesNotification extends AbstractNotification {

    private final SignedState signedState;

    // FUTURE WORK:
    //  this field can be removed once PlatformContext maintains a single notification engine per platform instance
    private final NodeId selfId;

    /**
     * Create a notification for a state that was unable to collect enough signatures.
     *
     * @param signedState the state that just became signed
     * @param selfId      the ID of this node
     */
    public StateLacksSignaturesNotification(final SignedState signedState, final NodeId selfId) {
        this.signedState = signedState;
        this.selfId = selfId;
    }

    /**
     * Get the signed state that was unable to collect sufficient signatures.
     */
    public SignedState getSignedState() {
        return signedState;
    }

    /**
     * The ID of this node.
     */
    public NodeId getSelfId() {
        return selfId;
    }
}
