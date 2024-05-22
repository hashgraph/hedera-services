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

package com.swirlds.platform.tss.bls.impl.internal;

import com.swirlds.base.utility.Pair;
import com.swirlds.platform.tss.bls.api.*;
import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Internal class implementing the logic for BLS
 */
public class BlsSchema {

    private final BilinearPairing pairing;
    private final Supplier<Group> signatureGroupSupplier;
    private final Supplier<Group> publicKeyGroupSupplier;
    // TODO: check, should we store this value ? it does not seem right
    private final GroupElement g1; // Group generator

    private BlsSchema(BilinearPairing pairing) {
        this.pairing = pairing;
        this.publicKeyGroupSupplier = pairing::getGroup2;
        this.signatureGroupSupplier = pairing::getGroup1;
        this.g1 = publicKeyGroupSupplier.get().randomElement(); // Group generator;
    }

    public static BlsSchema forPairing(byte type) {
        return new BlsSchema(CurveTypeMapping.getPairing(CurveType.fromIdByte(type)));
    }

    public static BlsSchema forPairing(CurveType type) {
        return new BlsSchema(CurveTypeMapping.getPairing(type));
    }

    private Group signatureGroup() {
        return signatureGroupSupplier.get();
    }

    private Group publicKeyGroup() {
        return publicKeyGroupSupplier.get();
    }

    // Secret key and public key
    // A secret key is a randomly chosen number between “1” and “q -1” (q is the order of “Zr”)
    // To generate the corresponding public key (when using “G₁” for public keys), we need to calculate
    // “pk = [sk]g1”, where “g1” is the selected generator of “G₁”.
    // In other words, “sk” is the number of times that “g1” is added to itself.
    // This calculation yields the public key, “pk”, which can be shared with others for verification of digital
    // signatures.
    public Pair<PrivateKey, PublicKey> createKeyPair() {
        FieldElement sk1 = pairing.getField().randomElement();
        GroupElement pk1 = g1.power(sk1); // Who should store g1????
        return Pair.of(new PrivateKey(sk1), new PublicKey(pk1));
    }

    public PublicKey createPublicKey(FieldElement sk1) {
        return new PublicKey(g1.power(sk1));
    }

    // Signing:
    // In order to sign a message “m”, the first step is to map it onto a point in group “G₂”,
    // if “G₂” is used for signatures. This can be done using various methods such as hashing to the curve.
    // After this step, the resulting point in “G₂” is referred to as “H(m)”.
    // The message is signed by computing the signature “σ = [sk]H(m)”, where “[sk]H(m)” represents multiplying the hash
    // point by the private key.
    // Sign the messages
    // Hash the message to a point in signatureField

    public Signature sign(byte[] message, PrivateKey privateKey) {

        GroupElement h = signatureGroupSupplier.get().elementFromHash(message);
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
    public Pair<PublicKey, Signature> aggregate(Pair<PublicKey, Signature>... aggregateElements) {
        GroupElement aggregatedSignature = Arrays.stream(aggregateElements)
                .skip(1)
                .map(Pair::value)
                .map(Signature::element)
                .reduce(aggregateElements[0].value().element().copy(), GroupElement::multiply);
        GroupElement aggregatedPublicKey = Arrays.stream(aggregateElements)
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
    public boolean verifySignature(byte[] message, PublicKey publicKey, Signature signature) {
        GroupElement hash = signatureGroupSupplier.get().elementFromHash(message);
        // Verify the signature using pairings
        PairingResult lhs = pairing.pairingBetween(signature.element(), g1);
        PairingResult rhs = pairing.pairingBetween(hash, publicKey.element());

        return lhs.isEquals(rhs);
    }

    public static byte[] getBytes(char type, byte[] objectBytes) {
        byte[] newByteArray = new byte[objectBytes.length];
        newByteArray[0] = combineEncodedValues(encodeType(type), objectBytes[0]);
        System.arraycopy(objectBytes, 1, newByteArray, 1, objectBytes.length);
        return newByteArray;
    }

    public static Signature deserializeSignature(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be null or empty");
        }
        if (bytes.length < Signature.MIN) {
            throw new IllegalArgumentException("it is not a valid serialized form of signature");
        }
        if (decodeType(bytes[0]) != Signature.TYPE) {
            throw new IllegalArgumentException("it is not a valid serialized form of signature");
        }
        byte curveType = decodeCurveType(bytes[0]);
        return new Signature(BlsSchema.forPairing(curveType).signatureGroup().elementFromBytes(bytes));
    }

    public static PrivateKey deserializePrivateKey(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be null or empty");
        }
        if (bytes.length < PrivateKey.MIN) {
            throw new IllegalArgumentException("it is not a valid serialized form of PrivateKey");
        }
        if (decodeType(bytes[0]) != PrivateKey.TYPE) {
            throw new IllegalArgumentException("it is not a valid serialized form of PrivateKey");
        }
        byte curveType = decodeCurveType(bytes[0]);

        return new PrivateKey(BlsSchema.forPairing(curveType).pairing.getField().elementFromBytes(bytes));
    }

    public static PublicKey deserializePublicKey(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Bytes cannot be null or empty");
        }
        if (bytes.length < PublicKey.MIN) {
            throw new IllegalArgumentException("it is not a valid serialized form of PublicKey");
        }
        if (decodeType(bytes[0]) != PublicKey.TYPE) {
            throw new IllegalArgumentException("it is not a valid serialized form of PublicKey");
        }
        byte curveType = decodeCurveType(bytes[0]);

        return new PublicKey(BlsSchema.forPairing(curveType).publicKeyGroup().elementFromBytes(bytes));
    }

    // Decoding the character from the upper 2 bits
    private static char decodeType(byte encodedValue) {
        byte charBits = (byte) ((encodedValue >> 6) & 0b11);
        return switch (charBits) {
            case 0b00 -> Signature.TYPE;
            case 0b01 -> PublicKey.TYPE;
            case 0b10 -> PrivateKey.TYPE;
            default -> throw new IllegalArgumentException("Invalid Type encoding");
        };
    }

    // Decoding the curve type from the lower 6 bits
    public static byte decodeCurveType(byte encodedValue) {
        return (byte) (encodedValue & 0b00111111);
    }

    // Encoding the character
    private static byte encodeType(char c) {
        return switch (c) {
            case Signature.TYPE -> 0b00;
            case PublicKey.TYPE -> 0b01;
            case PrivateKey.TYPE -> 0b10;
            default -> throw new IllegalArgumentException("Invalid Type encoding");
        };
    }

    // Encoding the curve type (assuming the curve type is an integer from 0 to 63)
    private static byte encodeCurveType(byte curveType) {
        if (curveType < 0 || curveType > 63) {
            throw new IllegalArgumentException("Curve type must be between 0 and 63");
        }
        return (byte) (curveType & 0b00111111); // Ensure only the lower 6 bits are used
    }

    // Combining both encoded values into a single byte
    private static byte combineEncodedValues(byte charEncoded, byte curveTypeEncoded) {
        return (byte) ((charEncoded << 6) | curveTypeEncoded);
    }
}
