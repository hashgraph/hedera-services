/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.common;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.SignaturePair.SignatureOneOfType;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.hedera.pbj.runtime.OneOf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Some utility methods that are useful for processing system contracts.
 */
public class SystemContractUtils {
    private SystemContractUtils() {
        // Utility class
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * When a signature map is passed to a system contract function and contains ECDSA signatures, before the signatures
     * can be verified with a {@link SignatureVerifier}, they must be preprocessed in the following way:
     * (1) If the v value is greater than 35, it must be checked to see if it matches the chain ID per EIP 155
     * (2) Strip the v value from the public key as it is not needed for verification
     * @param sigMap
     * @return a new SignatureMap with the ECDSA signatures preprocessed
     */
    public static SignatureMap preprocessEcdsaSignatures(@NonNull final SignatureMap sigMap, final int chainId) {
        final List<SignaturePair> newPairs = new ArrayList<>();
        for (var spair : sigMap.sigPair()) {
            if (spair.hasEcdsaSecp256k1()) {
                final var ecSig = requireNonNull(spair.ecdsaSecp256k1());
                if (ecSig.length() == 65) {
                    checkChainId(ecSig.toByteArray(), chainId);
                    spair = new SignaturePair(
                            spair.pubKeyPrefix(), new OneOf<>(SignatureOneOfType.ECDSA_SECP256K1, ecSig.slice(0, 64)));
                }
            }
            newPairs.add(spair);
        }
        return new SignatureMap(newPairs);
    }

    /**
     * Check that the v value in an ECDSA signature matches the chain ID if it is greater than 35 per EIP 155
     * @param ecSig
     * @param chainId
     */
    public static void checkChainId(final byte[] ecSig, final int chainId) {
        final int v = ecSig[64];
        if (v >= 35) {
            // See EIP 155 - https://eips.ethereum.org/EIPS/eip-155
            final var chainIdParityZero = 35 + (chainId * 2);
            if (v == chainIdParityZero || v == chainIdParityZero + 1) {
                return;
            }
            throw new IllegalArgumentException("v value in ECDSA signature does not match chain ID");
        }
    }
}
