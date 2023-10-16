/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.crypto;

import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.stream.HashSigner;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.system.PlatformConstructionException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;

/**
 * An instance capable of signing data with the platforms private signing key. This class is not thread safe.
 */
public class PlatformSigner implements Signer, HashSigner {
    private final Signature signature;

    /**
     * @param keysAndCerts
     * 		the platform's keys and certificates
     */
    public PlatformSigner(final KeysAndCerts keysAndCerts) {
        try {
            Signature s = Signature.getInstance(CryptoConstants.SIG_TYPE2, CryptoConstants.SIG_PROVIDER);
            s.initSign(keysAndCerts.sigKeyPair().getPrivate());
            this.signature = s;
        } catch (final NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException e) {
            throw new PlatformConstructionException(e);
        }
    }

    @Override
    public com.swirlds.common.crypto.Signature sign(final byte[] data) {
        try {
            signature.update(data);
            return new com.swirlds.common.crypto.Signature(SignatureType.RSA, signature.sign());
        } catch (SignatureException e) {
            // this can only occur if this signature object is not initialized properly, which we ensure is done in the
            // constructor. so this can never happen
            throw new CryptographyException(e);
        }
    }

    @Override
    public com.swirlds.common.crypto.Signature sign(final Hash hash) {
        CommonUtils.throwArgNull(hash, "hash");
        return sign(hash.getValue());
    }
}
