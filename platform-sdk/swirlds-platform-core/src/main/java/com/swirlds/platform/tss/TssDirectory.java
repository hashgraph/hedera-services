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
import java.util.Map;

/**
 * A directory of signers with a total number of shares and a signer with a {@link EcdhPrivateKey} matching the signer
 * ID.
 *
 * @param signers             the signers in the directory.
 * @param totalNumberOfShares the total number of shares in the directory.
 * @param signerId            the signer ID of the signer with a {@link EcdhPrivateKey}
 */
public record TssDirectory(
        @NonNull Map<TssSignerId, TssSigner> signers, int totalNumberOfShares, @NonNull TssSignerId signerId) {

    /**
     * Create a TSS directory with the specified signers, total number of shares, and signer ID and validate that the
     * sum of the number of shares of each signer matches the total number of shares and that the signer of the signer
     * id has an ECDH private key.
     *
     * @param signers             the signers in the directory.
     * @param totalNumberOfShares the total number of shares in the directory.
     * @param signerId            the signer ID of the signer with a {@link EcdhPrivateKey}
     * @return a new {@link TssDirectory}
     */
    public static TssDirectory create(
            @NonNull final Map<TssSignerId, TssSigner> signers,
            final int totalNumberOfShares,
            @NonNull final TssSignerId signerId) {
        final int total =
                signers.values().stream().mapToInt(TssSigner::numberOfShares).sum();
        if (total != totalNumberOfShares) {
            throw new IllegalArgumentException(
                    "The sum of shares belonging to signers in the director do not match the specified total.");
        }
        final TssSigner signer = signers.get(signerId);
        if (signer.ecdhPrivateKey() == null) {
            throw new IllegalArgumentException(
                    "The signer specified in the directory does not have an ECDH private key.");
        }
        return new TssDirectory(signers, totalNumberOfShares, signerId);
    }
}
