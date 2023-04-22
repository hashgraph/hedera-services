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

package com.hedera.node.app.signature.hapi;

import static com.hedera.node.app.signature.hapi.SignatureVerificationImpl.invalid;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * A concrete implementation of {@link SignatureVerifier} used by HAPI calls. An alternative implementation
 * should be created for verifying synthetic calls between services. For example, the smart contract system
 * would allow signature checks based on {@link com.hedera.hapi.node.base.ContractID} but not other kinds of
 * keys.
 */
public class HapiSignatureVerifierImpl implements SignatureVerifier {
    private final Cryptography cryptoEngine;

    private static final class HapiTransactionSignature extends TransactionSignature {
        private final Key key;

        public HapiTransactionSignature(
                @NonNull Key key,
                @NonNull byte[] contents,
                int signatureOffset,
                int signatureLength,
                int publicKeyOffset,
                int publicKeyLength,
                int messageOffset,
                int messageLength,
                @NonNull SignatureType signatureType) {
            super(
                    requireNonNull(contents),
                    signatureOffset,
                    signatureLength,
                    publicKeyOffset,
                    publicKeyLength,
                    messageOffset,
                    messageLength,
                    requireNonNull(signatureType));
            this.key = requireNonNull(key);
        }
    }

    @Inject
    public HapiSignatureVerifierImpl(@NonNull final Cryptography cryptoEngine) {
        this.cryptoEngine = requireNonNull(cryptoEngine);
    }

