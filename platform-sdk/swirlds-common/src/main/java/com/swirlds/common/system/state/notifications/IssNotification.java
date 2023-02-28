/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system.state.notifications;

import com.swirlds.common.notification.AbstractNotification;

/**
 * This {@link com.swirlds.common.notification.Notification Notification} is triggered when there is an ISS (i.e. an
 * Invalid State Signature). State is guaranteed to hold a reservation until the callback completes.
 */
public class IssNotification extends AbstractNotification {

    private final long round;

    public enum IssType {
        /**
         * Another node is in disagreement with the consensus hash.
         */
        OTHER_ISS,
        /**
         * This node is in disagreement with the consensus hash.
         */
        SELF_ISS,
        /**
         * There exists no consensus hash because of severe hash disagreement.
         */
        CATASTROPHIC_ISS
    }

    private final IssType issType;
    private final Long otherNodeId;

    /**
     * Create a new ISS notification.
     *
     * @param round       the round when the ISS occurred
     * @param issType     the type of the ISS
     * @param otherNodeId the node with an ISS. If this is a {@link IssType#CATASTROPHIC_ISS} then this is null.
     */
    public IssNotification(final long round, final IssType issType, final Long otherNodeId) {
        this.otherNodeId = otherNodeId;
        this.issType = issType;
        this.round = round;
    }

    /**
     * Get the ID of the node that has an ISS. Null if {@link #getIssType()} does not return {@link IssType#OTHER_ISS}.
     */
    public long getOtherNodeId() {
        return otherNodeId;
    }

    /**
     * Get the round of the ISS.
     */
    public long getRound() {
        return round;
    }

    /**
     * The type of the ISS.
     */
    public IssType getIssType() {
        return issType;
    }
}
