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

import com.swirlds.platform.hcm.api.pairings.Field;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.signaturescheme.PairingPrivateKey;
import com.swirlds.platform.hcm.api.tss.TssCiphertext;
import com.swirlds.platform.hcm.api.tss.TssPrivateShare;
import com.swirlds.platform.hcm.api.tss.TssShareId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * A TSS ciphertext, as utilized by the Groth21 scheme.
 *
 * @param chunkRandomness  TODO
 * @param shareCiphertexts TODO
 */
public record Groth21Ciphertext(
        @NonNull List<GroupElement> chunkRandomness, @NonNull Map<TssShareId, List<GroupElement>> shareCiphertexts)
        implements TssCiphertext {

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssPrivateShare decryptPrivateShare(
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull final TssShareId shareId,
            @NonNull final ElGamalCache elGamalCache) {

        final List<GroupElement> shareIdChunks = shareCiphertexts.get(shareId);
        if (chunkRandomness.size() != shareIdChunks.size()) {
            throw new IllegalArgumentException("Mismatched chunk randomness count and share chunk count");
        }

        final FieldElement keyElement = elGamalPrivateKey.secretElement();
        final Field keyField = keyElement.getField();
        final FieldElement zeroElement = keyField.zeroElement();

        FieldElement output = zeroElement;
        for (int i = 0; i < shareIdChunks.size(); i++) {
            final GroupElement chunkCiphertext = shareIdChunks.get(i);
            final GroupElement chunkRandomness = this.chunkRandomness.get(i);

            final GroupElement antiMask = chunkRandomness.power(zeroElement.subtract(keyElement));
            final GroupElement commitment = chunkCiphertext.add(antiMask);
            final FieldElement decryptedCommitment = elGamalCache.cacheMap().get(commitment);

            output = output.add(keyField.elementFromLong(elGamalCache.cacheMap().size())
                    .power(BigInteger.valueOf(i))
                    .multiply(decryptedCommitment));
        }

        return new TssPrivateShare(shareId, new PairingPrivateKey(elGamalPrivateKey.signatureSchema(), output));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
