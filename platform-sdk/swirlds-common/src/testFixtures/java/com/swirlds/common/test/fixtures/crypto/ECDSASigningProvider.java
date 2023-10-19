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

package com.swirlds.common.test.fixtures.crypto;

import static com.swirlds.common.test.fixtures.crypto.EcdsaUtils.asRawEcdsaSecp256k1Key;
import static com.swirlds.common.test.fixtures.crypto.EcdsaUtils.signDigestWithEcdsaSecp256k1;

import java.security.*;
import java.security.interfaces.ECPublicKey;

public class ECDSASigningProvider implements SigningProvider {
    /* Used to share a generated keypair between instance methods */
    private KeyPair activeKp;
    /**
     * indicates whether there is an available algorithm implementation & keypair
     */
    private boolean algorithmAvailable = false;

    /**
     * An instance of the Keccak digest algorithm used to hash the message to be signed.
     */
    private final MessageDigest keccakDigest;

    public ECDSASigningProvider() {
        generateActiveKeyPair();
        this.keccakDigest = createKeccakDigest();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] sign(byte[] msg) throws SignatureException {
        return signDigestWithEcdsaSecp256k1(activeKp.getPrivate(), keccak256(msg));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getPublicKeyBytes() {
        return asRawEcdsaSecp256k1Key((ECPublicKey) activeKp.getPublic());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSignatureLength() {
        return EcdsaUtils.SIGNATURE_LENGTH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getPrivateKeyBytes() {
        return activeKp.getPublic().getEncoded();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isAlgorithmAvailable() {
        return algorithmAvailable;
    }

    /**
     * Generate an ECDSASecp256K1 keypair
     */
    void generateActiveKeyPair() {
        try {
            activeKp = EcdsaUtils.genEcdsaSecp256k1KeyPair();
            algorithmAvailable = true;
        } catch (final NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchProviderException fatal) {
            throw new IllegalStateException("Tests cannot be trusted without working key-pair generation", fatal);
        }
    }

    private MessageDigest createKeccakDigest() {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("KECCAK-256");
        } catch (NoSuchAlgorithmException ignored) {
            try {
                digest = MessageDigest.getInstance("SHA3-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }

        return digest;
    }

    private byte[] keccak256(final byte[] bytes) {
        keccakDigest.reset();
        keccakDigest.update(bytes);
        return keccakDigest.digest();
    }
}
