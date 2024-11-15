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

package com.hedera.node.app.tss.cryptography.bls;

import com.hedera.node.app.tss.cryptography.pairings.api.Field;
import com.hedera.node.app.tss.cryptography.pairings.api.FieldElement;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.utils.ByteArrayUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 *  A bls private Key for a {@code PairingFriendlyCurve} under a specific {@link SignatureSchema}
 * @param element the element
 * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
 */
public record BlsPrivateKey(@NonNull FieldElement element, @NonNull SignatureSchema signatureSchema) {
    /**
     * Constructor.
     *
     * @param element the element
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     */
    public BlsPrivateKey {
        Objects.requireNonNull(element, "element must not be null");
        Objects.requireNonNull(signatureSchema, "signatureSchema must not be null");
    }

    /**
     * Creates a private key out of the CurveType and a random
     *
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     * @param random a source of randomness
     * @return a privateKey for that CurveType
     */
    @NonNull
    public static BlsPrivateKey create(@NonNull final SignatureSchema signatureSchema, @NonNull final Random random) {
        final Field field = Objects.requireNonNull(signatureSchema, "signatureSchema must not be null")
                .getPairingFriendlyCurve()
                .field();
        Objects.requireNonNull(random, "random must not be null");
        final FieldElement sk = field.random(random);
        return new BlsPrivateKey(sk, signatureSchema);
    }

    /**
     * Aggregates multiple {@link BlsPrivateKey} into a single {@link BlsPrivateKey} for efficient verification.
     *<p>
     * This method combines multiple private keys into a single aggregated
     * private key, which retains the same size as a regular {@link BlsPrivateKey} .
     * The aggregation is performed using finite field addition in the field defined by the curve in the signature schema.
     *<p>
     * An aggregated private key is indistinguishable from a non-aggregated private key in terms of size, reducing the
     * computational cost of verification.
     *
     * @param privateKeys A list of {@link BlsPrivateKey}, where each signature is a.
     * @return A single aggregated {@link BlsPrivateKey}.
     * @throws NullPointerException if signatures is null.
     * @throws IllegalArgumentException if there are not enough publicKeys to aggregate.
     * @throws IllegalArgumentException if the publicKeys schemas do not match.
     */
    public static BlsPrivateKey aggregate(@NonNull final List<BlsPrivateKey> privateKeys) {
        if (Objects.requireNonNull(privateKeys, "privateKeys must not be null").size() < 2) {
            throw new IllegalArgumentException("Not enough privateKeys to aggregate");
        }
        if (privateKeys.stream().map(BlsPrivateKey::signatureSchema).distinct().count() > 1) {
            throw new IllegalArgumentException("All keys should have the same schema");
        }
        final SignatureSchema schema = privateKeys.getFirst().signatureSchema();
        final List<FieldElement> elements =
                privateKeys.stream().map(BlsPrivateKey::element).toList();
        final FieldElement aggregatedElement =
                schema.getPairingFriendlyCurve().field().add(elements);
        return new BlsPrivateKey(aggregatedElement, schema);
    }

    /**
     * Create a public key from this private key.
     *
     * @return the public key
     */
    @NonNull
    public BlsPublicKey createPublicKey() {
        final GroupElement pk =
                this.signatureSchema.getPublicKeyGroup().generator().multiply(this.element);

        return new BlsPublicKey(pk, this.signatureSchema);
    }

    /**
     * Signs a message and returns the signature
     *
     * @param message the message to sign
     * @return the signature of the message represented by {@code message}
     */
    @NonNull
    public BlsSignature sign(final @NonNull byte[] message) {
        final GroupElement o =
                signatureSchema.getSignatureGroup().hashToCurve(message).multiply(this.element);
        return new BlsSignature(o, signatureSchema);
    }

    /**
     * Serializes this {@link BlsPrivateKey} into a byte array.
     *
     * @return the serialized form of this object
     */
    @NonNull
    public byte[] toBytes() {
        return new ByteArrayUtils.Serializer()
                .put(this.signatureSchema().toByte())
                .put(this.element()::toBytes)
                .toBytes();
    }

    /**
     * Returns a {@link BlsPrivateKey} instance out of this object serialized form
     * @param bytes the serialized form of this object
     * @return a {@link BlsPrivateKey} instance
     * @throws IllegalArgumentException if the key representation is invalid
     */
    @NonNull
    public static BlsPrivateKey fromBytes(@NonNull final byte[] bytes) {
        try {
            final ByteArrayUtils.Deserializer deserializer = new ByteArrayUtils.Deserializer(bytes);
            var schema = SignatureSchema.create(deserializer.readByte());
            var element = deserializer.read(
                    schema.getPairingFriendlyCurve().field()::fromBytes,
                    schema.getPairingFriendlyCurve().field().elementSize());
            return new BlsPrivateKey(element, schema);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Unable to deserialize pairing private key", e);
        }
    }
}
