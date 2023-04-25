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
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.SignaturePair.SignatureOneOfType;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.sigs.utils.MiscCryptoUtils;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.inject.Inject;

// A few problems:
//  - A single signature MAY apply to more than one key. If a signature doesn't have a prefix at all, it might
//    apply to any key! Or maybe the prefix isn't very long, and it matches multiple keys.
//  - A single key MAY match multiple signature prefixes. Which one to use? (try to match both? or most specific?)
//  - Maybe the same key shows up more than once. How does that happen? We have to deduplicate somehow...
//  - We should only match prefixes on the key types that match the key we have on hand

/**
 * A concrete implementation of {@link SignatureVerifier} that uses the {@link Cryptography} engine to verify the
 * signatures.
 */
public class SignatureVerifierImpl implements SignatureVerifier {
    /** The {@link Cryptography} engine to use for signature verification. */
    private final Cryptography cryptoEngine;

    /** Create a new instance with the given {@link Cryptography} engine. */
    @Inject
    public SignatureVerifierImpl(@NonNull final Cryptography cryptoEngine) {
        this.cryptoEngine = requireNonNull(cryptoEngine);
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public Future<SignatureVerification> verify(
            @NonNull final Key key, @NonNull final Bytes signedBytes, @NonNull final List<SignaturePair> sigPairs) {
        // Walk through the key and collect a HapiTransactionSignature for each signature that needs to be verified. If
        // there are no signatures to verify, then we clearly cannot verify the key!
        final var list = collectSignatures(signedBytes, sigPairs, key);
        if (list.isEmpty()) {
            return CompletableFuture.completedFuture(invalid(key));
        }

        // There may be duplicate keys in the above list. We need to remove them. (It would be a waste of resources
        // to do duplicate signature verifications).
        final var txSigs = list.stream().distinct().toList();

        // Collect a Future<Boolean> for each key that we have to verify. These will all be passed into the
        // SignatureVerificationFuture which will aggregate them.
        //noinspection unchecked
        cryptoEngine.verifyAsync((List<TransactionSignature>) (Object) txSigs);
        return new SignatureVerificationFuture(
                key, null, txSigs.stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair)));
    }

    @NonNull
    @Override
    public Future<SignatureVerification> verify(
            @NonNull Account hollowAccount, @NonNull Bytes signedBytes, @NonNull List<SignaturePair> sigPairs) {
        final var alias = hollowAccount.alias();
        if (hollowAccount.key() != null || alias == null || alias.length() != 20) {
            return CompletableFuture.completedFuture(invalid(hollowAccount));
        }

        for (final var sigPair : sigPairs) {
            // Only ECDSA(secp256k1) keys can be used for hollow accounts
            if (sigPair.signature().kind() == SignatureOneOfType.ECDSA_SECP256K1) {
                final var prefix = sigPair.pubKeyPrefix();
                // Keccak hash the prefix and compare to the alias. Note that this prefix WILL BE COMPRESSED. We need
                // to uncompress it first.
                final var compressedPrefixByteArray = new byte[(int) prefix.length()];
                prefix.getBytes(0, compressedPrefixByteArray);
                final var prefixByteArray = MiscCryptoUtils.decompressSecp256k1(compressedPrefixByteArray);
                final var hashedPrefixByteArray =
                        MiscCryptoUtils.extractEvmAddressFromDecompressedECDSAKey(prefixByteArray);
                final var hashedPrefix = Bytes.wrap(hashedPrefixByteArray);
                if (hashedPrefix.equals(alias)) {
                    // We have found it!
                    // Gather this signature to verify
                    final var key = Key.newBuilder()
                            .ecdsaSecp256k1(Bytes.wrap(prefixByteArray))
                            .build();
                    final var sigTx = createTransactionSignature(
                            signedBytes, key, sigPair, SignatureType.ECDSA_SECP256K1, key.ecdsaSecp256k1OrThrow());
                    cryptoEngine.verifyAsync(sigTx);
                    return new SignatureVerificationFuture(key, hollowAccount, Map.of(key, sigTx));
                }
            }
        }

        // FUTURE: Add to this method an "ecrecover" like method for getting the key

        // If we were unable to find a matching signature, then the hollow account key did not sign
        return CompletableFuture.completedFuture(invalid(hollowAccount));
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
    private List<HapiTransactionSignature> collectSignatures(
            @NonNull final Bytes signedBytes, @NonNull final List<SignaturePair> sigPairs, @NonNull final Key key) {

        return switch (key.key().kind()) {
            case ED25519 -> {
                final var txSig = collectSignature(signedBytes, sigPairs, key, key.ed25519OrThrow());
                yield txSig == null ? emptyList() : List.of(txSig);
            }
            case ECDSA_SECP256K1 -> {
                final var txSig = collectSignature(signedBytes, sigPairs, key, key.ecdsaSecp256k1OrThrow());
                yield txSig == null ? emptyList() : List.of(txSig);
            }
            case KEY_LIST -> collectSignatures(signedBytes, sigPairs, key.keyListOrThrow());
            case THRESHOLD_KEY -> collectSignatures(signedBytes, sigPairs, key.thresholdKeyOrThrow());
            case ECDSA_384, RSA_3072, CONTRACT_ID, DELEGATABLE_CONTRACT_ID, UNSET -> emptyList();
        };
    }

    /**
     * Gets the list of applicable signatures in the case of a {@link KeyList}.
     *
     * @param sigPairs The signature pairs to match against
     * @param keyList The list of keys to find corresponding signatures for
     */
    private List<HapiTransactionSignature> collectSignatures(
            @NonNull final Bytes signedBytes,
            @NonNull final List<SignaturePair> sigPairs,
            @NonNull final KeyList keyList) {
        // Every single key in the key list must contribute at least one TransactionSignature, or
        // the key was invalid for some reason. A KeyList is basically a ThresholdKey with the threshold
        // set to the number of keys in the list. Every single key must be valid.
        if (keyList.hasKeys()) {
            final var list = keyList.keysOrThrow();
            final var results = new ArrayList<HapiTransactionSignature>();
            for (final var subKey : list) {
                final var sigs = collectSignatures(signedBytes, sigPairs, subKey);
                if (sigs.isEmpty()) {
                    return emptyList();
                } else {
                    results.addAll(sigs);
                }
            }
            return results;
        }
        return emptyList();
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
    private List<HapiTransactionSignature> collectSignatures(
            @NonNull final Bytes signedBytes,
            @NonNull final List<SignaturePair> sigPairs,
            @NonNull final ThresholdKey thresholdKey) {
        // The ThresholdKey is like a KeyList, but only `threshold` number of keys must contribute at least
        // one signature.
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
                final var results = new ArrayList<HapiTransactionSignature>();
                for (final var subKey : list) {
                    final var sigs = collectSignatures(signedBytes, sigPairs, subKey);
                    if (sigs.isEmpty() && --numCanFail < 0) {
                        return emptyList();
                    } else {
                        results.addAll(sigs);
                    }
                }
                return results;
            }
        }
        return emptyList();
    }

    /** Collects the first signature pair for a simple key where the sig pair has the matching prefix */
    private HapiTransactionSignature collectSignature(
            @NonNull final Bytes signedBytes,
            @NonNull final List<SignaturePair> sigPairs,
            @NonNull final Key key,
            @NonNull final Bytes keyBytes) {

        final var sigType =
                switch (key.key().kind()) {
                    case ED25519 -> SignatureOneOfType.ED25519;
                    case ECDSA_SECP256K1 -> SignatureOneOfType.ECDSA_SECP256K1;
                    default -> throw new IllegalArgumentException("Unsupported signature type");
                };

        SignaturePair foundSigPair = null;
        for (final var sigPair : sigPairs) {
            if (sigPair.signature().kind() == sigType) {
                final var prefix = sigPair.pubKeyPrefix();
                final var foundSigPairPrefixLength =
                        foundSigPair == null ? -1 : foundSigPair.pubKeyPrefix().length();
                if (prefix.length() > foundSigPairPrefixLength && keyBytes.matchesPrefix(prefix)) {
                    // Figure out the SignatureType
                    foundSigPair = sigPair;
                }
            }
        }

        if (foundSigPair != null) {
            return switch (foundSigPair.signature().kind()) {
                case ED25519 -> createTransactionSignature(
                        signedBytes, key, foundSigPair, SignatureType.ED25519, keyBytes);
                case ECDSA_SECP256K1 -> {
                    final var compressedByteArray = new byte[(int) keyBytes.length()];
                    keyBytes.getBytes(0, compressedByteArray);
                    final var uncompressedByteArray = MiscCryptoUtils.decompressSecp256k1(compressedByteArray);
                    final var uncompressedBytes = Bytes.wrap(uncompressedByteArray);
                    yield createTransactionSignature(
                            signedBytes, key, foundSigPair, SignatureType.ECDSA_SECP256K1, uncompressedBytes);
                }
                default -> throw new IllegalArgumentException("Unsupported signature type");
            };
        }

        return null;
    }

    private HapiTransactionSignature createTransactionSignature(
            @NonNull final Bytes signedBytes,
            @NonNull final Key key,
            @NonNull final SignaturePair sigPair,
            @NonNull final SignatureType sigType,
            @NonNull final Bytes keyBytes) {
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
                sigType);
    }

    /**
     * Extends {@link TransactionSignature} to include the {@link Key} that we're checking the signature for.
     */
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof HapiTransactionSignature that)) return false;
            if (!super.equals(o)) return false;
            return Objects.equals(key, that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), key);
        }
    }
}
