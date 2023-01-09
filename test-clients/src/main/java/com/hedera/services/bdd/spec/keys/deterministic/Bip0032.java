/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.spec.keys.deterministic;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;

public class Bip0032 {
    private static final int KEY_SIZE = 512;
    private static final int ITERATIONS = 2048;
    private static final String ALGORITHM = "HmacSHA512";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int[] INDICES = {44, 3030, 0, 0, 0};
    private static final byte[] MAC_PASSWORD = "ed25519 seed".getBytes(Charset.forName("UTF-8"));

    public static byte[] seedFrom(String mnemonic) {
        var salt = "mnemonic";
        var gen = new PKCS5S2ParametersGenerator(new SHA512Digest());
        gen.init(mnemonic.getBytes(UTF_8), salt.getBytes(UTF_8), ITERATIONS);
        return ((KeyParameter) gen.generateDerivedParameters(KEY_SIZE)).getKey();
    }

    public static byte[] privateKeyFrom(byte[] seed)
            throws NoSuchAlgorithmException, InvalidKeyException, ShortBufferException {
        final byte[] buf = new byte[64];
        final Mac mac = Mac.getInstance(ALGORITHM);

        mac.init(new SecretKeySpec(MAC_PASSWORD, ALGORITHM));
        mac.update(seed);
        mac.doFinal(buf, 0);

        for (int i : INDICES) {
            mac.init(new SecretKeySpec(buf, 32, 32, ALGORITHM));
            mac.update((byte) 0x00);
            mac.update(buf, 0, 32);
            mac.update((byte) (i >> 24 | 0x80));
            mac.update((byte) (i >> 16));
            mac.update((byte) (i >> 8));
            mac.update((byte) i);
            mac.doFinal(buf, 0);
        }
        return Arrays.copyOfRange(buf, 0, 32);
    }
}
