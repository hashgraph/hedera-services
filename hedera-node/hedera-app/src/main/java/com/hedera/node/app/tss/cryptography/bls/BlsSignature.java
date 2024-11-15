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

import com.hedera.node.app.tss.cryptography.pairings.api.BilinearPairing;
import com.hedera.node.app.tss.cryptography.pairings.api.Group;
import com.hedera.node.app.tss.cryptography.pairings.api.GroupElement;
import com.hedera.node.app.tss.cryptography.pairings.api.PairingFriendlyCurve;
import com.hedera.node.app.tss.cryptography.utils.ByteArrayUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 *  A bls element for a {@code PairingFriendlyCurve} under a specific {@link SignatureSchema}
 * @param element the element
 * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
 */
public record BlsSignature(@NonNull GroupElement element, @NonNull SignatureSchema signatureSchema) {

    /**
     * Constructor.
     * @param element the element
     * @param signatureSchema defines which elliptic curve is used in the protocol, and how it's used
     */
    public BlsSignature {
        Objects.requireNonNull(element, "element must not be null");
        Objects.requireNonNull(signatureSchema, "signatureSchema must not be null");
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
     * Returns a {@link BlsSignature} instance out of this object serialized form
     * @param bytes the serialized form of this object
     * @return a {@link BlsSignature} instance
     * @throws IllegalArgumentException if the key representation is invalid
     */
    @NonNull
    public static BlsSignature fromBytes(@NonNull final byte[] bytes) {
        try {
            final ByteArrayUtils.Deserializer deserializer = new ByteArrayUtils.Deserializer(bytes);
            var schema = SignatureSchema.create(deserializer.readByte());
            var element = deserializer.read(
                    schema.getSignatureGroup()::fromBytes,
                    schema.getSignatureGroup().elementSize());
            return new BlsSignature(element, schema);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Unable to deserialize pairing private key", e);
        }
    }

    /**
     * Aggregates multiple {@link BlsSignature} into a single {@link BlsSignature} for efficient verification.
     *<p>
     * This method combines multiple signatures over the same message into a single aggregated
     * signature, which retains the same size as a regular BLS signature.
     * The aggregation is performed using elliptic curve point addition in the group defined by each signature schema,
     * where each signature is a point on the curve.
     *<p>
     * An aggregated signature is indistinguishable from a non-aggregated signature in terms of size reducing the
     * computational cost of verification.
     *
     * @param signatures A list of {@link BlsSignature}, where each signature is a point in the elliptic
     *                   curve group.
     * @return A single aggregated BLS signature.
     * @throws NullPointerException if signatures is null.
     * @throws IllegalArgumentException if there are not enough signatures to aggregate.
     * @throws IllegalArgumentException if the signature schemas do not match.
     */
    public static BlsSignature aggregate(@NonNull final List<BlsSignature> signatures) {
        if (Objects.requireNonNull(signatures, "signatures must not be null").size() < 2) {
            throw new IllegalArgumentException("Not enough signatures to aggregate");
        }
        if (signatures.stream().map(BlsSignature::signatureSchema).distinct().count() > 1) {
            throw new IllegalArgumentException("All signatures should have the same schema");
        }
        final SignatureSchema schema = signatures.getFirst().signatureSchema();
        final List<GroupElement> elements =
                signatures.stream().map(BlsSignature::element).toList();
        final GroupElement aggregatedElement = schema.getSignatureGroup().add(elements);
        return new BlsSignature(aggregatedElement, schema);
    }

    /**
     * Verify a signed message with the known public key.
     * <p>
     * To verify a signature, we need to ensure that the message m was signed with the corresponding private key “sk”
     * for the given public key “pk”.
     * <p>
     * The signature is considered valid only if the pairing between the generator of the public key group and the
     * signature “σ” is equal to the pairing between the public key and the message hashed to the signature group.
     * <p>
     * Mathematically, this verification can be expressed like this:
     * e(pk, H(m)) = e([sk]g1, H(m)) = e(g1, H(m))^(sk) = e(g1, [sk]H(m)) = e(g1, σ).
     *
     * @param publicKey the public key to verify with
     * @param message   the message that was signed
     * @return true if the signature is valid, false otherwise
     */
    public boolean verify(@NonNull final BlsPublicKey publicKey, @NonNull final byte[] message) {
        Objects.requireNonNull(publicKey, "publicKeyÒ must not be null");
        Objects.requireNonNull(message, "message must not be null");
        if (publicKey.signatureSchema() != signatureSchema) {
            throw new IllegalArgumentException("PublicKey does not match signatureSchema");
        }
        final Group signatureGroup = signatureSchema.getSignatureGroup();
        final Group publicKeyGroup = signatureSchema.getPublicKeyGroup();
        final PairingFriendlyCurve curve = signatureSchema.getPairingFriendlyCurve();
        final BilinearPairing a = curve.pairingBetween(publicKey.element(), signatureGroup.hashToCurve(message));
        final BilinearPairing b = curve.pairingBetween(publicKeyGroup.generator(), element);
        return a.compare(b);
    }
}
