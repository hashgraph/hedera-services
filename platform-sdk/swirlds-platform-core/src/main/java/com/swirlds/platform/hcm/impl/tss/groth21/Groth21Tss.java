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

package com.swirlds.platform.hcm.impl.tss.groth21;

import com.swirlds.platform.hcm.api.signaturescheme.PairingPublicKey;
import com.swirlds.platform.hcm.api.signaturescheme.PairingSignature;
import com.swirlds.platform.hcm.api.tss.Tss;
import com.swirlds.platform.hcm.api.tss.TssMessage;
import com.swirlds.platform.hcm.api.tss.TssPrivateKey;
import com.swirlds.platform.hcm.api.tss.TssPrivateShare;
import com.swirlds.platform.hcm.api.tss.TssPublicShare;
import com.swirlds.platform.hcm.api.tss.TssShareClaim;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A Groth21 implementation of a Threshold Signature Scheme.
 */
public class Groth21Tss implements Tss {
    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PairingSignature aggregateSignatures(@NonNull final List<PairingSignature> partialSignatures) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PairingPublicKey aggregatePublicShares(@NonNull final List<TssPublicShare> publicShares) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public TssPrivateKey aggregatePrivateKeys(@NonNull final List<TssPrivateKey> privateKeys) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssMessage generateTssMessage(
            @NonNull final List<TssShareClaim> pendingShareClaims,
            @NonNull final TssPrivateShare privateShare,
            final int threshold) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
