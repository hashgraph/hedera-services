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

package com.hedera.node.app.tss.cryptography.tss.groth21;

import com.hedera.node.app.tss.cryptography.bls.BlsPrivateKey;
import com.hedera.node.app.tss.cryptography.bls.BlsPublicKey;
import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.tss.api.TssMessage;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.cryptography.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.cryptography.tss.api.TssPublicShare;
import com.hedera.node.app.tss.cryptography.tss.api.TssServiceGenesisStage;
import com.hedera.node.app.tss.cryptography.tss.api.TssShareExtractor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Random;

/**
 *  The genesis stage of Threshold Signature Scheme based on Groth21 implementation.
 *  In this stage, given the lack of previous material, the generation of {@link TssMessage} is based on random information,
 *  and the aggregation rules for {@link TssPrivateShare} and {@link TssPrivateShare} follows bls addition.
 * @see TssServiceGenesisStage
 */
public class Groth21GenesisStage extends Groth21Stage implements TssServiceGenesisStage {

    /**
     * Constructor
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param random a source of randomness
     */
    public Groth21GenesisStage(@NonNull final SignatureSchema signatureSchema, @NonNull final Random random) {
        super(signatureSchema, random);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean verifyTssMessage(
            @NonNull final TssParticipantDirectory tssTargetParticipantDirectory,
            @NonNull final TssMessage tssMessage) {
        return super.verifyTssMessage(tssTargetParticipantDirectory, null, tssMessage);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssMessage generateTssMessage(@NonNull final TssParticipantDirectory participantDirectory) {
        final TssPrivateShare randomTssPrivateShare = // In genesis, we don't have an originating shareId
                new TssPrivateShare(-1, BlsPrivateKey.create(signatureSchema, random));
        return generateTssMessage(participantDirectory, randomTssPrivateShare);
    }

    /**
     * {@inheritDoc}
     * Aggregation by using {@link TssPublicShare#aggregate(List)} of the returned {@link TssPublicShare}s of this Extractor,
     *  will produce a new aggregated {@link BlsPublicKey} (known as ledgerId)
     */
    @Override
    @NonNull
    public TssShareExtractor shareExtractor(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> validTssMessages) {
        final KeyExtractionHelper<BlsPrivateKey, BlsPublicKey> helper = new KeyExtractionHelper<>(
                (i, s) -> s, // Just return the same key, in genesis we do bls aggregate
                BlsPrivateKey::aggregate,
                (i, p) -> p, // Just return the same key, in genesis we do bls aggregate
                BlsPublicKey::aggregate);
        return new Groth21ShareExtractor<>(
                signatureSchema, fromTssMessages(validTssMessages), participantDirectory, helper);
    }
}
