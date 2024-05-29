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
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.signaturescheme.PairingPrivateKey;
import com.swirlds.platform.hcm.api.signaturescheme.SignatureSchema;
import com.swirlds.platform.hcm.api.tss.TssMultishareCiphertext;
import com.swirlds.platform.hcm.api.tss.TssPrivateShare;
import com.swirlds.platform.hcm.api.tss.TssShareId;
import com.swirlds.platform.hcm.impl.internal.ElGamalCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A TSS ciphertext, as utilized by the Groth21 scheme.
 * <p>
 * Contains an encrypted share from an individual secret, to each existing share.
 *
 * @param chunkRandomness  the randomness used in generating the contained {@link Groth21ShareCiphertext}s
 * @param shareCiphertexts a map from {@link TssShareId}, to the corresponding {@link Groth21ShareCiphertext}
 */
public record Groth21MultishareCiphertext(
        @NonNull List<GroupElement> chunkRandomness, @NonNull Map<TssShareId, Groth21ShareCiphertext> shareCiphertexts)
        implements TssMultishareCiphertext {

    /**
     * Create a Groth21 multishare ciphertext.
     *
     * @param signatureSchema   the signature schema
     * @param randomness        the randomness to use when generating the share ciphertexts
     * @param unencryptedShares the unencrypted shares to encrypt
     * @return the Groth21 multishare ciphertext
     */
    public static Groth21MultishareCiphertext create(
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final List<FieldElement> randomness,
            @NonNull final List<Groth21UnencryptedShare> unencryptedShares) {

        final Group publicKeyGroup = signatureSchema.getPublicKeyGroup();
        final GroupElement publicKeyGenerator = publicKeyGroup.getGenerator();

        final List<GroupElement> chunkRandomness = new ArrayList<>();
        for (final FieldElement randomElement : randomness) {
            chunkRandomness.add(publicKeyGenerator.power(randomElement));
        }

        final Map<TssShareId, Groth21ShareCiphertext> shareCiphertexts = new HashMap<>();
        for (final Groth21UnencryptedShare unencryptedShare : unencryptedShares) {
            shareCiphertexts.put(
                    unencryptedShare.shareClaim().shareId(),
                    Groth21ShareCiphertext.create(randomness, unencryptedShare));
        }

        return new Groth21MultishareCiphertext(chunkRandomness, shareCiphertexts);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TssPrivateShare decryptPrivateShare(
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull final TssShareId shareId,
            @NonNull final ElGamalCache elGamalCache) {

        final Groth21ShareCiphertext shareCiphertext = shareCiphertexts.get(shareId);
        final List<GroupElement> ciphertextChunks = shareCiphertext.ciphertextElements();
        final int shareCiphertextSize = ciphertextChunks.size();

        if (chunkRandomness.size() != shareCiphertextSize) {
            throw new IllegalArgumentException("Mismatched chunk randomness count and ciphertext chunk count");
        }

        final FieldElement keyElement = elGamalPrivateKey.secretElement();
        final Field keyField = keyElement.getField();
        final FieldElement zeroElement = keyField.zeroElement();

        FieldElement output = zeroElement;
        for (int i = 0; i < shareCiphertextSize; i++) {
            final GroupElement chunkCiphertext = ciphertextChunks.get(i);
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
