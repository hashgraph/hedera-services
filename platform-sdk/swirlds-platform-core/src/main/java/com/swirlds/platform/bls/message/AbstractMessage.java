/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.bls.message;

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

import com.swirlds.common.system.NodeId;

/** A class representing a protocol message in the abstract */
public abstract class AbstractMessage implements ProtocolMessage {

    /** The id of the message sender */
    private final NodeId senderId;

    /**
     * Constructor
     *
     * @param senderId the id of the sender
     */
    protected AbstractMessage(final NodeId senderId) {
        this.senderId = throwArgNull(senderId, "senderId");
    }

    /** {@inheritDoc} */
    @Override
    public NodeId getSenderId() {
        return senderId;
    }
}
