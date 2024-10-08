package com.hedera.node.app.tss.impl;

import com.hedera.cryptography.pairings.signatures.api.PairingSignature;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssShareSignature;
import com.hedera.node.app.tss.TssLibrary;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PlaceholderTssLibrary implements TssLibrary {
    @NotNull
    @Override
    public TssShareSignature sign(@NotNull TssPrivateShare privateShare, @NotNull byte[] message) {
        return new TssShareSignature(privateShare.shareId(), PairingSignature.fromBytes(message));
    }

    @Override
    public boolean verifySignature(@NotNull TssParticipantDirectory participantDirectory, @NotNull List<TssPublicShare> publicShares, @NotNull TssShareSignature signature) {
        return false;
    }

    @NotNull
    @Override
    public PairingSignature aggregateSignatures(@NotNull List<TssShareSignature> partialSignatures) {
        return partialSignatures.getFirst().signature();
    }
}
