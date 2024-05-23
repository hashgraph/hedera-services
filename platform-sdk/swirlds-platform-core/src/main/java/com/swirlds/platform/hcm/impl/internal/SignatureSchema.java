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

package com.swirlds.platform.hcm.impl.internal;

import com.swirlds.base.utility.Pair;
import com.swirlds.platform.hcm.api.pairings.BilinearPairing;
import com.swirlds.platform.hcm.api.pairings.CurveType;
import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import com.swirlds.platform.hcm.api.pairings.PairingResult;
import com.swirlds.platform.hcm.api.signaturescheme.PrivateKey;
import com.swirlds.platform.hcm.api.signaturescheme.PublicKey;
import com.swirlds.platform.hcm.api.signaturescheme.Signature;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Internal class implementing the logic for Signature Schema
 */
public class SignatureSchema {

    private static final int RANDOM_SEED = 32;
    private final BilinearPairing pairing;
    private final GroupAssignment groupAssignment;

    private SignatureSchema(@NonNull final BilinearPairing pairing, @NonNull final GroupAssignment groupAssignment) {
        this.pairing = pairing;
        this.groupAssignment = groupAssignment;
    }

    @NonNull
    public static SignatureSchema forPairing(final byte type) {
        return forPairing(CurveType.fromIdByte(type));
    }

    @NonNull
    public static SignatureSchema forPairing(@NonNull final CurveType type) {
        final CurveTypeMapping mapping = CurveTypeMapping.getPairing(type);
        return new SignatureSchema(mapping.pairing, mapping.assignment);
    }

    @NonNull
    private Group signatureGroup() {
        return groupAssignment.getSignatureGroupFor(pairing);
    }

    @NonNull
    private Group publicKeyGroup() {
        return groupAssignment.getPublicKeyGroupFor(pairing);
    }

    // Secret key and public key
    // A secret key is a randomly chosen number between “1” and “q -1” (q is the order of “Zr”)
    @NonNull
    public Pair<PrivateKey, PublicKey> createKeyPair(@NonNull final SecureRandom random) {
        final FieldElement sk = pairing.getField().randomElement(random.generateSeed(RANDOM_SEED));
        return Pair.of(new PrivateKey(sk), createPublicKey(sk));
    }

    // To generate the corresponding public key (when using “G₁” for public keys), we need to calculate
    // “pk = [sk]g1”, where “g1” is the selected generator of “G₁”.
    // In other words, “sk” is the number of times that “g1” is added to itself.
    // This calculation yields the public key, “pk”, which can be shared with others for verification of digital
    // signatures.
    @NonNull
    public PublicKey createPublicKey(@NonNull final FieldElement sk) {
        return new PublicKey(publicKeyGroup().getGenerator().power(sk));
    }

    // Signing:
    // In order to sign a message “m”, the first step is to map it onto a point in group “G₂”,
    // if “G₂” is used for signatures. This can be done using various methods such as hashing to the curve.
    // After this step, the resulting point in “G₂” is referred to as “H(m)”.
    // The message is signed by computing the signature “σ = [sk]H(m)”, where “[sk]H(m)” represents multiplying the hash
    // point by the private key.
    // Sign the messages
    // Hash the message to a point in signatureField
    @NonNull
    public Signature sign(@NonNull final byte[] message, @NonNull final PrivateKey privateKey) {
        final GroupElement h = signatureGroup().elementFromHash(message);
        return new Signature(h.power(privateKey.element()));
    }

    // Aggregation:
    // BLS signatures have a unique property that makes them very useful in cryptography.
    // They can be aggregated, as stated in the original paper, which means that only two pairings are required to
    // verify a message signed by multiple parties.
    // This is a great advantage as pairings are computationally expensive.
    // In Ethereum2, signatures are aggregated over the same message.
    // To aggregate signatures, the corresponding “G₂” points are added together, and the corresponding “G₁” public key
    // points are also added together.
    // With the magic of pairings, it is possible to verify all the signatures together by checking that
    // “e(g1, σagg) = e(pkagg, H(m))”, where “σagg” and “pkagg” are the aggregated signature and public key points,
    // respectively.
    // This greatly simplifies the verification process and makes it faster and more efficient.
    // Whether the signatures are over different messages or over the same message, they can be aggregated with the same
    // process.
    // This property of BLS signatures makes them an ideal choice for use cases where many parties need to sign a
    // message, and their signatures need to be efficiently verified.
    // pkagg: aggregate public key
    // σagg: aggregate signature
    @NonNull
    public Pair<PublicKey, Signature> aggregate(@NonNull final Pair<PublicKey, Signature>... aggregateElements) {
        final GroupElement aggregatedSignature = Arrays.stream(aggregateElements)
                .skip(1)
                .map(Pair::value)
                .map(Signature::element)
                .reduce(aggregateElements[0].value().element().copy(), GroupElement::multiply);
        final GroupElement aggregatedPublicKey = Arrays.stream(aggregateElements)
                .skip(1)
                .map(Pair::key)
                .map(PublicKey::element)
                .reduce(aggregateElements[0].key().element().copy(), GroupElement::add);

        return Pair.of(new PublicKey(aggregatedPublicKey), new Signature(aggregatedSignature));
    }

    // Verification:
    // To verify a signature, we need to ensure that the message m was signed with the corresponding private key “sk”
    // for the given public key “pk”.
    // This is where pairing in cryptography becomes important.
    // The signature is considered valid only if the pairing between “g1” and the signature “σ” is equal to the pairing
    // between pk and the hash point “H(m)”.
    // The properties of pairings can be used to confirm this relationship. Specifically, we can calculate that:
    // e(pk, H(m)) = e([sk]g1, H(m)) = e(g1, H(m))^(sk) = e(g1, [sk]H(m)) = e(g1, σ).
    public boolean verifySignature(
            @NonNull final byte[] message, @NonNull final PublicKey publicKey, @NonNull final Signature signature) {
        final GroupElement hash = signatureGroup().elementFromHash(message);
        // Verify the signature using pairings
        // TODO: It might be important in which order we send the elements in the method. And given that we can decide
        // which group to use
        // we need to add some logic to identify where to put each parameter
        final PairingResult lhs =
                pairing.pairingBetween(signature.element(), publicKeyGroup().getGenerator());
        final PairingResult rhs = pairing.pairingBetween(hash, publicKey.element());

        return lhs.isEquals(rhs);
    }

    @NonNull
    public static Signature deserializeSignature(@NonNull final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be null or empty");
        }
        return new Signature(
                SignatureSchema.forPairing(bytes[0]).signatureGroup().elementFromBytes(bytes));
    }

    @NonNull
    public static PrivateKey deserializePrivateKey(@NonNull final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be null or empty");
        }

        return new PrivateKey(
                SignatureSchema.forPairing(bytes[0]).pairing.getField().elementFromBytes(bytes));
    }

    @NonNull
    public static PublicKey deserializePublicKey(@NonNull final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be null or empty");
        }

        return new PublicKey(
                SignatureSchema.forPairing(bytes[0]).publicKeyGroup().elementFromBytes(bytes));
    }
}
