// SPDX-License-Identifier: Apache-2.0
package com.swirlds.common.test.fixtures.crypto;

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
