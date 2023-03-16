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

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/** A class representing a BLS protocol message in the abstract */
public abstract class AbstractBlsProtocolMessage implements BlsProtocolMessage {

    /** The id of the message sender */
    @NonNull
    private final NodeId senderId;

    /**
     * Constructor
     *
     * @param senderId the id of the sender
     */
    protected AbstractBlsProtocolMessage(@NonNull final NodeId senderId) {
        this.senderId = Objects.requireNonNull(senderId, "senderId must not be null");
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public NodeId getSenderId() {
        return senderId;
    }
}
