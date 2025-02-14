// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state.notifications;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.notification.AbstractNotification;
import com.swirlds.common.platform.NodeId;

/**
 * A {@link com.swirlds.common.notification.Notification Notification} that a signed state been self signed. State is
 * guaranteed to hold a reservation until callback is finished.
 */
public class StateSelfSignedNotification extends AbstractNotification {

    private final long round;
    private final Signature selfSignature;

    private final Hash stateHash;

    // FUTURE WORK:
    //  this field can be removed once PlatformContext maintains a single notification engine per platform instance
    private final NodeId selfId;

    /**
     * Create a notification for state that was signed by this node.
     *
     * @param round         the round of the state that was signed
     * @param selfSignature this node's signature on the state
     * @param selfId        the ID of this node
     */
    public StateSelfSignedNotification(
            final long round, final Signature selfSignature, final Hash stateHash, final NodeId selfId) {
        this.round = round;
        this.selfSignature = selfSignature;
        this.selfId = selfId;
        this.stateHash = stateHash;
    }

    /**
     * The round of the state that was signed.
     */
    public long getRound() {
        return round;
    }

    /**
     * Get this node's signature on the state.
     */
    public Signature getSelfSignature() {
        return selfSignature;
    }

    /**
     * Get the hash of the state that was signed.
     */
    public Hash getStateHash() {
        return stateHash;
    }

    /**
     * The ID of this node.
     */
    public NodeId getSelfId() {
        return selfId;
    }
}
