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
import com.swirlds.platform.hcm.impl.internal.ElGamalCache;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * A ciphertext corresponding to a share.
 *
 * @param ciphertextElements the group elements that comprise the ciphertext
 */
public record EncryptedShare(@NonNull List<GroupElement> ciphertextElements) {
    /**
     * Creates a share ciphertext.
     *
     * @param randomness       the random field elements to use during ciphertext generation
     * @param unencryptedShare the unencrypted share to encrypt
     * @return the share ciphertext
     */
    public static EncryptedShare create(
            @NonNull final List<FieldElement> randomness, @NonNull final UnencryptedShare unencryptedShare) {

        if (randomness.isEmpty()) {
            throw new IllegalArgumentException("Randomness must have at least one element");
        }

        final Field field = randomness.getFirst().getField();
        final GroupElement publicKeyElement =
                unencryptedShare.shareClaim().publicKey().keyElement();
        final Group group = publicKeyElement.getGroup();
        final GroupElement generator = group.getGenerator();

        final byte[] shareBytes = unencryptedShare.shareElement().toBytes();

        final List<GroupElement> encryptedShareElements = new ArrayList<>();
        for (int i = 0; i < shareBytes.length; i++) {
            final FieldElement randomnessElement = randomness.get(i);
            final FieldElement byteElement = field.elementFromLong(shareBytes[i]);

            // TODO: is `add` the correct operation, or should it be `multiply`?
            encryptedShareElements.add(publicKeyElement.power(randomnessElement).add(generator.power(byteElement)));
        }

        return new EncryptedShare(encryptedShareElements);
    }

    /**
     * Decrypts the share ciphertext.
     *
     * @param elGamalPrivateKey the ElGamal private key to use for decryption
     * @param elGamalCache      the ElGamal cache to use for decryption
     * @param randomness        the randomness used during encryption
     * @return the decrypted share
     */
    @NonNull
    public PairingPrivateKey decryptPrivateKey(
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull final ElGamalCache elGamalCache,
            @NonNull final List<GroupElement> randomness) {

        if (randomness.size() != ciphertextElements.size()) {
            throw new IllegalArgumentException("Mismatched chunk randomness count and ciphertext chunk count");
        }

        final FieldElement keyElement = elGamalPrivateKey.secretElement();
        final Field keyField = keyElement.getField();
        final FieldElement zeroElement = keyField.zeroElement();

        FieldElement output = zeroElement;
        for (int i = 0; i < ciphertextElements.size(); i++) {
            final GroupElement chunkCiphertext = ciphertextElements.get(i);
            final GroupElement chunkRandomness = randomness.get(i);

            final GroupElement antiMask = chunkRandomness.power(zeroElement.subtract(keyElement));
            final GroupElement commitment = chunkCiphertext.add(antiMask);
            final FieldElement decryptedCommitment = elGamalCache.cacheMap().get(commitment);

            output = output.add(keyField.elementFromLong(elGamalCache.cacheMap().size())
                    .power(BigInteger.valueOf(i))
                    .multiply(decryptedCommitment));
        }

        return new PairingPrivateKey(elGamalPrivateKey.signatureSchema(), output);
    }
}
