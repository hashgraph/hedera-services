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

package com.hedera.cryptography.tss.impl;

import com.hedera.cryptography.pairings.signatures.api.PairingPrivateKey;
import com.hedera.cryptography.pairings.signatures.api.PairingPublicKey;
import com.hedera.cryptography.pairings.signatures.api.PairingSignature;
import com.hedera.cryptography.pairings.signatures.api.SignatureSchema;
import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssService;
import com.hedera.cryptography.tss.api.TssShareSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A prototype implementation of the TssService.
 * future-work:Complete this implementation.
 */
public class TssServiceImpl implements TssService {
    private final SignatureSchema signatureSchema;
    private final Random random;

    /**
     * Generates a new instance of this prototype implementation.
     *
     * @param signatureSchema the predefined parameters that define the curve and group selection
     * @param random the RNG
     */
    public TssServiceImpl(@NonNull final SignatureSchema signatureSchema, @NonNull final Random random) {
        this.signatureSchema = Objects.requireNonNull(signatureSchema, "signatureSchema must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(
            @NonNull final TssParticipantDirectory pendingParticipantDirectory,
            @NonNull final TssPrivateShare privateShare) {
        return new TssMessage(new byte[] {});
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(@NonNull final TssParticipantDirectory tssParticipantDirectory) {
        return new TssMessage(new byte[] {});
    }

    @Override
    public boolean verifyTssMessage(
            @NonNull final TssParticipantDirectory participantDirectory, @NonNull final TssMessage tssMessage) {
        return true;
    }

    @NonNull
    @Override
    public List<TssPrivateShare> decryptPrivateShares(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> validTssMessages) {
        return participantDirectory.getCurrentParticipantOwnedShares().stream()
                .map(sid -> new TssPrivateShare(sid, new PairingPrivateKey()))
                .toList();
    }

    @NonNull
    @Override
    public PairingPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares) {
        return PairingPrivateKey.create(this.signatureSchema, this.random);
    }

    @NonNull
    @Override
    public List<TssPublicShare> computePublicShares(
            @NonNull final TssParticipantDirectory participantDirectory, @NonNull final List<TssMessage> tssMessages) {
        return participantDirectory.getShareIds().stream()
                .map(sid -> new TssPublicShare(sid, new PairingPublicKey()))
                .toList();
    }

    @NonNull
    @Override
    public PairingPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares) {
        return new PairingPublicKey();
    }

    @NonNull
    @Override
    public TssShareSignature sign(@NonNull final TssPrivateShare privateShare, @NonNull final byte[] message) {
        return new TssShareSignature(privateShare.shareId(), new PairingSignature());
    }

    @Override
    public boolean verifySignature(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssPublicShare> publicShares,
            @NonNull final TssShareSignature signature) {
        return true;
    }

    @NonNull
    @Override
    public PairingSignature aggregateSignatures(@NonNull final List<TssShareSignature> partialSignatures) {
        return new PairingSignature();
    }
}
