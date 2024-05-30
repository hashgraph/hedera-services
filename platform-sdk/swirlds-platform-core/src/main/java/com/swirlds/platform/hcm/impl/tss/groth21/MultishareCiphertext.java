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

import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.signaturescheme.SignatureSchema;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * A TSS multishare ciphertext, as utilized by the Groth21 scheme.
 * <p>
 * Contains an encrypted share from an individual secret, to each existing share.
 *
 * @param chunkRandomness  the randomness used in generating the contained {@link EncryptedShare}s
 * @param shareCiphertexts the contained share ciphertexts, in order of the share IDs
 */
public record MultishareCiphertext(
        @NonNull List<GroupElement> chunkRandomness, @NonNull List<EncryptedShare> shareCiphertexts) {

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
            chunkRandomness.add(publicKeyGenerator.multiply(randomElement));
        }

        final List<EncryptedShare> shareCiphertexts = new ArrayList<>();
        for (final UnencryptedShare unencryptedShare : unencryptedShares) {
            shareCiphertexts.add(EncryptedShare.create(randomness, unencryptedShare));
        }

        return new MultishareCiphertext(chunkRandomness, shareCiphertexts);
    }

    public byte[] toBytes() {
        throw new UnsupportedOperationException("Not implemented");
    }
}
