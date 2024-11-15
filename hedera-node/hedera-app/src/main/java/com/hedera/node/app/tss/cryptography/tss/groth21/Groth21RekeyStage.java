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

import com.hedera.node.app.tss.cryptography.bls.SignatureSchema;
import com.hedera.node.app.tss.cryptography.tss.api.TssMessage;
import com.hedera.node.app.tss.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.cryptography.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.cryptography.tss.api.TssPublicShare;
import com.hedera.node.app.tss.cryptography.tss.api.TssServiceRekeyStage;
import com.hedera.node.app.tss.cryptography.tss.api.TssShareExtractor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Random;

/**
 *  The rekey stage of a Threshold Signature Scheme based on Groth21 implementation.
 *  In this stage, the generated {@link TssMessage} is based on previous material.
 *  When extracting keys, the aggregation rules for {@link TssPrivateShare} and {@link TssPrivateShare} follows {@link com.hedera.node.app.tss.cryptography.tss.extensions.Lagrange} interpolation
 *  and the list of {@link TssPublicShare} obtained aggregate to the previously obtained ledgerId
 * @see TssServiceRekeyStage
 */
public class Groth21RekeyStage extends Groth21Stage implements TssServiceRekeyStage {

    /**
     * Constructor
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param random a source of randomness
     */
    public Groth21RekeyStage(@NonNull final SignatureSchema signatureSchema, @NonNull final Random random) {
        super(signatureSchema, random);
    }

    /**
     * {@inheritDoc}
     * When extracting keys, the aggregation rules for {@link TssPrivateShare} and {@link TssPrivateShare} follows {@link com.hedera.node.app.tss.cryptography.tss.extensions.Lagrange} interpolation
     *  and the list of {@link TssPublicShare} obtained aggregate to the previously obtained ledgerId
     */
    @Override
    @NonNull
    public TssShareExtractor shareExtractor(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> validTssMessages) {
        final KeyExtractionHelper<TssPrivateShare, TssPublicShare> helper = new KeyExtractionHelper<>(
                TssPrivateShare::new,
                TssPrivateShare::aggregate, // The rekey aggregation is based in Lagrange interpolation
                TssPublicShare::new,
                TssPublicShare::aggregate); // The rekey aggregation is based in Lagrange interpolation
        return new Groth21ShareExtractor<>(
                signatureSchema, fromTssMessages(validTssMessages), participantDirectory, helper);
    }
}
