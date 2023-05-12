/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import java.security.Security;
import java.time.Instant;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public final class CryptoConstants {
    // number of bytes in a hash
    public static final int HASH_SIZE_BYTES = 48; // 384 bits (= 3*128)
    // size (in bits) of a public or private key
    public static final int SIG_KEY_SIZE_BITS = 3072;
    public static final int ENC_KEY_SIZE_BITS = 384; // 3*128 bits
    public static final int AGR_KEY_SIZE_BITS = 384; // 3*128 bits
    // max number of bytes in a signature
    // this might be as high as 16+2*ceiling(KEY_SIZE_BITS/8), but is 8 less than that here
    public static final int SIG_SIZE_BYTES = 384;
    // size of each symmetric key, in bytes
    public static final int SYM_KEY_SIZE_BYTES = 32; // 256 bits
    // the algorithms and providers to use (AGR is key agreement, ENC is encryption, SIG is signatures)
    public static final String AGR_TYPE = "EC";
    public static final String AGR_PROVIDER = "SunEC";
    public static final String ENC_TYPE = "EC";
    public static final String ENC_PROVIDER = "SunEC";
    public static final String SIG_TYPE1 = "RSA"; // or SHA384withRSA
    public static final String SIG_PROVIDER = getBCProviderName();
    public static final String SUN_RSA_SIGN_PROVIDER = "SunRsaSign";
    public static final String SIG_TYPE2 = "SHA384withRSA"; // or RSA
    /** this is the only TLS protocol we will allow */
    public static final String TLS_SUITE = "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384";
    // certificate settings
    public static final Instant DEFAULT_VALID_FROM = Instant.parse("2000-01-01T00:00:00Z");
    public static final Instant DEFAULT_VALID_TO = Instant.parse("2100-01-01T00:00:00Z");
    // SSL settings
    public static final String KEY_MANAGER_FACTORY_TYPE = "SunX509"; // recommended by FIPS
    public static final String TRUST_MANAGER_FACTORY_TYPE = "SunX509"; // recommended by FIPS
    public static final String SSL_VERSION = "TLSv1.2";
    // keystore settings
    public static final String KEYSTORE_TYPE = "pkcs12";
    public static final String PUBLIC_KEYS_FILE = "public.pfx";

    private CryptoConstants() {}

    /* Ensure BouncyCastle provider is added before the name is used */
    private static String getBCProviderName() {
        Security.addProvider(new BouncyCastleProvider());
        return BouncyCastleProvider.PROVIDER_NAME;
    }
}
