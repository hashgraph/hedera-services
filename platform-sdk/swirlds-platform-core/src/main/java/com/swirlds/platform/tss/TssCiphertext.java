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

package com.swirlds.platform.tss;

import com.swirlds.platform.tss.ecdh.EcdhPrivateKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A ciphertext produced by a single node.
 *
 * // TODO: this is stored in the state and needs to be in protobuf
 */
public interface TssCiphertext {
    /**
     * Extract the private share data from this ciphertext.
     * <p>
     * The private share decrypted by this method is not the final private share. Rather, it is a partial private share.
     *
     * @param ecdhPrivateKey the private key of the node that is extracting the private shares
     * @param shareId        the ID of the private share to decrypt
     * @return the private share decrypted from this ciphertext
     */
    @NonNull
    TssPrivateKey decryptPrivateKey(@NonNull final EcdhPrivateKey ecdhPrivateKey, @NonNull TssShareId shareId);

    /**
     * Extract public share data for a given share from this ciphertext.
     *
     * @param shareId the share ID of the public share to extract
     * @return the public shares extracted from this ciphertext
     */
    @NonNull
    TssPublicShare extractPublicShare(@NonNull final TssShareId shareId);

    /**
     * Serialize this ciphertext to bytes.
     *
     * @return the serialized ciphertext
     */
    byte[] toBytes();
}
