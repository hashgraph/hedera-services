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
import com.swirlds.platform.bls.protocol.CrsProtocol;
import com.swirlds.platform.bls.protocol.RandomGroupElements;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * The message type sent in the first {@link CrsProtocol} round
 *
 * <p>Contains a commitment from the sender
 */
public class CommitmentMessage extends AbstractBlsProtocolMessage {

    /** A byte array representing a commitment */
    @NonNull
    private final byte[] commitment;

    /**
     * Constructor which calculates the commitment
     *
     * @param senderId            the id of the sender
     * @param randomGroupElements the elements being committed to
     * @param digest              the digest used to generate the commitment
     */
    public CommitmentMessage(
            @NonNull final NodeId senderId,
            @NonNull final RandomGroupElements randomGroupElements,
            @NonNull final MessageDigest digest) {

        super(senderId);

        Objects.requireNonNull(randomGroupElements, "randomGroupElements must not be null");
        Objects.requireNonNull(digest, "digest must not be null");

        this.commitment = randomGroupElements.commit(digest);
    }

    /**
     * Constructor for when commitment has already been calculated
     *
     * @param senderId   the id of the sender
     * @param commitment a byte array representing the commitment the message will contain
     * @param digest     the digest used to generate the commitment
     */
    public CommitmentMessage(
            @NonNull final NodeId senderId, @NonNull final byte[] commitment, @NonNull final MessageDigest digest) {

        super(senderId);

        Objects.requireNonNull(commitment, "commitment must not be null");
        Objects.requireNonNull(digest, "digest must not be null");

        if (commitment.length != digest.getDigestLength()) {
            throw new IllegalArgumentException(
                    String.format("commitment must be of length %s", digest.getDigestLength()));
        }

        this.commitment = commitment;
    }

    /**
     * Gets the byte array representing the commitment contained in the message
     *
     * @return the commitment
     */
    @NonNull
    public byte[] getCommitment() {
        return commitment;
    }
}
