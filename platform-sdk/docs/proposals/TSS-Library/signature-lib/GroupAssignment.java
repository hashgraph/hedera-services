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

package com.hedera.cryptography.signaturescheme.api;

import com.hedera.cryptography.pairings.api.BilinearPairing;
import com.hedera.cryptography.pairings.api.Group;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;

/**
 * An enum to clarify which group public keys and signatures are in, for a given
 * {@link SignatureSchema SignatureSchema}
 */
public enum GroupAssignment {
    /**
     * The group for signatures is the first group in the pairing, and the group for public keys is the second group.
     */
    GROUP1_FOR_SIGNING(BilinearPairing::getGroup1, BilinearPairing::getGroup2),
    /**
     * The group for signatures is the second group in the pairing, and the group for public keys is the first group.
     */
    GROUP1_FOR_PUBLIC_KEY(BilinearPairing::getGroup2, BilinearPairing::getGroup1);

    private final Function<BilinearPairing, Group> signatureGroup;
    private final Function<BilinearPairing, Group> publicKeyGroup;

    /**
     * Constructor
     *
     * @param signatureGroup a function that takes a pairing and returns the group for signatures
     * @param publicKeyGroup a function that takes a pairing and returns the group for public keys
     */
    GroupAssignment(
            final Function<BilinearPairing, Group> signatureGroup,
            final Function<BilinearPairing, Group> publicKeyGroup) {
        this.signatureGroup = signatureGroup;
        this.publicKeyGroup = publicKeyGroup;
    }

    /**
     * Get the group for signatures for a given pairing
     *
     * @param pairing the pairing
     * @return the group for signatures
     */
    @NonNull
    public Group getSignatureGroupFor(@NonNull final BilinearPairing pairing) {
        return signatureGroup.apply(pairing);
    }

    /**
     * Get the group for public keys for a given pairing
     *
     * @param pairing the pairing
     * @return the group for public keys
     */
    @NonNull
    public Group getPublicKeyGroupFor(@NonNull final BilinearPairing pairing) {
        return publicKeyGroup.apply(pairing);
    }
}
