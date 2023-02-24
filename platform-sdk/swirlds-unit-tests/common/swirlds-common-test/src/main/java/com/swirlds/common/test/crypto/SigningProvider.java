/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import java.security.SignatureException;

public interface SigningProvider {
    /**
     * Signs a message and returns its signgature
     * @param msg
     * 		raw byte array of message
     * @return
     * 		raw byte array of signature
     * @throws SignatureException
     */
    byte[] sign(final byte[] msg) throws SignatureException;

    /**
     * Return the public key used for signature
     * @return
     * 		raw byte array of signature of public key
     */
    byte[] getPublicKeyBytes();

    /**
     * return number of bytes in signature
     */
    int getSignatureLength();

    /**
     * Return the private key used for signature
     * @return
     * 		raw byte array of signature of private key
     */
    byte[] getPrivateKeyBytes();

    boolean isAlgorithmAvailable();
}
