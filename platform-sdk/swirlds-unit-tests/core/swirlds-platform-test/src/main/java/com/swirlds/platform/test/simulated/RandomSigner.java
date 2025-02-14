// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.test.simulated;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.stream.Signer;
import java.util.Random;

/**
 * Creates random signatures with the source provided
 */
public class RandomSigner implements Signer {
    final Random random;

    public RandomSigner(final Random random) {
        this.random = random;
    }

    @Override
    public Signature sign(final byte[] data) {
        final byte[] sig = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(sig);
        return new Signature(SignatureType.RSA, sig);
    }
}
