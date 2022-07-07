/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.legacy.core;

import com.swirlds.common.utility.CommonUtils;
import java.io.Serializable;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import net.i2p.crypto.eddsa.EdDSAPublicKey;

public class KeyPairObj implements Serializable {
    private static final long serialVersionUID = 9146375644904969927L;
    private String publicKey;

    public KeyPairObj(final String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPublicKeyAbyteStr() throws InvalidKeySpecException {
        return CommonUtils.hex(((EdDSAPublicKey) getPublicKey()).getAbyte());
    }

    private PublicKey getPublicKey() throws IllegalArgumentException, InvalidKeySpecException {
        final var pubKeybytes = CommonUtils.unhex(publicKey);
        final var pencoded = new X509EncodedKeySpec(pubKeybytes);
        return new EdDSAPublicKey(pencoded);
    }
}
