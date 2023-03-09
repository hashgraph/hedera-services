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
import com.swirlds.platform.bls.protocol.CrsProtocol;

/**
 * The message type sent in the first {@link CrsProtocol} round
 *
 * <p>Contains a commitment from the sender
 */
public class CommitmentMessage extends AbstractMessage {
    public static final int SHA256_DIGEST_SIZE = 32;

    /** A byte array representing a commitment */
    private final byte[] commitment;

    /**
     * Constructor
     *
     * @param senderId the id of the sender
     * @param commitment a byte array representing the commitment the message will contain
     */
    public CommitmentMessage(final NodeId senderId, final byte[] commitment) {

        super(senderId);

        throwArgNull(commitment, "commitment");

        if (commitment.length != SHA256_DIGEST_SIZE) {
            throw new IllegalArgumentException(String.format("commitment must be of length %s", SHA256_DIGEST_SIZE));
        }

        this.commitment = commitment;
    }

    /**
     * Gets the byte array representing the commitment contained in the message
     *
     * @return the commitment
     */
    public byte[] getCommitment() {
        return commitment;
    }
}
