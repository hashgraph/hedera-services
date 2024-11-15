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

package com.hedera.node.app.tss.cryptography.tss.api;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.tss.cryptography.bls.BlsPrivateKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Represents the private information of the participant executing the protocol.
 * Containing its participantId and the private key to decrypt {@link TssMessage} CipherTexts intended for the {@code participantId}.
 *
 * @param participantId the ID of the {@code participant} owning this directory.
 * @param tssDecryptPrivateKey the key to decrypt {@link TssMessage} CipherTexts intended for the {@code participantId}.
 */
public record TssParticipantPrivateInfo(long participantId, @NonNull BlsPrivateKey tssDecryptPrivateKey) {
    /**
     * Constructor
     *
     * @param participantId identification of the participant
     */
    public TssParticipantPrivateInfo {
        requireNonNull(tssDecryptPrivateKey, "tssDecryptPrivateKey must not be null");
    }

    /**
     * The list of participant's owned shareIds.
     * @param participantDirectory the candidate directory
     * @return the shares owned by the participant {@code participantId}.
     */
    @NonNull
    public List<Integer> ownedShares(@NonNull final TssParticipantDirectory participantDirectory) {
        return Objects.requireNonNull(participantDirectory, "participantDirectory must not be null")
                .ownedShares(this.participantId);
    }
}