    @Override
    @NonNull
    public Future<SignatureVerification> verify(
            @NonNull final Bytes signedBytes, @NonNull final List<SignaturePair> sigPairs, @NonNull final Key key) {
        // Walk through the key in **depth first order** and collect a VerificationArgs for each signature that needs to
        // be verified. If there are no signatures to verify, then the key is invalid.
        final var txSigs = new ArrayList<HapiTransactionSignature>(100); // Should be bigger than we ever need
        collectSignatures(signedBytes, sigPairs, key, txSigs);
        if (txSigs.isEmpty()) {
            return CompletableFuture.completedFuture(invalid(key));
        }

        // There may be duplicate keys in the above txSigs. We need to remove them.
        for (int i = txSigs.size() - 1; i >= 1; i--) {
            final var a = txSigs.get(i).key;
            for (int j = i - 1; j >= 0; j--) {
                final var b = txSigs.get(j).key;
                if (a.equals(b)) {
                    txSigs.remove(i);
                }
            }
        }

        // Collect a Future<Boolean> for each key that we had to verify.
        //noinspection unchecked
        cryptoEngine.verifyAsync((List<TransactionSignature>) (Object) txSigs);
        return new SignatureVerificationFuture(
                key, null, txSigs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair)));
    }

    @NonNull
    @Override
    public Future<SignatureVerification> verify(
            @NonNull Bytes signedBytes, @NonNull List<SignaturePair> sigPairs, @NonNull Account hollowAccount) {
        return null;
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
    private void collectSignatures(
            @NonNull final Bytes signedBytes,
            @NonNull final List<SignaturePair> sigPairs,
            @NonNull final Key key,
            @NonNull final List<HapiTransactionSignature> collectedSignatures) {

        // If this is a duplicate, then we can ... OH CRAP. I hate this!
        if (collectedSignatures.contains(key)) {
            return;
        }

        switch (key.key().kind()) {
            case ED25519 -> {
                final var txSig = collectSignature(signedBytes, sigPairs, key, key.ed25519OrThrow());
                if (txSig != null) {
                    collectedSignatures.add(txSig);
                }
            }
            case ECDSA_SECP256K1 -> {
                final var txSig = collectSignature(signedBytes, sigPairs, key, key.ecdsaSecp256k1OrThrow());
                if (txSig != null) {
                    collectedSignatures.add(txSig);
                }
            }
            case KEY_LIST -> collectSignatures(signedBytes, sigPairs, key.keyListOrThrow(), collectedSignatures);
            case THRESHOLD_KEY -> collectSignatures(
                    signedBytes, sigPairs, key.thresholdKeyOrThrow(), collectedSignatures);
            case ECDSA_384, RSA_3072, CONTRACT_ID, DELEGATABLE_CONTRACT_ID, UNSET -> {
                /* Nothing to contribute */
            }
        }
    }

    /**
     * Gets the list of applicable signatures in the case of a {@link KeyList}.
     *
     * @param sigPairs The signature pairs to match against
     * @param keyList The list of keys to find corresponding signatures for
     */
    private void collectSignatures(
            @NonNull final Bytes signedBytes,
            @NonNull final List<SignaturePair> sigPairs,
            @NonNull final KeyList keyList,
            @NonNull final List<HapiTransactionSignature> collectedSignatures) {
        // Every single key in the key list must contribute at least one TransactionSignature, or
        // the key was invalid for some reason. A KeyList is basically a ThresholdKey with the threshold
        // set to the number of keys in the list. Every single key must be valid.
        final var originalNumCollectedSignatures = collectedSignatures.size();
        if (keyList.hasKeys()) {
            final var list = keyList.keysOrThrow();
            for (final var subKey : list) {
                final var startNumCollectedSignatures = collectedSignatures.size();
                collectSignatures(signedBytes, sigPairs, subKey, collectedSignatures);
                // If we did not collect AT LEAST one signature then we need to rollback the elements
                // in `collectedSignatures` to what it was before we started iterating and then bail.
                final var endNumCollectedSignatures = collectedSignatures.size();
                if (startNumCollectedSignatures == endNumCollectedSignatures) {
                    if (collectedSignatures.size() > originalNumCollectedSignatures) {
                        collectedSignatures
                                .subList(originalNumCollectedSignatures, collectedSignatures.size())
                                .clear();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Collect signature pairs for a {@link ThresholdKey}.
     *
     * <p>Suppose a threshold key is 2/4, meaning that 2 of the 4 keys must sign. Suppose the transaction has
     * 3 signatures on it from 3 of those 4 keys, but only 2 of them are valid signatures, the third being
     * some nonsense. The transaction should succeed. If we short-circuited this method after collecting 2 of 4
     * keys, we might have failed the transaction. So the specification is, that if it is possible to select a set of
     * signatures and keys that would succeed, then a valid consensus node implementation will select those keys to
     * succeed.
     */
    private void collectSignatures(
            @NonNull final Bytes signedBytes,
            @NonNull final List<SignaturePair> sigPairs,
            @NonNull final ThresholdKey thresholdKey,
            @NonNull final List<HapiTransactionSignature> collectedSignatures) {
        // The ThresholdKey is like a KeyList, but only `threshold` number of keys must contribute at least
        // one signature.
        final var originalNumCollectedSignatures = collectedSignatures.size();
        if (thresholdKey.hasKeys()) {
            final var keys = thresholdKey.keysOrThrow();
            if (keys.hasKeys()) {
                // It should be impossible for the threshold to ever be non-positive. But if it were to ever happen,
                // we will treat it as though the threshold were 1. This allows the user to fix their problem and
                // set an appropriate threshold. Likewise, if the threshold is greater than the number of keys, then
                // we clamp to the number of keys. This also shouldn't be possible, but if it happens, we give the
                // user a chance to fix their account.
                final var list = keys.keysOrThrow();
                final var threshold = Math.max(Math.min(list.size(), thresholdKey.threshold()), 1);

                var numCanFail = list.size() - threshold;
                for (final var subKey : list) {
                    final var startNumCollectedSignatures = collectedSignatures.size();
                    collectSignatures(signedBytes, sigPairs, subKey, collectedSignatures);
                    final var endNumCollectedSignatures = collectedSignatures.size();
                    if (startNumCollectedSignatures == endNumCollectedSignatures) {
                        if (collectedSignatures.size() > originalNumCollectedSignatures) {
                            if (--numCanFail < 0) {
                                collectedSignatures
                                        .subList(originalNumCollectedSignatures, collectedSignatures.size())
                                        .clear();
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /** Collects the first signature pair for a simple key where the sig pair has the matching prefix */
    private HapiTransactionSignature collectSignature(
            @NonNull final Bytes signedBytes,
            @NonNull final List<SignaturePair> sigPairs,
            @NonNull final Key key,
            @NonNull final Bytes keyBytes) {

        for (final var sigPair : sigPairs) {
            final var prefix = sigPair.pubKeyPrefix();
            if (keyBytes.matchesPrefix(prefix)) {
                // Figure out the SignatureType
                final var type =
                        switch (sigPair.signature().kind()) {
                            case ED25519 -> SignatureType.ED25519;
                            case ECDSA_SECP256K1 -> SignatureType.ECDSA_SECP256K1;
                            default -> throw new IllegalArgumentException("Unsupported signature type");
                        };

                // The platform API requires a single content array with all the data inside it.
                // I need to do some array copies to make this work.
                final Bytes sigBytes = sigPair.signature().as();
                final var sigBytesLength = (int) sigBytes.length();
                final var keyBytesLength = (int) keyBytes.length();
                final var signedBytesLength = (int) signedBytes.length();
                final var contents = new byte[sigBytesLength + keyBytesLength + signedBytesLength];
                final var sigOffset = 0;
                final var keyOffset = sigOffset + sigBytesLength;
                final var dataOffset = keyOffset + keyBytesLength;
                sigBytes.getBytes(0, contents, sigOffset, sigBytesLength);
                keyBytes.getBytes(0, contents, keyOffset, keyBytesLength);
                signedBytes.getBytes(0, contents, dataOffset, signedBytesLength);

                // Gather this signature to verify
                return new HapiTransactionSignature(
                        key,
                        contents,
                        sigOffset,
                        sigBytesLength,
                        keyOffset,
                        keyBytesLength,
                        dataOffset,
                        sigBytesLength,
                        type);
            }
        }
        return null;
    }
}
