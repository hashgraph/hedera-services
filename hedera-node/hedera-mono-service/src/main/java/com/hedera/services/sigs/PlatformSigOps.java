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
package com.hedera.services.sigs;

import static com.hedera.services.keys.HederaKeyTraversal.visitSimpleKeys;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.factories.TxnScopedPlatformSigFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import java.util.List;

/** Provides static methods to work with {@link com.swirlds.common.crypto.Signature} objects. */
public final class PlatformSigOps {
    private PlatformSigOps() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Return the result of trying to create one or more platform sigs using a given {@link
     * TxnScopedPlatformSigFactory}, where this {@code factory} should be invoked for each public
     * key in a left-to-right DFS traversal of the simple keys from a list of Hedera keys, using
     * signature bytes from a given {@link PubKeyToSigBytes}.
     *
     * @param hederaKeys a list of Hedera keys to traverse for public keys.
     * @param sigBytesFn a source of cryptographic signatures to associate to the public keys.
     * @param factory a factory to convert public keys and cryptographic sigs into sigs.
     * @return the result of attempting this creation.
     */
    public static PlatformSigsCreationResult createCryptoSigsFrom(
            final List<JKey> hederaKeys,
            final PubKeyToSigBytes sigBytesFn,
            final TxnScopedPlatformSigFactory factory) {
        final var result = new PlatformSigsCreationResult();
        for (final var hederaKey : hederaKeys) {
            visitSimpleKeys(
                    hederaKey,
                    primitiveKey -> createCryptoSigFor(primitiveKey, sigBytesFn, factory, result));
        }
        return result;
    }

    private static void createCryptoSigFor(
            final JKey primitiveKey,
            final PubKeyToSigBytes sigBytesFn,
            final TxnScopedPlatformSigFactory factory,
            final PlatformSigsCreationResult result) {
        if (result.hasFailed() || primitiveKey.hasHollowKey()) {
            return;
        }

        try {
            if (primitiveKey.hasEd25519Key()) {
                final var key = primitiveKey.getEd25519();
                final var sig = sigBytesFn.sigBytesFor(key);
                if (sig.length > 0) {
                    result.getPlatformSigs().add(factory.signBodyWithEd25519(key, sig));
                }
            } else if (primitiveKey.hasECDSAsecp256k1Key()) {
                final var key = primitiveKey.getECDSASecp256k1Key();
                final var sig = sigBytesFn.sigBytesFor(key);
                if (sig.length > 0) {
                    result.getPlatformSigs()
                            .add(factory.signKeccak256DigestWithSecp256k1(key, sig));
                }
            }
        } catch (KeyPrefixMismatchException kmpe) {
            /* No problem if a signature map is ambiguous for a key linked to a scheduled transaction. */
            if (!primitiveKey.isForScheduledTxn()) {
                result.setTerminatingEx(kmpe);
            }
        } catch (Exception e) {
            result.setTerminatingEx(e);
        }
    }
}
