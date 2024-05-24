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

package com.swirlds.platform.hcm.api.tss;

import com.swirlds.platform.hcm.api.signaturescheme.PairingPrivateKey;
import com.swirlds.platform.hcm.impl.tss.groth21.ElGamalCache;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A ciphertext produced by a single node.
 */
public interface TssCiphertext {
    /**
     * Extract the private key data from this ciphertext.
     * <p>
     * The private key decrypted by this method is not the final private key. Rather, it is a partial private key.
     *
     * @param elGamalPrivateKey the private key of the node that is extracting the private shares
     * @param shareId           the ID of the private key to decrypt
     * @param elGamalCache      TODO
     * @return the private key decrypted from this ciphertext
     */
    @NonNull
    TssPrivateKey decryptPrivateKey(
            @NonNull final PairingPrivateKey elGamalPrivateKey,
            @NonNull TssShareId shareId,
            @NonNull final ElGamalCache elGamalCache);

    /**
     * Serialize this ciphertext to bytes.
     *
     * @return the serialized ciphertext
     */
    byte[] toBytes();
}
