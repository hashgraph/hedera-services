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

package com.hedera.services.bdd.junit.hedera.embedded.fakes;

import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssMessage;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.api.TssPrivateShare;
import com.hedera.node.app.tss.api.TssPublicShare;
import com.hedera.node.app.tss.api.TssShareId;
import com.hedera.node.app.tss.api.TssShareSignature;
import com.hedera.node.app.tss.pairings.FakeFieldElement;
import com.hedera.node.app.tss.pairings.PairingPrivateKey;
import com.hedera.node.app.tss.pairings.PairingPublicKey;
import com.hedera.node.app.tss.pairings.PairingSignature;
import com.hedera.node.app.tss.pairings.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.math.BigInteger;
import java.util.List;

public class FakeTssLibrary implements TssLibrary {
    private static final SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(new byte[] {1});
    private static final PairingPrivateKey PRIVATE_KEY =
            new PairingPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);
    @NonNull
    @Override
    public TssMessage generateTssMessage(@NonNull final TssParticipantDirectory tssParticipantDirectory) {
        return new TssMessage(new byte[0]);
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(
            @NonNull final TssParticipantDirectory tssParticipantDirectory,
            @NonNull final TssPrivateShare privateShare) {
        return new TssMessage(new byte[0]);
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
        return List.of(new TssPrivateShare(new TssShareId(0), PRIVATE_KEY));
    }

    @NonNull
    @Override
    public PairingPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares) {
        return PRIVATE_KEY;
    }

    @NonNull
    @Override
    public List<TssPublicShare> computePublicShares(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> validTssMessages) {
        return List.of();
    }

    @NonNull
    @Override
    public PairingPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares) {
        return PRIVATE_KEY.createPublicKey();
    }

    @NonNull
    @Override
    public TssShareSignature sign(@NonNull final TssPrivateShare privateShare, @NonNull final byte[] message) {
        return null;
    }

    @Override
    public boolean verifySignature(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssPublicShare> publicShares,
            @NonNull final TssShareSignature signature) {
        return false;
    }

    @NonNull
    @Override
    public PairingSignature aggregateSignatures(@NonNull final List<TssShareSignature> partialSignatures) {
        return null;
    }
}
