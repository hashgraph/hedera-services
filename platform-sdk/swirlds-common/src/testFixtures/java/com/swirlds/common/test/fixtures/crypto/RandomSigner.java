package com.swirlds.common.test.fixtures.crypto;

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.stream.Signer;

import java.util.Random;

public class RandomSigner implements Signer {
    private final Random random;

    public RandomSigner(final Random random) {
        this.random = random;
    }

    @Override
    public Signature sign(byte[] data) {
        final byte[] s = new byte[SignatureType.RSA.signatureLength()];
        random.nextBytes(s);
        return new Signature(SignatureType.RSA, s);
    }
}
