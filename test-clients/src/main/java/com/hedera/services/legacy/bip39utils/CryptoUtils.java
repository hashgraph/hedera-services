package com.hedera.services.legacy.bip39utils;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import org.spongycastle.asn1.pkcs.PBKDF2Params;
import org.spongycastle.crypto.digests.SHA512Digest;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class CryptoUtils {

    public static byte[] getSecureRandomData(int length){
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return bytes;
    }

    public static byte[] sha256Digest(byte[] message) {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        byte[] hash = digest.digest(message);
        return hash;
    }

    public static byte[] deriveKey( byte[] seed, long index, int length) {
        byte[] password = new byte[seed.length + Long.BYTES];
        for (int i = 0; i < seed.length; i++) {
            password[i] = seed[i];
        }
        byte[] indexData = longToBytes(index);
        int c = 0;
        for (int i = indexData.length-1; i >=0 ; i--) {
            password[seed.length + c] = indexData[i];
            c++;
        }

        byte[] salt = new byte[]{-1};;

        String passwordStr = Hex.toHexString(password);
        PBKDF2Params params = new PBKDF2Params(salt,2048, length*8);

        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA512Digest());
        gen.init(password, params.getSalt(), params.getIterationCount().intValue());

        byte[] derivedKey = ((KeyParameter)gen.generateDerivedParameters(length*8)).getKey();

        return derivedKey;
    }

    public static byte[] longToBytes(long x) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        return buffer.array();
    }
}
