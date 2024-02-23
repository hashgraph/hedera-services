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
