/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.crypto;

import static com.swirlds.common.test.crypto.EcdsaUtils.asRawEcdsaSecp256k1Key;
import static com.swirlds.common.test.crypto.EcdsaUtils.signDigestWithEcdsaSecp256k1;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.interfaces.ECPublicKey;

public class ECDSASigningProvider implements SigningProvider {
    /* Used to share a generated keypair between instance methods */
    private KeyPair activeKp;
    /**
     * indicates whether there is an available algorithm implementation & keypair
     */
    private boolean algorithmAvailable = false;

    public ECDSASigningProvider() {
        generateActiveKeyPair();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] sign(byte[] msg) throws SignatureException {
        return signDigestWithEcdsaSecp256k1(activeKp.getPrivate(), msg);
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
}
