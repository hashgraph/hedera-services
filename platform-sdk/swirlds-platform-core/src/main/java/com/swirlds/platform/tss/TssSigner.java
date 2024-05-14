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
import com.swirlds.platform.tss.ecdh.EcdhPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A signer in the {@link TssDirectory}.
 *
 * @param tssSignerId      the signer id
 * @param ecdhPublicKey    the signer's {@link EcdhPublicKey}
 * @param ecdhPrivateKey   the signer's {@link EcdhPrivateKey}
 * @param numberOfShares   the number of shares the signer has
 * @param sharePublicKeys  the {@link TssPublicKey}s for the signer's shares
 * @param sharePrivateKeys the {@link TssPrivateKey}s for the signer's shares
 */
public record TssSigner(
        @NonNull TssSignerId tssSignerId,
        @NonNull EcdhPublicKey ecdhPublicKey,
        @Nullable EcdhPrivateKey ecdhPrivateKey,
        int numberOfShares,
        @Nullable Map<TssShareId, TssPublicKey> sharePublicKeys,
        @Nullable Map<TssShareId, TssPrivateKey> sharePrivateKeys) {

    /**
     * Sign a message with the private keys of the shares. If the keys do not exist, this method will return null.
     *
     * @param blsCryptography the BLS cryptography to use for signing
     * @param message         the message to sign
     * @return a map of share ids to signatures signed by the shares' private keys.
     */
    @Nullable
    public Map<TssShareId, TssSignature> sign(@NonNull BlsCryptography blsCryptography, byte[] message) {
        if (sharePrivateKeys == null || sharePrivateKeys.isEmpty()) {
            return null;
        }
        final Map<TssShareId, TssSignature> signatures = new HashMap<>();
        for (final TssShareId tssShareId : sharePublicKeys.keySet()) {
            final TssSignature signature = blsCryptography.sign(sharePrivateKeys.get(tssShareId), message);
            signatures.put(tssShareId, signature);
        }
        return signatures;
    }

    /**
     * Create a new signer with the given parameters and validate the input. Validation includes the following:
     * <ul>
     *     <li>signerId is not null</li>
     *     <li>ecdhPublicKey is not null</li>
     *     <li>sharePublicKeys is not null</li>
     *     <li>number of shares matches the number of public keys </li>
     *     <li>if the private keys exist, the number of shares matches the number of private keys</li>
     *     <li>if the private keys exist, the share ids are the same between the private and public BLS keys</li>
     *     <li>if the private keys exist, the public and private keys match for the same share ids</li>
     * </ul>
     *
     * @param tssSignerId      the signer id
     * @param ecdhPublicKey    the ECDH public key
     * @param numberOfShares   the number of shares the signer has
     * @param sharePublicKeys  the BLS public keys for the shares
     * @param ecdhPrivateKey   the ECDH private key
     * @param sharePrivateKeys the BLS private keys for the shares
     * @return a new signer
     */
    public static @NonNull TssSigner create(
            @NonNull BlsCryptography blsCryptography,
            @NonNull TssSignerId tssSignerId,
            @NonNull EcdhPublicKey ecdhPublicKey,
            int numberOfShares,
            @NonNull Map<TssShareId, TssPublicKey> sharePublicKeys,
            @Nullable EcdhPrivateKey ecdhPrivateKey,
            @Nullable Map<TssShareId, TssPrivateKey> sharePrivateKeys) {
        Objects.requireNonNull(tssSignerId);
        Objects.requireNonNull(ecdhPublicKey);
        Objects.requireNonNull(sharePublicKeys);
        if (numberOfShares != sharePublicKeys.size()) {
            throw new IllegalArgumentException("Number of shares does not match the number of public keys");
        }
        if (sharePrivateKeys != null) {
            if (numberOfShares != sharePrivateKeys.size()) {
                throw new IllegalArgumentException("Number of shares does not match the number of private keys");
            }
            if (!sharePublicKeys.keySet().equals(sharePrivateKeys.keySet())) {
                throw new IllegalArgumentException("Share ids do not match between public and private keys");
            }
            // validate private and public keys match
            // Is there a better way of doing this?  Maybe a BLS library method?
            final byte[] message = {0x01, 0x02, 0x03, 0x04, 0x05};
            for (TssShareId tssShareId : sharePublicKeys.keySet()) {
                final TssPrivateKey privateKey = sharePrivateKeys.get(tssShareId);
                final TssSignature signature = blsCryptography.sign(privateKey, message);
                if (!blsCryptography.verifySignature(sharePublicKeys.get(tssShareId), signature, message)) {
                    throw new IllegalArgumentException(
                            "Public and private keys do not match for share id " + tssShareId);
                }
            }
        }
        return new TssSigner(
                tssSignerId, ecdhPublicKey, ecdhPrivateKey, numberOfShares, sharePublicKeys, sharePrivateKeys);
    }
}
