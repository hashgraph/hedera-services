/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto.engine;

import static com.swirlds.common.crypto.SignatureType.ECDSA_SECP256K1;
import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.logging.LogMarker.TESTING_EXCEPTIONS;

import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.logging.LogMarker;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.InvalidParameterSpecException;
import java.util.Arrays;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

/**
 * Verifies signatures created with a ECDSA(secp256k1) private key.
 */
public class EcdsaSecp256k1Verifier {
    public static final int EC_COORD_SIZE = 32;
    private static final Logger logger = LogManager.getLogger(EcdsaSecp256k1Verifier.class);
    private static final byte SIGN_MASK = (byte) 0x80;
    private static final byte ASN1_INTEGER_TAG = (byte) 0x02;
    private static final byte ASN1_SEQUENCE_TAG = (byte) 0x30;
    private static final byte[] ZERO = {(byte) 0x00};
    private static final int TWO = 2;
    private static final int THREE = 3;
    private static final int FOUR = 4;
    private static final int FIVE = 5;
    private static final int SIX = 6;
    private final KeyFactory ecKeyFactory;
    private final ECParameterSpec secp256k1Params;
    private final Signature algorithm;

    public EcdsaSecp256k1Verifier() {
        try {
            ecKeyFactory = KeyFactory.getInstance(ECDSA_SECP256K1.keyAlgorithm());
            final AlgorithmParameters params = AlgorithmParameters.getInstance(ECDSA_SECP256K1.keyAlgorithm());
            params.init(new ECGenParameterSpec(ECDSA_SECP256K1.ellipticalCurve()));
            secp256k1Params = params.getParameterSpec(ECParameterSpec.class);
            algorithm = Signature.getInstance(ECDSA_SECP256K1.signingAlgorithm(), ECDSA_SECP256K1.provider());
        } catch (InvalidParameterSpecException | NoSuchAlgorithmException | NoSuchProviderException fatal) {
            throw new CryptographyException(fatal, LogMarker.ERROR);
        }
    }

    /**
     * Given a "raw" ECDSA signature as the concatenation of its {@code (r, s)} points as unsigned big-endians,
     * returns the ASN.1 DER encoding of the same signature.
     *
     * @param rawEcdsaSig
     * 		the raw ECDSA signature
     * @return the ASN.1 DER encoding of the signature
     */
    public static byte[] asn1DerEncode(final byte[] rawEcdsaSig) {
        final byte[] r = minifiedPositiveBigEndian(Arrays.copyOfRange(rawEcdsaSig, 0, EC_COORD_SIZE));
        final byte[] s = minifiedPositiveBigEndian(Arrays.copyOfRange(rawEcdsaSig, EC_COORD_SIZE, rawEcdsaSig.length));

        final int len = r.length + s.length + 6;

        final byte[] derSig = new byte[len];
        derSig[0] = ASN1_SEQUENCE_TAG;
        derSig[1] = (byte) (len - TWO);

        derSig[TWO] = ASN1_INTEGER_TAG;
        derSig[THREE] = (byte) r.length;
        System.arraycopy(r, 0, derSig, FOUR, r.length);

        derSig[FOUR + r.length] = ASN1_INTEGER_TAG;
        derSig[FIVE + r.length] = (byte) s.length;
        System.arraycopy(s, 0, derSig, SIX + r.length, s.length);

        return derSig;
    }

    /**
     * Given a positive integer value as an <i>unsigned</i> big-endian byte array, returns the minimal-sized
     * <i>signed</i> big-endian byte array for that value.
     *
     * @param v
     * 		a positive integer encoded in unsigned big-endian
     * @return the minimal-sized signed big-endian encoding of the input
     */
    public static byte[] minifiedPositiveBigEndian(final byte[] v) {
        int firstNonZero = 0;
        while (firstNonZero < v.length && v[firstNonZero] == 0) {
            firstNonZero++;
        }
        if (firstNonZero == v.length) {
            return ZERO;
        }
        byte[] result;
        if ((v[firstNonZero] & SIGN_MASK) == 0) {
            if (firstNonZero == 0) {
                result = v;
            } else {
                result = Arrays.copyOfRange(v, firstNonZero, v.length);
            }
        } else {
            final byte[] minified = new byte[1 + v.length - firstNonZero];
            System.arraycopy(v, firstNonZero, minified, 1, v.length - firstNonZero);
            result = minified;
        }
        return result;
    }

