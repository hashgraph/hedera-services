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

package com.swirlds.crypto.tss.impl;

import com.swirlds.crypto.signaturescheme.api.PairingPrivateKey;
import com.swirlds.crypto.signaturescheme.api.PairingPublicKey;
import com.swirlds.crypto.signaturescheme.api.PairingSignature;
import com.swirlds.crypto.tss.TssMessage;
import com.swirlds.crypto.tss.TssParticipantDirectory;
import com.swirlds.crypto.tss.TssPrivateShare;
import com.swirlds.crypto.tss.TssPublicShare;
import com.swirlds.crypto.tss.TssService;
import com.swirlds.crypto.tss.TssShareSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A mock implementation of the TssService.
 */
public class TssServiceImpl implements TssService {

    @NonNull
    @Override
    public TssMessage generateTssMessages(
            @NonNull final TssParticipantDirectory pendingParticipantDirectory,
            @NonNull final TssPrivateShare privateShare) {
        return new TssMessage(new byte[] {});
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(@NonNull final TssParticipantDirectory pendingParticipantDirectory) {
        return new TssMessage(new byte[] {});
    }

    @Override
    public boolean verifyTssMessage(
            @NonNull final TssParticipantDirectory participantDirectory, @NonNull final TssMessage tssMessages) {
        return true;
    }

    @Nullable
    @Override
    public List<TssPrivateShare> decryptPrivateShares(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssMessage> validTssMessages) {
        return participantDirectory.getOwnedShareIds().stream()
                .map(sid -> new TssPrivateShare(sid, new PairingPrivateKey()))
                .toList();
    }

    @NonNull
    @Override
    public PairingPrivateKey aggregatePrivateShares(@NonNull final List<TssPrivateShare> privateShares) {
        return new PairingPrivateKey();
    }

    @Nullable
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
