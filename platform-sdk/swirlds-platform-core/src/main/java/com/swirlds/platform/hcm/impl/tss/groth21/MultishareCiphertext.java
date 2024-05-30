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
import com.swirlds.platform.hcm.api.tss.TssPrivateShare;
import com.swirlds.platform.hcm.api.tss.TssShareId;
import com.swirlds.platform.hcm.impl.internal.ElGamalCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A TSS multishare ciphertext, as utilized by the Groth21 scheme.
 * <p>
 * Contains an encrypted share from an individual secret, to each existing share.
 */
public class MultishareCiphertext {

    /**
     * The randomness used in generating the contained {@link EncryptedShare}s
     */
    private final List<GroupElement> chunkRandomness;

    /**
     * The contained share ciphertexts, in order of the share IDs
     */
    private final List<EncryptedShare> shareCiphertexts;

    /**
     * A map from share ID to index in the {@link #shareCiphertexts} list. Created lazily.
     */
    private Map<TssShareId, Integer> shareIdToIndexMap = null;

    /**
     * Create a multishare ciphertext.
     *
     * @param chunkRandomness  the randomness used in generating the contained {@link EncryptedShare}s
     * @param shareCiphertexts the contained share ciphertexts, in order of the share IDs
     */
    private MultishareCiphertext(
            @NonNull final List<GroupElement> chunkRandomness, @NonNull final List<EncryptedShare> shareCiphertexts) {

        this.chunkRandomness = Objects.requireNonNull(chunkRandomness);
        this.shareCiphertexts = Objects.requireNonNull(shareCiphertexts);
    }

    /**
     * Create a Groth21 multishare ciphertext.
     *
     * @param signatureSchema   the signature schema
     * @param randomness        the randomness to use when generating the share ciphertexts
     * @param unencryptedShares the unencrypted shares to encrypt
     * @return the Groth21 multishare ciphertext
     */
    public static MultishareCiphertext create(
            @NonNull final SignatureSchema signatureSchema,
            @NonNull final List<FieldElement> randomness,
            @NonNull final List<UnencryptedShare> unencryptedShares) {

        final Group publicKeyGroup = signatureSchema.getPublicKeyGroup();
        final GroupElement publicKeyGenerator = publicKeyGroup.getGenerator();

        final List<GroupElement> chunkRandomness = new ArrayList<>();
        for (final FieldElement randomElement : randomness) {
            chunkRandomness.add(publicKeyGenerator.power(randomElement));
        }

        final List<EncryptedShare> shareCiphertexts = new ArrayList<>();
        for (final UnencryptedShare unencryptedShare : unencryptedShares) {
            shareCiphertexts.add(EncryptedShare.create(randomness, unencryptedShare));
        }

        return new MultishareCiphertext(chunkRandomness, shareCiphertexts);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public TssPrivateShare decryptPrivateShare(
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull final TssShareId shareId,
            @NonNull final ElGamalCache elGamalCache) {

        final EncryptedShare shareCiphertext = getEncryptedShare(shareId);
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

    @NonNull
    private Map<TssShareId, Integer> getShareIdToIndexMap() {
        if (shareIdToIndexMap != null) {
            return shareIdToIndexMap;
        }

        shareIdToIndexMap = new HashMap<>();
        for (int i = 0; i < shareCiphertexts.size(); i++) {
            shareIdToIndexMap.put(shareCiphertexts.get(i).shareId(), i);
        }

        return shareIdToIndexMap;
    }

    @NonNull
    private EncryptedShare getEncryptedShare(@NonNull final TssShareId shareId) {
        return shareCiphertexts.get(getShareIdToIndexMap().get(shareId));
    }

    @NonNull
    public List<GroupElement> getChunkRandomness() {
        return chunkRandomness;
    }

    @NonNull
    public List<EncryptedShare> getShareCiphertexts() {
        return shareCiphertexts;
    }

    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
