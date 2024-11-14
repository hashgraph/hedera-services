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

import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

/**
 * Threshold Signature Scheme (TSS) Genesis Stage is the setup stage where all participants in the scheme collaborate to discover a shared polynomial.
 * <p>
 *  Contract:
 * <ul>
 *     <li>Generate a {@link TssMessage} from a random share</li>
 *     <li>Verify {@link TssMessage} with a {@link TssParticipantDirectory}</li>
 *     <li>Obtain a stateful {@link TssShareExtractor} from a {@link TssParticipantDirectory} and a list of <strong>previously verified</strong> {@link TssMessage}s</li>
 * </ul>
 */
public interface TssServiceGenesisStage {

    /**
     * Generate a {@link TssMessage} for a {@code participantDirectory}.
     * This method is used to bootstrap the protocol as it does not need the existence of a previously generated key material.
     *
     * @param participantDirectory the candidate tss directory
     * @return a {@link TssMessage} produced from a random share.
     */
    @NonNull
    TssMessage generateTssMessage(@NonNull TssParticipantDirectory participantDirectory);

    /**
     * Verify that a {@link TssMessage} is valid against the zk proof.
     *
     * @apiNote It is responsibility of callers to make sure the call of this method happens before any processing.
     * @param participantDirectory the candidate directory
     * @param tssMessage the {@link TssMessage} to validate
     * @return true if the message is valid, false otherwise
     */
    boolean verifyTssMessage(@NonNull TssParticipantDirectory participantDirectory, @NonNull TssMessage tssMessage);

    /**
     * Creates a stateful tssShareExtractor that allows to extract:
     * <ul>
     *      <li>all private shares that belongs to this participant</li>
     *      <li>all public shares for all the participants in the scheme.</li>
     * </ul>
     *<p>
     * It is the responsibility of the caller to ensure:
     * <ul>
     *  <li> that the list of processed {@link TssMessage} messages were previously verified</li>
     * </ul>
     *
     * The aggregation of the obtained {@link TssPublicShare}s using {@link TssPublicShare#aggregate(List)}
     *  will produce a new aggregated {@link BlsPublicKey} (known as ledgerId)
     *
     * @apiNote In this case the existence of threshold number of messages is not a requirement but a desired situation.
     * The more participants collaborating to the entropy of the protocol produces better results.
     * Ideally every participant in the scheme collaborates in the generation phase, but if not, one can consider the same threshold as if
     * we were rekeying a committee where every participant has exactly one share.
     *
     * @param participantDirectory the candidate directory
     * @param validMessages a list of <strong>previously verified</strong> {@link TssMessage}s
     * @return a stateful instance of the {@link TssShareExtractor}
     */
    TssShareExtractor shareExtractor(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull List<TssMessage> validMessages);
}
