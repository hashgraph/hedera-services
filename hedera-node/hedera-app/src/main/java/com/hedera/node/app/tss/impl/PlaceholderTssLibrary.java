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

package com.hedera.node.app.tss.impl;

import com.hedera.cryptography.pairings.signatures.api.PairingSignature;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.cryptography.tss.api.TssShareSignature;
import com.hedera.node.app.tss.TssLibrary;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class PlaceholderTssLibrary implements TssLibrary {
    @NotNull
    @Override
    public TssShareSignature sign(@NotNull TssPrivateShare privateShare, @NotNull byte[] message) {
        return new TssShareSignature(privateShare.shareId(), PairingSignature.fromBytes(message));
    }

    @Override
    public boolean verifySignature(
            @NotNull TssParticipantDirectory participantDirectory,
            @NotNull List<TssPublicShare> publicShares,
            @NotNull TssShareSignature signature) {
        return false;
    }

    @NotNull
    @Override
    public PairingSignature aggregateSignatures(@NotNull List<TssShareSignature> partialSignatures) {
        return partialSignatures.getFirst().signature();
    }
}
