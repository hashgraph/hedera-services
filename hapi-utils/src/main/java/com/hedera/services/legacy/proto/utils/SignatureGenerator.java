package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API
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

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.apache.commons.codec.binary.Hex;

import java.security.InvalidKeyException;
import java.security.PrivateKey;
import java.security.SignatureException;

public class SignatureGenerator {
  /**
   * Signs a message with a private key.
   *
   * @param msgBytes to be signed
   * @param priv private key
   * @return signature in hex format
   * @throws InvalidKeyException if the key is invalid
   * @throws SignatureException if there is an error in the signature
   */
  public static String signBytes(byte[] msgBytes, PrivateKey priv) throws InvalidKeyException, SignatureException {
    byte[] sigBytes = null;
    if (priv instanceof EdDSAPrivateKey) {
      EdDSAEngine engine = new EdDSAEngine();
      engine.initSign(priv);
      sigBytes = engine.signOneShot(msgBytes);
    } else {
      throw new IllegalArgumentException("Only Ed25519 signatures are supported at this time!");
    }

    String sigHex = Hex.encodeHexString(sigBytes);
    return sigHex;
  }

}
