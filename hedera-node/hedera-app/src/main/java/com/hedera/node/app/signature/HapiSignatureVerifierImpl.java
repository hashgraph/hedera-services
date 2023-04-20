/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.signature;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.SignatureType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import javax.inject.Inject;

/**
 * A concrete implementation of {@link SignatureVerifier} used by HAPI calls. An alternative implementation
 * should be created for verifying synthetic calls between services. For example, the smart contract system
 * would allow signature checks based on {@link com.hedera.hapi.node.base.ContractID} but not other kinds of
 * keys.
 */
public class HapiSignatureVerifierImpl implements SignatureVerifier {
    private final Cryptography cryptoEngine;

    @Inject
    public HapiSignatureVerifierImpl(@NonNull final Cryptography cryptoEngine) {
        this.cryptoEngine = requireNonNull(cryptoEngine);
    }

    @Override
    @NonNull
    public Future<Boolean> verify(
            @NonNull final Bytes signedBytes, @NonNull final List<SignaturePair> sigPairs, @NonNull final Key key) {
        // Collect all applicable signature pairs. If there are no applicable signature pairs,
        // then there is nothing to verify, and we can return a future that reports FALSE.
        final var applicableSigPairs = collectSignatures(sigPairs, key);
        if (applicableSigPairs.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        // Prepare to call the cryptography engine. We will end up calling it once for
        // each signature that needs to be verified.
        final var data = new byte[(int) signedBytes.length()];
        signedBytes.getBytes(0, data);

        final var futures = new ArrayList<Future<Boolean>>(applicableSigPairs.size());
        for (final var sigPair : applicableSigPairs) {
            // When collecting signatures, the only kind returned are those with bytes
            final Bytes signature = sigPair.signatureBytes;
            final var signatureData = new byte[(int) signature.length()];
            signature.getBytes(0, signatureData);

            final Bytes keyBytes = sigPair.keyBytes;
            final var keyData = new byte[(int) keyBytes.length()];
            keyBytes.getBytes(0, keyData);

            final var future = cryptoEngine.verifyAsync(data, signatureData, keyData, sigPair.type());
            futures.add(future);
        }

        return new SignatureVerificationResult(futures);
    }

    /**
     * Given a {@link Key}, find all applicable {@link SignaturePair}s. For example, if the key
     * is a {@link KeyList}, then find all {@link SignaturePair}s with a key prefix matching the
     * required key in the list.
     *
     * @param sigPairs The list of {@link SignaturePair}s to look through for matches.
     * @param key The {@link Key} we use to find matching pairs
     * @return An empty {@link List} if the method is unable to find all required {@link SignaturePair}s.
     * A single element {@link List} if the key was (ultimately) a simple type (ED25519, ECDSA(secp256k1), etc.
     * Multiple elements in the {@link List} if the key resolved to a {@link KeyList} with multiple elements,
     * or a {@link ThresholdKey} with a threshold greater than 1, etc.
     */
    @NonNull
    private List<VerificationArgs> collectSignatures(
            @NonNull final List<SignaturePair> sigPairs, @NonNull final Key key) {

        return switch (key.key().kind()) {
                // You cannot have a Contract ID key as a required signer on a HAPI transaction
            case UNSET, DELEGATABLE_CONTRACT_ID, CONTRACT_ID -> Collections.emptyList();
            case ECDSA_384 -> collectSignature(sigPairs, key.ecdsa384OrThrow());
            case ED25519 -> collectSignature(sigPairs, key.ed25519OrThrow());
            case RSA_3072 -> collectSignature(sigPairs, key.rsa3072OrThrow());
            case ECDSA_SECP256K1 -> collectSignature(sigPairs, key.ecdsaSecp256k1OrThrow());
            case KEY_LIST -> collectSignatures(sigPairs, key.keyListOrThrow());
            case THRESHOLD_KEY -> collectSignatures(sigPairs, key.thresholdKeyOrThrow());
        };
    }

    /**
     * Gets the list of applicable signatures in the case of a {@link KeyList}.
     *
     * @param sigPairs The signature pairs to match against
     * @param keyList The list of keys to find corresponding signatures for
     * @return A {@link List} of applicable {@link SignaturePair}s, if any.
     */
    @NonNull
    private List<VerificationArgs> collectSignatures(
            @NonNull final List<SignaturePair> sigPairs, @NonNull final KeyList keyList) {
        // Iterate over all the keys in the list, delegating to collectSignatures for each one
        if (!keyList.hasKeys()) {
            // There are no keys in this key list, which means, nothing is authorized.
            return Collections.emptyList();
        }

        final var keys = keyList.keysOrThrow();
        final var collectedSignatures = new ArrayList<VerificationArgs>(keys.size());
        for (final var key : keys) {
            // Every key in a KeyList must be valid, so if any of these keys have no associated signature,
            // then the KeyList as a whole is invalid, and we return an empty list. The KeyList might be
            // part of a ThresholdKey, so we don't want to presume it fails the whole transaction, but at least
            // it makes no sense to validate any signatures from this list.
            final var sigsToAdd = collectSignatures(sigPairs, key);
            if (sigsToAdd.isEmpty()) {
                return Collections.emptyList();
            }
            collectedSignatures.addAll(sigsToAdd);
        }
        return collectedSignatures;
    }

    /** Collect signature pairs for a {@link ThresholdKey}. */
    @NonNull
    private List<VerificationArgs> collectSignatures(
            @NonNull final List<SignaturePair> sigPairs, @NonNull final ThresholdKey thresholdKey) {
        // Iterate over all the keys in the list, delegating to collectSignatures for each one,
        // and keep track of which signatures were successful and which weren't. The ones that
        // were not successful, we can ignore. If we do not have enough successful signatures,
        // then we throw the appropriate exception.
        if (!thresholdKey.hasKeys()) {
            // There is no key list, which means, nothing is authorized.
            return Collections.emptyList();
        }

        // Iterate over all the keys in the list, delegating to collectSignatures for each one. If they have
        // signatures, then we accumulate them and increase the `numSuccessfulKeys` by 1 (regardless of how many
        // signatures they gathered). At the end, if we have exceeded the threshold, then we return ALL the
        // signatures we collected. This is really important.
        //
        // Suppose a threshold key is 2/4, meaning that 2 of the 4 keys must sign. Suppose the transaction has
        // 3 signatures on it from 3 of those 4 keys, but only 2 of them are valid signatures, the third being
        // some nonsense. The transaction should succeed. If we short-circuited this method after collecting 2 of 4
        // keys, we might have failed the transaction. So the specification should be that if it is possible to
        // select a set of signatures and keys that would succeed, then a valid consensus node implementation will
        // select those keys to succeed.
        var numSuccessfulKeys = 0;
        final var keyList = thresholdKey.keysOrThrow();
        final var keys = keyList.keysOrElse(Collections.emptyList());
        final var collectedSignatures =
                new ArrayList<VerificationArgs>(keyList.keysOrThrow().size());
        for (final var key : keys) {
            final var sigs = collectSignatures(sigPairs, key);
            if (!sigs.isEmpty()) {
                collectedSignatures.addAll(sigs);
                numSuccessfulKeys++;
            }
        }

        // It should be impossible for the threshold to ever be non-positive. But if it were to ever happen,
        // we will treat it as though the threshold were 1. This allows the user to fix their problem and
        // set an appropriate threshold. Likewise, if the threshold is greater than the number of keys, then
        // we clamp to the number of keys. This also shouldn't be possible, but if it happens, we give the
        // user a chance to fix their account.
        var threshold = thresholdKey.threshold();
        if (threshold <= 0) threshold = 1;
        if (threshold > keys.size()) threshold = keys.size();

        // If we didn't meet the minimum threshold for signers, then we're bad.
        return (numSuccessfulKeys >= threshold) ? collectedSignatures : Collections.emptyList();
    }

    /** Collects the first signature pair for a simple key where the sig pair has the matching prefix */
    @NonNull
    private List<VerificationArgs> collectSignature(
            @NonNull final List<SignaturePair> signatures, @NonNull final Bytes keyBytes) {

        for (final var signature : signatures) {
            final var prefix = signature.pubKeyPrefix();
            if (prefix.length() > 0 && keyBytes.matchesPrefix(prefix)) {
                final var type =
                        switch (signature.signature().kind()) {
                            case ED25519 -> SignatureType.ED25519;
                            case ECDSA_SECP256K1 -> SignatureType.ECDSA_SECP256K1;
                            default -> throw new IllegalArgumentException("Unsupported signature type");
                        };
                return List.of(
                        new VerificationArgs(keyBytes, signature.signature().as(), type));
            }
        }
        return Collections.emptyList();
    }

    private record VerificationArgs(Bytes keyBytes, Bytes signatureBytes, SignatureType type) {}
}
