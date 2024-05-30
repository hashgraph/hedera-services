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
import com.swirlds.platform.hcm.api.tss.TssShareId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * A ciphertext corresponding to a share.
 *
 * @param shareId            the share ID
 * @param ciphertextElements the group elements that comprise the ciphertext
 */
public record EncryptedShare(@NonNull TssShareId shareId, @NonNull List<GroupElement> ciphertextElements) {
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

        return new EncryptedShare(unencryptedShare.shareClaim().shareId(), encryptedShareElements);
    }
}
