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

package com.hedera.node.app.tss;

import static com.hedera.node.app.tss.handlers.TssUtils.SIGNATURE_SCHEMA;

import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.cryptography.bls.BlsPublicKey;
import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.cryptography.tss.api.TssMessage;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssParticipantPrivateInfo;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssService;
import com.hedera.cryptography.tss.api.TssShareSignature;
import com.hedera.cryptography.tss.impl.Groth21Service;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.SecureRandom;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
// Future: This intermediate class can be deleted and use `TssService` directly in all the places needed.

public class TssLibraryImpl implements TssLibrary {
    private static final BlsPrivateKey AGGREGATED_PRIVATE_KEY =
            BlsPrivateKey.create(SIGNATURE_SCHEMA, new SecureRandom());
    private byte[] message = new byte[0];

    private TssService tssService;
    private SecureRandom random = new SecureRandom();
    private Supplier<NodeInfo> selfNodeInfo;

    @Inject
    public TssLibraryImpl(AppContext appContext) {
        this.tssService = new Groth21Service(SIGNATURE_SCHEMA, random);
        this.selfNodeInfo = appContext.selfNodeInfoSupplier();
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(@NonNull final TssParticipantDirectory tssParticipantDirectory) {
        return tssService.genesisStage().generateTssMessage(tssParticipantDirectory);
    }

    @NonNull
    @Override
    public TssMessage generateTssMessage(
            @NonNull TssParticipantDirectory tssParticipantDirectory, @NonNull TssPrivateShare privateShare) {
        return tssService.rekeyStage().generateTssMessage(tssParticipantDirectory, privateShare);
    }

    @Override
    public boolean verifyTssMessage(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull Bytes tssMessageBytes) {
        try {
            final var tssMessage = tssService.messageFromBytes(participantDirectory, tssMessageBytes.toByteArray());
            return tssService.rekeyStage().verifyTssMessage(participantDirectory, null, tssMessage);
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    @Override
    public List<TssPrivateShare> decryptPrivateShares(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull List<TssMessage> validTssMessages) {
        // TODO: How to get the BlsPrivateKey here
        final var tssPrivateInfo =
                new TssParticipantPrivateInfo(selfNodeInfo.get().nodeId(), AGGREGATED_PRIVATE_KEY);
        return tssService
                .rekeyStage()
                .shareExtractor(participantDirectory, validTssMessages)
                .ownedPrivateShares(tssPrivateInfo);
    }

    @NonNull
    @Override
    public List<TssPublicShare> computePublicShares(
            @NonNull TssParticipantDirectory participantDirectory, @NonNull List<TssMessage> validTssMessages) {
        return tssService
                .rekeyStage()
                .shareExtractor(participantDirectory, validTssMessages)
                .allPublicShares();
    }

    @NonNull
    @Override
    public BlsPublicKey aggregatePublicShares(@NonNull List<TssPublicShare> publicShares) {
        return TssPublicShare.aggregate(publicShares);
    }

    @NonNull
    @Override
    public BlsSignature aggregateSignatures(@NonNull List<TssShareSignature> partialSignatures) {
        return TssShareSignature.aggregate(partialSignatures);
    }

    @NonNull
    @Override
    public TssShareSignature sign(@NonNull TssPrivateShare privateShare, @NonNull byte[] message) {
        return privateShare.sign(message);
    }

    @Override
    public boolean verifySignature(
            @NonNull final TssParticipantDirectory participantDirectory,
            @NonNull final List<TssPublicShare> publicShares,
            @NonNull final TssShareSignature signature) {
        // shareIds are starting from 1. So, when looking up in publicShares list, we
        // need to subtract 1
        return signature.verify(publicShares.get(signature.shareId() - 1), message);
    }

    @Override
    public TssMessage getTssMessageFromBytes(Bytes tssMessage, TssParticipantDirectory participantDirectory) {
        try {
            return tssService.messageFromBytes(participantDirectory, tssMessage.toByteArray());
        } catch (Exception e) {
            return null;
        }
    }
}