    // Thread local caches to avoid allocating memory for each verification. They will leak memory for each thread used
    // for verification but only just over 100 bytes to totally worth it.
    private static final ThreadLocal<LibSecp256k1.secp256k1_ecdsa_signature> SIGNATURE_CACHE =
            ThreadLocal.withInitial(LibSecp256k1.secp256k1_ecdsa_signature::new);
    private static final ThreadLocal<LibSecp256k1.secp256k1_pubkey> PUB_KEY_CACHE =
            ThreadLocal.withInitial(LibSecp256k1.secp256k1_pubkey::new);
    private static final ThreadLocal<byte[]> PUBLIC_KEY_INPUT_CACHE =
            ThreadLocal.withInitial(() -> {
                byte[] publicKeyInput = new byte[65];
                publicKeyInput[0] = 0x04;
                return publicKeyInput;
            });

    /**
     * Verifies a ECDSA(secp256k1) signature of a message is valid for a given public key.
     *
     * The public key must be 64 bytes where the first 32 bytes are the x-coordinate
     * of the public key, and the second 32 bytes are the y-coordinate of the public key.
     *
     * The signature must be 64 bytes where the first 32 bytes are the {code r} value of
     * the signature and the second 32 bytes are the {@code s} value of the signature.
     *
     * All encodings are to be unsigned big-endian.
     *
     * @param rawSig
     * 		the (r, s) signature to be verified
     * @param msg
     * 		the original message that was signed
     * @param pubKey
     * 		the public key to use to verify the signature
     * @return true if the signature is valid
     */
    public boolean verify(final byte[] rawSig, final byte[] msg, final byte[] pubKey) {
        // convert signature to native format
        final LibSecp256k1.secp256k1_ecdsa_signature nativeSignature = SIGNATURE_CACHE.get();
        final var signatureParseResult = LibSecp256k1.secp256k1_ecdsa_signature_parse_compact(
                LibSecp256k1.CONTEXT, nativeSignature, rawSig);
        if (signatureParseResult != 1) {
            logger.debug(
                    TESTING_EXCEPTIONS.getMarker(),
                    () -> "Failed to parse signature [ publicKey = %s, rawSig = %s ]"
                            .formatted(hex(pubKey), hex(rawSig)));
            return false;
        }
        // Normalize the signature to lower-S form. This will return 1 if the signature was normalized, 0 otherwise.
        LibSecp256k1.secp256k1_ecdsa_signature_normalize(LibSecp256k1.CONTEXT, nativeSignature, nativeSignature);
        // convert public key to input format
        final byte[] publicKeyInput = PUBLIC_KEY_INPUT_CACHE.get();
        System.arraycopy(pubKey,0, publicKeyInput, 1, 64);
        // convert public key to native format
        final LibSecp256k1.secp256k1_pubkey pubkey = PUB_KEY_CACHE.get();
        final int keyParseResult = LibSecp256k1.secp256k1_ec_pubkey_parse(
                LibSecp256k1.CONTEXT,
                pubkey,
                publicKeyInput,
                publicKeyInput.length);
        if (keyParseResult != 1) throw new RuntimeException("Failed to parse public key");
        // verify signature
        final int result = LibSecp256k1.secp256k1_ecdsa_verify(
                LibSecp256k1.CONTEXT,
                nativeSignature,
                msg,
                pubkey);
        return result == 1;
    }

    private ECPublicKey asCryptographic(final byte[] pubKey) throws InvalidKeySpecException {
        final BigInteger xCoord = new BigInteger(1, Arrays.copyOfRange(pubKey, 0, EC_COORD_SIZE));
        final BigInteger yCoord = new BigInteger(1, Arrays.copyOfRange(pubKey, EC_COORD_SIZE, pubKey.length));

        final ECPoint curvePoint = new ECPoint(xCoord, yCoord);
        final ECPublicKeySpec ecPublicKeySpec = new ECPublicKeySpec(curvePoint, secp256k1Params);
        return (ECPublicKey) ecKeyFactory.generatePublic(ecPublicKeySpec);
    }
}
