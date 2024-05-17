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

package com.swirlds.platform.tss.groth21;

import com.swirlds.platform.tss.Tss;
import com.swirlds.platform.tss.TssMessage;
import com.swirlds.platform.tss.TssPrivateKey;
import com.swirlds.platform.tss.TssPrivateShare;
import com.swirlds.platform.tss.TssPublicShare;
import com.swirlds.platform.tss.TssShareClaim;
import com.swirlds.platform.tss.signing.PublicKey;
import com.swirlds.platform.tss.signing.Signature;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

/**
 * A Groth21 implementation of a Threshold Signature Scheme.
 */
public class Groth21Tss<P extends PublicKey> implements Tss<P> {
    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public Signature<P> aggregateSignatures(@NonNull final List<Signature<P>> partialSignatures) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public P aggregatePublicShares(@NonNull final List<TssPublicShare<P>> publicShares) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public TssPrivateKey<P> aggregatePrivateKeys(@NonNull final List<TssPrivateKey<P>> privateKeys) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssMessage<P> generateTssMessage(
            @NonNull final List<TssShareClaim> pendingShareClaims,
            @NonNull final TssPrivateShare<P> privateShare,
            final int threshold) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
