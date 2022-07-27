/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.sourcing;

import com.hedera.services.legacy.exception.KeyPrefixMismatchException;

/**
 * Defines a type that is a source of the cryptographic signatures associated to given public keys.
 * It is useful to define an explicit type for this simple behavior, because to create a {@link
 * com.swirlds.common.crypto.Signature}, you must have:
 *
 * <ol>
 *   <li>The raw data that was signed.
 *   <li>The public key matching the private key used to sign the data.
 *   <li>The cryptographic signature that resulted.
 * </ol>
 *
 * A {@code PubKeyToSigBytes} implementation lets us obtain the third ingredient given the second.
 */
public interface PubKeyToSigBytes {
    byte[] EMPTY_SIG = {};

    /**
     * Return the cryptographic signature associated to a given public key in some context
     * (presumably the creation of a {@link com.swirlds.common.crypto.Signature}).
     *
     * @param pubKey a public key whose private key was used to sign some data.
     * @return the cryptographic signature that resulted.
     * @throws KeyPrefixMismatchException if the desired cryptographic signature doesn't match
     *     prefix.
     */
    byte[] sigBytesFor(byte[] pubKey) throws KeyPrefixMismatchException;

    /**
     * For each full-public-key-to-signature mapping that has not been used by {@link
     * PubKeyToSigBytes#sigBytesFor(byte[])} since the last call to {@link
     * PubKeyToSigBytes#resetAllSigsToUnused()} on this instance, invokes the given observer with
     * the key and signature of the mapping.
     *
     * <p>Used to create {@link com.swirlds.common.crypto.TransactionSignature} instances based on
     * {@link com.hederahashgraph.api.proto.java.SignaturePair}s with a full public key prefix, but
     * <b>without</b> a matching public key obviously linked to the parent transaction.
     *
     * <p>A canonical example would be the supply key of a token that will be minted through an HTS
     * precompile run as part of a {@code ContractCall} transaction. This key is not
     * <b>obviously</b> linked to the top-level {@code ContractCall}; but it will ultimately need to
     * sign, hence requires a signature supplied with full public key prefix.
     *
     * @param obs an observer to be shown all the unused full-public-key-to-signature mappings
     */
    default void forEachUnusedSigWithFullPrefix(SigObserver obs) {
        /* No-op */
    }

    /**
     * Checks if there is any full-public-key-to-signature mapping that has not been used by {@link
     * PubKeyToSigBytes#sigBytesFor(byte[])} since the last call to {@link
     * PubKeyToSigBytes#resetAllSigsToUnused()} on this instance
     *
     * @return true if there is any unused signature mapping
     */
    default boolean hasAtLeastOneUnusedSigWithFullPrefix() {
        return false;
    }

    /** Resets all internal public-key-to-signature mappings to unused. */
    default void resetAllSigsToUnused() {
        /* No-op */
    }
}
